package cgc.cgc.navigation

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertTrue

class HybridPathfinderTest {
	@Test
	fun `short destination permits a bounded setup correction`() {
		val route = HybridPathfinder(OpenWorld()).find(
			BlockPos(0, 64, 0), BlockPos(3, 64, 0), -90f,
			NavigationOptions(allowEtherwarp = false, planningBudgetMs = 500)
		)
		assertTrue(route.reachedGoal, "route=$route")
		assertTrue(route.actions.all { it.type == NavigationActionType.WALK })
	}

	@Test
	fun `long route buffers multiple Etherwarps`() {
		val route = HybridPathfinder(OpenWorld()).find(
			BlockPos(0, 64, 0), BlockPos(80, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000)
		)
		assertTrue(route.actions.count { it.type == NavigationActionType.ETHERWARP } >= 2)
		assertTrue(route.actions.all { it.type == NavigationActionType.ETHERWARP || it.type in SETUP_TYPES })
		assertTrue(maxSetupRun(route.actions) <= 3)
	}

	@Test
	fun `uses only a short setup movement to expose an Etherwarp`() {
		val route = HybridPathfinder(PeekWorld()).find(
			BlockPos(0, 64, 0), BlockPos(20, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000, maxExpansions = 40_000)
		)
		assertTrue(route.reachedGoal)
		assertTrue(route.actions.first().type == NavigationActionType.WALK)
		assertTrue(route.actions.any { it.type == NavigationActionType.ETHERWARP })
		assertTrue(maxSetupRun(route.actions) <= 3)
	}

	@Test
	fun `confirmed raycast target is not crowded out by speculative blocks`() {
		val route = HybridPathfinder(ConfirmedTargetWorld()).find(
			BlockPos(0, 64, 0), BlockPos(50, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000)
		)
		assertTrue(route.actions.firstOrNull()?.type == NavigationActionType.ETHERWARP)
	}

	@Test
	fun `aligned Etherwarp wins over a slightly farther sideways target`() {
		val route = HybridPathfinder(AlignmentWorld()).find(
			BlockPos(0, 64, 0), BlockPos(60, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000)
		)
		val firstWarp = route.actions.first { it.type == NavigationActionType.ETHERWARP }
		assertTrue(firstWarp.end.z == 0, "expected aligned landing, got ${firstWarp.end}")
	}

	@Test
	fun `first scan uses actual eye position when standing on a ledge`() {
		val route = HybridPathfinder(LedgeWorld()).find(
			BlockPos(0, 64, 0), BlockPos(40, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000),
			initialEye = Vec3(0.95, 65.54, 0.5)
		)
		assertTrue(route.actions.firstOrNull()?.type == NavigationActionType.ETHERWARP)
	}

	@Test
	fun `confined short Etherwarp is returned instead of an empty route`() {
		val route = HybridPathfinder(TightTunnelWorld()).find(
			BlockPos(0, 64, 0), BlockPos(30, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000)
		)
		assertTrue(route.actions.firstOrNull()?.type == NavigationActionType.ETHERWARP)
	}

	@Test
	fun `fine angular scan discovers a narrow tunnel floor target`() {
		val route = HybridPathfinder(NarrowAngleTunnelWorld()).find(
			BlockPos(0, 64, 0), BlockPos(40, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000)
		)
		assertTrue(route.actions.firstOrNull()?.type == NavigationActionType.ETHERWARP)
	}

	@Test
	fun `exact visible hit point is preserved when block center is occluded`() {
		val world = EdgeVisibleWorld()
		val route = HybridPathfinder(world).find(
			BlockPos(0, 64, 0), BlockPos(30, 64, 0), -90f,
			NavigationOptions(planningBudgetMs = 1_000)
		)
		val warp = route.actions.first { it.type == NavigationActionType.ETHERWARP }
		assertTrue(warp.targetPoint == world.visiblePoint)
	}

	@Test
	fun `candidate outside actual tuner range is never emitted`() {
		val world = RangeAwareWorld()
		val shortRange = HybridPathfinder(world).find(
			BlockPos(0, 64, 0), BlockPos(30, 64, 0), -90f,
			NavigationOptions(etherwarpRange = 57.0, planningBudgetMs = 500)
		)
		val tunedRange = HybridPathfinder(world).find(
			BlockPos(0, 64, 0), BlockPos(30, 64, 0), -90f,
			NavigationOptions(etherwarpRange = 61.0, planningBudgetMs = 500)
		)
		assertTrue(shortRange.actions.none { it.type == NavigationActionType.ETHERWARP })
		assertTrue(tunedRange.actions.any { it.type == NavigationActionType.ETHERWARP })
	}

