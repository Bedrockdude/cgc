package cgc.cgc.module.impl.dungeon.autopuzzles.waterboard

import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleAimController
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleAimProfile
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleController
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleEtherwarp
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInputOwner
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInputSession
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInteraction
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleItems
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleRoomCoordinates
import cgc.cgc.module.impl.dungeon.autopuzzles.ProjectileAimPlanner
import cgc.cgc.runtime.CgcRenderer3D
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.ItemUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.Locale

class WaterboardController(private val settings: WaterboardSubModule) : AutoPuzzleController {
	override val roomNames = setOf("Water Board")

	private enum class State {
		RECOGNIZING,
		PREPARE_ACTION,
		ROTATE_WALK,
		WALKING,
		AIM_WARP,
		WAIT_WARP,
		AIM_LEVER,
		WAIT_DUE,
		WAIT_ACK,
		FALL_DELAY,
		FALL_AIM,
		FALL_WAIT,
		DONE
	}

	private data class Action(
		val scheduled: WaterboardSolver.ScheduledAction,
		val leverPos: BlockPos,
		val approach: Vec3,
		val etherwarpSupport: BlockPos,
		val expectedPowered: Boolean
	)

	private data class Recognition(val pattern: Int, val gates: Set<Int>)

	private var state = State.RECOGNIZING
	private var lockedRun = Long.MIN_VALUE
	private var runSequence = 0L
	private var roomSignature: String? = null
	private var stableRecognition: Recognition? = null
	private var stableTicks = 0
	private var actions = emptyList<Action>()
	private var actionIndex = 0
	private var waterZeroAtMs: Long? = null
	private var waterClickAtMs: Long? = null
	private var nextActionAtMs = 0L
	private var lease: AutoPuzzleInputOwner.Lease? = null
	private var inputSession: AutoPuzzleInputSession? = null
	private val aim = AutoPuzzleAimController()
	private var etherwarpSlot = -1
	private var clickSlot = -1
	private var warpTarget: AutoPuzzleEtherwarp.Target? = null
	private var warpAttempts = 0
	private var stateAtMs = 0L
	private var aimDeadlineMs = 0L
	private var clickRetries = 0
	private var clickRayRetries = 0
	private var currentClickAcknowledged = false
	private var lateAcknowledgedLeverPos: BlockPos? = null
	private var lateAcknowledgedUntilMs = 0L
	private var walkTarget: Vec3? = null
	private var walkClosestDistance = Double.POSITIVE_INFINITY
	private var interferenceReason: String? = null
	private var leverPositions = emptyMap<BlockPos, WaterLever>()
	private var gatePositions = emptySet<BlockPos>()
	private var initiallyClosedGatePositions = emptySet<BlockPos>()
	private val openedGatePositions = hashSetOf<BlockPos>()
	private var currentRenderPos: Vec3? = null
	private var currentRenderText: String? = null

	override fun tickStart(client: Minecraft) {
		val session = inputSession ?: return
		val reason = session.cancellationReason(AutoPuzzleContext.monotonicNowMs()) ?: return
		stop("cancelled by $reason", terminal = true)
	}

	override fun tick(context: AutoPuzzleContext) {
		updateCountdown(context.nowMs)
		if (lockedRun == context.runSequence || state == State.DONE) return
		if (!context.dungeonStarted || context.runSequence <= 0L) return
		if (roomSignature != null && roomSignature != context.roomSignature) {
			stop("room orientation changed", terminal = true)
			return
		}
		if (state != State.RECOGNIZING && (!context.player.isAlive || context.client.screen != null)) {
			stop(if (!context.player.isAlive) "the player is unavailable" else "a screen was opened", terminal = true)
			return
		}
		interferenceReason?.let {
			interferenceReason = null
			stop(it, terminal = true)
			return
		}
		if (state != State.RECOGNIZING && !activeRecognitionValid(context)) {
			stop("the recognized Waterboard gates changed unexpectedly", terminal = true)
			return
		}

		if (state != State.RECOGNIZING && state !in setOf(State.FALL_DELAY, State.FALL_AIM, State.FALL_WAIT) && context.player.y < FALL_Y) {
			beginFall(context)
			return
		}

		when (state) {
			State.RECOGNIZING -> tickRecognition(context)
			State.PREPARE_ACTION -> tickPrepareAction(context)
			State.ROTATE_WALK -> tickRotateWalk(context)
			State.WALKING -> tickWalking(context)
			State.AIM_WARP -> tickAimWarp(context)
			State.WAIT_WARP -> tickWaitWarp(context)
			State.AIM_LEVER -> tickAimLever(context)
			State.WAIT_DUE -> tickWaitDue(context)
			State.WAIT_ACK -> tickWaitAck(context)
			State.FALL_DELAY -> tickFallDelay(context)
			State.FALL_AIM -> tickFallAim(context)
			State.FALL_WAIT -> tickFallWait(context)
			State.DONE -> Unit
		}
	}

