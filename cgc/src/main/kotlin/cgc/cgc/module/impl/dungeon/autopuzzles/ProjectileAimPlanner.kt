package cgc.cgc.module.impl.dungeon.autopuzzles

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object ProjectileAimPlanner {
	sealed interface Result {
		data class Safe(val point: Vec3) : Result
		data class Unsafe(val reason: String) : Result
	}

	internal enum class FirstHit {
		TARGET,
		UNINTENDED,
		OTHER
	}

	fun entity(
		context: AutoPuzzleContext,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean
	): Result {
		val box = target.boundingBox
		val targetPoints = SAMPLE_OFFSETS.map { (x, y, z) ->
			Vec3(
				box.minX + (box.maxX - box.minX) * x,
				box.minY + (box.maxY - box.minY) * y,
				box.minZ + (box.maxZ - box.minZ) * z
			)
		}
		return candidateAimPoints(context.player.eyePosition, targetPoints, terminator)
			.firstOrNull { point -> isSafeEntityAim(context, point, target, unintendedTargets, terminator) }
			?.let(Result::Safe)
			?: Result.Unsafe(
				if (terminator) "no safe three-arrow fan reaches the intended target first"
				else "no visible shot reaches the intended target first"
			)
	}

	fun block(
		context: AutoPuzzleContext,
		target: BlockPos,
		terminator: Boolean = false,
		unintendedTargets: Collection<BlockPos> = emptySet()
	): Result {
		if (!context.level.isLoaded(target)) return Result.Unsafe("target block is not loaded")
		val shape = context.level.getBlockState(target).getShape(context.level, target)
		if (shape.isEmpty) return Result.Unsafe("target block has no visible collision shape")
		val bounds = shape.bounds().move(target)
		val targetPoints = SAMPLE_OFFSETS.map { (x, y, z) ->
			Vec3(
				bounds.minX + (bounds.maxX - bounds.minX) * x,
				bounds.minY + (bounds.maxY - bounds.minY) * y,
				bounds.minZ + (bounds.maxZ - bounds.minZ) * z
			)
		}
		return candidateAimPoints(context.player.eyePosition, targetPoints, terminator)
			.firstOrNull { point ->
				isSafeBlockAim(
					context = context,
					centerDirection = point.subtract(context.player.eyePosition).normalize(),
					target = target,
					unintendedTargets = unintendedTargets,
					terminator = terminator,
					blockMode = ClipContext.Block.OUTLINE
				)
			}
			?.let(Result::Safe)
			?: Result.Unsafe(
				if (terminator) "no safe three-arrow fan reaches the target block first"
				else "target block is not visible"
			)
	}

	fun liveRayHitsEntity(
		context: AutoPuzzleContext,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean
	): Boolean {
		if (!target.isAlive) return false
		return isSafeEntityDirection(context, context.player.lookAngle, target, unintendedTargets, terminator)
	}

	fun liveRayHitsBlock(
		context: AutoPuzzleContext,
		target: BlockPos,
		terminator: Boolean = false,
		unintendedTargets: Collection<BlockPos> = emptySet()
	): Boolean {
		if (!context.level.isLoaded(target)) return false
		return isSafeBlockAim(
			context = context,
			centerDirection = context.player.lookAngle,
			target = target,
			unintendedTargets = unintendedTargets,
			terminator = terminator,
			blockMode = ClipContext.Block.COLLIDER
		)
	}

	internal fun isSafeFan(firstHits: Collection<FirstHit>): Boolean =
		FirstHit.TARGET in firstHits && FirstHit.UNINTENDED !in firstHits

	private fun isSafeEntityAim(
		context: AutoPuzzleContext,
		aimPoint: Vec3,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean
	): Boolean {
		val direction = aimPoint.subtract(context.player.eyePosition)
		if (direction.lengthSqr() < EPSILON) return false
		return isSafeEntityDirection(context, direction.normalize(), target, unintendedTargets, terminator)
	}

	private fun isSafeEntityDirection(
		context: AutoPuzzleContext,
		centerDirection: Vec3,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean
	): Boolean {
		val hits = projectileDirections(centerDirection, terminator).map { direction ->
			firstEntityHit(context, direction, target, unintendedTargets)
		}
		return isSafeFan(hits)
	}

	private fun firstEntityHit(
		context: AutoPuzzleContext,
		direction: Vec3,
		target: Entity,
		unintendedTargets: Collection<Entity>
	): FirstHit {
		val eye = context.player.eyePosition
		val end = eye.add(direction.normalize().scale(PROJECTILE_CHECK_RANGE))
		val blockHit = context.level.clip(
			ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.player)
		)
		val blockDistance = if (blockHit.type == HitResult.Type.MISS) {
			Double.POSITIVE_INFINITY
		} else {
			eye.distanceToSqr(blockHit.location)
		}

		fun entityDistance(entity: Entity): Double? {
			if (!entity.isAlive) return null
			val hit = entity.boundingBox.inflate(ENTITY_BOX_INFLATION).clip(eye, end).orElse(null) ?: return null
			val distance = eye.distanceToSqr(hit)
			return distance.takeIf { it <= blockDistance + EPSILON }
		}

		val targetDistance = entityDistance(target)
		val unintendedDistance = unintendedTargets.asSequence()
			.filter { it.id != target.id }
			.mapNotNull(::entityDistance)
			.minOrNull()
		if (unintendedDistance != null && (targetDistance == null || unintendedDistance <= targetDistance + EPSILON)) {
			return FirstHit.UNINTENDED
		}
		return if (targetDistance != null) FirstHit.TARGET else FirstHit.OTHER
	}

	private fun isSafeBlockAim(
		context: AutoPuzzleContext,
		centerDirection: Vec3,
		target: BlockPos,
		unintendedTargets: Collection<BlockPos>,
		terminator: Boolean,
		blockMode: ClipContext.Block
	): Boolean {
		val hits = projectileDirections(centerDirection, terminator).map { direction ->
			firstBlockHit(context, direction, target, unintendedTargets, blockMode)
		}
		return isSafeFan(hits)
	}

	private fun firstBlockHit(
		context: AutoPuzzleContext,
		direction: Vec3,
		target: BlockPos,
		unintendedTargets: Collection<BlockPos>,
		blockMode: ClipContext.Block
	): FirstHit {
		val eye = context.player.eyePosition
		val end = eye.add(direction.normalize().scale(PROJECTILE_CHECK_RANGE))
		val hit = context.level.clip(
			ClipContext(eye, end, blockMode, ClipContext.Fluid.NONE, context.player)
		)
		if (hit.type != HitResult.Type.BLOCK) return FirstHit.OTHER
		return when (hit.blockPos) {
			target -> FirstHit.TARGET
			in unintendedTargets -> FirstHit.UNINTENDED
			else -> FirstHit.OTHER
		}
	}

	private fun projectileDirections(centerDirection: Vec3, terminator: Boolean): List<Vec3> =
		if (terminator) {
			TerminatorFanGeometry.directions(centerDirection).map { it.direction }
		} else {
			listOf(centerDirection.normalize())
		}

	private fun candidateAimPoints(eye: Vec3, targetPoints: List<Vec3>, terminator: Boolean): Sequence<Vec3> {
		val lanes = if (terminator) TerminatorFanLane.entries else listOf(TerminatorFanLane.CENTER)
		return lanes.asSequence().flatMap { lane ->
			targetPoints.asSequence().mapNotNull { targetPoint ->
				val delta = targetPoint.subtract(eye)
				val distance = delta.length()
				if (!distance.isFinite() || distance < EPSILON) return@mapNotNull null
				val cameraDirection = TerminatorFanGeometry.cameraDirectionForLaneHit(delta.scale(1.0 / distance), lane)
				eye.add(cameraDirection.scale(distance))
			}
		}
	}

	private val SAMPLE_OFFSETS = listOf(
		Triple(0.50, 0.50, 0.50),
		Triple(0.25, 0.50, 0.50),
		Triple(0.75, 0.50, 0.50),
		Triple(0.50, 0.30, 0.50),
		Triple(0.50, 0.70, 0.50),
		Triple(0.50, 0.50, 0.25),
		Triple(0.50, 0.50, 0.75),
		Triple(0.20, 0.30, 0.20),
		Triple(0.80, 0.30, 0.80),
		Triple(0.20, 0.70, 0.80),
		Triple(0.80, 0.70, 0.20)
	)
	private const val EPSILON = 1.0E-5
	private const val ENTITY_BOX_INFLATION = 0.02
	private const val PROJECTILE_CHECK_RANGE = 120.0
}
