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
	 * The usual spectator-safe start produces three light transitions: the first
	 * is skipped and the final two form the opening sequence. The server can also
	 * finish the display after only two transitions, in which case both belong to
	 * the opening sequence. The caller must wait for the input phase and an extra
	 * grace period before accepting that two-transition variant.
	 */
	fun openingSkipPattern(allowTwoTransitionVariant: Boolean = false): List<BlockPos>? =
		when {
			observedButtons.size >= OPENING_SKIP_OBSERVATION_COUNT ->
				observedButtons.takeLast(OPENING_SKIP_PATTERN_LENGTH)
			allowTwoTransitionVariant && observedButtons.size == OPENING_SKIP_PATTERN_LENGTH ->
				observedButtons.toList()
			else -> null
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
