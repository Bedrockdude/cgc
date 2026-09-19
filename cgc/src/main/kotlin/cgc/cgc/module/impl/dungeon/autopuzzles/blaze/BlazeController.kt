package cgc.cgc.module.impl.dungeon.autopuzzles.blaze

import cgc.cgc.data.Colour
import cgc.cgc.dungeon.DungeonPuzzle
import cgc.cgc.dungeon.DungeonPuzzleState
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleAimController
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleAimProfile
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleController
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInputOwner
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInputSession
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInteraction
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleItems
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleRoomCoordinates
import cgc.cgc.module.impl.dungeon.autopuzzles.ProjectileAimPlanner
import cgc.cgc.runtime.CgcRenderer3D
import cgc.cgc.utils.ChatUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom

class BlazeController(private val settings: BlazeSubModule) : AutoPuzzleController {
	override val roomNames = setOf("Higher Blaze", "Lower Blaze")

	private enum class State {
		WAITING,
		FACE_CENTER,
		EDGE_WALK,
		EDGE_STOP,
		AIM_TARGET,
		SHOT_GAP,
		WAIT_REMOVAL,
		DONE
	}

	private data class RenderTarget(val id: Int, val box: AABB, val center: Vec3)

	private var state = State.WAITING
	private var roomSignature: String? = null
	private var runSequence = 0L
	private var lease: AutoPuzzleInputOwner.Lease? = null
	private var inputSession: AutoPuzzleInputSession? = null
	private val aim = AutoPuzzleAimController()
	private var shortbow: AutoPuzzleItems.Shortbow? = null
	private var currentTargetId: Int? = null
	private var preAimTargetId: Int? = null
	private var aimedPoint: Vec3? = null
	private var baselineIds = emptySet<Int>()
	private var burstAttempt = 0
	private var burstStarted = false
	private var stateAtMs = 0L
	private var nextActionAtMs = 0L
	private var centerAlignedTicks = 0
	private var requiresStepOff = false
	private var observedOffStart = false
	private var lastOnStart = false
	private var renderTargets = emptyList<RenderTarget>()
	private var renderStartBox: AABB? = null

	override fun tickStart(client: Minecraft) {
		val session = inputSession ?: return
		val reason = session.cancellationReason(AutoPuzzleContext.monotonicNowMs()) ?: return
		stop("cancelled by $reason", terminal = true)
	}

