package cgc.cgc.module.impl.dungeon.autopuzzles.icefill

import cgc.cgc.dungeon.DungeonPuzzle
import cgc.cgc.dungeon.DungeonPuzzleState
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
import cgc.cgc.runtime.CgcRenderer3D
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.ItemUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

internal fun iceFillLaunchCursor(start: BlockPos, path: List<BlockPos>): Int? {
	val first = path.firstOrNull() ?: return null
	if (start == first) return 0
	val adjacent = start.y == first.y &&
		kotlin.math.abs(start.x - first.x) + kotlin.math.abs(start.z - first.z) == 1
	return if (adjacent) -1 else null
}

class IceFillController(private val settings: IceFillSubModule) : AutoPuzzleController {
	override val roomNames = setOf("Ice Fill")

	private enum class State {
		WAITING,
		AIM_ROUTE_WARP,
		WAIT_ROUTE_WARP,
		STRAFE_AIM,
		STRAFE,
		TURN,
		STAIR_AIM,
		STAIR_WALK,
		RECOVERY_AIM,
		RECOVERY_WAIT,
		WAIT_RESET,
		WAIT_GREEN,
		DONE
	}

	private data class Launch(val direction: Direction, val cursor: Int)
	private data class RouteRecording(
		val floorIndex: Int,
		val signature: String,
		val spaces: Set<BlockPos>,
		val end: BlockPos,
		val cells: MutableList<BlockPos>,
		val facings: MutableList<Direction>,
		val movements: MutableList<IceFillRouteStore.MovementInput?>
	)
	private data class AppliedRoute(
		val floor: IceFillSolver.Floor,
		val facings: Map<BlockPos, Direction>,
		val movements: Map<BlockPos, IceFillRouteStore.MovementInput>
	)

	private val calibrationStore = IceFillCalibrationStore()
	private val routeStore = IceFillRouteStore()
	private val aim = AutoPuzzleAimController()
	private var state = State.WAITING
	private var lockedRun = Long.MIN_VALUE
	private var runSequence = 0L
	private var roomSignature: String? = null
	private var solvedSignature: String? = null
	private var floors = emptyList<IceFillSolver.Floor>()
	private var recordedFacings = emptyList<Map<BlockPos, Direction>>()
	private var recordedMovements = emptyList<Map<BlockPos, IceFillRouteStore.MovementInput>>()
	private var renderPaths = emptyList<List<Vec3>>()
	private var worldStarts = emptyList<BlockPos>()
	private var fallY = Double.NaN
	private var floorIndex = 0
	private var cursor = -1
	private var frameForward: Direction? = null
	private var pendingIndex = -1
	private var warpTarget: AutoPuzzleEtherwarp.Target? = null
	private var warpStartedAtMs = 0L
	private var strafeEndIndex = -1
	private var strafeStartedIndex = -1
	private var strafeKey: net.minecraft.client.KeyMapping? = null
	private var stairSource: BlockPos? = null
	private var stairTarget: BlockPos? = null
	private var stateAtMs = 0L
	private var lease: AutoPuzzleInputOwner.Lease? = null
	private var inputSession: AutoPuzzleInputSession? = null
	private var etherwarpSlot = -1
	private val recoveryCounts = IntArray(3)
	private var interferenceReason: String? = null
	private var preflightReason: String? = null
	private var routeRecording: RouteRecording? = null
	private val reportedLegacyLayouts = hashSetOf<String>()

	fun toggleRouteRecording() {
		val context = calibrationContext() ?: return
		ensureSolved(context)
		if (routeRecording != null) {
			finishRouteRecording(context)
			return
		}
		val located = floors.withIndex().mapNotNull { (index, floor) ->
			routeCellAt(context.player.position(), floor.spaces)?.let { index to it }
		}.minByOrNull { (_, cell) -> horizontalDistanceSqr(context.player.position(), cell.center) }
		if (located == null) {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill recording failed: stand on the first ice cell of a floor.")
			return
		}
		val (index, cell) = located
		val floor = floors[index]
		if (cell != floor.start) {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill recording failed: this is not the detected first cell of Floor ${index + 1}.")
			return
		}
		cleanupInput()
		state = State.WAITING
		lockedRun = context.runSequence
		val signature = layoutSignature(context, floor.spaces)
			routeRecording = RouteRecording(
			floorIndex = index,
			signature = signature,
			spaces = floor.spaces.toSet(),
			end = floor.end,
			cells = arrayListOf(cell),
			facings = arrayListOf(cardinalFacing(context.player.yRot)),
			movements = arrayListOf(null)
		)
		ChatUtils.chat("§a[Auto Puzzles] Recording Ice Fill Floor ${index + 1}. Traverse every cell, then press the recording key again on the final cell.")
	}

	fun captureFallY() {
		val context = calibrationContext() ?: return
		if (!context.player.onGround()) {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill calibration failed: stand on the intended threshold block.")
			return
		}
		val relative = AutoPuzzleRoomCoordinates.relativePosition(context.room, context.player.position())
		runCatching { calibrationStore.setFallY(relative.y) }
			.onSuccess { ChatUtils.chat("§a[Auto Puzzles] Ice Fill Fall Y set to ${"%.2f".format(java.util.Locale.ROOT, relative.y)}.") }
			.onFailure { ChatUtils.chat("§c[Auto Puzzles] Ice Fill calibration could not be saved.") }
	}

	fun captureFloorStart(index: Int) {
		val context = calibrationContext() ?: return
		if (!context.player.onGround()) {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill calibration failed: stand on the intended floor start block.")
			return
		}
		val feet = context.player.blockPosition()
		val support = feet.below()
		val state = context.level.getBlockState(support)
		if (state.getCollisionShape(context.level, support).isEmpty) {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill calibration failed: the start must have a solid support block.")
			return
		}
		val relative = AutoPuzzleRoomCoordinates.relativeBlock(context.room, feet)
		runCatching { calibrationStore.setFloorStart(index, relative) }
			.onSuccess { ChatUtils.chat("§a[Auto Puzzles] Ice Fill Floor ${index + 1} Start set to ${relative.x}, ${relative.y}, ${relative.z}.") }
			.onFailure { ChatUtils.chat("§c[Auto Puzzles] Ice Fill calibration could not be saved.") }
	}

