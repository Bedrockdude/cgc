package cgc.cgc.runtime

import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInputSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysicalInputTrackerTest {
	@AfterTest
	fun cleanUp() = PhysicalInputTracker.reset()

	@Test
	fun `keyboard presses are ignored for one second then cancel while releases do not`() {
		PhysicalInputTracker.reset()
		val session = AutoPuzzleInputSession.acquire(1_000L)!!
		PhysicalInputTracker.onKeyAt(65, 0, 1_010L)
		assertNull(session.cancellationReason(1_010L))
		PhysicalInputTracker.onKeyAt(65, 1, 1_020L)
		assertNull(session.cancellationReason(1_020L))
		PhysicalInputTracker.onKeyAt(65, 0, 1_030L)
		PhysicalInputTracker.onKeyAt(66, 1, 2_000L)
		assertEquals("keyboard input", session.cancellationReason(2_000L))
	}

	@Test
	fun `mouse button events share the one second grace`() {
		PhysicalInputTracker.reset()
		val session = AutoPuzzleInputSession.acquire(1_000L)!!
		PhysicalInputTracker.onMouseButtonAt(1, 1, 1_999L)
		PhysicalInputTracker.onMouseButtonAt(1, 0, 1_999L)
		assertNull(session.cancellationReason(2_000L))
		PhysicalInputTracker.onMouseButtonAt(1, 1, 2_001L)
		assertEquals("mouse button input", session.cancellationReason(2_001L))
	}

	@Test
	fun `scroll events share the one second grace`() {
		PhysicalInputTracker.reset()
		val session = AutoPuzzleInputSession.acquire(1_000L)!!
		PhysicalInputTracker.onScrollAt(0.0, 1.0, 1_999L)
		assertNull(session.cancellationReason(2_000L))
		PhysicalInputTracker.onScrollAt(0.0, -1.0, 2_001L)
		assertEquals("mouse wheel input", session.cancellationReason(2_001L))
	}

	@Test
	fun `mouse movement is baselined through the exact grace boundary`() {
		PhysicalInputTracker.reset()
		PhysicalInputTracker.onMouseMoveAt(10.0, 10.0, 1_900L)
		val session = AutoPuzzleInputSession.acquire(2_000L)!!
		PhysicalInputTracker.onMouseMoveAt(11.0, 10.0, 2_999L)
		assertNull(session.cancellationReason(2_999L))
		PhysicalInputTracker.onMouseMoveAt(12.0, 10.0, 3_000L)
		assertEquals("mouse movement", session.cancellationReason(3_000L))
	}

	@Test
	fun `an in grace mouse event remains ignored when first checked at the boundary`() {
		PhysicalInputTracker.reset()
		val session = AutoPuzzleInputSession.acquire(2_000L)!!
		PhysicalInputTracker.onMouseMoveAt(10.0, 10.0, 2_999L)
		assertNull(session.cancellationReason(3_000L))
	}

	@Test
	fun `first observed mouse movement after reset is still an input event`() {
		PhysicalInputTracker.reset()
		val session = AutoPuzzleInputSession.acquire(2_000L)!!
		PhysicalInputTracker.onMouseMoveAt(10.0, 10.0, 3_000L)
		assertEquals("mouse movement", session.cancellationReason(3_000L))
	}

	@Test
	fun `held physical input prevents acquisition`() {
		PhysicalInputTracker.reset()
		PhysicalInputTracker.onMouseButton(1, 1)
		assertNull(AutoPuzzleInputSession.acquire(0L))
		PhysicalInputTracker.onMouseButton(1, 0)
		assertTrue(AutoPuzzleInputSession.acquire(1L) != null)
	}
}
