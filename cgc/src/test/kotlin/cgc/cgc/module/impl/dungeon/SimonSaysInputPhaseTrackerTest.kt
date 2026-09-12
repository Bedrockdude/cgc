package cgc.cgc.module.impl.dungeon

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimonSaysInputPhaseTrackerTest {
	@Test
	fun `restored marker confirms lights captured during display phase`() {
		val tracker = SimonSaysInputPhaseTracker()
		tracker.arm(markerIsButton = true)
		tracker.observe(markerIsButton = false, nowMs = 100L)
		tracker.observe(markerIsButton = true, nowMs = 900L)

		assertTrue(tracker.confirmsPattern(lastPatternLightAtMs = 850L))
	}

	@Test
	fun `initial stone button does not falsely confirm input phase`() {
		val tracker = SimonSaysInputPhaseTracker()
		tracker.arm(markerIsButton = true)

		assertFalse(tracker.confirmsPattern(lastPatternLightAtMs = 100L))
	}

	@Test
	fun `same packet marker before final light is tolerated`() {
		val tracker = SimonSaysInputPhaseTracker()
		tracker.arm(markerIsButton = false)
		tracker.observe(markerIsButton = true, nowMs = 900L)

		assertTrue(tracker.confirmsPattern(lastPatternLightAtMs = 950L))
	}

	@Test
	fun `stale marker does not confirm a later light`() {
		val tracker = SimonSaysInputPhaseTracker()
		tracker.arm(markerIsButton = false)
		tracker.observe(markerIsButton = true, nowMs = 300L)

		assertFalse(tracker.confirmsPattern(lastPatternLightAtMs = 900L))
	}
}