	private fun calibrationContext(): AutoPuzzleContext? {
		val context = AutoPuzzleContext.create(Minecraft.getInstance())
		if (context == null || context.room.displayName != "Ice Fill") {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill calibration is only available inside the recognized Ice Fill room.")
			return null
		}
		return context
	}

	override fun tickStart(client: Minecraft) {
		val session = inputSession ?: return
		val reason = session.cancellationReason(AutoPuzzleContext.monotonicNowMs()) ?: return
		stop("cancelled by $reason", terminal = true)
	}

	override fun tick(context: AutoPuzzleContext) {
		if (!IceFillSolver.scanAreaLoaded(context)) {
			floors = emptyList()
			recordedFacings = emptyList()
			recordedMovements = emptyList()
			renderPaths = emptyList()
			solvedSignature = null
			routeRecording = null
			if (state !in setOf(State.WAITING, State.DONE, State.WAIT_GREEN)) {
				stop("part of the Ice Fill room became unloaded", terminal = true)
			}
			return
		}
		ensureSolved(context)
		if (routeRecording != null) {
			tickRouteRecording(context)
			val recording = routeRecording
			if (recording != null) {
				ChatUtils.actionBar("§c● RECORDING ICE FILL FLOOR ${recording.floorIndex + 1} §7(${recording.cells.size}/${recording.spaces.size} cells) §ePress the record key to save")
			}
			return
		}
		if (lockedRun == context.runSequence || state == State.DONE) return
		val puzzleState = context.puzzleState(DungeonPuzzle.ICE_FILL)
		if (puzzleState == DungeonPuzzleState.GREEN && state == State.WAITING) {
			state = State.DONE
			return
		}
		if (puzzleState == DungeonPuzzleState.FAILED && state == State.WAITING && context.runSequence > 0L) {
			lockBeforeLease(context.runSequence, "the dungeon puzzle was already marked failed")
			return
		}
		if (puzzleState == DungeonPuzzleState.FAILED && state != State.WAITING) {
			stop("the dungeon puzzle was marked failed", terminal = true)
			return
		}
		if (state == State.WAIT_GREEN) {
			if (puzzleState == DungeonPuzzleState.GREEN) state = State.DONE
			return
		}
		if (!context.dungeonStarted || context.runSequence <= 0L) return
		if (roomSignature != null && roomSignature != context.roomSignature) {
			stop("room orientation changed", terminal = true)
			return
		}
		if (state != State.WAITING && (!context.player.isAlive || context.client.screen != null)) {
			stop(if (!context.player.isAlive) "the player is unavailable" else "a screen was opened", terminal = true)
			return
		}
		interferenceReason?.let {
			interferenceReason = null
			stop(it, terminal = true)
			return
		}

		if (state != State.WAITING && state !in setOf(State.RECOVERY_AIM, State.RECOVERY_WAIT, State.WAIT_RESET) && context.player.y < fallY) {
			beginRecovery(context)
			return
		}

		if (state == State.WAITING) {
			tickWaiting(context)
			return
		}
		if (context.runSequence != runSequence) {
			runChanged(context.runSequence)
			return
		}
		if (state !in setOf(State.RECOVERY_AIM, State.RECOVERY_WAIT, State.WAIT_RESET, State.STAIR_AIM, State.STAIR_WALK)) {
			futurePackedInterference(context)?.let { return stop(it, terminal = true) }
		}

		when (state) {
			State.AIM_ROUTE_WARP -> tickAimRouteWarp(context)
			State.WAIT_ROUTE_WARP -> tickWaitRouteWarp(context)
			State.STRAFE_AIM -> tickStrafeAim(context)
			State.STRAFE -> tickStrafe(context)
			State.TURN -> tickTurn(context)
			State.STAIR_AIM -> tickStairAim(context)
			State.STAIR_WALK -> tickStairWalk(context)
			State.RECOVERY_AIM -> tickRecoveryAim(context)
			State.RECOVERY_WAIT -> tickRecoveryWait(context)
			State.WAIT_RESET -> tickWaitReset(context)
			else -> Unit
		}
	}

	private fun ensureSolved(context: AutoPuzzleContext) {
		if (solvedSignature == context.roomSignature && floors.isNotEmpty()) return
		val applied = IceFillSolver.observe(context).map { floor -> applyRecordedRoute(context, floor) }
		floors = applied.map { it.floor }
		recordedFacings = applied.map { it.facings }
		recordedMovements = applied.map { it.movements }
		solvedSignature = context.roomSignature
		renderPaths = floors.map { floor -> floor.path.map { Vec3(it.x + 0.5, it.y + 0.01, it.z + 0.5) } }
	}

	private fun applyRecordedRoute(
		context: AutoPuzzleContext,
		floor: IceFillSolver.Floor
	): AppliedRoute {
		val stored = routeStore.route(layoutSignature(context, floor.spaces))
			?: return AppliedRoute(floor, emptyMap(), emptyMap())
		val cells = stored.cells.map { AutoPuzzleRoomCoordinates.worldBlock(context.room, it.x, it.y, it.z) }
		val valid = cells.size == floor.spaces.size && cells.toSet() == floor.spaces &&
			cells.firstOrNull() == floor.start && cells.lastOrNull() == floor.end &&
			cells.zipWithNext().all { (first, second) -> manhattanHorizontal(first, second) == 1 && first.y == second.y }
		if (!valid) return AppliedRoute(floor, emptyMap(), emptyMap())
		val facings = if (stored.roomRelativeFacings) {
			stored.facings.map { AutoPuzzleRoomCoordinates.worldDirection(context.room, it) }
		} else {
			// Version-one recordings did not save their room rotation, so their
			// route is reusable but their exact camera frame cannot be recovered.
			emptyList()
		}
		val facingMap = cells.indices
			.takeIf { facings.size == cells.size }
			?.associate { cells[it] to facings[it] }
			.orEmpty()
		val movementMap = cells.indices.mapNotNull { index ->
			stored.movements.getOrNull(index)?.let { cells[index] to it }
		}.toMap()
		if ((!stored.roomRelativeFacings || movementMap.isEmpty()) && reportedLegacyLayouts.add(layoutSignature(context, floor.spaces))) {
			ChatUtils.chat("§e[Auto Puzzles] This Ice Fill route predates exact transition recording. Its cell order is preserved; re-record it once for exact replay.")
		}
		return AppliedRoute(floor.copy(path = cells), facingMap, movementMap)
	}

