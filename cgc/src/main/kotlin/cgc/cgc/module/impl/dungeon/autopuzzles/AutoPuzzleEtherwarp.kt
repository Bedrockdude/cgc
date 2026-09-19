package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.utils.ItemUtils
import cgc.cgc.navigation.MinecraftNavigationWorld
import cgc.cgc.navigation.collisionPartAimCandidates
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object AutoPuzzleEtherwarp {
	data class Target(val support: BlockPos, val aimPoint: Vec3, val expectedFeet: Vec3, val range: Double)

	fun target(context: AutoPuzzleContext, support: BlockPos): Result<Target> = runCatching {
		require(context.level.isLoaded(support)) { "Etherwarp target is not loaded" }
		val state = context.level.getBlockState(support)
		require(!state.getCollisionShape(context.level, support).isEmpty) { "Etherwarp target is not solid" }
		require(context.level.getBlockState(support.above()).getCollisionShape(context.level, support.above()).isEmpty) {
			"Etherwarp landing feet are obstructed"
		}
		require(context.level.getBlockState(support.above(2)).getCollisionShape(context.level, support.above(2)).isEmpty) {
			"Etherwarp landing head is obstructed"
		}
		val held = context.player.inventory.selectedItem
		require(ItemUtils.isEtherwarp(held)) { "the selected item is not Etherwarp-capable" }
		val range = (57 + ItemUtils.tunerDistance(held)).toDouble()
		val eye = context.player.position().add(0.0, context.player.getEyeHeight(Pose.CROUCHING).toDouble(), 0.0)
		val world = MinecraftNavigationWorld(context.level)
		val point = collisionPartAimCandidates(state.getCollisionShape(context.level, support), support)
			.asSequence()
			.filter { eye.distanceToSqr(it) <= range * range }
			.mapNotNull { candidate ->
				val rotation = rotationTo(eye, candidate)
				val prediction = world.predictEtherwarp(eye, rotation.first, rotation.second, range) ?: return@mapNotNull null
				prediction.point.takeIf { prediction.targetBlock == support && prediction.landingFeet == support.above() }
			}
			.minByOrNull { eye.distanceToSqr(it) }
			?: error("Etherwarp target is not visible to the navigation raycast")
		Target(support, point, Vec3(support.x + 0.5, support.y + 1.0, support.z + 0.5), range)
	}

	fun liveRayHits(context: AutoPuzzleContext, target: Target): Boolean {
		val crosshair = context.client.hitResult as? BlockHitResult
		if (crosshair?.type == HitResult.Type.BLOCK && crosshair.blockPos == target.support) return true
		val prediction = MinecraftNavigationWorld(context.level).predictEtherwarp(
			context.player.eyePosition,
			context.player.yRot,
			context.player.xRot,
			target.range
		) ?: return false
		return prediction.targetBlock == target.support && prediction.landingFeet == target.support.above()
	}

	private fun rotationTo(from: Vec3, target: Vec3): Pair<Float, Float> {
		val delta = target.subtract(from)
		val horizontal = kotlin.math.sqrt(delta.x * delta.x + delta.z * delta.z)
		return (Math.toDegrees(kotlin.math.atan2(delta.z, delta.x)).toFloat() - 90.0f) to
			-Math.toDegrees(kotlin.math.atan2(delta.y, horizontal)).toFloat()
	}
}
