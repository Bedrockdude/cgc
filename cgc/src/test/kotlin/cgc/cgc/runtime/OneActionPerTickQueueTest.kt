package cgc.cgc.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneActionPerTickQueueTest {
	@Test
	fun `first action is immediate and later actions are deferred in order`() {
		val queue = OneActionPerTickQueue<String>()

		assertFalse(queue.shouldDelay("first"))
		assertTrue(queue.shouldDelay("second"))
		assertTrue(queue.shouldDelay("third"))
		assertEquals(2, queue.pendingCount())

		assertEquals("second", queue.beginTick())
		assertTrue(queue.shouldDelay("fourth"))
		assertEquals("third", queue.beginTick())
		assertEquals("fourth", queue.beginTick())
		assertNull(queue.beginTick())
	}

	@Test
	fun `immediate action reservation is atomic and does not enqueue on failure`() {
		val queue = OneActionPerTickQueue<String>()
		var ran = false

		assertTrue(queue.tryRunImmediately {
			ran = true
			assertFalse(queue.shouldDelay("reserved"))
		})
		assertTrue(ran)
		assertFalse(queue.tryRunImmediately { error("busy reservation must not run") })
		assertEquals(0, queue.pendingCount())

		assertNull(queue.beginTick())
		assertTrue(queue.tryRunImmediately { })
	}

	@Test
	fun `marking vanilla use consumes slot without deferring its packets`() {
		val queue = OneActionPerTickQueue<String>()

		queue.markUsed()
		queue.markUsed()

		assertFalse(queue.tryRunImmediately { error("vanilla use must consume the immediate slot") })
		assertEquals(0, queue.pendingCount())
	}

	@Test
	fun `unavailable connection retains deferred actions`() {
		val queue = OneActionPerTickQueue<String>()

		assertFalse(queue.shouldDelay("first"))
		assertTrue(queue.shouldDelay("second"))
		assertNull(queue.beginTick(canFlush = false))
		assertEquals(1, queue.pendingCount())
		assertEquals("second", queue.beginTick())
	}

	@Test
	fun `clear removes deferred actions and resets the current tick`() {
		val queue = OneActionPerTickQueue<String>()

		assertFalse(queue.shouldDelay("first"))
		assertTrue(queue.shouldDelay("second"))
		queue.clear()

		assertEquals(0, queue.pendingCount())
		assertFalse(queue.shouldDelay("new first"))
	}
}
