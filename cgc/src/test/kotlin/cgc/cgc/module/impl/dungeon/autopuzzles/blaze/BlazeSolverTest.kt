package cgc.cgc.module.impl.dungeon.autopuzzles.blaze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlazeSolverTest {
	@Test
	fun `maximum health parser accepts commas and rejects unrelated labels`() {
		assertEquals(12_500_000, BlazeSolver.parseMaximumHealth("[Lv15] Blaze 2,400/12,500,000❤"))
		assertNull(BlazeSolver.parseMaximumHealth("[Lv15] Zombie 2,400/12,500❤"))
		assertNull(BlazeSolver.parseMaximumHealth("Blaze"))
	}
}
