package cgc.cgc.navigation

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EtherwarpRaycastTest {
	@Test
	fun `partial block cell conservatively blocks a corner ray`() {
		val stairCell = BlockPos(3, 65, 0)
		val target = BlockPos(8, 65, 0)
		val hit = conservativeFirstBlock(Vec3(0.5, 65.9, 0.05), Vec3(9.0, 65.9, 0.05)) {
			it == stairCell || it == target
		}
		assertEquals(stairCell, hit?.block)
	}

	@Test
	fun `clear cells reach the intended target`() {
		val target = BlockPos(8, 65, 0)
		val hit = conservativeFirstBlock(Vec3(0.5, 65.5, 0.5), Vec3(9.0, 65.5, 0.5)) { it == target }
		assertEquals(target, hit?.block)
	}

	@Test
	fun `aim candidates stay on real components of a stair shape`() {
		val lower = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
		val upperBack = Shapes.box(0.0, 0.5, 0.5, 1.0, 1.0, 1.0)
		val candidates = collisionPartAimCandidates(Shapes.or(lower, upperBack), BlockPos.ZERO)
		// This region exists inside the union bounds but is the empty upper-front stair corner.
		assertEquals(false, candidates.any { it.y > 0.5 && it.z < 0.5 })
	}

	@Test
	fun `Etherwarp space rules distinguish transparent from valid landing space`() {
		assertTrue(isEtherwarpRayTransparent(Blocks.AIR))
		assertTrue(isEtherwarpRayTransparent(Blocks.LADDER))
		assertFalse(isEtherwarpRayTransparent(Blocks.STONE))
		assertTrue(isValidEtherwarpLandingSpace(Blocks.AIR))
		assertFalse(isValidEtherwarpLandingSpace(Blocks.LADDER))
		assertFalse(isValidEtherwarpLandingSpace(Blocks.POTTED_DANDELION))
	}
}
