package cgc.cgc.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals

class DungeonPuzzleStateTrackerTest {
	@Test
	fun `parser recognizes only the four authoritative puzzle lines`() {
		val parsed = DungeonPuzzleStateTracker.parseLines(
			listOf(
				" §eHigher Or Lower: §f[✦]",
				" Creeper Beams: [✔]",
				" Ice Fill: [✖]",
				" Water Board: [✦]",
				" Tic Tac Toe: [✔]"
			)
		)

		assertEquals(DungeonPuzzleState.DISCOVERED, parsed[DungeonPuzzle.BLAZE])
		assertEquals(DungeonPuzzleState.GREEN, parsed[DungeonPuzzle.CREEPER_BEAMS])
		assertEquals(DungeonPuzzleState.FAILED, parsed[DungeonPuzzle.ICE_FILL])
		assertEquals(DungeonPuzzleState.DISCOVERED, parsed[DungeonPuzzle.WATER_BOARD])
		assertEquals(4, parsed.size)
	}

	@Test
	fun `failure symbol wins if a malformed line contains multiple symbols`() {
		val parsed = DungeonPuzzleStateTracker.parseLines(listOf(" Ice Fill: [✔✖]"))
		assertEquals(DungeonPuzzleState.FAILED, parsed[DungeonPuzzle.ICE_FILL])
	}
}