	private fun tickRouteRecording(context: AutoPuzzleContext) {
		val recording = routeRecording ?: return
		val cell = routeCellAt(context.player.position(), recording.spaces) ?: return
		val facing = cardinalFacing(context.player.yRot)
		if (cell == recording.cells.last()) {
			recording.facings[recording.facings.lastIndex] = facing
			activeMovementInput(context)?.let { recording.movements[recording.movements.lastIndex] = it }
			return
		}
		if (cell in recording.cells || manhattanHorizontal(recording.cells.last(), cell) != 1 || cell.y != recording.cells.last().y) {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill recording cancelled: the route revisited or skipped an ice cell.")
			routeRecording = null
			return
		}
		val departureFacing = recording.facings.last()
		val movement = direction(recording.cells.last(), cell)
		val pressed = activeMovementInput(context)
		recording.movements[recording.movements.lastIndex] = movement?.let { worldMovement ->
			pressed?.takeIf { movementDirection(departureFacing, it) == worldMovement }
				?: inferMovementInput(departureFacing, worldMovement)
		}
		recording.cells += cell
		recording.facings += facing
		recording.movements += null
	}

	private fun finishRouteRecording(context: AutoPuzzleContext) {
		val recording = routeRecording ?: return
		tickRouteRecording(context)
		if (routeRecording == null) return
		if (recording.cells.size != recording.spaces.size || recording.cells.toSet() != recording.spaces || recording.cells.last() != recording.end) {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill recording is incomplete (${recording.cells.size}/${recording.spaces.size} cells); recording remains active.")
			return
		}
		val relativeCells = recording.cells.map { AutoPuzzleRoomCoordinates.relativeBlock(context.room, it) }
		val relativeFacings = recording.facings.map { AutoPuzzleRoomCoordinates.relativeDirection(context.room, it) }
		runCatching {
			routeStore.save(recording.signature, IceFillRouteStore.Route(relativeCells, relativeFacings, movements = recording.movements.toList()))
		}.onFailure {
			ChatUtils.chat("§c[Auto Puzzles] Ice Fill route could not be saved.")
			return
		}
		val floor = floors[recording.floorIndex].copy(path = recording.cells.toList())
		floors = floors.toMutableList().also { it[recording.floorIndex] = floor }
		recordedFacings = recordedFacings.toMutableList().also {
			while (it.size < floors.size) it += emptyMap()
			it[recording.floorIndex] = recording.cells.indices.associate { step -> recording.cells[step] to recording.facings[step] }
		}
		recordedMovements = recordedMovements.toMutableList().also {
			while (it.size < floors.size) it += emptyMap()
			it[recording.floorIndex] = recording.cells.indices.mapNotNull { step ->
				recording.movements[step]?.let { movement -> recording.cells[step] to movement }
			}.toMap()
		}
		renderPaths = floors.map { current -> current.path.map { Vec3(it.x + 0.5, it.y + 0.01, it.z + 0.5) } }
		routeRecording = null
		ChatUtils.chat("§a[Auto Puzzles] Saved Ice Fill Floor ${recording.floorIndex + 1} route for layout ${recording.signature.take(8)}.")
	}

	private fun layoutSignature(context: AutoPuzzleContext, spaces: Collection<BlockPos>): String =
		IceFillRouteStore.layoutSignature(spaces.map { AutoPuzzleRoomCoordinates.relativeBlock(context.room, it) })

	private fun routeCellAt(position: Vec3, spaces: Collection<BlockPos>): BlockPos? =
		spaces.firstOrNull { occupiesCell(position, it) }

	private fun activeMovementInput(context: AutoPuzzleContext): IceFillRouteStore.MovementInput? = when {
		context.client.options.keyUp.isDown -> IceFillRouteStore.MovementInput.FORWARD
		context.client.options.keyDown.isDown -> IceFillRouteStore.MovementInput.BACKWARD
		context.client.options.keyLeft.isDown -> IceFillRouteStore.MovementInput.LEFT
		context.client.options.keyRight.isDown -> IceFillRouteStore.MovementInput.RIGHT
		else -> null
	}

	private fun inferMovementInput(facing: Direction, movement: Direction): IceFillRouteStore.MovementInput? = when (movement) {
		facing -> IceFillRouteStore.MovementInput.FORWARD
		facing.opposite -> IceFillRouteStore.MovementInput.BACKWARD
		facing.counterClockWise -> IceFillRouteStore.MovementInput.LEFT
		facing.clockWise -> IceFillRouteStore.MovementInput.RIGHT
		else -> null
	}

	private fun movementDirection(facing: Direction, input: IceFillRouteStore.MovementInput): Direction = when (input) {
		IceFillRouteStore.MovementInput.FORWARD -> facing
		IceFillRouteStore.MovementInput.BACKWARD -> facing.opposite
		IceFillRouteStore.MovementInput.LEFT -> facing.counterClockWise
		IceFillRouteStore.MovementInput.RIGHT -> facing.clockWise
	}

	private fun cardinalFacing(yaw: Float): Direction = when (Math.floorMod(kotlin.math.round(yaw / 90.0f).toInt(), 4)) {
		0 -> Direction.SOUTH
		1 -> Direction.WEST
		2 -> Direction.NORTH
		else -> Direction.EAST
	}

