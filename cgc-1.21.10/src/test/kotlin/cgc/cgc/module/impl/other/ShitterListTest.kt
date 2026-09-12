package cgc.cgc.module.impl.other

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShitterListTest {
	@Test
	fun `reads dungeon Party Finder joins`() {
		assertEquals(
			"ExamplePlayer",
			ShitterList.partyFinderJoinName("Party Finder > ExamplePlayer joined the dungeon group! (Mage Level 42)")
		)
	}

	@Test
	fun `reads ranked group Party Finder joins`() {
		assertEquals(
			"ExamplePlayer",
			ShitterList.partyFinderJoinName("Party Finder > [MVP+] ExamplePlayer joined the group! (Combat Level 60)")
		)
	}

	@Test
	fun `ignores normal party joins`() {
		assertNull(ShitterList.partyFinderJoinName("ExamplePlayer joined the party."))
	}
}
