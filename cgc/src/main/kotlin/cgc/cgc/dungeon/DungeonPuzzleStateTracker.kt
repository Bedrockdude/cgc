package cgc.cgc.dungeon

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.util.EnumMap

enum class DungeonPuzzle(val tabName: String) {
	BLAZE("Higher Or Lower"),
	CREEPER_BEAMS("Creeper Beams"),
	ICE_FILL("Ice Fill"),
	WATER_BOARD("Water Board")
}

enum class DungeonPuzzleState {
	UNKNOWN,
	DISCOVERED,
	GREEN,
	FAILED
}

/**
 * Deliberately small tab-list tracker for the four Auto Puzzles rooms. It does
 * not infer completion from elapsed time or from a partial player-info packet.
 */
object DungeonPuzzleStateTracker {
	private val states = EnumMap<DungeonPuzzle, DungeonPuzzleState>(DungeonPuzzle::class.java).apply {
		DungeonPuzzle.entries.forEach { put(it, DungeonPuzzleState.UNKNOWN) }
	}
	private var trackedRunSequence = 0L

	fun tick(client: Minecraft, runSequence: Long) {
		if (trackedRunSequence != runSequence) {
			onRunStarted(runSequence)
		}
		val connection = client.connection ?: return
		val lines = connection.getListedOnlinePlayers().mapNotNull { info ->
			info.tabListDisplayName?.string ?: info.profile.name
		}
		applySnapshot(parseLines(lines))
	}

	fun state(puzzle: DungeonPuzzle): DungeonPuzzleState =
		states[puzzle] ?: DungeonPuzzleState.UNKNOWN

	fun snapshot(): Map<DungeonPuzzle, DungeonPuzzleState> =
		EnumMap(states)

	fun onRunStarted(runSequence: Long) {
		trackedRunSequence = runSequence
		states.keys.forEach { states[it] = DungeonPuzzleState.UNKNOWN }
	}

	fun resetTransient() {
		states.keys.forEach { states[it] = DungeonPuzzleState.UNKNOWN }
	}

	internal fun parseLines(lines: Iterable<String>): Map<DungeonPuzzle, DungeonPuzzleState> {
		val parsed = EnumMap<DungeonPuzzle, DungeonPuzzleState>(DungeonPuzzle::class.java)
		for (raw in lines) {
			val line = (ChatFormatting.stripFormatting(raw) ?: raw).trim()
			val puzzle = DungeonPuzzle.entries.firstOrNull { puzzle ->
				line.contains(puzzle.tabName, ignoreCase = true)
			} ?: continue
			val state = when {
				'✖' in line -> DungeonPuzzleState.FAILED
				'✔' in line -> DungeonPuzzleState.GREEN
				'✦' in line -> DungeonPuzzleState.DISCOVERED
				else -> continue
			}
			parsed[puzzle] = state
		}
		return parsed
	}

	private fun applySnapshot(parsed: Map<DungeonPuzzle, DungeonPuzzleState>) {
		for (puzzle in DungeonPuzzle.entries) {
			states[puzzle] = parsed[puzzle] ?: DungeonPuzzleState.UNKNOWN
		}
	}
}