	private fun tickWaiting(context: AutoPuzzleContext) {
		if (floors.size != 3) return
		val calibration = calibrationStore.current()
		if (!calibration.complete) return
		val starts = calibration.floorStarts.map { relative ->
			val pos = relative ?: return
			AutoPuzzleRoomCoordinates.worldBlock(context.room, pos.x, pos.y, pos.z)
		}
		val threshold = calibration.fallY ?: return
		val onFirstStart = onStart(context, starts[0])
		val occupiedStart = starts[0].takeIf { onFirstStart && it == floors[0].path.firstOrNull() }
		if (floors.any { floor -> floor.path.any { packed(context, it) && it != occupiedStart } }) {
			lockBeforeLease(context.runSequence, "packed ice was already present on entry")
			return
		}
		if (!onFirstStart) return
		val launches = starts.indices.map { index ->
			validatedLaunch(context, starts[index], floors[index])
		}
		if (launches.any { it == null }) {
			reportPreflight("a floor start is neither the first route cell nor one safe block before it")
			return
		}
		for (index in 0..1) {
			if (!hasStraightSingleStairConnector(context, floors[index].path.last(), starts[index + 1])) {
				reportPreflight("a calibrated Ice Fill floor connector is not the expected supported single-stair path")
				return
			}
		}
		val firstLaunch = launches[0] ?: run {
			reportPreflight("the first Ice Fill launch direction is unavailable")
			return
		}
		preflightReason = null
		val slot = AutoPuzzleItems.firstEtherwarp(context) ?: return
		val session = AutoPuzzleInputSession.acquire(context.nowMs) ?: return
		val acquired = AutoPuzzleInputOwner.acquire("Ice Fill", context.client) ?: return
		if (!AutoPuzzleItems.select(context, slot)) {
			acquired.close()
			return
		}
		lease = acquired
		inputSession = session
		etherwarpSlot = slot
		worldStarts = starts
		fallY = threshold
		runSequence = context.runSequence
		roomSignature = context.roomSignature
		floorIndex = 0
		cursor = firstLaunch.cursor
		frameForward = firstLaunch.direction
		recoveryCounts.fill(0)
		lease?.press(context.client.options.keyShift)
		if (cursor >= 0) decideNext(context) else beginRouteWarp(context, 0)
	}

	private fun beginRouteWarp(context: AutoPuzzleContext, targetIndex: Int) {
		val floor = floors.getOrNull(floorIndex) ?: return stop("the active Ice Fill floor was lost", terminal = true)
		val cell = floor.path.getOrNull(targetIndex) ?: return stop("the next Ice Fill cell was lost", terminal = true)
		if (!AutoPuzzleItems.select(context, etherwarpSlot) || !ItemUtils.isEtherwarp(context.player.inventory.selectedItem)) {
			return stop("the Etherwarp item is unavailable", terminal = true)
		}
		lease?.press(context.client.options.keyShift)
		val result = AutoPuzzleEtherwarp.target(context, cell.below())
		val target = result.getOrElse { return stop(it.message ?: "the route Etherwarp target is invalid", terminal = true) }
		pendingIndex = targetIndex
		warpTarget = target
		aim.start(context, target.aimPoint, 1.0, AutoPuzzleAimProfile.ETHERWARP)
		state = State.AIM_ROUTE_WARP
		stateAtMs = context.nowMs
	}

	private fun tickAimRouteWarp(context: AutoPuzzleContext) {
		val target = warpTarget ?: return stop("the route Etherwarp target was lost", terminal = true)
		if (!nearCell(context.player.position(), routeSource() ?: return stop("the route source was lost", terminal = true), ROUTE_SOURCE_DISTANCE)) {
			return stop("the player moved away from the active Ice Fill route cell", terminal = true)
		}
		val update = aim.update(context)
		if (!update.ready) {
			if (context.nowMs - stateAtMs >= AIM_TIMEOUT_MS) stop("could not aim at the route Etherwarp target", terminal = true)
			return
		}
		if (!context.player.isShiftKeyDown) return
		if (!AutoPuzzleEtherwarp.liveRayHits(context, target)) {
			if (context.nowMs - stateAtMs >= AIM_TIMEOUT_MS) stop("the route Etherwarp ray became obstructed", terminal = true)
			return
		}
		if (!ItemUtils.isEtherwarp(context.player.inventory.selectedItem) || !AutoPuzzleInteraction.useHeldItem(context)) {
			return stop("could not use the route Etherwarp", terminal = true)
		}
		aim.clear()
		warpStartedAtMs = context.nowMs
		state = State.WAIT_ROUTE_WARP
	}

	private fun tickWaitRouteWarp(context: AutoPuzzleContext) {
		val floor = floors.getOrNull(floorIndex) ?: return stop("the active Ice Fill floor was lost", terminal = true)
		val cell = floor.path.getOrNull(pendingIndex) ?: return stop("the route Etherwarp destination was lost", terminal = true)
		if (nearCell(context.player.position(), cell, WARP_LANDING_DISTANCE)) {
			cursor = pendingIndex
			warpTarget = null
			decideNext(context)
			return
		}
		if (context.player.y < fallY) {
			beginRecovery(context)
			return
		}
		if (context.nowMs - warpStartedAtMs >= ROUTE_WARP_OBSERVE_MS) {
			stop("the route Etherwarp landed away from its intended cell", terminal = true)
		}
	}

	private fun decideNext(context: AutoPuzzleContext) {
		val floor = floors.getOrNull(floorIndex) ?: return stop("the active Ice Fill floor was lost", terminal = true)
		if (cursor >= floor.path.lastIndex) {
			if (floorIndex >= 2) {
				cleanupInput()
				state = State.WAIT_GREEN
			} else {
				beginStairTransition(context)
			}
			return
		}
		val nextDirection = direction(floor.path[cursor], floor.path[cursor + 1])
			?: return stop("the Ice Fill path contains a non-adjacent edge", terminal = true)
		val forward = frameForward ?: return stop("the Ice Fill camera frame was lost", terminal = true)
		val recordedForward = recordedFacings.getOrNull(floorIndex)?.get(floor.path[cursor])
		if (recordedForward != null && recordedForward != forward) {
			beginTurn(context, recordedForward)
			return
		}
		val recordedMovement = recordedMovements.getOrNull(floorIndex)?.get(floor.path[cursor])
		if (recordedMovement != null && movementDirection(forward, recordedMovement) == nextDirection) {
			beginStrafe(context, nextDirection, recordedMovement)
			return
		}
		when (nextDirection) {
			forward -> beginRouteWarp(context, cursor + 1)
			forward.opposite -> beginTurn(context, nextDirection)
			forward.clockWise, forward.counterClockWise -> {
				if (recordedForward == null && IceFillMovementPlanner.shouldPromoteLateralRun(floor.path, cursor, forward)) beginTurn(context, nextDirection)
				else beginStrafe(context, nextDirection)
			}
			else -> stop("the Ice Fill path left the horizontal plane", terminal = true)
		}
	}

