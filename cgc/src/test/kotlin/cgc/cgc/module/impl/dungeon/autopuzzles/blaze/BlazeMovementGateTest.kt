package cgc.cgc.module.impl.dungeon.autopuzzles.blaze

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlazeMovementGateTest {
	@Test
	fun `edge walk always releases after the bounded approach`() {
		assertFalse(BlazeMovementGate.edgeWalkFinished(149L))
		assertTrue(BlazeMovementGate.edgeWalkFinished(150L))
		assertTrue(BlazeMovementGate.edgeWalkFinished(2_000L))
	}

	@Test
	fun `low removal wait enables pre aim without declaring the previous Blaze alive`() {
		assertTrue(BlazeRemovalGate.shouldBeginPreAim(100L, 100L))
		assertFalse(BlazeRemovalGate.shouldRetry(100L))
		assertFalse(BlazeRemovalGate.shouldRetry(2_499L))
		assertTrue(BlazeRemovalGate.shouldRetry(2_500L))
	}
}