	private fun tickRecognition(context: AutoPuzzleContext) {
		val recognition = recognize(context) ?: run {
			stableRecognition = null
			stableTicks = 0
			return
		}
		if (stableRecognition != recognition) {
			stableRecognition = recognition
			stableTicks = 1
			return
		}
		if (++stableTicks < RECOGNITION_STABLE_TICKS) return
		if (cgc.cgc.runtime.PhysicalInputTracker.hasHeldKeyOrButton()) return

		val warp = AutoPuzzleItems.firstEtherwarp(context)
			?: return lockBeforeLease(context.runSequence, "no Etherwarp-capable item is in the hotbar")
		val click = AutoPuzzleItems.firstWaterboardClickItem(context)
			?: return lockBeforeLease(context.runSequence, "no Dungeon Breaker is in the hotbar")
		val solution = WaterboardSolver.solve(recognition.pattern, recognition.gates)
			?: return lockBeforeLease(context.runSequence, "the recognized Waterboard solution is missing")
		val built = buildActions(context, solution)
		if (built.isEmpty()) return lockBeforeLease(context.runSequence, "the Waterboard solution contains no actions")
		if (built.any { powered(context, it.leverPos) != false }) {
			return lockBeforeLease(context.runSequence, "the Waterboard levers were already changed")
		}
		val session = AutoPuzzleInputSession.acquire(context.nowMs, requireReleased = true) ?: return
		val acquired = AutoPuzzleInputOwner.acquire("Waterboard", context.client) ?: return
		lease = acquired
		inputSession = session
		etherwarpSlot = warp
		clickSlot = click
		actions = built
		actionIndex = 0
		runSequence = context.runSequence
		roomSignature = context.roomSignature
		leverPositions = built.associate { it.leverPos to it.scheduled.lever }
		gatePositions = (0..4).mapTo(hashSetOf()) { AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 56, 4 - it) }
		initiallyClosedGatePositions = recognition.gates.mapTo(hashSetOf()) {
			AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 56, 4 - it)
		}
		openedGatePositions.clear()
		state = State.PREPARE_ACTION
		stateAtMs = context.nowMs
		updateCountdown(context.nowMs)
	}

	private fun recognize(context: AutoPuzzleContext): Recognition? {
		val pattern = recognizePattern(context) ?: return null
		val gates = closedGateIndices(context) ?: return null
		return if (gates.size == 3) Recognition(pattern, gates) else null
	}

	private fun recognizePattern(context: AutoPuzzleContext): Int? {
		val required = listOf(
			AutoPuzzleRoomCoordinates.worldBlock(context.room, -1, 77, 12),
			AutoPuzzleRoomCoordinates.worldBlock(context.room, 1, 78, 12),
			AutoPuzzleRoomCoordinates.worldBlock(context.room, -1, 78, 12)
		)
		if (required.any { !context.level.isLoaded(it) }) return null
		return WaterboardSolver.recognizePattern { x, y, z ->
			context.level.getBlockState(AutoPuzzleRoomCoordinates.worldBlock(context.room, x, y, z)).block
		}
	}

	private fun closedGateIndices(context: AutoPuzzleContext): Set<Int>? {
		val positions = (0..4).associateWith { index ->
			AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 56, 4 - index)
		}
		if (positions.values.any { !context.level.isLoaded(it) }) return null
		return positions.filterValues { pos ->
			context.level.getBlockState(pos).`is`(BlockTags.WOOL)
		}.keys
	}

	private fun activeRecognitionValid(context: AutoPuzzleContext): Boolean {
		val recognition = stableRecognition ?: return false
		val currentlyClosedIndices = closedGateIndices(context) ?: return false
		val openedIndices = openedGatePositions.mapNotNullTo(hashSetOf()) { opened ->
			(0..4).firstOrNull { index ->
				AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 56, 4 - index) == opened
			}
		}
		if (!WaterboardSolver.gateSnapshotValid(
			initiallyClosed = recognition.gates,
			currentlyClosed = currentlyClosedIndices,
			previouslyOpened = openedIndices,
			waterStarted = waterFlowExpected()
		)) return false
		if (waterFlowExpected()) {
			for (index in recognition.gates - currentlyClosedIndices) {
				openedGatePositions.add(AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 56, 4 - index))
			}
		}
		return true
	}

	private fun buildActions(context: AutoPuzzleContext, solution: WaterboardSolver.Solution): List<Action> =
		solution.actions.map { scheduled ->
			val lever = scheduled.lever
			val geometry = WaterboardData.geometry.getValue(lever).etherwarpSupport
			Action(
				scheduled = scheduled,
				leverPos = AutoPuzzleRoomCoordinates.legacyWaterBlock(context.room, lever.blockX, lever.blockY, lever.blockZ),
				approach = AutoPuzzleRoomCoordinates.legacyWaterPoint(context.room, lever.approachX, lever.approachY, lever.approachZ),
				etherwarpSupport = BlockPos.containing(
					AutoPuzzleRoomCoordinates.legacyWaterPoint(context.room, geometry.x, geometry.y, geometry.z)
				),
				expectedPowered = scheduled.occurrence % 2 == 0
			)
		}

	private fun tickPrepareAction(context: AutoPuzzleContext) {
		val action = currentAction() ?: return finish()
		if (context.nowMs < nextActionAtMs && !isDue(action, context.nowMs)) return
		if (inLeverRange(context, action)) {
			beginLeverAim(context, action)
			return
		}
		if (action.scheduled.lever != WaterLever.WATER) {
			val walk = WaterboardRoutePlanner.safeStraightWalk(context.level, context.player, action.approach)
			if (walk != null) {
				walkTarget = walk.target
				val look = Vec3(walk.target.x, context.player.eyePosition.y, walk.target.z)
				aim.start(context, look, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.NORMAL)
				state = State.ROTATE_WALK
				stateAtMs = context.nowMs
				return
			}
		}
		beginWarp(context, action, retry = false)
	}

	private fun tickRotateWalk(context: AutoPuzzleContext) {
		val target = walkTarget ?: return stop("the walking target was lost", terminal = true)
		if (!aim.update(context).finished) {
			if (context.nowMs - stateAtMs >= AIM_ACQUISITION_TIMEOUT_MS) {
				stop("could not align the straight walking segment", terminal = true)
			}
			return
		}
		aim.clear()
		if (WaterboardRoutePlanner.safeStraightWalk(context.level, context.player, target) == null) {
			return stop("the straight walking route became unsafe", terminal = true)
		}
		walkClosestDistance = horizontalDistance(context.player.position(), target)
		lease?.press(context.client.options.keyUp)
		stateAtMs = context.nowMs
		state = State.WALKING
	}

	private fun tickWalking(context: AutoPuzzleContext) {
		val target = walkTarget ?: return stop("the walking target was lost", terminal = true)
		val distance = horizontalDistance(context.player.position(), target)
		if (distance <= WALK_ARRIVAL_DISTANCE) {
			lease?.release(context.client.options.keyUp)
			walkTarget = null
			val action = currentAction() ?: return finish()
			if (!inLeverRange(context, action)) return stop("the straight walk did not reach the lever", terminal = true)
			beginLeverAim(context, action)
			return
		}
		if (distance > walkClosestDistance + WALK_DIVERGENCE || context.nowMs - stateAtMs > WALK_TIMEOUT_MS) {
			lease?.release(context.client.options.keyUp)
			return stop("the straight walking route diverged", terminal = true)
		}
		walkClosestDistance = minOf(walkClosestDistance, distance)
	}

	private fun beginWarp(context: AutoPuzzleContext, action: Action, retry: Boolean) {
		if (!AutoPuzzleItems.select(context, etherwarpSlot) || !ItemUtils.isEtherwarp(context.player.inventory.selectedItem)) {
			return stop("the Etherwarp item is unavailable", terminal = true)
		}
		lease?.press(context.client.options.keyShift)
		val result = waterboardWarpTarget(context, action)
		val target = result.getOrElse { return stop(it.message ?: "the Etherwarp target is invalid", terminal = true) }
		warpTarget = target
		if (!retry) warpAttempts = 1
		aim.start(context, target.aimPoint, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.ETHERWARP)
		state = State.AIM_WARP
		stateAtMs = context.nowMs
		aimDeadlineMs = context.nowMs + AIM_ACQUISITION_TIMEOUT_MS
	}

	private fun waterboardWarpTarget(
		context: AutoPuzzleContext,
		action: Action
	): Result<AutoPuzzleEtherwarp.Target> {
		val candidates = WaterboardRoutePlanner.etherwarpSupportCandidates(
			action.etherwarpSupport,
			action.approach,
			action.leverPos,
			LEVER_REACH
		).sortedBy { support ->
			val landing = Vec3(support.x + 0.5, support.y + 1.0, support.z + 0.5)
			context.player.position().distanceTo(landing) * 0.35 + action.approach.distanceTo(landing)
		}
		var preferredFailure: String? = null
		for (support in candidates) {
			val result = AutoPuzzleEtherwarp.target(context, support)
			if (support == action.etherwarpSupport) preferredFailure = result.exceptionOrNull()?.message
			result.getOrNull()?.let { return Result.success(it) }
		}

		val detail = preferredFailure ?: "the preferred support is invalid"
		return Result.failure(IllegalStateException("no visible Waterboard Etherwarp support was available near the saved position ($detail)"))
	}

	private fun tickAimWarp(context: AutoPuzzleContext) {
		val target = warpTarget ?: return stop("the Etherwarp target was lost", terminal = true)
		val update = aim.update(context)
		if (!update.ready) {
			if (context.nowMs >= aimDeadlineMs) stop("could not acquire the Etherwarp target", terminal = true)
			return
		}
		if (!context.player.isShiftKeyDown) return
		if (!AutoPuzzleEtherwarp.liveRayHits(context, target)) {
			if (context.nowMs >= aimDeadlineMs) stop("the live Etherwarp ray no longer reaches its target", terminal = true)
			return
		}
		if (!ItemUtils.isEtherwarp(context.player.inventory.selectedItem) || !AutoPuzzleInteraction.useHeldItem(context)) {
			return stop("could not use Etherwarp", terminal = true)
		}
		aim.clear()
		stateAtMs = context.nowMs
		state = State.WAIT_WARP
	}

	private fun tickWaitWarp(context: AutoPuzzleContext) {
		val target = warpTarget ?: return stop("the Etherwarp landing target was lost", terminal = true)
		val landed = horizontalDistance(context.player.position(), target.expectedFeet) <= WARP_LANDING_HORIZONTAL &&
			kotlin.math.abs(context.player.y - target.expectedFeet.y) <= WARP_LANDING_Y
		if (landed) {
			lease?.release(context.client.options.keyShift)
			warpTarget = null
			val action = currentAction() ?: return finish()
			if (!inLeverRange(context, action)) return stop("Etherwarp landed outside lever reach", terminal = true)
			beginLeverAim(context, action)
			return
		}
		val elapsed = context.nowMs - stateAtMs
		if (elapsed < WARP_LAND_TIMEOUT_MS) return
		if (waterZeroAtMs == null && warpAttempts < 2) {
			if (!safelySupported(context)) {
				stop("the missed pre-water Etherwarp did not leave the player safely supported", terminal = true)
				return
			}
			if (elapsed < WARP_LAND_TIMEOUT_MS + WARP_RETRY_DELAY_MS) return
			warpAttempts++
			beginWarp(context, currentAction() ?: return finish(), retry = true)
			return
		}
		stop(if (waterZeroAtMs == null) "Etherwarp missed twice before water was opened" else "Etherwarp missed after water was opened", terminal = true)
	}

	private fun beginLeverAim(context: AutoPuzzleContext, action: Action) {
		lease?.release(context.client.options.keyShift)
		val result = ProjectileAimPlanner.directBlock(context, action.leverPos)
		val point = (result as? ProjectileAimPlanner.Result.Safe)?.point
			?: return stop((result as ProjectileAimPlanner.Result.Unsafe).reason, terminal = true)
		aim.start(context, point, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.NORMAL)
		state = State.AIM_LEVER
		stateAtMs = context.nowMs
		aimDeadlineMs = context.nowMs + AIM_ACQUISITION_TIMEOUT_MS
	}

	private fun tickAimLever(context: AutoPuzzleContext) {
		val action = currentAction() ?: return finish()
		if (!inLeverRange(context, action)) return stop("the current lever is out of vanilla reach", terminal = true)
		val result = aim.update(context)
		if (!result.finished) {
			if (context.nowMs >= aimDeadlineMs) stop("could not aim at the current lever", terminal = true)
			return
		}
		if (!AutoPuzzleItems.select(context, clickSlot) || !isClickItem(context)) {
			return stop("the Waterboard click item is unavailable", terminal = true)
		}
		state = State.WAIT_DUE
		stateAtMs = context.nowMs
	}

	private fun tickWaitDue(context: AutoPuzzleContext) {
		val action = currentAction() ?: return finish()
		if (!isClickItem(context)) return stop("the Waterboard click item is unavailable", terminal = true)
		if (!isDue(action, context.nowMs)) return
		val currentPowered = powered(context, action.leverPos)
		if (currentPowered == null) return stop("the expected lever is missing", terminal = true)
		if (currentPowered == action.expectedPowered) {
			if (clickRetries > 0) {
				acknowledgeCurrentAction(context)
				return
			}
			return stop("the current lever changed before its scheduled click", terminal = true)
		}
		state = State.WAIT_ACK
		stateAtMs = context.nowMs
		currentClickAcknowledged = false
		if (!AutoPuzzleInteraction.clickLever(context, action.leverPos, LEVER_REACH)) {
			if (clickRayRetries >= MAX_CLICK_RAY_RETRIES) {
				return stop("could not dispatch a verified click to the current lever", terminal = true)
			}
			clickRayRetries++
			beginLeverAim(context, action)
			return
		}
		if (action.scheduled.lever == WaterLever.WATER && waterZeroAtMs == null && waterClickAtMs == null) {
			waterClickAtMs = context.nowMs
		}
	}

	private fun tickWaitAck(context: AutoPuzzleContext) {
		val action = currentAction() ?: return finish()
		val current = powered(context, action.leverPos)
		if (currentClickAcknowledged || current == action.expectedPowered) {
			acknowledgeCurrentAction(context)
			return
		}
		if (context.nowMs - stateAtMs < LEVER_CONFIRM_TIMEOUT_MS) return
		if (clickRetries >= 1) {
			stop("the current lever did not acknowledge either click", terminal = true)
			return
		}
		clickRetries++
		beginLeverAim(context, action)
	}

	private fun beginFall(context: AutoPuzzleContext) {
		lease?.releaseMovement()
		lease?.release(context.client.options.keyShift)
		aim.clear()
		state = State.FALL_DELAY
		stateAtMs = context.nowMs
		warpTarget = null
	}

	private fun tickFallDelay(context: AutoPuzzleContext) {
		if (context.nowMs - stateAtMs < FALL_RESCUE_DELAY_MS) return
		if (!AutoPuzzleItems.select(context, etherwarpSlot) || !ItemUtils.isEtherwarp(context.player.inventory.selectedItem)) {
			return stop("fell below the Waterboard floor and the Etherwarp item is unavailable", terminal = true)
		}
		lease?.press(context.client.options.keyShift)
		val supportPoint = AutoPuzzleRoomCoordinates.worldPoint(context.room, 0.5, 59.0, -0.5)
		val result = AutoPuzzleEtherwarp.target(context, BlockPos.containing(supportPoint))
		val target = result.getOrElse {
			return stop("fell below the Waterboard floor and the rescue target is invalid", terminal = true)
		}
		warpTarget = target
		aim.start(context, target.aimPoint, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.ETHERWARP)
		state = State.FALL_AIM
		aimDeadlineMs = context.nowMs + AIM_ACQUISITION_TIMEOUT_MS
	}

	private fun tickFallAim(context: AutoPuzzleContext) {
		val target = warpTarget ?: return stop("the fall-rescue target was lost", terminal = true)
		val update = aim.update(context)
		if (!update.ready) {
			if (context.nowMs >= aimDeadlineMs) stop("could not aim at the fall-rescue target", terminal = true)
			return
		}
		if (!context.player.isShiftKeyDown) return
		if (!AutoPuzzleEtherwarp.liveRayHits(context, target) || !AutoPuzzleInteraction.useHeldItem(context)) {
			return stop("could not use the fall-rescue Etherwarp", terminal = true)
		}
		aim.clear()
		state = State.FALL_WAIT
		stateAtMs = context.nowMs
	}

	private fun tickFallWait(context: AutoPuzzleContext) {
		val target = warpTarget ?: return stop("the fall-rescue landing target was lost", terminal = true)
		val landed = context.player.y >= FALL_Y && horizontalDistance(context.player.position(), target.expectedFeet) <= WARP_LANDING_HORIZONTAL
		if (landed) {
			stop("a fall was rescued; automation will not resume this run", terminal = true)
			return
		}
		if (context.nowMs - stateAtMs >= FALL_RESCUE_LAND_TIMEOUT_MS) {
			stop("the fall-rescue Etherwarp did not land safely", terminal = true)
		}
	}

	private fun isDue(action: Action, nowMs: Long): Boolean {
		val zero = waterZeroAtMs
		return if (zero == null) {
			action.scheduled.timeSeconds == 0.0
		} else {
			nowMs >= zero + (action.scheduled.timeSeconds * 1_000.0).toLong()
		}
	}

	private fun inLeverRange(context: AutoPuzzleContext, action: Action): Boolean =
		context.player.eyePosition.distanceToSqr(action.leverPos.center) <= LEVER_REACH * LEVER_REACH

	private fun powered(context: AutoPuzzleContext, pos: BlockPos): Boolean? {
		val state = context.level.getBlockState(pos)
		return if (state.block is LeverBlock) state.getValue(LeverBlock.POWERED) else null
	}

	private fun isClickItem(context: AutoPuzzleContext): Boolean {
		val selected = context.player.inventory.selectedItem
		return AutoPuzzleItems.isDungeonBreaker(selected)
	}

	private fun acknowledgeCurrentAction(context: AutoPuzzleContext) {
		val completed = currentAction()
		if (completed != null) {
			lateAcknowledgedLeverPos = completed.leverPos
			lateAcknowledgedUntilMs = context.nowMs + LATE_LEVER_UPDATE_GRACE_MS
			if (completed.scheduled.lever == WaterLever.WATER && waterZeroAtMs == null) {
				waterZeroAtMs = waterClickAtMs ?: context.nowMs
			}
		}
		waterClickAtMs = null
		actionIndex++
		currentClickAcknowledged = false
		clickRetries = 0
		clickRayRetries = 0
		nextActionAtMs = context.nowMs + settings.actionDelay.value.toLong()
		if (actionIndex >= actions.size) finish() else state = State.PREPARE_ACTION
		updateCountdown(context.nowMs)
	}

	private fun safelySupported(context: AutoPuzzleContext): Boolean {
		if (!context.player.onGround()) return false
		return !context.level.noCollision(context.player, context.player.boundingBox.move(0.0, -SUPPORT_CHECK_DEPTH, 0.0))
	}

	private fun waterFlowExpected(): Boolean =
		waterZeroAtMs != null || waterClickAtMs != null

	private fun currentAction(): Action? = actions.getOrNull(actionIndex)

	private fun updateCountdown(nowMs: Long) {
		val action = currentAction()
		if (action == null || state in setOf(State.RECOGNIZING, State.DONE)) {
			currentRenderPos = null
			currentRenderText = null
			return
		}
		currentRenderPos = action.leverPos.center.add(0.0, 1.25, 0.0)
		val zero = waterZeroAtMs
		val remaining = if (zero == null) action.scheduled.timeSeconds else
			(zero + action.scheduled.timeSeconds * 1_000.0 - nowMs) / 1_000.0
		currentRenderText = if (remaining <= 0.0) "CLICK" else String.format(Locale.ROOT, "%.1f", remaining)
	}

	override fun render(context: LevelRenderContext) {
		val pos = currentRenderPos ?: return
		val text = currentRenderText ?: return
		CgcRenderer3D.worldText(text, pos, settings.countdownColor.value, depth = false, scale = 1.35f)
	}

	override fun frame(client: Minecraft) = aim.frameUpdate(client)

	override fun blockChanged(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		if (lease == null) return
		if (pos in gatePositions) {
			val initiallyClosed = pos in initiallyClosedGatePositions
			val wasClosed = oldState?.`is`(BlockTags.WOOL) == true
			val isClosed = newState.`is`(BlockTags.WOOL)
			val expectedOpening = initiallyClosed && wasClosed && !isClosed && waterFlowExpected()
			if (expectedOpening) {
				openedGatePositions.add(pos)
			} else if (wasClosed != isClosed || isClosed && pos in openedGatePositions) {
				interferenceReason = "a Waterboard gate changed outside the programmed action"
			}
			return
		}
		if (pos !in leverPositions) return
		val action = currentAction()
		val newPowered = if (newState.block is LeverBlock) newState.getValue(LeverBlock.POWERED) else null
		if (state == State.WAIT_ACK && action != null && pos == action.leverPos) {
			if (newPowered == action.expectedPowered) currentClickAcknowledged = true
			return
		}
		if (pos == lateAcknowledgedLeverPos && AutoPuzzleContext.monotonicNowMs() <= lateAcknowledgedUntilMs) return
		if (clickRetries > 0 && state in setOf(State.AIM_LEVER, State.WAIT_DUE) && action != null &&
			pos == action.leverPos && newPowered == action.expectedPowered
		) return
		interferenceReason = "a Waterboard lever changed outside the programmed action"
	}

	private fun finish() {
		cleanupInput()
		state = State.DONE
		currentRenderPos = null
		currentRenderText = null
	}

	private fun lockBeforeLease(run: Long, reason: String) {
		lockedRun = run
		runSequence = run
		state = State.RECOGNIZING
		ChatUtils.chat("§c[Auto Puzzles] Waterboard stopped: $reason.")
	}

	override fun stop(reason: String, terminal: Boolean) {
		val wasActive = lease != null || state !in setOf(State.RECOGNIZING, State.DONE)
		if (terminal && wasActive && runSequence > 0L) lockedRun = runSequence
		cleanupInput()
		state = State.RECOGNIZING
		currentRenderPos = null
		currentRenderText = null
		if (wasActive) ChatUtils.chat("§c[Auto Puzzles] Waterboard stopped: $reason.")
	}

	override fun leaveRoom() {
		if (state !in setOf(State.RECOGNIZING, State.DONE)) stop("the Waterboard room was left", terminal = true)
		stableRecognition = null
		stableTicks = 0
		roomSignature = null
		currentRenderPos = null
		currentRenderText = null
	}

	override fun runChanged(runSequence: Long) {
		cleanupInput()
		state = State.RECOGNIZING
		lockedRun = Long.MIN_VALUE
		this.runSequence = runSequence
		roomSignature = null
		stableRecognition = null
		stableTicks = 0
	}

	override fun worldReset() {
		if (lease != null && runSequence > 0L) lockedRun = runSequence
		cleanupInput()
		state = State.RECOGNIZING
		roomSignature = null
		stableRecognition = null
		stableTicks = 0
		currentRenderPos = null
		currentRenderText = null
	}

	private fun cleanupInput() {
		aim.clear()
		lease?.close()
		lease = null
		inputSession = null
		warpTarget = null
		walkTarget = null
		actions = emptyList()
		actionIndex = 0
		waterZeroAtMs = null
		waterClickAtMs = null
		nextActionAtMs = 0L
		leverPositions = emptyMap()
		gatePositions = emptySet()
		initiallyClosedGatePositions = emptySet()
		openedGatePositions.clear()
		interferenceReason = null
		clickRetries = 0
		clickRayRetries = 0
		currentClickAcknowledged = false
		lateAcknowledgedLeverPos = null
		lateAcknowledgedUntilMs = 0L
		etherwarpSlot = -1
		clickSlot = -1
		warpAttempts = 0
	}

	private fun horizontalDistance(first: Vec3, second: Vec3): Double {
		val x = first.x - second.x
		val z = first.z - second.z
		return kotlin.math.sqrt(x * x + z * z)
	}

	private companion object {
		const val RECOGNITION_STABLE_TICKS = 5
		const val LEVER_REACH = 4.5
		const val LEVER_CONFIRM_TIMEOUT_MS = 650L
		const val AIM_ACQUISITION_TIMEOUT_MS = 1_500L
		const val WARP_LAND_TIMEOUT_MS = 900L
		const val WARP_RETRY_DELAY_MS = 250L
		const val WARP_LANDING_HORIZONTAL = 1.25
		const val WARP_LANDING_Y = 0.85
		const val WALK_ARRIVAL_DISTANCE = 0.22
		const val WALK_DIVERGENCE = 0.25
		const val WALK_TIMEOUT_MS = 4_000L
		const val FALL_Y = 59.0
		const val FALL_RESCUE_DELAY_MS = 500L
		const val FALL_RESCUE_LAND_TIMEOUT_MS = 1_000L
		const val SUPPORT_CHECK_DEPTH = 0.08
		const val MAX_CLICK_RAY_RETRIES = 3
		const val LATE_LEVER_UPDATE_GRACE_MS = 1_500L
	}
}
