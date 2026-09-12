package cgc.cgc.navigation

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

interface NavigationWorld {
	fun loaded(pos: BlockPos): Boolean
	fun standable(feet: BlockPos): Boolean
	fun safeTeleportLanding(feet: BlockPos): Boolean
	fun lineClear(from: Vec3, to: Vec3): Boolean
	fun lineHits(from: Vec3, to: Vec3, target: BlockPos): Boolean
	fun firstCollision(from: Vec3, to: Vec3): BlockPos?
	fun raycastCollision(from: Vec3, to: Vec3): NavigationRayHit? =
		firstCollision(from, to)?.let { NavigationRayHit(it, Vec3.atCenterOf(it)) }
	fun predictEtherwarp(from: Vec3, yaw: Float, pitch: Float, distance: Double): NavigationEtherwarpPrediction? {
		val yawRadians = Math.toRadians(yaw.toDouble())
		val pitchRadians = Math.toRadians(pitch.toDouble())
		val cosPitch = kotlin.math.cos(pitchRadians)
		val direction = Vec3(-kotlin.math.sin(yawRadians) * cosPitch, -kotlin.math.sin(pitchRadians), kotlin.math.cos(yawRadians) * cosPitch)
		val hit = raycastCollision(from, from.add(direction.scale(distance))) ?: return null
		return NavigationEtherwarpPrediction(hit.block, hit.block.above(), hit.point)
	}
	fun clearancePenalty(feet: BlockPos): Double
	fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double = 0.08): Boolean
}

data class NavigationRayHit(val block: BlockPos, val point: Vec3)
data class NavigationEtherwarpPrediction(val targetBlock: BlockPos, val landingFeet: BlockPos, val point: Vec3)

class MinecraftNavigationWorld(private val level: ClientLevel) : NavigationWorld {
	override fun loaded(pos: BlockPos): Boolean = level.isLoaded(pos)

	override fun standable(feet: BlockPos): Boolean {
		if (!loaded(feet)) return false
		val body = AABB(feet.x + 0.2, feet.y.toDouble(), feet.z + 0.2, feet.x + 0.8, feet.y + 1.8, feet.z + 0.8)
		val support = level.getBlockState(feet.below()).getCollisionShape(level, feet.below())
		return !support.isEmpty && level.noCollision(body)
	}

	override fun safeTeleportLanding(feet: BlockPos): Boolean =
		// Confinement affects route quality, not physical validity. A centered player that
		// fully fits may be a necessary landing in a one-block-wide dungeon tunnel.
		standable(feet) && playerVolumeClear(center(feet), TELEPORT_MARGIN)

