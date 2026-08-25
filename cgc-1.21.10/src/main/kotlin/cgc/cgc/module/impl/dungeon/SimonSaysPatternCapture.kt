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

	fun record(button: BlockPos) {
		// The caller already filters sea-lantern -> sea-lantern packet repeats.
		// Equal positions here are therefore real, separate flashes and may be a
		// valid consecutive repeat in the Simon Says sequence.
		observedButtons.add(button.immutable())
	}

	/**
	 * A spectator-safe start produces three light transitions: the first is the
	 * skipped button and the final two are the opening sequence to solve.  Two
	 * observations are therefore intentionally ambiguous and must never become
	 * a clickable pattern.
	 */
	fun openingSkipPattern(): List<BlockPos>? =
		if (observedButtons.size >= OPENING_SKIP_OBSERVATION_COUNT) {
			observedButtons.takeLast(OPENING_SKIP_PATTERN_LENGTH)
		} else {
			null
		}

	/**
	 * Once the skipped button and the next light are known, the latter remains
	 * the first solve target when the third transition arrives.  This permits
	 * useful pre-aiming without treating the partial capture as click-safe.
	 */
	fun openingSkipFirstCandidate(): BlockPos? =
		when (observedButtons.size) {
			0, 1 -> null
			2 -> observedButtons.last()
			else -> observedButtons.takeLast(OPENING_SKIP_PATTERN_LENGTH).first()
		}

	fun observations(): List<BlockPos> = observedButtons.toList()

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

	private companion object {
		const val OPENING_SKIP_OBSERVATION_COUNT = 3
		const val OPENING_SKIP_PATTERN_LENGTH = 2
	}
}

internal sealed interface PatternReplayResult {
	data object Incomplete : PatternReplayResult
	data object Mismatch : PatternReplayResult
	data class Ready(val buttons: List<BlockPos>) : PatternReplayResult
}