	private fun beginStrafe(
		context: AutoPuzzleContext,
		movement: Direction,
		recordedInput: IceFillRouteStore.MovementInput? = null
	) {
		val floor = floors[floorIndex]
		val forward = frameForward ?: return stop("the Ice Fill camera frame was lost", terminal = true)
		var end = cursor + 1
		while (end < floor.path.lastIndex && direction(floor.path[end], floor.path[end + 1]) == movement) {
			val recordedForward = recordedFacings.getOrNull(floorIndex)?.get(floor.path[end])
			if (recordedForward != null && recordedForward != forward) break
			val nextInput = recordedMovements.getOrNull(floorIndex)?.get(floor.path[end])
			if (recordedInput != null && nextInput != recordedInput) break
			end++
		}
		strafeEndIndex = end
		strafeStartedIndex = cursor
		val key = when (recordedInput) {
			IceFillRouteStore.MovementInput.FORWARD -> context.client.options.keyUp
			IceFillRouteStore.MovementInput.BACKWARD -> context.client.options.keyDown
			IceFillRouteStore.MovementInput.LEFT -> context.client.options.keyLeft
			IceFillRouteStore.MovementInput.RIGHT -> context.client.options.keyRight
			null -> if (movement == forward.counterClockWise) context.client.options.keyLeft else context.client.options.keyRight
		}
		strafeKey = key
		val look = horizontalLookPoint(context, forward)
		aim.start(context, look, settings.turnSpeed.value.toDouble(), AutoPuzzleAimProfile.ICE_TURN)
		stateAtMs = context.nowMs
		state = State.STRAFE_AIM
	}

	private fun tickStrafeAim(context: AutoPuzzleContext) {
		val current = routeSource() ?: return stop("the Ice Fill strafe source was lost", terminal = true)
		if (!occupiesCell(context.player.position(), current)) {
			return stop("the player moved away while aligning the Ice Fill strafe", terminal = true)
		}
		if (!aim.update(context).finished) {
			if (context.nowMs - stateAtMs >= TURN_TIMEOUT_MS) stop("the Ice Fill strafe alignment timed out", terminal = true)
			return
		}
		aim.clear()
		val key = strafeKey ?: return stop("the Ice Fill strafe key was lost", terminal = true)
		lease?.press(key)
		stateAtMs = context.nowMs
		state = State.STRAFE
	}

	private fun tickStrafe(context: AutoPuzzleContext) {
		val floor = floors.getOrNull(floorIndex) ?: return stop("the active Ice Fill floor was lost", terminal = true)
		val end = floor.path.getOrNull(strafeEndIndex) ?: return stop("the strafe target was lost", terminal = true)
		if ((strafeStartedIndex.coerceAtLeast(0)..strafeEndIndex).none {
			nearCell(context.player.position(), floor.path[it], STRAFE_CORRIDOR_DISTANCE)
		}) return stop("the player left the Ice Fill strafe corridor", terminal = true)
		val nearest = (cursor + 1..strafeEndIndex).minByOrNull { horizontalDistanceSqr(context.player.position(), floor.path[it].center) }
		if (nearest != null && nearCell(context.player.position(), floor.path[nearest], CELL_PROGRESS_DISTANCE)) {
			if (nearest > cursor + 1) return stop("the strafe skipped an Ice Fill cell", terminal = true)
			if (nearest == cursor + 1) cursor = nearest
		}
		if (occupiesCell(context.player.position(), end)) {
			cursor = strafeEndIndex
			strafeKey?.let { lease?.release(it) }
			strafeKey = null
			decideNext(context)
			return
		}
		if (context.nowMs - stateAtMs > STRAFE_TIMEOUT_PER_CELL_MS * (strafeEndIndex - strafeStartedIndex + 1)) {
			stop("the Ice Fill strafe did not reach its target", terminal = true)
		}
	}

	private fun beginTurn(context: AutoPuzzleContext, direction: Direction) {
		val current = floors[floorIndex].path[cursor]
		if (!occupiesCell(context.player.position(), current)) {
			return stop("the player was no longer on the Ice Fill turn cell", terminal = true)
		}
		val target = horizontalLookPoint(context, direction)
		frameForward = direction
		aim.start(context, target, settings.turnSpeed.value.toDouble(), AutoPuzzleAimProfile.ICE_TURN)
		state = State.TURN
		stateAtMs = context.nowMs
	}

	private fun tickTurn(context: AutoPuzzleContext) {
		val current = routeSource() ?: return stop("the Ice Fill turn cell was lost", terminal = true)
		if (!occupiesCell(context.player.position(), current)) {
			return stop("the player moved away from the Ice Fill turn cell", terminal = true)
		}
		if (!aim.update(context).finished) {
			if (context.nowMs - stateAtMs >= TURN_TIMEOUT_MS) stop("the slow Ice Fill turn timed out", terminal = true)
			return
		}
		aim.clear()
		decideNext(context)
	}

	private fun beginStairTransition(context: AutoPuzzleContext) {
		val target = worldStarts.getOrNull(floorIndex + 1)
			?: return stop("the next calibrated floor start is missing", terminal = true)
		val current = floors[floorIndex].path.last()
		val direction = straightDirection(current, target)
			?: return stop("the calibrated stair transition is not a straight line", terminal = true)
		if (!hasStraightSingleStairConnector(context, current, target)) {
			return stop("the expected supported single-stair connector is missing", terminal = true)
		}
		stairSource = current
		stairTarget = target
		frameForward = direction
		val look = Vec3(
			context.player.eyePosition.x + direction.stepX * TURN_LOOK_DISTANCE,
			context.player.eyePosition.y,
			context.player.eyePosition.z + direction.stepZ * TURN_LOOK_DISTANCE
		)
		aim.start(context, look, settings.turnSpeed.value.toDouble(), AutoPuzzleAimProfile.ICE_TURN)
		state = State.STAIR_AIM
		stateAtMs = context.nowMs
	}

