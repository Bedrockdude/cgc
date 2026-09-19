package cgc.cgc.module.impl.dungeon.autopuzzles

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

/**
 * Chooses non-central aim points and verifies them against the actual shortbow
 * flight model: 3.0 initial speed, 0.99 drag, 0.05 gravity, the bow's launch
 * offset, and the Terminator's centre/plus-or-minus-five-degree fan.
 */
object ProjectileAimPlanner {
	sealed interface Result {
		data class Safe(val point: Vec3) : Result
		data class Unsafe(val reason: String) : Result
	}

	internal enum class FirstHit { TARGET, UNINTENDED, OTHER }

	fun entity(
		context: AutoPuzzleContext,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean,
		ignoredBlockCollision: (BlockPos) -> Boolean = { false }
	): Result {
		val targetPoints = sampledPoints(target.boundingBox)
		return candidateAimPoints(context.player.eyePosition, targetPoints, terminator)
			.firstOrNull { point -> isSafeEntityAim(context, point, target, unintendedTargets, terminator, ignoredBlockCollision) }
			?.let(Result::Safe)
			?: Result.Unsafe(
				if (terminator) "no safe simulated three-arrow fan reaches the intended target first"
				else "no simulated arrow trajectory reaches the intended target first"
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
		val targetPoints = sampledPoints(shape.bounds().move(target))
		return candidateAimPoints(context.player.eyePosition, targetPoints, terminator)
			.firstOrNull { point -> isSafeBlockAimPoint(context, point, target, terminator, unintendedTargets) }
			?.let(Result::Safe)
			?: Result.Unsafe(
				if (terminator) "no safe simulated three-arrow fan reaches the target block first"
				else "no simulated arrow trajectory reaches the target block"
			)
	}

	/** A vanilla interaction ray, used for Waterboard's non-projectile lever click. */
	fun directBlock(context: AutoPuzzleContext, target: BlockPos): Result {
		if (!context.level.isLoaded(target)) return Result.Unsafe("target block is not loaded")
		val shape = context.level.getBlockState(target).getShape(context.level, target)
		if (shape.isEmpty) return Result.Unsafe("target block has no visible collision shape")
		return sampledPoints(shape.bounds().move(target)).firstOrNull { point ->
			val hit = context.level.clip(
				ClipContext(context.player.eyePosition, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, context.player)
			)
			hit.type == HitResult.Type.BLOCK && hit.blockPos == target
		}?.let(Result::Safe) ?: Result.Unsafe("target block is not visible")
	}

	fun liveRayHitsEntity(
		context: AutoPuzzleContext,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean,
		ignoredBlockCollision: (BlockPos) -> Boolean = { false }
	): Boolean = target.isAlive && isSafeEntityDirection(
		context, context.player.lookAngle, target, unintendedTargets, terminator, ignoredBlockCollision
	)

	fun isSafeEntityAimPoint(
		context: AutoPuzzleContext,
		aimPoint: Vec3,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean,
		ignoredBlockCollision: (BlockPos) -> Boolean = { false }
	): Boolean = isSafeEntityAim(context, aimPoint, target, unintendedTargets, terminator, ignoredBlockCollision)

	fun liveRayHitsBlock(
		context: AutoPuzzleContext,
		target: BlockPos,
		terminator: Boolean = false,
		unintendedTargets: Collection<BlockPos> = emptySet()
	): Boolean = isSafeBlockDirection(context, context.player.lookAngle, target, unintendedTargets, terminator)

	fun isSafeBlockAimPoint(
		context: AutoPuzzleContext,
		aimPoint: Vec3,
		target: BlockPos,
		terminator: Boolean = false,
		unintendedTargets: Collection<BlockPos> = emptySet()
	): Boolean {
		val direction = aimPoint.subtract(context.player.eyePosition)
		return direction.lengthSqr() >= EPSILON &&
			isSafeBlockDirection(context, direction.normalize(), target, unintendedTargets, terminator)
	}

	internal fun isSafeFan(firstHits: Collection<FirstHit>): Boolean =
		FirstHit.TARGET in firstHits && FirstHit.UNINTENDED !in firstHits

	internal fun displacementAfterTicks(initialVelocity: Vec3, ticks: Int): Vec3 {
		var position = Vec3.ZERO
		var motion = initialVelocity
		repeat(ticks.coerceAtLeast(0)) {
			position = position.add(motion)
			motion = Vec3(motion.x * DRAG, motion.y * DRAG - GRAVITY, motion.z * DRAG)
		}
		return position
	}

	private fun isSafeEntityAim(
		context: AutoPuzzleContext,
		aimPoint: Vec3,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean,
		ignoredBlockCollision: (BlockPos) -> Boolean
	): Boolean {
		val direction = aimPoint.subtract(context.player.eyePosition)
		return direction.lengthSqr() >= EPSILON &&
			isSafeEntityDirection(context, direction.normalize(), target, unintendedTargets, terminator, ignoredBlockCollision)
	}

	private fun isSafeEntityDirection(
		context: AutoPuzzleContext,
		centerDirection: Vec3,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		terminator: Boolean,
		ignoredBlockCollision: (BlockPos) -> Boolean
	): Boolean {
		val launch = launchPosition(context.player.eyePosition, centerDirection)
		val hits = projectileDirections(centerDirection, terminator).map { direction ->
			firstEntityHit(context, launch, direction, target, unintendedTargets, ignoredBlockCollision)
		}
		return isSafeFan(hits)
	}

	private fun firstEntityHit(
		context: AutoPuzzleContext,
		launch: Vec3,
		direction: Vec3,
		target: Entity,
		unintendedTargets: Collection<Entity>,
		ignoredBlockCollision: (BlockPos) -> Boolean
	): FirstHit {
		var position = launch
		var motion = direction.normalize().scale(ARROW_SPEED)
		repeat(MAX_FLIGHT_TICKS) {
			val end = position.add(motion)
			val blockDistance = firstBlockingPoint(context, position, end, motion, ignoredBlockCollision)
				?.let(position::distanceToSqr) ?: Double.POSITIVE_INFINITY
			fun distance(entity: Entity): Double? {
				if (!entity.isAlive) return null
				val hit = entity.boundingBox.inflate(ENTITY_BOX_INFLATION).clip(position, end).orElse(null) ?: return null
				return position.distanceToSqr(hit)
			}
			val targetDistance = distance(target)
			val unintendedDistance = unintendedTargets.asSequence().filter { it.id != target.id }
				.mapNotNull(::distance).minOrNull()
			if (unintendedDistance != null && unintendedDistance <= blockDistance + EPSILON &&
				(targetDistance == null || unintendedDistance <= targetDistance + EPSILON)
			) return FirstHit.UNINTENDED
			if (targetDistance != null && targetDistance <= blockDistance + EPSILON) return FirstHit.TARGET
			if (blockDistance.isFinite()) return FirstHit.OTHER
			position = end
			motion = Vec3(motion.x * DRAG, motion.y * DRAG - GRAVITY, motion.z * DRAG)
		}
		return FirstHit.OTHER
	}

	private fun isSafeBlockDirection(
		context: AutoPuzzleContext,
		centerDirection: Vec3,
		target: BlockPos,
		unintendedTargets: Collection<BlockPos>,
		terminator: Boolean
	): Boolean {
		val launch = launchPosition(context.player.eyePosition, centerDirection)
		return isSafeFan(projectileDirections(centerDirection, terminator).map { direction ->
			firstBlockHit(context, launch, direction, target, unintendedTargets)
		})
	}

	private fun firstBlockHit(
		context: AutoPuzzleContext,
		launch: Vec3,
		direction: Vec3,
		target: BlockPos,
		unintendedTargets: Collection<BlockPos>
	): FirstHit {
		var position = launch
		var motion = direction.normalize().scale(ARROW_SPEED)
		repeat(MAX_FLIGHT_TICKS) {
			val hit = context.level.clip(
				ClipContext(position, position.add(motion), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.player)
			)
			if (hit.type == HitResult.Type.BLOCK) return when (hit.blockPos) {
				target -> FirstHit.TARGET
				in unintendedTargets -> FirstHit.UNINTENDED
				else -> FirstHit.OTHER
			}
			position = position.add(motion)
			motion = Vec3(motion.x * DRAG, motion.y * DRAG - GRAVITY, motion.z * DRAG)
		}
		return FirstHit.OTHER
	}

	private fun firstBlockingPoint(
		context: AutoPuzzleContext,
		segmentStart: Vec3,
		segmentEnd: Vec3,
		direction: Vec3,
		ignoredBlockCollision: (BlockPos) -> Boolean
	): Vec3? {
		var start = segmentStart
		val advance = direction.normalize().scale(RAY_ADVANCE_STEP)
		repeat(MAX_IGNORED_BLOCK_COLLISIONS) {
			val hit = context.level.clip(
				ClipContext(start, segmentEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.player)
			)
			if (hit.type == HitResult.Type.MISS) return null
			if (!ignoredBlockCollision(hit.blockPos)) return hit.location
			var next = hit.location.add(advance)
			while (BlockPos.containing(next) == hit.blockPos && next.distanceToSqr(segmentEnd) > RAY_ADVANCE_STEP_SQ) {
				next = next.add(advance)
			}
			start = next
		}
		return segmentStart
	}

	private fun candidateAimPoints(eye: Vec3, targets: List<Vec3>, terminator: Boolean): Sequence<Vec3> {
		val lanes = if (terminator) TerminatorFanLane.entries else listOf(TerminatorFanLane.CENTER)
		return targets.asSequence().flatMap { target ->
			lanes.asSequence().flatMap { lane -> ballisticCameraDirections(eye, target, lane).asSequence() }
		}
	}

	private fun ballisticCameraDirections(eye: Vec3, target: Vec3, lane: TerminatorFanLane): List<Vec3> {
		var horizontal = target.subtract(eye).let { Vec3(it.x, 0.0, it.z) }
		if (horizontal.lengthSqr() < EPSILON) return emptyList()
		var cameraHorizontal = TerminatorFanGeometry.cameraDirectionForLaneHit(horizontal.normalize(), lane)
		repeat(2) {
			val delta = target.subtract(launchPosition(eye, cameraHorizontal))
			horizontal = Vec3(delta.x, 0.0, delta.z)
			if (horizontal.lengthSqr() >= EPSILON) {
				cameraHorizontal = TerminatorFanGeometry.cameraDirectionForLaneHit(horizontal.normalize(), lane)
			}
		}
		val delta = target.subtract(launchPosition(eye, cameraHorizontal))
		val horizontalDistance = Vec3(delta.x, 0.0, delta.z).length()
		if (horizontalDistance < EPSILON) return emptyList()
		val horizontalDirection = Vec3(delta.x / horizontalDistance, 0.0, delta.z / horizontalDistance)
		return (1..MAX_FLIGHT_TICKS).map { ticks ->
			val dragSum = dragSum(ticks)
			val horizontalVelocity = horizontalDistance / dragSum
			val verticalVelocity = (delta.y - GRAVITY / (1.0 - DRAG) * (dragSum - ticks)) / dragSum
			val speedError = abs(kotlin.math.sqrt(horizontalVelocity * horizontalVelocity + verticalVelocity * verticalVelocity) - ARROW_SPEED)
			val projectile = Vec3(
				horizontalDirection.x * horizontalVelocity,
				verticalVelocity,
				horizontalDirection.z * horizontalVelocity
			).normalize()
			Triple(speedError, ticks, TerminatorFanGeometry.cameraDirectionForLaneHit(projectile, lane))
		}.sortedWith(compareBy<Triple<Double, Int, Vec3>> { it.first }.thenBy { it.second })
			.take(BALLISTIC_CANDIDATES_PER_POINT)
			.map { (_, _, direction) -> eye.add(direction.scale(maxOf(1.0, eye.distanceTo(target)))) }
	}

	private fun sampledPoints(box: AABB): List<Vec3> {
		val random = ThreadLocalRandom.current()
		val offsets = buildList {
			repeat(RANDOM_SAMPLES) {
				add(Triple(random.nextDouble(0.20, 0.80), random.nextDouble(0.20, 0.80), random.nextDouble(0.20, 0.80)))
			}
			addAll(FALLBACK_SAMPLE_OFFSETS)
		}
		return offsets.map { (x, y, z) -> Vec3(
			box.minX + (box.maxX - box.minX) * x,
			box.minY + (box.maxY - box.minY) * y,
			box.minZ + (box.maxZ - box.minZ) * z
		) }
	}

	private fun launchPosition(eye: Vec3, cameraDirection: Vec3): Vec3 {
		val horizontal = kotlin.math.sqrt(cameraDirection.x * cameraDirection.x + cameraDirection.z * cameraDirection.z)
		if (horizontal < EPSILON) return eye.add(0.0, -LAUNCH_Y_OFFSET, 0.0)
		return eye.add(
			-cameraDirection.z / horizontal * LAUNCH_HORIZONTAL_OFFSET,
			-LAUNCH_Y_OFFSET,
			cameraDirection.x / horizontal * LAUNCH_HORIZONTAL_OFFSET
		)
	}

	private fun projectileDirections(centerDirection: Vec3, terminator: Boolean): List<Vec3> =
		if (terminator) TerminatorFanGeometry.directions(centerDirection).map { it.direction }
		else listOf(centerDirection.normalize())

	private fun dragSum(ticks: Int): Double = (1.0 - Math.pow(DRAG, ticks.toDouble())) / (1.0 - DRAG)

	private val FALLBACK_SAMPLE_OFFSETS = listOf(
		Triple(0.35, 0.50, 0.50), Triple(0.65, 0.50, 0.50),
		Triple(0.50, 0.35, 0.50), Triple(0.50, 0.65, 0.50),
		Triple(0.50, 0.50, 0.35), Triple(0.50, 0.50, 0.65),
		Triple(0.50, 0.50, 0.50)
	)
	private const val EPSILON = 1.0E-5
	private const val ARROW_SPEED = 3.0
	private const val DRAG = 0.99
	private const val GRAVITY = 0.05
	private const val LAUNCH_HORIZONTAL_OFFSET = 0.16
	private const val LAUNCH_Y_OFFSET = 0.10
	private const val ENTITY_BOX_INFLATION = 0.02
	private const val MAX_FLIGHT_TICKS = 60
	private const val BALLISTIC_CANDIDATES_PER_POINT = 6
	private const val RANDOM_SAMPLES = 12
	private const val MAX_IGNORED_BLOCK_COLLISIONS = 32
	private const val RAY_ADVANCE_STEP = 0.05
	private const val RAY_ADVANCE_STEP_SQ = RAY_ADVANCE_STEP * RAY_ADVANCE_STEP
}
