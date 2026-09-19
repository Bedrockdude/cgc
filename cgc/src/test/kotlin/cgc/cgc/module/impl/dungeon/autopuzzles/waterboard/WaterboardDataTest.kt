package cgc.cgc.module.impl.dungeon.autopuzzles.waterboard

import java.security.MessageDigest
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaterboardDataTest {
	@Test
	fun `all forty pinned schedules load and preserve serial tie order`() {
		assertEquals("F027B7F0B924FEF2FE9F100074D12EEE23C8234B8FC4FB5DB5EBDAD1E45741E8", hash("assets/cgc/autopuzzles/waterSolutions.json"))
		assertEquals(setOf(0, 1, 2, 3), WaterboardData.solutions.keys)
		assertTrue(WaterboardData.solutions.values.all { it.size == 10 })
		for (pattern in 0..3) {
			for (gateKey in WaterboardData.solutions.getValue(pattern).keys) {
				val gates = gateKey.map { it.digitToInt() }.toSet()
				val candidate = WaterboardSolver.solve(pattern, gates)!!
				assertTrue(candidate.actions.isNotEmpty())
				val zeroActions = candidate.actions.filter { it.timeSeconds == 0.0 }
				val waterIndex = zeroActions.indexOfLast { it.lever == WaterLever.WATER }
				assertTrue(waterIndex >= 0)
				assertTrue(waterIndex < 0 || zeroActions.drop(waterIndex + 1).none { it.lever != WaterLever.WATER })
			}
		}
		val solution = WaterboardSolver.solve(0, setOf(0, 1, 2))!!
		val zeroOrder = solution.actions.filter { it.timeSeconds == 0.0 }.map { it.lever }
		assertEquals(
			listOf(WaterLever.QUARTZ, WaterLever.GOLD, WaterLever.DIAMOND, WaterLever.TERRACOTTA, WaterLever.WATER),
			zeroOrder
		)
	}

	@Test
	fun `RSZ position resource and license remain byte exact`() {
		assertEquals("B813BFBCFE907F315F79452D128C8933185417046CA6ACE2069E4A9333BCFCDB", hash("assets/cgc/autopuzzles/waterboardPositions.json"))
		assertEquals("3C1AA0677BB05540B65890D855835714E2B033B90F3DF493CD28A1689576D8E4", hash("assets/cgc/autopuzzles/LICENSE-RSZ.txt"))
		assertEquals(7, WaterboardData.geometry.size)
		assertEquals(WaterboardData.Point(19.5, 60.0, 20.5), WaterboardData.geometry.getValue(WaterLever.QUARTZ).etherwarpSupport)
	}

	@Test
	fun `goal gates may only open monotonically after water starts`() {
		val initial = setOf(0, 1, 2)
		assertTrue(WaterboardSolver.gateSnapshotValid(initial, initial, emptySet(), waterStarted = false))
		assertFalse(WaterboardSolver.gateSnapshotValid(initial, setOf(0, 1), emptySet(), waterStarted = false))
		assertTrue(WaterboardSolver.gateSnapshotValid(initial, setOf(0, 1), emptySet(), waterStarted = true))
		assertFalse(WaterboardSolver.gateSnapshotValid(initial, setOf(0, 1, 4), emptySet(), waterStarted = true))
		assertFalse(WaterboardSolver.gateSnapshotValid(initial, setOf(0, 1, 2), setOf(2), waterStarted = true))
	}

	@Test
	fun `Etherwarp fallback checks supports one block above and below the saved height`() {
		val preferred = BlockPos(10, 60, 20)
		val candidates = WaterboardRoutePlanner.nearbyEtherwarpSupports(preferred)
		assertEquals(26, candidates.size)
		assertFalse(preferred in candidates)
		assertTrue(preferred.below() in candidates)
		assertTrue(preferred.above() in candidates)
		assertEquals(candidates.size, candidates.distinct().size)
	}

	@Test
	fun `Waterboard fallback searches around saved point approach and lever while retaining reach`() {
		val lever = BlockPos(10, 61, 10)
		val candidates = WaterboardRoutePlanner.etherwarpSupportCandidates(
			BlockPos(19, 60, 20), Vec3(12.4, 60.0, 11.3), lever, 4.5
		)

		assertTrue(BlockPos(10, 60, 10) in candidates)
		assertTrue(candidates.all { Vec3(it.x + 0.5, it.y + 1.0, it.z + 0.5).distanceToSqr(lever.center) <= 4.5 * 4.5 })
	}

	private fun hash(resource: String): String {
		val bytes = javaClass.classLoader.getResourceAsStream(resource)!!.use { it.readAllBytes() }
		return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
	}
}