	private fun tickStairAim(context: AutoPuzzleContext) {
		val end = floors.getOrNull(floorIndex)?.path?.lastOrNull()
			?: return stop("the Ice Fill stair source was lost", terminal = true)
		if (!nearCell(context.player.position(), end, STAIR_SOURCE_DISTANCE)) {
			return stop("the player moved away from the Ice Fill stair entrance", terminal = true)
		}
		if (!aim.update(context).finished) {
			if (context.nowMs - stateAtMs >= TURN_TIMEOUT_MS) stop("the stair alignment timed out", terminal = true)
			return
		}
		aim.clear()
		lease?.press(context.client.options.keyUp)
		state = State.STAIR_WALK
		stateAtMs = context.nowMs
	}

	private fun tickStairWalk(context: AutoPuzzleContext) {
		val target = stairTarget ?: return stop("the next calibrated floor start is missing", terminal = true)
		val source = stairSource ?: return stop("the Ice Fill stair source was lost", terminal = true)
		if (!insideStairCorridor(context.player.position(), source, target)) {
			return stop("the player left the straight Ice Fill stair corridor", terminal = true)
		}
		if (nearCell(context.player.position(), target, STAIR_ARRIVAL_DISTANCE)) {
			lease?.release(context.client.options.keyUp)
			stairSource = null
			stairTarget = null
			floorIndex++
			val launch = validatedLaunch(context, target, floors[floorIndex])
				?: return stop("the next floor start no longer connects to its first route cell", terminal = true)
			cursor = launch.cursor
			frameForward = launch.direction
			if (cursor >= 0) decideNext(context) else beginRouteWarp(context, 0)
			return
		}
		if (context.nowMs - stateAtMs >= STAIR_TIMEOUT_MS) {
			stop("the crouched stair transition did not reach the next floor", terminal = true)
		}
	}

	private fun beginRecovery(context: AutoPuzzleContext) {
		if (floorIndex !in 0..2) return stop("the active Ice Fill floor is unknown", terminal = true)
		recoveryCounts[floorIndex]++
		if (recoveryCounts[floorIndex] > MAX_RECOVERIES_PER_FLOOR) {
			return stop("Ice Fill recovery was exhausted on floor ${floorIndex + 1}", terminal = true)
		}
		strafeKey?.let { lease?.release(it) }
		strafeKey = null
		lease?.releaseMovement()
		aim.clear()
		if (!AutoPuzzleItems.select(context, etherwarpSlot) || !ItemUtils.isEtherwarp(context.player.inventory.selectedItem)) {
			return stop("the recovery Etherwarp item is unavailable", terminal = true)
		}
		lease?.press(context.client.options.keyShift)
		val start = worldStarts.getOrNull(floorIndex)
			?: return stop("the calibrated recovery start is missing", terminal = true)
		val result = AutoPuzzleEtherwarp.target(context, start.below())
		val target = result.getOrElse { return stop(it.message ?: "the recovery Etherwarp target is invalid", terminal = true) }
		warpTarget = target
		aim.start(context, target.aimPoint, 1.0, AutoPuzzleAimProfile.ETHERWARP)
		state = State.RECOVERY_AIM
		stateAtMs = context.nowMs
	}

	private fun tickRecoveryAim(context: AutoPuzzleContext) {
		val target = warpTarget ?: return stop("the recovery Etherwarp target was lost", terminal = true)
		val update = aim.update(context)
		if (!update.ready) {
			if (context.nowMs - stateAtMs >= AIM_TIMEOUT_MS) retryOrAbortRecovery(context, "could not aim at the recovery block")
			return
		}
		if (!context.player.isShiftKeyDown) return
		if (!AutoPuzzleEtherwarp.liveRayHits(context, target) || !AutoPuzzleInteraction.useHeldItem(context)) {
			retryOrAbortRecovery(context, "could not use the recovery Etherwarp")
			return
		}
		aim.clear()
		state = State.RECOVERY_WAIT
		stateAtMs = context.nowMs
	}

	private fun tickRecoveryWait(context: AutoPuzzleContext) {
		val start = worldStarts.getOrNull(floorIndex)
			?: return stop("the calibrated recovery start is missing", terminal = true)
		if (context.player.y >= fallY && nearCell(context.player.position(), start, RECOVERY_LANDING_DISTANCE)) {
			state = State.WAIT_RESET
			stateAtMs = context.nowMs
			return
		}
		if (context.nowMs - stateAtMs >= RECOVERY_LAND_TIMEOUT_MS) {
			retryOrAbortRecovery(context, "the recovery Etherwarp did not land safely")
		}
	}

	private fun retryOrAbortRecovery(context: AutoPuzzleContext, reason: String) {
		if (recoveryCounts[floorIndex] >= MAX_RECOVERIES_PER_FLOOR) stop(reason, terminal = true)
		else beginRecovery(context)
	}

	private fun tickWaitReset(context: AutoPuzzleContext) {
		val floor = floors.getOrNull(floorIndex) ?: return stop("the failed Ice Fill floor was lost", terminal = true)
		val start = worldStarts[floorIndex]
		if (resetReady(context, floor, start)) {
			val refreshed = IceFillSolver.observe(context).map { applyRecordedRoute(context, it).floor }
			if (refreshed.size != 3 || refreshed[floorIndex].spaces != floor.spaces || refreshed[floorIndex].path.isEmpty()) {
				return stop("the reset Ice Fill floor could not be re-solved", terminal = true)
			}
			floors = refreshed
			renderPaths = floors.map { solved -> solved.path.map { Vec3(it.x + 0.5, it.y + 0.01, it.z + 0.5) } }
			val launch = validatedLaunch(context, start, floors[floorIndex])
				?: return stop("the reset floor entrance no longer connects to its route", terminal = true)
			cursor = launch.cursor
			frameForward = launch.direction
			if (cursor >= 0) decideNext(context) else beginRouteWarp(context, 0)
			return
		}
		if (context.nowMs - stateAtMs >= FLOOR_RESET_TIMEOUT_MS) {
			stop("the failed Ice Fill floor did not fully reset within 10 seconds", terminal = true)
		}
	}