	override fun lineClear(from: Vec3, to: Vec3): Boolean =
		level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).type == HitResult.Type.MISS

	override fun lineHits(from: Vec3, to: Vec3, target: BlockPos): Boolean {
		val hit = level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
		return hit.type == HitResult.Type.BLOCK && hit.blockPos == target
	}

	override fun firstCollision(from: Vec3, to: Vec3): BlockPos? {
		val hit = level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
		return hit.blockPos.takeIf { hit.type == HitResult.Type.BLOCK }
	}

	override fun raycastCollision(from: Vec3, to: Vec3): NavigationRayHit? {
		return conservativeFirstBlock(from, to) { pos ->
			loaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
		}
	}

	override fun predictEtherwarp(from: Vec3, yaw: Float, pitch: Float, distance: Double): NavigationEtherwarpPrediction? {
		val yawRadians = Math.toRadians(yaw.toDouble())
		val pitchRadians = Math.toRadians(pitch.toDouble())
		val cosPitch = kotlin.math.cos(pitchRadians)
		val direction = Vec3(-kotlin.math.sin(yawRadians) * cosPitch, -kotlin.math.sin(pitchRadians), kotlin.math.cos(yawRadians) * cosPitch)
		val hit = conservativeFirstBlock(from, from.add(direction.scale(distance))) { pos ->
			!loaded(pos) || !isEtherwarpTransparent(level.getBlockState(pos).block)
		} ?: return null
		if (!loaded(hit.block)) return null
		val targetBlock = level.getBlockState(hit.block).block
		// These have fractional top heights that require a fractional landing state.
		if (targetBlock is FenceBlock || targetBlock is FenceGateBlock || targetBlock is WallBlock) return null
		val feet = hit.block.above()
		val head = hit.block.above(2)
		if (!loaded(feet) || !loaded(head)) return null
		if (!isValidEtherwarpSpace(level.getBlockState(feet).block)) return null
		if (!isValidEtherwarpSpace(level.getBlockState(head).block)) return null
		return NavigationEtherwarpPrediction(hit.block, feet, hit.point)
	}

	override fun clearancePenalty(feet: BlockPos): Double {
		if (!playerVolumeClear(center(feet), WALK_MARGIN)) return 20.0
		var penalty = 0.0
		for ((dx, dz) in CLEARANCE_OFFSETS) {
			val sample = center(feet).add(dx, 0.0, dz)
			if (!playerVolumeClear(sample, 0.0)) penalty += when {
				kotlin.math.abs(dx) <= 0.55 && kotlin.math.abs(dz) <= 0.55 -> 2.5
				kotlin.math.abs(dx) <= 1.05 && kotlin.math.abs(dz) <= 1.05 -> 0.8
				else -> 0.25
			}
		}
		if (!level.noCollision(playerBox(center(feet)).move(0.0, 0.35, 0.0))) penalty += 2.0
		return penalty
	}

	override fun movementCorridorClear(fromFeet: Vec3, toFeet: Vec3, extraMargin: Double): Boolean {
		val distance = fromFeet.distanceTo(toFeet)
		val samples = kotlin.math.ceil(distance / CORRIDOR_STEP).toInt().coerceAtLeast(1)
		for (index in 0..samples) {
			val sample = fromFeet.lerp(toFeet, index / samples.toDouble())
			if (!playerVolumeClear(sample, extraMargin)) return false
		}
		return true
	}

	private fun isValidEtherwarpSpace(block: Block): Boolean = isValidEtherwarpLandingSpace(block)

	private fun isEtherwarpTransparent(block: Block): Boolean = isEtherwarpRayTransparent(block)

	private companion object {
		const val PLAYER_HALF_WIDTH = 0.30
		const val PLAYER_HEIGHT = 1.80
		const val WALK_MARGIN = 0.06
		const val TELEPORT_MARGIN = 0.14
		const val CORRIDOR_STEP = 0.15
		val CLEARANCE_OFFSETS = buildList {
			for (radius in listOf(0.55, 1.0, 1.5)) {
				add(radius to 0.0); add(-radius to 0.0); add(0.0 to radius); add(0.0 to -radius)
				add(radius to radius); add(radius to -radius); add(-radius to radius); add(-radius to -radius)
			}
		}
	}

	private fun center(feet: BlockPos) = Vec3(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
	private fun playerBox(feet: Vec3, margin: Double = 0.0) = AABB(
		feet.x - PLAYER_HALF_WIDTH - margin, feet.y, feet.z - PLAYER_HALF_WIDTH - margin,
		feet.x + PLAYER_HALF_WIDTH + margin, feet.y + PLAYER_HEIGHT, feet.z + PLAYER_HALF_WIDTH + margin
	)
	private fun playerVolumeClear(feet: Vec3, margin: Double) = level.noCollision(playerBox(feet, margin))
}

internal fun isValidEtherwarpLandingSpace(block: Block): Boolean =
	isEtherwarpRayTransparent(block) && block !is LadderBlock && block !is FlowerPotBlock && block !is SkullBlock && block !is WallSkullBlock

internal fun isEtherwarpRayTransparent(block: Block): Boolean = when (block) {
		is ButtonBlock, is DoublePlantBlock, is SkullBlock, is WallSkullBlock, is SaplingBlock,
		is FlowerBlock, is StemBlock, is CropBlock, is RailBlock, is BubbleColumnBlock,
		is SnowLayerBlock, is TripWireBlock, is TripWireHookBlock, is FireBlock, is AirBlock,
		is TorchBlock, is VineBlock, is LadderBlock, is TallFlowerBlock, is TallDryGrassBlock,
		is BushBlock, is SeagrassBlock, is TallSeagrassBlock, is SugarCaneBlock, is LiquidBlock,
		is MushroomBlock, is TallGrassBlock, is PistonHeadBlock, is WebBlock, is ShortDryGrassBlock,
		is DryVegetationBlock, is SmallDripleafBlock, is LeverBlock, is NetherWartBlock,
		is NetherPortalBlock, is RedStoneWireBlock, is ComparatorBlock, is RedstoneTorchBlock,
		is RepeaterBlock -> true
		else -> false
	}