	override fun tick(context: AutoPuzzleContext) {
		val reversed = context.room.displayName.equals("Lower Blaze", ignoreCase = true)
		val standingPoint = if (reversed) {
			AutoPuzzleRoomCoordinates.worldPosition(context.room, -6.5, 47.0, 1.5)
		} else {
			AutoPuzzleRoomCoordinates.worldPosition(context.room, -6.5, 97.0, 0.5)
		}
		renderStartBox = AABB(
			standingPoint.x - 0.5,
			standingPoint.y - 1.0,
			standingPoint.z - 0.5,
			standingPoint.x + 0.5,
			standingPoint.y,
			standingPoint.z + 0.5
		)

		if (!BlazeSolver.scanAreaLoaded(context)) {
			renderTargets = emptyList()
			if (state != State.WAITING && state != State.DONE) {
				stop("part of the Blaze room became unloaded", terminal = true)
			}
			return
		}
		val observed = BlazeSolver.observe(context, reversed)
		renderTargets = observed.map { RenderTarget(it.entity.id, it.entity.boundingBox, it.entity.boundingBox.center) }
		when (context.puzzleState(DungeonPuzzle.BLAZE)) {
			DungeonPuzzleState.GREEN -> {
				cleanup()
				state = State.DONE
				return
			}
			DungeonPuzzleState.FAILED -> {
				if (state !in setOf(State.WAITING, State.DONE)) stop("the dungeon puzzle was marked failed", terminal = true)
				return
			}
			else -> Unit
		}
		val onStart = isOnStart(context, standingPoint)
		lastOnStart = onStart
		if (requiresStepOff) {
			if (!onStart) observedOffStart = true
			if (!(onStart && observedOffStart)) return
			requiresStepOff = false
			observedOffStart = false
		}

		if (roomSignature != null && roomSignature != context.roomSignature) {
			stop("room orientation changed", terminal = true)
			return
		}
		if (state != State.WAITING && state != State.DONE && context.runSequence != runSequence) {
			cleanup()
			state = State.WAITING
		}
		if (state != State.WAITING && (!context.player.isAlive || context.client.screen != null)) {
			stop(if (!context.player.isAlive) "the player is unavailable" else "a screen was opened", terminal = true)
			return
		}

		if (state == State.WAITING) {
			if (!context.dungeonStarted || context.runSequence <= 0L || !onStart || observed.isEmpty()) return
			val bow = AutoPuzzleItems.firstShortbow(context) ?: run {
				failWithoutLease("no shortbow is in the hotbar", context.runSequence)
				return
			}
			val session = AutoPuzzleInputSession.acquire(context.nowMs) ?: return
			val acquired = AutoPuzzleInputOwner.acquire("Blaze", context.client) ?: return
			if (!AutoPuzzleItems.select(context, bow.slot)) {
				acquired.close()
				return
			}
			lease = acquired
			inputSession = session
			shortbow = bow
			runSequence = context.runSequence
			roomSignature = context.roomSignature
			stateAtMs = context.nowMs
			centerAlignedTicks = 0
			val center = AutoPuzzleRoomCoordinates.worldPosition(context.room, 0.5, context.player.eyePosition.y, 0.5)
			aim.start(context, center, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.BLAZE)
			state = State.FACE_CENTER
			return
		}

		val unexpected = unexpectedRemoval(observed)
		if (unexpected != null) {
			stop(unexpected, terminal = true)
			return
		}

		when (state) {
			State.FACE_CENTER -> {
				if (aim.update(context).finished) centerAlignedTicks++ else centerAlignedTicks = 0
				if (centerAlignedTicks >= REQUIRED_ALIGNMENT_TICKS) {
					aim.clear()
					lease?.press(context.client.options.keyShift)
					lease?.press(context.client.options.keyUp)
					stateAtMs = context.nowMs
					state = State.EDGE_WALK
				} else if (context.nowMs - stateAtMs >= AIM_ACQUISITION_TIMEOUT_MS) {
					stop("could not face the Blaze room center", terminal = true)
				}
			}
			State.EDGE_WALK -> tickEdgeWalk(context)
			State.EDGE_STOP -> tickEdgeStop(context, observed)
			State.AIM_TARGET -> tickAim(context, observed)
			State.SHOT_GAP -> tickShotGap(context, observed)
			State.WAIT_REMOVAL -> tickRemoval(context, observed)
			State.DONE, State.WAITING -> Unit
		}
	}

	private fun tickEdgeWalk(context: AutoPuzzleContext) {
		if (BlazeMovementGate.edgeWalkFinished(context.nowMs - stateAtMs)) {
			lease?.release(context.client.options.keyUp)
			stateAtMs = context.nowMs
			state = State.EDGE_STOP
			return
		}
	}

	private fun tickEdgeStop(context: AutoPuzzleContext, observed: List<BlazeSolver.Target>) {
		val velocity = context.player.deltaMovement
		val speedSq = velocity.x * velocity.x + velocity.z * velocity.z
		if (speedSq > EDGE_STOP_SPEED_SQ) {
			if (context.nowMs - stateAtMs > EDGE_SETTLE_TIMEOUT_MS) {
				stop("could not settle at the safe shooting edge", terminal = true)
			}
			return
		}
		beginNextTarget(context, observed)
	}

	private fun beginNextTarget(context: AutoPuzzleContext, observed: List<BlazeSolver.Target>) {
		if (observed.isEmpty()) {
			cleanup()
			state = State.DONE
			return
		}
		val bow = shortbow ?: run {
			stop("the selected shortbow is unavailable", terminal = true)
			return
		}
		val target = observed.first().entity
		val plan = ProjectileAimPlanner.entity(context, target, observed.drop(1).map { it.entity }, bow.terminator, removedCenterPillar(context))
		val point = (plan as? ProjectileAimPlanner.Result.Safe)?.point ?: run {
			stop((plan as ProjectileAimPlanner.Result.Unsafe).reason, terminal = true)
			return
		}
		currentTargetId = target.id
		baselineIds = observed.mapTo(linkedSetOf()) { it.entity.id }
		burstAttempt = 1
		burstStarted = false
		aimedPoint = point
		aim.start(context, point, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.BLAZE)
		state = State.AIM_TARGET
		stateAtMs = context.nowMs
	}