	private fun futurePackedInterference(context: AutoPuzzleContext): String? {
		for ((index, floor) in floors.withIndex()) {
			val startIndex = if (index == floorIndex) (cursor + 2).coerceAtLeast(0) else 0
			if (index < floorIndex) continue
			if (floor.path.drop(startIndex).any { packed(context, it) }) {
				return "an Ice Fill cell changed ahead of the active route"
			}
		}
		return null
	}

	private fun packed(context: AutoPuzzleContext, feet: BlockPos): Boolean =
		context.level.getBlockState(feet.below()).`is`(Blocks.PACKED_ICE)

	private fun routeSource(): BlockPos? =
		if (cursor < 0) worldStarts.getOrNull(floorIndex) else floors.getOrNull(floorIndex)?.path?.getOrNull(cursor)

	override fun blockChanged(pos: BlockPos, oldState: net.minecraft.world.level.block.state.BlockState?, newState: net.minecraft.world.level.block.state.BlockState) {
		if (lease == null || state == State.WAIT_RESET) return
		if (!newState.`is`(Blocks.PACKED_ICE)) return
		for ((floorNumber, floor) in floors.withIndex()) {
			val index = floor.path.indexOf(pos.above())
			if (index < 0) continue
			if (floorNumber != floorIndex || index > cursor + 1) {
				interferenceReason = "an Ice Fill cell changed out of route order"
			}
			return
		}
	}

	override fun render(context: LevelRenderContext) {
		if (routeRecording != null) return
		val width = settings.lineThickness.value.toFloat()
		for (path in renderPaths) {
			CgcRenderer3D.lineList(path, settings.pathColor.value, settings.pathColor.value, depth = false, width = width)
		}
	}

	override fun frame(client: Minecraft) = aim.frameUpdate(client)

	private fun validatedLaunch(context: AutoPuzzleContext, start: BlockPos, floor: IceFillSolver.Floor): Launch? {
		val firstIce = floor.path.firstOrNull() ?: return null
		val cursor = iceFillLaunchCursor(start, floor.path) ?: return null
		val support = start.below()
		if (!context.level.isLoaded(support) || context.level.getBlockState(support).getCollisionShape(context.level, support).isEmpty) return null
		if (cursor == 0) {
			val next = floor.path.getOrNull(1) ?: return null
			return direction(firstIce, next)?.let { Launch(it, 0) }
		}
		if (context.level.getBlockState(support).`is`(Blocks.ICE) || context.level.getBlockState(support).`is`(Blocks.PACKED_ICE)) return null
		return direction(start, firstIce)?.let { Launch(it, -1) }
	}

	private fun resetReady(context: AutoPuzzleContext, floor: IceFillSolver.Floor, start: BlockPos): Boolean =
		floor.path.withIndex().all { (index, feet) ->
			val state = context.level.getBlockState(feet.below())
			state.`is`(Blocks.ICE) || (index == 0 && feet == start && state.`is`(Blocks.PACKED_ICE))
		}

	private fun hasStraightSingleStairConnector(context: AutoPuzzleContext, start: BlockPos, end: BlockPos): Boolean {
		val direction = straightDirection(start, end) ?: return false
		if (end.y != start.y + 1) return false
		val distance = manhattanHorizontal(start, end)
		if (distance !in 1..MAX_STAIR_HORIZONTAL) return false
		var stairCount = 0
		for (step in 1..distance) {
			val x = start.x + direction.stepX * step
			val z = start.z + direction.stepZ * step
			val supports = (start.y - 1..end.y - 1).map { y -> BlockPos(x, y, z) }
			if (supports.any { !context.level.isLoaded(it) }) return false
			val solid = supports.filter { pos ->
				!context.level.getBlockState(pos).getCollisionShape(context.level, pos).isEmpty
			}
			if (solid.isEmpty()) return false
			stairCount += solid.count { context.level.getBlockState(it).`is`(Blocks.STONE_BRICK_STAIRS) }
		}
		return stairCount == 1
	}

	private fun insideStairCorridor(position: Vec3, start: BlockPos, end: BlockPos): Boolean {
		if (position.y < start.y - STAIR_VERTICAL_TOLERANCE || position.y > end.y + STAIR_VERTICAL_TOLERANCE) return false
		val startCenter = start.center
		val endCenter = end.center
		val dx = endCenter.x - startCenter.x
		val dz = endCenter.z - startCenter.z
		val lengthSq = dx * dx + dz * dz
		if (lengthSq <= 0.0) return false
		val rawProjection = ((position.x - startCenter.x) * dx + (position.z - startCenter.z) * dz) / lengthSq
		if (rawProjection < -STAIR_PROJECTION_TOLERANCE || rawProjection > 1.0 + STAIR_PROJECTION_TOLERANCE) return false
		val projection = rawProjection.coerceIn(0.0, 1.0)
		val closestX = startCenter.x + dx * projection
		val closestZ = startCenter.z + dz * projection
		val lateralX = position.x - closestX
		val lateralZ = position.z - closestZ
		return lateralX * lateralX + lateralZ * lateralZ <= STAIR_CORRIDOR_DISTANCE * STAIR_CORRIDOR_DISTANCE
	}

	private fun horizontalLookPoint(context: AutoPuzzleContext, direction: Direction): Vec3 {
		val eye = context.player.eyePosition
		val vertical = -kotlin.math.tan(Math.toRadians(context.player.xRot.toDouble())) * TURN_LOOK_DISTANCE
		return Vec3(
			eye.x + direction.stepX * TURN_LOOK_DISTANCE,
			eye.y + vertical,
			eye.z + direction.stepZ * TURN_LOOK_DISTANCE
		)
	}

	private fun direction(from: BlockPos, to: BlockPos): Direction? = when {
		to.y != from.y -> null
		to.x - from.x == 1 && to.z == from.z -> Direction.EAST
		to.x - from.x == -1 && to.z == from.z -> Direction.WEST
		to.z - from.z == 1 && to.x == from.x -> Direction.SOUTH
		to.z - from.z == -1 && to.x == from.x -> Direction.NORTH
		else -> null
	}

	private fun straightDirection(from: BlockPos, to: BlockPos): Direction? {
		val dx = to.x - from.x
		val dz = to.z - from.z
		if (dx != 0 && dz != 0) return null
		if (kotlin.math.abs(dx) + kotlin.math.abs(dz) > MAX_STAIR_HORIZONTAL) return null
		return when {
			dx > 0 -> Direction.EAST
			dx < 0 -> Direction.WEST
			dz > 0 -> Direction.SOUTH
			dz < 0 -> Direction.NORTH
			else -> null
		}
	}

