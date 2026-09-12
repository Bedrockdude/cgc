package cgc.cgc.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DungeonRunSequenceTest {
	@Test
	fun `duplicate starts are suppressed but a world reset permits the next confirmed run`() {
		DungeonState.reset()
		val before = DungeonState.runSequence
		assertTrue(DungeonState.noteDungeonStart(10_000L))
		assertEquals(before + 1L, DungeonState.runSequence)
		assertFalse(DungeonState.noteDungeonStart(10_001L))
		assertEquals(before + 1L, DungeonState.runSequence)

		DungeonState.reset()
		assertTrue(DungeonState.noteDungeonStart(10_002L))
		assertEquals(before + 2L, DungeonState.runSequence)
	}
}