	private fun maxSetupRun(actions: List<NavigationAction>): Int {
		var current = 0
		var maximum = 0
		for (action in actions) {
			current = if (action.type in setOf(NavigationActionType.WALK, NavigationActionType.ASCEND, NavigationActionType.DESCEND)) current + 1 else 0
			maximum = maxOf(maximum, current)
		}
		return maximum
	}

	private class OpenWorld : NavigationWorld {
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = standable(feet)
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = true
		override fun firstCollision(from: Vec3, to: Vec3): BlockPos? =
			from.lerp(to, 0.5).let(BlockPos::containing).let { BlockPos(it.x, 63, it.z) }
		override fun clearancePenalty(feet: BlockPos) = 0.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class PeekWorld : NavigationWorld {
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet.y == 64 && feet.z == 1
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = from.z > 1.0
		override fun firstCollision(from: Vec3, to: Vec3): BlockPos? =
			if (from.z > 1.0) BlockPos(BlockPos.containing(to).x, 63, 1) else null
		override fun clearancePenalty(feet: BlockPos) = 0.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class ConfirmedTargetWorld : NavigationWorld {
		private val support = BlockPos(30, 63, 0)
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet == support.above()
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = target == support
		override fun firstCollision(from: Vec3, to: Vec3) = support
		override fun clearancePenalty(feet: BlockPos) = 0.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class AlignmentWorld : NavigationWorld {
		private val aligned = BlockPos(30, 63, 0)
		private val sideways = BlockPos(34, 63, 14)
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet == aligned.above() || feet == sideways.above()
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = target == aligned || target == sideways
		override fun firstCollision(from: Vec3, to: Vec3) = if (kotlin.math.abs(to.z - from.z) < 4.0) aligned else sideways
		override fun clearancePenalty(feet: BlockPos) = 0.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class LedgeWorld : NavigationWorld {
		private val support = BlockPos(30, 63, 0)
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet == support.above()
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = from.x > .8 && target == support
		override fun firstCollision(from: Vec3, to: Vec3) = support.takeIf { from.x > .8 }
		override fun clearancePenalty(feet: BlockPos) = 0.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class TightTunnelWorld : NavigationWorld {
		private val support = BlockPos(8, 63, 0)
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet == support.above()
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = target == support
		override fun firstCollision(from: Vec3, to: Vec3) = support
		override fun clearancePenalty(feet: BlockPos) = 8.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class NarrowAngleTunnelWorld : NavigationWorld {
		private val support = BlockPos(10, 63, 0)
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet == support.above()
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = target == support
		override fun firstCollision(from: Vec3, to: Vec3): BlockPos? = null
		override fun raycastCollision(from: Vec3, to: Vec3): NavigationRayHit? {
			val delta = to.subtract(from)
			val yaw = Math.toDegrees(kotlin.math.atan2(delta.z, delta.x)).toFloat() - 90f
			val pitch = -Math.toDegrees(kotlin.math.atan2(delta.y, kotlin.math.sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
			return if (kotlin.math.abs(net.minecraft.util.Mth.wrapDegrees(yaw + 90f)) < .55f && kotlin.math.abs(pitch - 6f) < .55f) {
				NavigationRayHit(support, Vec3.atCenterOf(support))
			} else null
		}
		override fun clearancePenalty(feet: BlockPos) = 7.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class EdgeVisibleWorld : NavigationWorld {
		private val support = BlockPos(20, 63, 0)
		val visiblePoint = Vec3(20.02, 63.85, 0.5)
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet == support.above()
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = false
		override fun firstCollision(from: Vec3, to: Vec3): BlockPos? = null
		override fun raycastCollision(from: Vec3, to: Vec3) = NavigationRayHit(support, visiblePoint)
		override fun clearancePenalty(feet: BlockPos) = 0.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private class RangeAwareWorld : NavigationWorld {
		private val target = BlockPos(20, 63, 0)
		override fun loaded(pos: BlockPos) = true
		override fun standable(feet: BlockPos) = feet.y == 64
		override fun safeTeleportLanding(feet: BlockPos) = feet == target.above()
		override fun lineClear(from: Vec3, to: Vec3) = true
		override fun lineHits(from: Vec3, to: Vec3, target: BlockPos) = true
		override fun firstCollision(from: Vec3, to: Vec3): BlockPos? = null
		override fun predictEtherwarp(from: Vec3, yaw: Float, pitch: Float, distance: Double) =
			if (distance >= 60.0) NavigationEtherwarpPrediction(target, target.above(), Vec3.atCenterOf(target)) else null
		override fun clearancePenalty(feet: BlockPos) = 0.0
		override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double) = true
	}

	private companion object {
		val SETUP_TYPES = setOf(NavigationActionType.WALK, NavigationActionType.ASCEND, NavigationActionType.DESCEND)
	}
}