	private fun tickAim(context: AutoPuzzleContext, observed: List<BlazeSolver.Target>) {
		val targetId = currentTargetId ?: return stop("lost the current Blaze target", terminal = true)
		val target = observed.firstOrNull { it.entity.id == targetId }?.entity ?: return handleTargetRemoved(context, observed)
		val bow = shortbow ?: return stop("the selected shortbow is unavailable", terminal = true)
		if (!AutoPuzzleItems.isShortbow(context.player.inventory.selectedItem)) {
			return stop("the selected hotbar item is no longer a shortbow", terminal = true)
		}
		val unintended = observed.filter { it.entity.id != targetId }.map { it.entity }
		val currentPoint = aimedPoint
		if (currentPoint == null || !ProjectileAimPlanner.isSafeEntityAimPoint(
				context,
				currentPoint,
				target,
				unintended,
				bow.terminator,
				removedCenterPillar(context)
			)
		) {
			val plan = ProjectileAimPlanner.entity(context, target, unintended, bow.terminator, removedCenterPillar(context))
			val safe = plan as? ProjectileAimPlanner.Result.Safe
				?: return stop((plan as ProjectileAimPlanner.Result.Unsafe).reason, terminal = true)
			aimedPoint = safe.point
			aim.start(context, safe.point, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.BLAZE)
		}
		val ready = aim.update(context).ready && ProjectileAimPlanner.liveRayHitsEntity(
			context,
			target,
			observed.filter { it.entity.id != targetId }.map { it.entity },
			bow.terminator,
			removedCenterPillar(context)
		)
		if (!ready) {
			if (context.nowMs - stateAtMs >= AIM_ACQUISITION_TIMEOUT_MS) {
				stop("could not acquire a safe live ray to the intended Blaze", terminal = true)
			}
			return
		}
		if (!AutoPuzzleInteraction.useHeldItem(context)) return stop("could not fire the shortbow", terminal = true)
		burstStarted = true
		nextActionAtMs = context.nowMs + ThreadLocalRandom.current().nextLong(40L, 51L)
		state = State.SHOT_GAP
	}

	private fun tickShotGap(context: AutoPuzzleContext, observed: List<BlazeSolver.Target>) {
		if (currentTargetId !in observed.map { it.entity.id }) return handleTargetRemoved(context, observed)
		if (context.nowMs < nextActionAtMs) return
		val targetId = currentTargetId ?: return stop("lost the current Blaze target", terminal = true)
		val target = observed.firstOrNull { it.entity.id == targetId }?.entity ?: return handleTargetRemoved(context, observed)
		val bow = shortbow ?: return stop("the selected shortbow is unavailable", terminal = true)
		val plan = ProjectileAimPlanner.entity(context, target, observed.filter { it.entity.id != targetId }.map { it.entity }, bow.terminator, removedCenterPillar(context))
		if (plan !is ProjectileAimPlanner.Result.Safe) return stop((plan as ProjectileAimPlanner.Result.Unsafe).reason, terminal = true)
		if (!ProjectileAimPlanner.liveRayHitsEntity(
				context,
				target,
				observed.filter { it.entity.id != targetId }.map { it.entity },
				bow.terminator,
				removedCenterPillar(context)
			)
		) {
			return stop("the live ray became unsafe before the second shortbow shot", terminal = true)
		}
		if (!AutoPuzzleItems.isShortbow(context.player.inventory.selectedItem) || !AutoPuzzleInteraction.useHeldItem(context)) {
			return stop("could not fire the second shortbow shot", terminal = true)
		}
		stateAtMs = context.nowMs
		state = State.WAIT_REMOVAL
	}

