package cgc.cgc.module.impl.dungeon

import net.minecraft.core.BlockPos

internal class SimonSaysPatternCapture(
	private val maximumPatternLength: Int
) {
	private val observedButtons = arrayListOf<BlockPos>()

	init {
		require(maximumPatternLength > 0)
	}

	val observationCount: Int
		get() = observedButtons.size

	fun record(button: BlockPos): Boolean {
		if (observedButtons.lastOrNull() == button) {
			return false
		}
		observedButtons.add(button.immutable())
		return true
	}

	fun openingPattern(): List<BlockPos>? {
		val length = inferredOpeningPatternLength(observedButtons.size)
		return if (length > 0) observedButtons.takeLast(length) else null
	}

	fun nextPattern(previousPattern: List<BlockPos>): PatternReplayResult {
		val expectedLength = previousPattern.size + 1
		if (expectedLength > maximumPatternLength || observedButtons.size < expectedLength) {
			return PatternReplayResult.Incomplete
		}

		val candidate = observedButtons.takeLast(expectedLength)
		return if (candidate.dropLast(1) == previousPattern) {
			PatternReplayResult.Ready(candidate)
		} else {
			PatternReplayResult.Mismatch
		}
	}

	fun clear() {
		observedButtons.clear()
	}

	// Spectator-safe startup can concatenate the one-, two-, and three-button
	// displays. These ranges select the newest complete display from that stream.
	private fun inferredOpeningPatternLength(observationCount: Int): Int =
		when (observationCount) {
			0 -> 0
			1 -> 1
			in 2..3 -> 2
			in 4..6 -> 3
			in 7..9 -> 4
			else -> maximumPatternLength
		}.coerceAtMost(observationCount).coerceAtMost(maximumPatternLength)
}

internal sealed interface PatternReplayResult {
	data object Incomplete : PatternReplayResult
	data object Mismatch : PatternReplayResult
	data class Ready(val buttons: List<BlockPos>) : PatternReplayResult
}
