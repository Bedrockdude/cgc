package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.utils.ItemUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
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
		val plan = ProjectileAimPlanner.block(context, support)
		val point = (plan as? ProjectileAimPlanner.Result.Safe)?.point
			?: error((plan as ProjectileAimPlanner.Result.Unsafe).reason)
		require(context.player.eyePosition.distanceToSqr(point) <= range * range) { "Etherwarp target is out of range" }
		Target(support, point, Vec3(support.x + 0.5, support.y + 1.0, support.z + 0.5), range)
	}

	fun liveRayHits(context: AutoPuzzleContext, target: Target): Boolean {
		val eye = context.player.eyePosition
		val end = eye.add(context.player.lookAngle.scale(target.range))
		val hit = context.level.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.player))
		return hit.type == HitResult.Type.BLOCK && hit.blockPos == target.support
	}
}