	private fun tickRemoval(context: AutoPuzzleContext, observed: List<BlazeSolver.Target>) {
		if (currentTargetId !in observed.map { it.entity.id }) return handleTargetRemoved(context, observed)
		val elapsed = context.nowMs - stateAtMs
		if (BlazeRemovalGate.shouldBeginPreAim(elapsed, settings.blazeRemovalWait.value.toLong())) {
			updateNextTargetPreAim(context, observed)
		}
		if (!BlazeRemovalGate.shouldRetry(elapsed)) return
		if (burstAttempt >= 2) {
			stop("the Blaze survived both shot bursts", terminal = true)
			return
		}
		burstAttempt++
		preAimTargetId = null
		val targetId = currentTargetId ?: return stop("lost the current Blaze target", terminal = true)
		val target = observed.firstOrNull { it.entity.id == targetId }?.entity
			?: return handleTargetRemoved(context, observed)
		val bow = shortbow ?: return stop("the selected shortbow is unavailable", terminal = true)
		val plan = ProjectileAimPlanner.entity(context, target, observed.filter { it.entity.id != targetId }.map { it.entity }, bow.terminator, removedCenterPillar(context))
		val point = (plan as? ProjectileAimPlanner.Result.Safe)?.point
			?: return stop((plan as ProjectileAimPlanner.Result.Unsafe).reason, terminal = true)
		aimedPoint = point
		aim.start(context, point, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.BLAZE)
		state = State.AIM_TARGET
		stateAtMs = context.nowMs
	}

	private fun updateNextTargetPreAim(context: AutoPuzzleContext, observed: List<BlazeSolver.Target>) {
		val current = currentTargetId ?: return
		val next = observed.firstOrNull { it.entity.id != current }?.entity ?: return
		val bow = shortbow ?: return
		val unintended = observed.filter { it.entity.id != next.id }.map { it.entity }
		val existingPoint = aimedPoint
		if (preAimTargetId == next.id && existingPoint != null && ProjectileAimPlanner.isSafeEntityAimPoint(
				context, existingPoint, next, unintended, bow.terminator, removedCenterPillar(context)
			)
		) {
			aim.update(context)
			return
		}
		val plan = ProjectileAimPlanner.entity(context, next, unintended, bow.terminator, removedCenterPillar(context))
		val point = (plan as? ProjectileAimPlanner.Result.Safe)?.point ?: return
		preAimTargetId = next.id
		aimedPoint = point
		aim.start(context, point, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.BLAZE)
	}

	private fun handleTargetRemoved(context: AutoPuzzleContext, observed: List<BlazeSolver.Target>) {
		val targetId = currentTargetId ?: return stop("lost the current Blaze target", terminal = true)
		if (!burstStarted) {
			stop("the intended Blaze died before its shot burst began", terminal = true)
			return
		}
		val remainingIds = observed.mapTo(hashSetOf()) { it.entity.id }
		val unexpected = (baselineIds - remainingIds) - targetId
		if (unexpected.isNotEmpty()) {
			stop("a different Blaze died before the intended target", terminal = true)
			return
		}
		val preparedId = preAimTargetId
		val prepared = observed.firstOrNull { it.entity.id == preparedId }
		currentTargetId = null
		preAimTargetId = null
		if (prepared != null && observed.firstOrNull()?.entity?.id == prepared.entity.id) {
			currentTargetId = prepared.entity.id
			baselineIds = observed.mapTo(linkedSetOf()) { it.entity.id }
			burstAttempt = 1
			burstStarted = false
			state = State.AIM_TARGET
			stateAtMs = context.nowMs
			return
		}
		beginNextTarget(context, observed)
	}

	private fun unexpectedRemoval(observed: List<BlazeSolver.Target>): String? {
		if (state !in setOf(State.AIM_TARGET, State.SHOT_GAP, State.WAIT_REMOVAL)) return null
		val current = currentTargetId ?: return null
		val removed = baselineIds - observed.mapTo(hashSetOf()) { it.entity.id }
		return if (removed.any { it != current }) "a different Blaze died before the intended target" else null
	}

	private fun removedCenterPillar(context: AutoPuzzleContext): (BlockPos) -> Boolean = { world ->
		// The current Blaze room permits arrows through its former center column.
		// Keep every other live block and entity collision in the safety check.
		val local = AutoPuzzleRoomCoordinates.relativeBlock(context.room, world)
		local.x == 0 && local.z == 0 && local.y in LEGACY_CENTER_PILLAR_Y
	}

