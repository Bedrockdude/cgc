package cgc.cgc.module.impl.dungeon.autopuzzles.creeperbeams

import net.minecraft.core.BlockPos
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreeperBeamDataTest {
	@Test
	fun `pinned table has one normalized duplicate without reordering`() {
		assertEquals("58E8E27A06C1AF9F3B4C9ADE130D452970455CD0F18F9366279A9B0121FAAA70", hash())
		assertEquals(14, CreeperBeamData.sourcePairs.size)
		assertEquals(13, CreeperBeamData.normalizedPairs.size)
		assertEquals(CreeperBeamData.sourcePairs[9], CreeperBeamData.sourcePairs[11])
		assertEquals(CreeperBeamData.sourcePairs[9], CreeperBeamData.normalizedPairs[9])
	}

	@Test
	fun `automation requires exactly four untouched disjoint pairs`() {
		val ready = (0..3).map { index -> pair(index, index * 2, index * 2 + 1) }
		assertEquals(ready, assertIs<CreeperBeamSolver.AutomationResult.Ready>(CreeperBeamSolver.selectAutomationPairs(ready)).pairs)

		val mixed = ready.toMutableList().also {
			it[0] = it[0].copy(firstState = CreeperBeamSolver.EndpointState.PRISMARINE)
		}
		assertIs<CreeperBeamSolver.AutomationResult.Ineligible>(CreeperBeamSolver.selectAutomationPairs(mixed))

		val overlapping = ready.toMutableList().also { it[3] = it[3].copy(first = it[0].first) }
		assertIs<CreeperBeamSolver.AutomationResult.Ineligible>(CreeperBeamSolver.selectAutomationPairs(overlapping))

		val reversed = ready.reversed()
		assertEquals(ready, assertIs<CreeperBeamSolver.AutomationResult.Ready>(CreeperBeamSolver.selectAutomationPairs(reversed)).pairs)
	}

	private fun hash(): String {
		val bytes = javaClass.classLoader.getResourceAsStream("assets/cgc/autopuzzles/creeperBeamSolutions.json")!!.use { it.readAllBytes() }
		return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
	}

	private fun pair(index: Int, first: Int, second: Int) = CreeperBeamSolver.WorldPair(
		sourceIndex = index,
		first = BlockPos(first, 70, 0),
		second = BlockPos(second, 80, 0),
		firstState = CreeperBeamSolver.EndpointState.SEA_LANTERN,
		secondState = CreeperBeamSolver.EndpointState.SEA_LANTERN
	)
}
