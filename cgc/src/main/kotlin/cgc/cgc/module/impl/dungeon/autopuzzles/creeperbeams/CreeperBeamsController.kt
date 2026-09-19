package cgc.cgc.module.impl.dungeon.autopuzzles.creeperbeams

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
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class CreeperBeamsController(private val settings: CreeperBeamsSubModule) : AutoPuzzleController {
	override val roomNames = setOf("Creeper Beams")

	private enum class State { WAITING, AIM_FIRST, WAIT_FIRST_FLIGHT, AIM_SECOND, WAIT_RESULT, WAIT_GREEN, DONE }
	private data class RenderPair(val pair: CreeperBeamSolver.WorldPair, val colorIndex: Int)

	private var state = State.WAITING
	private var lockedRun = Long.MIN_VALUE
	private var runSequence = 0L
	private var roomSignature: String? = null
	private var lease: AutoPuzzleInputOwner.Lease? = null
	private var inputSession: AutoPuzzleInputSession? = null
	private val aim = AutoPuzzleAimController()
	private var shortbow: AutoPuzzleItems.Shortbow? = null
	private var pairs = emptyList<CreeperBeamSolver.WorldPair>()
	private var pairIndex = 0
	private var attempt = 0
	private var resultWaitAtMs = 0L
	private var aimStartedAtMs = 0L
	private var aimedPoint: Vec3? = null
	private val updatedEndpoints = hashSetOf<BlockPos>()
	private val shotEndpoints = hashSetOf<BlockPos>()
	private var renderPairs = emptyList<RenderPair>()
	private var renderStartBox: AABB? = null

	override fun tickStart(client: Minecraft) {
		val session = inputSession ?: return
		val reason = session.cancellationReason(AutoPuzzleContext.monotonicNowMs()) ?: return
		stop("cancelled by $reason", terminal = true)
	}

	override fun tick(context: AutoPuzzleContext) {
		val start = AutoPuzzleRoomCoordinates.worldPosition(context.room, 0.5, 75.0, 0.5)
		renderStartBox = AABB(start.x - 0.5, start.y - 1.0, start.z - 0.5, start.x + 0.5, start.y, start.z + 0.5)
		if (!CreeperBeamSolver.endpointsLoaded(context)) {
			renderPairs = emptyList()
			if (state !in setOf(State.WAITING, State.DONE, State.WAIT_GREEN)) {
				stop("part of the Creeper Beams room became unloaded", terminal = true)
			}
			return
		}
		val observedPairs = CreeperBeamSolver.observe(context.level, context)
		val renderOrder = pairs.takeIf { it.isNotEmpty() } ?: observedPairs
		renderPairs = observedPairs.filter { it.active }.map { pair ->
			val index = renderOrder.indexOfFirst { it.sourceIndex == pair.sourceIndex }.takeIf { it >= 0 }
				?: observedPairs.indexOf(pair)
			RenderPair(pair, index)
		}

		val puzzleState = context.puzzleState(DungeonPuzzle.CREEPER_BEAMS)
		if (puzzleState == DungeonPuzzleState.GREEN && state == State.WAITING) {
			state = State.DONE
			return
		}
		if (puzzleState == DungeonPuzzleState.FAILED && state == State.WAITING && context.runSequence > 0L) {
			lockBeforeLease(context.runSequence, "the dungeon puzzle was already marked failed")
			return
		}
		if (puzzleState == DungeonPuzzleState.FAILED && state !in setOf(State.WAITING, State.DONE)) {
			stop("the dungeon puzzle was marked failed", terminal = true)
			return
		}
		if (state == State.WAIT_GREEN) {
			if (puzzleState == DungeonPuzzleState.GREEN) {
				state = State.DONE
			}
			return
		}
		if (state == State.DONE || lockedRun == context.runSequence) return
		if (roomSignature != null && roomSignature != context.roomSignature) {
			stop("room orientation changed", terminal = true)
			return
		}
		if (state != State.WAITING && (!context.player.isAlive || context.client.screen != null)) {
			stop(if (!context.player.isAlive) "the player is unavailable" else "a screen was opened", terminal = true)
			return
		}

		if (state == State.WAITING) {
			if (!context.dungeonStarted || context.runSequence <= 0L || !isOnStart(context, start)) return
			val bow = AutoPuzzleItems.firstShortbow(context) ?: return
			val selection = CreeperBeamSolver.selectAutomationPairs(CreeperBeamSolver.observe(context.level, context))
			val ready = selection as? CreeperBeamSolver.AutomationResult.Ready
				?: return lockBeforeLease(context.runSequence, (selection as CreeperBeamSolver.AutomationResult.Ineligible).reason)
			val session = AutoPuzzleInputSession.acquire(context.nowMs) ?: return
			val acquired = AutoPuzzleInputOwner.acquire("Creeper Beams", context.client) ?: return
			if (!AutoPuzzleItems.select(context, bow.slot)) {
				acquired.close()
				return
			}
			lease = acquired
			inputSession = session
			shortbow = bow
			pairs = ready.pairs
			pairIndex = 0
			attempt = 1
			runSequence = context.runSequence
			roomSignature = context.roomSignature
			updatedEndpoints.clear()
			shotEndpoints.clear()
			beginAim(context, first = true)
			return
		}

		if (context.runSequence != runSequence) {
			runChanged(context.runSequence)
			return
		}
		val interference = futurePairInterference(context)
		if (interference != null) {
			stop(interference, terminal = true)
			return
		}

		when (state) {
			State.AIM_FIRST -> tickAim(context, first = true)
			State.WAIT_FIRST_FLIGHT -> tickFirstFlight(context)
			State.AIM_SECOND -> tickAim(context, first = false)
			State.WAIT_RESULT -> tickResult(context)
			else -> Unit
		}
	}

	private fun beginAim(context: AutoPuzzleContext, first: Boolean) {
		val pair = pairs.getOrNull(pairIndex) ?: return completeLocalWork()
		val target = if (first) pair.first else pair.second
		val bow = shortbow ?: return stop("the selected shortbow is unavailable", terminal = true)
		val result = ProjectileAimPlanner.block(context, target, bow.terminator, unintendedEndpoints(target))
		val safePoint = (result as? ProjectileAimPlanner.Result.Safe)?.point
			?: return stop((result as ProjectileAimPlanner.Result.Unsafe).reason, terminal = true)
		aimedPoint = safePoint
		aim.start(
			context,
			safePoint,
			settings.aimSpeed.value.toDouble(),
			if (first && attempt == 1 && pairIndex == 0) AutoPuzzleAimProfile.NORMAL else AutoPuzzleAimProfile.CHAINED
		)
		state = if (first) State.AIM_FIRST else State.AIM_SECOND
		aimStartedAtMs = context.nowMs
	}

	private fun tickAim(context: AutoPuzzleContext, first: Boolean) {
		val pair = pairs.getOrNull(pairIndex) ?: return completeLocalWork()
		val target = if (first) pair.first else pair.second
		val bow = shortbow ?: return stop("the selected shortbow is unavailable", terminal = true)
		if (!AutoPuzzleItems.isShortbow(context.player.inventory.selectedItem)) {
			return stop("the selected hotbar item is no longer a shortbow", terminal = true)
		}
		if (target in updatedEndpoints && target !in shotEndpoints) {
			return stop("the current beam endpoint changed before its shot", terminal = true)
		}
		if (CreeperBeamSolver.state(context.level, target) != CreeperBeamSolver.EndpointState.SEA_LANTERN) {
			return stop("the current beam endpoint was no longer untouched before its shot", terminal = true)
		}
		val unintended = unintendedEndpoints(target)
		val currentPoint = aimedPoint
		if (currentPoint == null || !ProjectileAimPlanner.isSafeBlockAimPoint(context, currentPoint, target, bow.terminator, unintended)) {
			val result = ProjectileAimPlanner.block(context, target, bow.terminator, unintended)
			val safe = result as? ProjectileAimPlanner.Result.Safe
				?: return stop((result as ProjectileAimPlanner.Result.Unsafe).reason, terminal = true)
			aimedPoint = safe.point
			aim.start(context, safe.point, settings.aimSpeed.value.toDouble(), AutoPuzzleAimProfile.CHAINED)
		}
		val ready = aim.update(context).ready && ProjectileAimPlanner.liveRayHitsBlock(
			context,
			target,
			bow.terminator,
			unintended
		)
		if (!ready) {
			if (context.nowMs - aimStartedAtMs >= AIM_ACQUISITION_TIMEOUT_MS) {
				stop("could not acquire a safe live ray to the current beam endpoint", terminal = true)
			}
			return
		}
		if (!AutoPuzzleInteraction.useHeldItem(context)) return stop("could not fire the shortbow", terminal = true)
		shotEndpoints.add(target)
		if (first) {
			resultWaitAtMs = context.nowMs
			state = State.WAIT_FIRST_FLIGHT
		} else {
			resultWaitAtMs = context.nowMs
			state = State.WAIT_RESULT
		}
	}

	private fun tickFirstFlight(context: AutoPuzzleContext) {
		if (context.nowMs - resultWaitAtMs < FIRST_ARROW_HOLD_MS) return
		val pair = pairs.getOrNull(pairIndex) ?: return completeLocalWork()
		if (CreeperBeamSolver.state(context.level, pair.second) == CreeperBeamSolver.EndpointState.PRISMARINE) {
			resultWaitAtMs = context.nowMs
			state = State.WAIT_RESULT
		} else {
			beginAim(context, first = false)
		}
	}

	private fun tickResult(context: AutoPuzzleContext) {
		val pair = pairs.getOrNull(pairIndex) ?: return completeLocalWork()
		val first = CreeperBeamSolver.state(context.level, pair.first)
		val second = CreeperBeamSolver.state(context.level, pair.second)
		if (first == CreeperBeamSolver.EndpointState.PRISMARINE && second == CreeperBeamSolver.EndpointState.PRISMARINE) {
			pairIndex++
			attempt = 1
			updatedEndpoints.clear()
			shotEndpoints.clear()
			if (pairIndex >= pairs.size) completeLocalWork() else beginAim(context, first = true)
			return
		}
		if (first == CreeperBeamSolver.EndpointState.OTHER || second == CreeperBeamSolver.EndpointState.OTHER) {
			stop("a beam endpoint changed to an unexpected block", terminal = true)
			return
		}
		if (context.nowMs - resultWaitAtMs < PAIR_RESULT_WAIT_MS) return
		if (attempt >= MAX_PAIR_ATTEMPTS) {
			stop("the beam endpoints did not confirm after three shot cycles", terminal = true)
			return
		}
		attempt++
		updatedEndpoints.clear()
		when {
			first == CreeperBeamSolver.EndpointState.PRISMARINE -> {
				shotEndpoints.remove(pair.second)
				beginAim(context, first = false)
			}
			second == CreeperBeamSolver.EndpointState.PRISMARINE -> {
				shotEndpoints.remove(pair.first)
				beginAim(context, first = true)
			}
			else -> {
				shotEndpoints.clear()
				beginAim(context, first = true)
			}
		}
	}

	private fun futurePairInterference(context: AutoPuzzleContext): String? {
		for (index in 0 until pairIndex) {
			val pair = pairs[index]
			if (CreeperBeamSolver.state(context.level, pair.first) != CreeperBeamSolver.EndpointState.PRISMARINE ||
				CreeperBeamSolver.state(context.level, pair.second) != CreeperBeamSolver.EndpointState.PRISMARINE
			) return "a completed beam pair changed out of sequence"
		}
		for (index in pairIndex + 1 until pairs.size) {
			val pair = pairs[index]
			if (CreeperBeamSolver.state(context.level, pair.first) != CreeperBeamSolver.EndpointState.SEA_LANTERN ||
				CreeperBeamSolver.state(context.level, pair.second) != CreeperBeamSolver.EndpointState.SEA_LANTERN
			) return "a future beam pair changed out of sequence"
		}
		return null
	}

	private fun unintendedEndpoints(target: BlockPos): Set<BlockPos> =
		pairs.asSequence()
			.flatMap { sequenceOf(it.first, it.second) }
			.filter { it != target }
			.toSet()

	override fun blockChanged(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		val pair = pairs.getOrNull(pairIndex) ?: return
		if (pos == pair.first || pos == pair.second) updatedEndpoints.add(pos)
	}

	private fun completeLocalWork() {
		cleanupInput()
		state = State.WAIT_GREEN
	}

	override fun render(context: LevelRenderContext) {
		renderStartBox?.let { CgcRenderer3D.outlineBox(it, settings.startWaypointColor.value, depth = false) }
		val width = settings.lineThickness.value.toFloat()
		for (renderPair in renderPairs) {
			val pair = renderPair.pair
			val color = pairColor(renderPair.colorIndex)
			CgcRenderer3D.outlineBox(AABB(pair.first), color, depth = false)
			CgcRenderer3D.outlineBox(AABB(pair.second), color, depth = false)
			CgcRenderer3D.lineList(listOf(pair.first.center, pair.second.center), color, color, depth = false, width = width)
		}
	}

	override fun frame(client: Minecraft) = aim.frameUpdate(client)

	private fun pairColor(index: Int): Colour = when (index % 4) {
		0 -> settings.pair1Color.value
		1 -> settings.pair2Color.value
		2 -> settings.pair3Color.value
		else -> settings.pair4Color.value
	}

	private fun isOnStart(context: AutoPuzzleContext, point: Vec3): Boolean {
		if (!context.player.onGround()) return false
		val pos = context.player.position()
		val dx = pos.x - point.x
		val dz = pos.z - point.z
		return dx * dx + dz * dz <= START_DISTANCE_SQ && kotlin.math.abs(pos.y - point.y) <= START_Y_TOLERANCE
	}

	private fun lockBeforeLease(run: Long, reason: String) {
		lockedRun = run
		state = State.WAITING
		ChatUtils.chat("§c[Auto Puzzles] Creeper Beams stopped: $reason.")
	}

	override fun stop(reason: String, terminal: Boolean) {
		val wasActive = state !in setOf(State.WAITING, State.DONE)
		if (terminal && wasActive && runSequence > 0L) lockedRun = runSequence
		cleanupInput()
		state = State.WAITING
		if (wasActive) ChatUtils.chat("§c[Auto Puzzles] Creeper Beams stopped: $reason.")
	}

	override fun leaveRoom() {
		if (state !in setOf(State.WAITING, State.DONE, State.WAIT_GREEN)) stop("the Creeper Beams room was left", terminal = true)
		renderPairs = emptyList()
		renderStartBox = null
		roomSignature = null
	}

	override fun runChanged(runSequence: Long) {
		cleanupInput()
		pairs = emptyList()
		state = State.WAITING
		lockedRun = Long.MIN_VALUE
		this.runSequence = runSequence
		roomSignature = null
	}

	override fun worldReset() {
		val active = state !in setOf(State.WAITING, State.DONE)
		if (active && runSequence > 0L) lockedRun = runSequence
		cleanupInput()
		pairs = emptyList()
		state = State.WAITING
		renderPairs = emptyList()
		renderStartBox = null
		roomSignature = null
	}

	private fun cleanupInput() {
		aim.clear()
		lease?.close()
		lease = null
		inputSession = null
		shortbow = null
		aimedPoint = null
		aimStartedAtMs = 0L
		updatedEndpoints.clear()
		shotEndpoints.clear()
	}

	private companion object {
		const val START_DISTANCE_SQ = 0.18
		const val START_Y_TOLERANCE = 0.20
		const val FIRST_ARROW_HOLD_MS = 450L
		const val PAIR_RESULT_WAIT_MS = 1_200L
		const val MAX_PAIR_ATTEMPTS = 3
		const val AIM_ACQUISITION_TIMEOUT_MS = 2_000L
	}
}