	override fun render(context: LevelRenderContext) {
		renderStartBox?.let { CgcRenderer3D.outlineBox(it, settings.startWaypointColor.value, depth = false) }
		val targets = renderTargets
		for ((index, target) in targets.withIndex()) {
			CgcRenderer3D.outlineBox(target.box, targetColour(index), depth = false)
		}
		val width = settings.lineThickness.value.toFloat()
		if (targets.size >= 2) {
			CgcRenderer3D.lineList(listOf(targets[0].center, targets[1].center), settings.firstLineColor.value, settings.firstLineColor.value, depth = false, width = width)
		}
		if (targets.size >= 3) {
			CgcRenderer3D.lineList(listOf(targets[1].center, targets[2].center), settings.secondLineColor.value, settings.secondLineColor.value, depth = false, width = width)
		}
	}

	override fun frame(client: Minecraft) = aim.frameUpdate(client)

	private fun targetColour(index: Int): Colour = when (index) {
		0 -> settings.firstBlazeColor.value
		1 -> settings.secondBlazeColor.value
		else -> settings.otherBlazeColor.value
	}

	private fun isOnStart(context: AutoPuzzleContext, point: Vec3): Boolean {
		if (!context.player.onGround()) return false
		val pos = context.player.position()
		return horizontalDistanceSqr(pos, point) <= START_DISTANCE_SQ && kotlin.math.abs(pos.y - point.y) <= START_Y_TOLERANCE
	}

	private fun horizontalDistanceSqr(first: Vec3, second: Vec3): Double {
		val x = first.x - second.x
		val z = first.z - second.z
		return x * x + z * z
	}

	private fun failWithoutLease(reason: String, run: Long) {
		runSequence = run
		requiresStepOff = true
		observedOffStart = false
		ChatUtils.chat("§c[Auto Puzzles] Blaze stopped: $reason.")
	}

	override fun stop(reason: String, terminal: Boolean) {
		val wasActive = state != State.WAITING && state != State.DONE || lease != null
		cleanup()
		state = State.WAITING
		if (terminal && wasActive) {
			requiresStepOff = true
			observedOffStart = !lastOnStart
		}
		if (wasActive) ChatUtils.chat("§c[Auto Puzzles] Blaze stopped: $reason.")
	}

	override fun leaveRoom() {
		if (state != State.WAITING && state != State.DONE) stop("the Blaze room was left", terminal = true)
		renderTargets = emptyList()
		renderStartBox = null
		roomSignature = null
	}

	override fun runChanged(runSequence: Long) {
		cleanup()
		state = State.WAITING
		this.runSequence = runSequence
		requiresStepOff = false
		observedOffStart = false
	}

	override fun worldReset() {
		val active = state != State.WAITING && state != State.DONE
		cleanup()
		state = State.WAITING
		if (active) requiresStepOff = true
		renderTargets = emptyList()
		renderStartBox = null
		roomSignature = null
	}

	private fun cleanup() {
		aim.clear()
		lease?.close()
		lease = null
		inputSession = null
		shortbow = null
		currentTargetId = null
		preAimTargetId = null
		aimedPoint = null
		baselineIds = emptySet()
		burstAttempt = 0
		burstStarted = false
	}

	private companion object {
		const val START_DISTANCE_SQ = 0.18
		const val START_Y_TOLERANCE = 0.20
		const val EDGE_STOP_SPEED_SQ = 0.0004
		const val REQUIRED_ALIGNMENT_TICKS = 2
		const val EDGE_SETTLE_TIMEOUT_MS = 1_200L
		const val AIM_ACQUISITION_TIMEOUT_MS = 2_000L
		val LEGACY_CENTER_PILLAR_Y = 20..125
	}
}

internal object BlazeMovementGate {
	fun edgeWalkFinished(elapsedMs: Long): Boolean = elapsedMs >= 150L
}

internal object BlazeRemovalGate {
	fun shouldBeginPreAim(elapsedMs: Long, configuredWaitMs: Long): Boolean = elapsedMs >= configuredWaitMs
	fun shouldRetry(elapsedMs: Long): Boolean = elapsedMs >= KILL_CONFIRM_TIMEOUT_MS

	private const val KILL_CONFIRM_TIMEOUT_MS = 2_500L
}
