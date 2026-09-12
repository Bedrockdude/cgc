package cgc.cgc.navigation

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.sqrt

class HybridPathfinder(
	private val world: NavigationWorld,
	private val costs: MovementCostModel = MovementCostModel()
) {
	private val etherwarpCache = hashMapOf<EtherwarpScanKey, List<EtherwarpCandidate>>()
	fun find(
		start: BlockPos,
		goal: BlockPos,
		initialYaw: Float,
		options: NavigationOptions = NavigationOptions(),
		failedEdges: Set<NavigationEdgeKey> = emptySet(),
		initialPitch: Float = 0f,
		initialEye: Vec3? = null
	): NavigationRoute {
		val began = System.nanoTime()
		val deadline = began + options.planningBudgetMs * 1_000_000L
		val queue = PriorityQueue<Node>(compareBy { it.f })
		val bestByState = hashMapOf<SearchKey, Double>()
		val initial = Node(State(start, heading(initialYaw), pitchBucket(initialPitch), 0, null), 0.0, heuristic(start, goal), null, null)
		queue.add(initial)
		bestByState[searchKey(initial.state)] = 0.0
		var best = initial
		var reached: Node? = null
		var expanded = 0
		var walks = 0
		var etherwarps = 0
		var collision = 0
		var rays = 0

		while (queue.isNotEmpty() && expanded < options.maxExpansions && System.nanoTime() < deadline) {
			val node = queue.remove()
			if (node.g != bestByState[searchKey(node.state)]) continue
			expanded++
			if (distance(node.state.pos, goal) <= options.arrivalRadius) { reached = node; break }
			if (partialRouteScore(node, goal) < partialRouteScore(best, goal)) best = node

			val edges = arrayListOf<NavigationAction>()
			if (node.state.setupMoves < options.maxConsecutiveSetupMoves) for ((dx, dz) in DIRECTIONS) {
				val horizontal = node.state.pos.offset(dx, 0, dz)
				val end = when {
					world.standable(horizontal) -> horizontal
					world.standable(horizontal.above()) -> horizontal.above()
					else -> horizontal.below().takeIf(world::standable)
				} ?: continue
				collision++
				val type = when {
					end.y > node.state.pos.y -> NavigationActionType.ASCEND
					end.y < node.state.pos.y -> NavigationActionType.DESCEND
					else -> NavigationActionType.WALK
				}
				val corridorClear = if (type == NavigationActionType.ASCEND) {
					world.movementCorridorClear(center(node.state.pos, 0.0), center(node.state.pos.above(), 0.0)) &&
						world.movementCorridorClear(center(node.state.pos.above(), 0.0), center(end, 0.0))
				} else world.movementCorridorClear(center(node.state.pos, 0.0), center(end, 0.0))
				if (!corridorClear) continue
				val yaw = yaw(node.state.pos, end)
				val time = costs.walkBlockMs * if (dx != 0 && dz != 0) 1.414 else 1.0
					edges += NavigationAction(type, node.state.pos, end, time + if (type == NavigationActionType.ASCEND) costs.jumpMs else 0.0,
					riskPenaltyMs = world.clearancePenalty(end) * 42.0, rotationPenaltyMs = costs.rotationMs(yaw(node.state.heading), yaw),
					switchPenaltyMs = costs.switchMs(node.state.previous, type), yaw = yaw)
				walks++
			}

			if (options.allowEtherwarp) {
				val eye = if (node.parent == null) initialEye ?: center(node.state.pos, CROUCH_EYE_HEIGHT) else center(node.state.pos, CROUCH_EYE_HEIGHT)
				for (candidate in etherwarpCandidates(node.state.pos, goal, eye, options.etherwarpRange)) {
					val support = candidate.targetBlock
					val end = candidate.landingFeet
					if (end == node.state.pos) continue
					if (!world.safeTeleportLanding(end)) continue
					val point = candidate.point
					if (eye.distanceTo(point) > options.etherwarpRange + 0.1) continue
					rays++; collision++
					val yp = candidate.yaw to candidate.pitch
					val geometryPenalty = etherwarpGeometryPenalty(node.state.pos, end, goal)
					edges += NavigationAction(NavigationActionType.ETHERWARP, node.state.pos, end, costs.etherwarpUseMs,
					world.clearancePenalty(end) * 75.0 + geometryPenalty,
					costs.rotationMs(yaw(node.state.heading), pitch(node.state.pitch), yp.first, yp.second),
					costs.switchMs(node.state.previous, NavigationActionType.ETHERWARP), yp.first, yp.second, support, point)
				etherwarps++
				}
			}

			for (edge in edges) {
				if (NavigationEdgeKey(edge.type, edge.start, edge.end) in failedEdges) continue
				val setupMoves = if (edge.type in WALK_TYPES) node.state.setupMoves + 1 else 0
				val state = State(edge.end, heading(edge.yaw ?: yaw(node.state.heading)), pitchBucket(edge.pitch ?: pitch(node.state.pitch)), setupMoves, edge.type)
				val key = searchKey(state)
				val g = node.g + edge.totalCostMs
				if (g >= bestByState.getOrDefault(key, Double.POSITIVE_INFINITY)) continue
				bestByState[key] = g
				queue += Node(state, g, g + options.heuristicWeight * heuristic(edge.end, goal), node, edge)
			}
		}

		val terminal = reached ?: best
		val rawActions = generateSequence(terminal) { it.parent }.mapNotNull { it.action }.toList().asReversed()
		// A partial route may only commit setup movement when a known Etherwarp follows it.
		// This prevents successive replans from degrading into unlimited ordinary walking.
		val actions = if (reached != null) rawActions else rawActions.dropLastWhile { it.type in WALK_TYPES }
		return NavigationRoute(actions, reached != null, NavigationDiagnostics(
			(System.nanoTime() - began) / 1_000_000L, expanded, walks, etherwarps, collision, rays
		))
	}

	private fun etherwarpCandidates(from: BlockPos, goal: BlockPos, eye: Vec3, range: Double): List<EtherwarpCandidate> {
		val key = EtherwarpScanKey(from, quantizeEyeOffset(eye.x - from.x), quantizeEyeOffset(eye.z - from.z))
		val discovered = etherwarpCache.getOrPut(key) { scanEtherwarpSphere(eye, goal, range) }
		return discovered.asSequence()
			.filter { world.loaded(it.targetBlock) }
			.filter { world.safeTeleportLanding(it.landingFeet) }
			.sortedBy { candidatePriority(from, it.landingFeet, goal) }
			.take(MAX_ETHERWARP_CANDIDATES)
			.toList()
	}

	private fun scanEtherwarpSphere(eye: Vec3, goal: BlockPos, range: Double): List<EtherwarpCandidate> {
		val byLanding = linkedMapOf<BlockPos, EtherwarpCandidate>()
		val direct = rotation(eye, Vec3.atCenterOf(goal))
		// A one-degree goal cone preserves precision down long narrow corridors.
		for (pitchOffset in -GOAL_CONE_DEGREES..GOAL_CONE_DEGREES step GOAL_CONE_STEP_DEGREES) {
			for (yawOffset in -GOAL_CONE_DEGREES..GOAL_CONE_DEGREES step GOAL_CONE_STEP_DEGREES) {
				scanRay(eye, direct.first + yawOffset, direct.second + pitchOffset, range, byLanding)
			}
		}
		if (byLanding.values.count { world.safeTeleportLanding(it.landingFeet) } >= MIN_GOAL_CONE_LANDINGS) {
			return byLanding.values.toList()
		}
		// RSA-style spherical scan discovers surfaces unrelated to the direct goal bearing.
		var pitch = -88f
		while (pitch <= 88f) {
			val cosPitch = kotlin.math.cos(Math.toRadians(pitch.toDouble())).coerceAtLeast(0.05)
			val yawStep = (SPHERE_YAW_STEP_DEGREES / cosPitch).toFloat()
			var yaw = 0f
			while (yaw < 360f) {
				scanRay(eye, yaw, pitch, range, byLanding)
				yaw += yawStep
			}
			pitch += SPHERE_PITCH_STEP_DEGREES
		}
		return byLanding.values.toList()
	}

	private fun scanRay(eye: Vec3, yawDegrees: Float, pitchDegrees: Float, range: Double, out: MutableMap<BlockPos, EtherwarpCandidate>) {
		val pitch = pitchDegrees.coerceIn(-89f, 89f)
		val prediction = world.predictEtherwarp(eye, yawDegrees, pitch, range) ?: return
		val landing = prediction.landingFeet
		val candidate = EtherwarpCandidate(prediction.targetBlock, landing, prediction.point, yawDegrees, pitch)
		val previous = out[landing]
		if (previous == null || eye.distanceToSqr(prediction.point) > eye.distanceToSqr(previous.point)) out[landing] = candidate
	}

	private fun quantizeEyeOffset(offset: Double): Int = kotlin.math.round(offset * 8.0).toInt()
	private fun searchKey(state: State) = SearchKey(state.pos, state.setupMoves, state.previous)

	private fun etherwarpGeometryPenalty(from: BlockPos, end: BlockPos, goal: BlockPos): Double {
		val progress = distance(from, goal) - distance(end, goal)
		val warpDistance = distance(from, end)
		val lateral = lateralDistance(from, end, goal)
		val backwardPenalty = (-progress).coerceAtLeast(0.0) * 180.0
		val weakProgressPenalty = (MIN_GOOD_WARP_PROGRESS - progress).coerceAtLeast(0.0) * 35.0
		val lateralPenalty = lateral * 45.0
		val shortPenalty = (PREFERRED_WARP_DISTANCE - warpDistance).coerceAtLeast(0.0) * 7.0
		return backwardPenalty + weakProgressPenalty + lateralPenalty + shortPenalty
	}

	private fun candidatePriority(from: BlockPos, end: BlockPos, goal: BlockPos): Double {
		val progress = distance(from, goal) - distance(end, goal)
		return distance(end, goal) + lateralDistance(from, end, goal) * 1.75 - progress * 0.35
	}

	private fun lateralDistance(from: BlockPos, end: BlockPos, goal: BlockPos): Double {
		val gx = (goal.x - from.x).toDouble()
		val gy = (goal.y - from.y).toDouble()
		val gz = (goal.z - from.z).toDouble()
		val goalLength = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(1.0)
		val mx = (end.x - from.x).toDouble()
		val my = (end.y - from.y).toDouble()
		val mz = (end.z - from.z).toDouble()
		val projection = (mx * gx + my * gy + mz * gz) / goalLength
		val movementSquared = mx * mx + my * my + mz * mz
		return sqrt((movementSquared - projection * projection).coerceAtLeast(0.0))
	}

	private fun heuristic(a: BlockPos, b: BlockPos): Double = distance(a, b) / 55.0 * costs.etherwarpUseMs * 0.75
	private fun partialRouteScore(node: Node, goal: BlockPos): Double =
		distance(node.state.pos, goal) + node.g / PARTIAL_COST_SCALE_MS
	private fun distance(a: BlockPos, b: BlockPos): Double { val x=a.x-b.x; val y=a.y-b.y; val z=a.z-b.z; return sqrt((x*x+y*y+z*z).toDouble()) }
	private fun center(p: BlockPos, y: Double) = Vec3(p.x + .5, p.y + y, p.z + .5)
	private fun yaw(a: BlockPos, b: BlockPos) = Math.toDegrees(atan2((b.z-a.z).toDouble(), (b.x-a.x).toDouble())).toFloat()-90f
	private fun heading(yaw: Float) = ((kotlin.math.round((yaw % 360 + 360) % 360 / 45f).toInt()) and 7)
	private fun yaw(bucket: Int) = bucket * 45f
	private fun pitchBucket(pitch: Float) = kotlin.math.round(pitch.coerceIn(-70f, 70f) / 10f).toInt()
	private fun pitch(bucket: Int) = bucket * 10f
	private fun rotation(from: Vec3, to: Vec3): Pair<Float, Float> { val dx=to.x-from.x; val dy=to.y-from.y; val dz=to.z-from.z; val h=sqrt(dx*dx+dz*dz); return (Math.toDegrees(atan2(dz,dx)).toFloat()-90f) to -Math.toDegrees(atan2(dy,h)).toFloat() }
	private data class State(val pos: BlockPos, val heading: Int, val pitch: Int, val setupMoves: Int, val previous: NavigationActionType?)
	private data class SearchKey(val pos: BlockPos, val setupMoves: Int, val previous: NavigationActionType?)
	private data class Node(val state: State, val g: Double, val f: Double, val parent: Node?, val action: NavigationAction?)
	private data class EtherwarpCandidate(val targetBlock: BlockPos, val landingFeet: BlockPos, val point: Vec3, val yaw: Float, val pitch: Float)
	private data class EtherwarpScanKey(val pos: BlockPos, val eyeOffsetX: Int, val eyeOffsetZ: Int)
	private companion object {
		val DIRECTIONS = listOf(1 to 0,-1 to 0,0 to 1,0 to -1,1 to 1,1 to -1,-1 to 1,-1 to -1)
		val WALK_TYPES = setOf(NavigationActionType.WALK, NavigationActionType.ASCEND, NavigationActionType.DESCEND)
		const val MAX_ETHERWARP_CANDIDATES = 64
		const val CROUCH_EYE_HEIGHT = 1.27
		const val GOAL_CONE_DEGREES = 14
		const val GOAL_CONE_STEP_DEGREES = 1
		const val MIN_GOAL_CONE_LANDINGS = 8
		const val SPHERE_YAW_STEP_DEGREES = 4.0
		const val SPHERE_PITCH_STEP_DEGREES = 4.0f
		const val MIN_GOOD_WARP_PROGRESS = 10.0
		const val PREFERRED_WARP_DISTANCE = 35.0
		// Partial routes still need to advance through confined areas where every legal
		// landing is expensive. This scale prevents a safe short warp losing to no action.
		const val PARTIAL_COST_SCALE_MS = 400.0
	}
}
