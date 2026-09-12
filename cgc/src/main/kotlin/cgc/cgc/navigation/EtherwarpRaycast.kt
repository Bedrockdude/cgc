package cgc.cgc.navigation

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs
import kotlin.math.floor

/**
 * Walks occupied block cells rather than holes in a partial collision shape.
 * Hypixel Etherwarp can reject lines that a vanilla collider ray considers open,
 * notably rays passing through the empty corner of stairs.
 */
internal fun conservativeFirstBlock(
	from: Vec3,
	to: Vec3,
	isBlocking: (BlockPos) -> Boolean
): NavigationRayHit? {
	val dx = to.x - from.x
	val dy = to.y - from.y
	val dz = to.z - from.z
	var x = floor(from.x).toInt()
	var y = floor(from.y).toInt()
	var z = floor(from.z).toInt()
	val stepX = dx.compareTo(0.0)
	val stepY = dy.compareTo(0.0)
	val stepZ = dz.compareTo(0.0)
	val deltaX = if (stepX == 0) Double.POSITIVE_INFINITY else abs(1.0 / dx)
	val deltaY = if (stepY == 0) Double.POSITIVE_INFINITY else abs(1.0 / dy)
	val deltaZ = if (stepZ == 0) Double.POSITIVE_INFINITY else abs(1.0 / dz)
	var maxX = boundaryTime(from.x, dx, x, stepX)
	var maxY = boundaryTime(from.y, dy, y, stepY)
	var maxZ = boundaryTime(from.z, dz, z, stepZ)
	var enteredAt = 0.0

	repeat(MAX_TRAVERSED_CELLS) {
		val pos = BlockPos(x, y, z)
		if (isBlocking(pos)) return NavigationRayHit(pos, from.lerp(to, enteredAt.coerceIn(0.0, 1.0)))
		val next = minOf(maxX, maxY, maxZ)
		if (!next.isFinite() || next > 1.0) return null
		enteredAt = next
		// Match Etherwarp's one-axis-at-a-time DDA ordering. Advancing every tied axis
		// can skip a cell touched at an exact edge/corner and invent a clear ray.
		when {
			maxX <= maxY && maxX <= maxZ -> { x += stepX; maxX += deltaX }
			maxY <= maxZ -> { y += stepY; maxY += deltaY }
			else -> { z += stepZ; maxZ += deltaZ }
		}
	}
	return null
}

private fun boundaryTime(origin: Double, delta: Double, cell: Int, step: Int): Double {
	if (step == 0) return Double.POSITIVE_INFINITY
	val boundary = if (step > 0) cell + 1.0 else cell.toDouble()
	return (boundary - origin) / delta
}

private const val MAX_TRAVERSED_CELLS = 512

/** Samples each real collision component independently instead of the union bounds. */
internal fun collisionPartAimCandidates(shape: VoxelShape, block: BlockPos, inset: Double = 0.018): List<Vec3> {
	val boxes = shape.toAabbs().ifEmpty { listOf(AABB.unitCubeFromLowerCorner(Vec3.ZERO)) }
	return buildList {
		for (local in boxes) {
			val box = local.move(block)
			val xs = collisionAxisSamples(box.minX, box.maxX, inset)
			val ys = collisionAxisSamples(box.minY, box.maxY, inset)
			val zs = collisionAxisSamples(box.minZ, box.maxZ, inset)
			add(Vec3((box.minX + box.maxX) * .5, (box.minY + box.maxY) * .5, (box.minZ + box.maxZ) * .5))
			for (x in xs) for (y in ys) for (z in zs) add(Vec3(x, y, z))
		}
	}.distinct()
}

private fun collisionAxisSamples(min: Double, max: Double, inset: Double): List<Double> {
	val size = max - min
	if (size <= inset * 2.0) return listOf((min + max) * .5)
	val safeInset = inset.coerceAtMost(size * .33)
	return listOf(min + safeInset, (min + max) * .5, max - safeInset)
}
