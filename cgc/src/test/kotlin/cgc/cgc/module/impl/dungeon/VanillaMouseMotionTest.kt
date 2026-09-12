package cgc.cgc.module.impl.dungeon

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VanillaMouseMotionTest {
	@Test
	fun `mouse gain matches vanilla sensitivity transform`() {
		val sensitivity = 0.39436619718309857
		val base = sensitivity * 0.6000000238418579 + 0.20000000298023224
		val expected = base * base * base * 8.0 * 0.15

		assertEquals(expected, VanillaMouseMotion.degreesPerCount(sensitivity), 1.0E-12)
	}

	@Test
	fun `rotation deltas are representable as whole raw mouse counts`() {
		val gain = VanillaMouseMotion.degreesPerCount(0.39436619718309857)
		for (delta in listOf(-43.7, -8.25, -0.09, 0.0, 0.09, 8.25, 43.7)) {
			val counts = VanillaMouseMotion.rawCounts(delta, gain)
			val reconstructed = counts * gain
			assertTrue(abs(reconstructed - delta) <= gain * 0.5 + 1.0E-12)
		}
	}

	@Test
	fun `invalid mouse gain cannot emit input`() {
		assertEquals(0, VanillaMouseMotion.rawCounts(12.0, 0.0))
		assertEquals(0, VanillaMouseMotion.rawCounts(Double.NaN, 0.1))
	}
}
