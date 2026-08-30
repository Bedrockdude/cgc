package cgc.cgc.module.impl.dungeon

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SimonSaysPatternCaptureTest {
	private val first = BlockPos(110, 120, 92)
	private val second = BlockPos(110, 121, 93)
	private val third = BlockPos(110, 122, 94)

	@Test
	fun `opening skip prefers three transitions and discards the first`() {
		val capture = SimonSaysPatternCapture(5)
		capture.record(first)
		capture.record(second)

		assertEquals(null, capture.openingSkipPattern())
		assertEquals(listOf(first, second), capture.openingSkipPattern(allowTwoTransitionVariant = true))
		assertEquals(null, capture.openingSkipFirstCandidate())

		capture.record(third)

		assertEquals(listOf(second, third), capture.openingSkipPattern())
		assertEquals(second, capture.openingSkipFirstCandidate())
	}

	@Test
	fun `two ambiguous opening lights do not choose a speculative pre aim target`() {
		val capture = SimonSaysPatternCapture(5)
		capture.record(first)
		capture.record(second)

		assertEquals(null, capture.openingSkipFirstCandidate())
	}

	@Test
	fun `replay can append a button used earlier in the sequence`() {
		val capture = SimonSaysPatternCapture(5)
		capture.record(first)
		capture.record(second)
		capture.record(first)

		val result = assertIs<PatternReplayResult.Ready>(capture.nextPattern(listOf(first, second)))
		assertEquals(listOf(first, second, first), result.buttons)
	}

	@Test
	fun `replay preserves a consecutive repeated button`() {
		val capture = SimonSaysPatternCapture(5)
		capture.record(first)
		capture.record(first)

		val result = assertIs<PatternReplayResult.Ready>(capture.nextPattern(listOf(first)))
		assertEquals(listOf(first, first), result.buttons)
	}

	@Test
	fun `consecutive repeated buttons remain distinct light transitions`() {
		val capture = SimonSaysPatternCapture(5)

		capture.record(first)
		capture.record(first)
		capture.record(second)
		assertEquals(3, capture.observationCount)
		assertEquals(listOf(first, second), capture.openingSkipPattern())
	}

	@Test
	fun `replay mismatch is rejected instead of becoming a click sequence`() {
		val capture = SimonSaysPatternCapture(5)
		capture.record(first)
		capture.record(third)
		capture.record(second)

		assertIs<PatternReplayResult.Mismatch>(capture.nextPattern(listOf(first, second)))
	}

	@Test
	fun `incomplete replay waits for the remaining observations`() {
		val capture = SimonSaysPatternCapture(5)
		capture.record(first)
		capture.record(second)

		assertIs<PatternReplayResult.Incomplete>(capture.nextPattern(listOf(first, second)))
	}
}
