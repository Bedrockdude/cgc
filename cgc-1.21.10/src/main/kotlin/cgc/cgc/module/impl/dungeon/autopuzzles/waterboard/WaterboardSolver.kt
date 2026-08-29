package cgc.cgc.module.impl.dungeon.autopuzzles.waterboard

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object WaterboardSolver {
	data class ScheduledAction(val lever: WaterLever, val timeSeconds: Double, val occurrence: Int)
	data class Solution(val pattern: Int, val gates: String, val actions: List<ScheduledAction>)

	fun recognizePattern(blockAt: (x: Int, y: Int, z: Int) -> Block): Int? =
		when {
			blockAt(-1, 77, 12) == Blocks.TERRACOTTA -> 0
			blockAt(1, 78, 12) == Blocks.EMERALD_BLOCK -> 1
			blockAt(-1, 78, 12) == Blocks.DIAMOND_BLOCK -> 2
			blockAt(-1, 78, 12) == Blocks.QUARTZ_BLOCK -> 3
			else -> null
		}

	fun solve(pattern: Int, closedGates: Set<Int>): Solution? {
		if (closedGates.size != 3 || closedGates.any { it !in 0..4 }) return null
		val key = closedGates.sorted().joinToString("")
		val schedule = WaterboardData.solutions[pattern]?.get(key) ?: return null
		val actions = schedule.flatMap { (lever, times) ->
			times.mapIndexed { occurrence, time -> ScheduledAction(lever, time, occurrence) }
		}.sortedWith(compareBy<ScheduledAction> { it.timeSeconds }.thenBy { it.lever.legacyOrder }.thenBy { it.occurrence })
		return Solution(pattern, key, actions)
	}

	/**
	 * Once water starts, an initially closed goal gate may open, but a gate that
	 * began open may not close and an opened goal gate may not close again.
	 */
	fun gateSnapshotValid(
		initiallyClosed: Set<Int>,
		currentlyClosed: Set<Int>,
		previouslyOpened: Set<Int>,
		waterStarted: Boolean
	): Boolean {
		if (currentlyClosed.any { it !in initiallyClosed }) return false
		if (previouslyOpened.any { it in currentlyClosed }) return false
		return waterStarted || currentlyClosed == initiallyClosed
	}
}
