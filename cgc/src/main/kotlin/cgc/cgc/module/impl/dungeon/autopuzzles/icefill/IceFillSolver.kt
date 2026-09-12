package cgc.cgc.module.impl.dungeon.autopuzzles.icefill

import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleRoomCoordinates
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import java.util.ArrayDeque

object IceFillSolver {
	data class Floor(val spaces: Set<BlockPos>, val start: BlockPos, val end: BlockPos, val path: List<BlockPos>)

	fun scanAreaLoaded(context: AutoPuzzleContext): Boolean =
		AutoPuzzleRoomCoordinates.horizontalChunksLoaded(
			context.level,
			context.room.mainX - SCAN_RADIUS,
			context.room.mainX + SCAN_RADIUS,
			context.room.mainZ - SCAN_RADIUS,
			context.room.mainZ + SCAN_RADIUS
		)

	fun observe(context: AutoPuzzleContext): List<Floor> {
		if (!scanAreaLoaded(context)) return emptyList()
		val spaces = linkedSetOf<BlockPos>()
		for (dx in -22..22) for (dz in -22..22) for (y in 68..73) {
			val ice = BlockPos(context.room.mainX + dx, y, context.room.mainZ + dz)
			val state = context.level.getBlockState(ice)
			if (!state.`is`(net.minecraft.world.level.block.Blocks.ICE) && !state.`is`(net.minecraft.world.level.block.Blocks.PACKED_ICE)) continue
			if (!context.level.getBlockState(ice.above()).isAir) continue
			spaces.add(ice.above())
		}
		if (spaces.isEmpty()) return emptyList()
		val checkpoints = listOf(
			AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 69, -8),
			AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 70, -3),
			AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 71, 4),
			AutoPuzzleRoomCoordinates.worldBlock(context.room, 0, 71, 11)
		)
		val ordered = clusters(spaces).sortedBy { cluster -> cluster.minOf { it.distSqr(checkpoints[0]) } }
		if (ordered.size != 3) return emptyList()
		return ordered.mapIndexed { index, cluster ->
			val start = cluster.minBy { it.distSqr(checkpoints[index]) }
			val end = cluster.minBy { it.distSqr(checkpoints[index + 1]) }
			Floor(cluster, start, end, solve(cluster, start, end))
		}.takeIf { floors -> floors.all { it.path.size == it.spaces.size && it.path.isNotEmpty() } } ?: emptyList()
	}

	fun clusters(spaces: Set<BlockPos>): List<Set<BlockPos>> {
		val unvisited = spaces.toMutableSet()
		val result = arrayListOf<Set<BlockPos>>()
		while (unvisited.isNotEmpty()) {
			val first = unvisited.first()
			val queue = ArrayDeque<BlockPos>()
			val cluster = linkedSetOf<BlockPos>()
			queue.add(first)
			unvisited.remove(first)
			while (queue.isNotEmpty()) {
				val current = queue.removeFirst()
				cluster.add(current)
				for (direction in Direction.Plane.HORIZONTAL) {
					val next = current.relative(direction)
					if (unvisited.remove(next)) queue.add(next)
				}
			}
			result.add(cluster)
		}
		return result
	}

	fun solve(spaces: Set<BlockPos>, start: BlockPos, end: BlockPos): List<BlockPos> {
		if (start !in spaces || end !in spaces || spaces.isEmpty()) return emptyList()
		val graph = spaces.associateWith { pos ->
			Direction.Plane.HORIZONTAL.map { pos.relative(it) }.filter { it in spaces }
		}
		val visited = hashSetOf(start)
		val path = arrayListOf(start)

		fun dfs(current: BlockPos): Boolean {
			if (visited.size == spaces.size) return current == end
			if (current == end) return false
			val neighbors = graph[current].orEmpty()
				.filter { it !in visited }
				.sortedBy { next -> graph[next].orEmpty().count { it !in visited } }
			for (next in neighbors) {
				visited.add(next)
				path.add(next)
				if (dfs(next)) return true
				path.removeAt(path.lastIndex)
				visited.remove(next)
			}
			return false
		}

		return if (dfs(start)) path.toList() else emptyList()
	}

	private const val SCAN_RADIUS = 22
}