	private fun onStart(context: AutoPuzzleContext, start: BlockPos): Boolean =
		context.player.onGround() && nearCell(context.player.position(), start, START_DISTANCE)

	private fun nearCell(position: Vec3, feet: BlockPos, distance: Double): Boolean =
		horizontalDistanceSqr(position, feet.center) <= distance * distance && kotlin.math.abs(position.y - feet.y) <= 0.80

	private fun occupiesCell(position: Vec3, feet: BlockPos): Boolean {
		val horizontal = BlockPos.containing(position.x, feet.y.toDouble(), position.z)
		return horizontal.x == feet.x && horizontal.z == feet.z && kotlin.math.abs(position.y - feet.y) <= 0.80
	}

	private fun horizontalDistanceSqr(position: Vec3, target: Vec3): Double {
		val dx = position.x - target.x
		val dz = position.z - target.z
		return dx * dx + dz * dz
	}

	private fun manhattanHorizontal(first: BlockPos, second: BlockPos): Int =
		kotlin.math.abs(first.x - second.x) + kotlin.math.abs(first.z - second.z)

	private fun lockBeforeLease(run: Long, reason: String) {
		lockedRun = run
		runSequence = run
		ChatUtils.chat("§c[Auto Puzzles] Ice Fill stopped: $reason.")
	}

	private fun reportPreflight(reason: String) {
		if (preflightReason == reason) return
		preflightReason = reason
		ChatUtils.chat("§c[Auto Puzzles] Ice Fill is not ready: $reason.")
	}

	override fun stop(reason: String, terminal: Boolean) {
		val wasActive = lease != null || state !in setOf(State.WAITING, State.DONE)
		if (terminal && wasActive && runSequence > 0L) lockedRun = runSequence
		cleanupInput()
		routeRecording = null
		state = State.WAITING
		if (wasActive) ChatUtils.chat("§c[Auto Puzzles] Ice Fill stopped: $reason.")
	}

	override fun leaveRoom() {
		if (state !in setOf(State.WAITING, State.DONE, State.WAIT_GREEN)) stop("the Ice Fill room was left", terminal = true)
		floors = emptyList()
		recordedFacings = emptyList()
		recordedMovements = emptyList()
		renderPaths = emptyList()
		solvedSignature = null
		roomSignature = null
		preflightReason = null
		routeRecording = null
	}

	override fun runChanged(runSequence: Long) {
		cleanupInput()
		state = State.WAITING
		lockedRun = Long.MIN_VALUE
		this.runSequence = runSequence
		roomSignature = null
		recoveryCounts.fill(0)
		preflightReason = null
		routeRecording = null
	}

	override fun worldReset() {
		if (lease != null && runSequence > 0L) lockedRun = runSequence
		cleanupInput()
		state = State.WAITING
		floors = emptyList()
		recordedFacings = emptyList()
		recordedMovements = emptyList()
		renderPaths = emptyList()
		solvedSignature = null
		roomSignature = null
		preflightReason = null
		routeRecording = null
	}

	private fun cleanupInput() {
		aim.clear()
		strafeKey?.let { lease?.release(it) }
		strafeKey = null
		lease?.close()
		lease = null
		inputSession = null
		warpTarget = null
		stairSource = null
		stairTarget = null
		pendingIndex = -1
		etherwarpSlot = -1
		interferenceReason = null
	}

	private companion object {
		const val START_DISTANCE = 0.42
		const val WARP_LANDING_DISTANCE = 0.72
		const val ROUTE_SOURCE_DISTANCE = 0.72
		const val CELL_PROGRESS_DISTANCE = 0.45
		const val STRAFE_CORRIDOR_DISTANCE = 0.80
		const val STAIR_SOURCE_DISTANCE = 0.55
		const val STAIR_ARRIVAL_DISTANCE = 0.45
		const val STAIR_CORRIDOR_DISTANCE = 0.65
		const val STAIR_VERTICAL_TOLERANCE = 0.85
		const val STAIR_PROJECTION_TOLERANCE = 0.15
		const val RECOVERY_LANDING_DISTANCE = 0.65
		const val AIM_TIMEOUT_MS = 2_000L
		const val ROUTE_WARP_OBSERVE_MS = 1_200L
		const val STRAFE_TIMEOUT_PER_CELL_MS = 2_000L
		const val TURN_TIMEOUT_MS = 5_000L
		const val STAIR_TIMEOUT_MS = 5_000L
		const val RECOVERY_LAND_TIMEOUT_MS = 1_000L
		const val FLOOR_RESET_TIMEOUT_MS = 10_000L
		const val MAX_RECOVERIES_PER_FLOOR = 3
		const val TURN_LOOK_DISTANCE = 8.0
		const val MAX_STAIR_HORIZONTAL = 5
	}
}

internal object IceFillMovementPlanner {
	fun shouldPromoteLateralRun(path: List<BlockPos>, cursor: Int, forward: Direction): Boolean {
		if (cursor !in 0 until path.lastIndex) return false
		val movement = direction(path[cursor], path[cursor + 1]) ?: return false
		if (movement != forward.clockWise && movement != forward.counterClockWise) return false
		var end = cursor + 1
		while (end < path.lastIndex && direction(path[end], path[end + 1]) == movement) end++
		val runLength = end - cursor
		val following = if (end < path.lastIndex) direction(path[end], path[end + 1]) else null
		return runLength >= MIN_PROMOTED_RUN_LENGTH || following == forward.opposite
	}

	private fun direction(from: BlockPos, to: BlockPos): Direction? = when {
		to.y != from.y -> null
		to.x - from.x == 1 && to.z == from.z -> Direction.EAST
		to.x - from.x == -1 && to.z == from.z -> Direction.WEST
		to.z - from.z == 1 && to.x == from.x -> Direction.SOUTH
		to.z - from.z == -1 && to.x == from.x -> Direction.NORTH
		else -> null
	}

	private const val MIN_PROMOTED_RUN_LENGTH = 2
}
