package cgc.cgc.module.impl.dungeon

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimonSaysPatternCaptureTest {
	private val first = BlockPos(110, 120, 92)
	private val second = BlockPos(110, 121, 93)
	private val third = BlockPos(110, 122, 94)

	@Test
	fun `opening capture keeps the newest inferred sequence`() {
		val capture = SimonSaysPatternCapture(5)
		capture.record(first)
		capture.record(first)
		capture.record(second)
		capture.record(first)
		capture.record(second)
		capture.record(third)

		assertEquals(listOf(first, second, third), capture.openingPattern())
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
	fun `consecutive duplicate block observations are ignored`() {
		val capture = SimonSaysPatternCapture(5)

		assertTrue(capture.record(first))
		assertFalse(capture.record(first))
		assertTrue(capture.record(second))
		assertEquals(2, capture.observationCount)
		assertIs<PatternReplayResult.Ready>(capture.nextPattern(listOf(first)))
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
