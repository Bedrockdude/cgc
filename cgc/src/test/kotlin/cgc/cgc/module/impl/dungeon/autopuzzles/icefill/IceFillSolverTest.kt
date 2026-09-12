package cgc.cgc.module.impl.dungeon.autopuzzles.icefill

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IceFillSolverTest {
	@TempDir
	lateinit var directory: Path

	@Test
	fun `Hamiltonian solver covers each cell once and ends at checkpoint exit`() {
		val spaces = setOf(BlockPos(0, 70, 0), BlockPos(1, 70, 0), BlockPos(1, 70, 1), BlockPos(0, 70, 1))
		val path = IceFillSolver.solve(spaces, BlockPos(0, 70, 0), BlockPos(0, 70, 1))
		assertEquals(spaces.size, path.size)
		assertEquals(spaces, path.toSet())
		assertEquals(BlockPos(0, 70, 1), path.last())
		assertTrue(path.zipWithNext().all { (a, b) -> kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.z - b.z) == 1 })
	}

	@Test
	fun `an ice start already occupies the first route cell`() {
		val path = listOf(BlockPos(0, 70, 0), BlockPos(1, 70, 0))
		assertEquals(0, iceFillLaunchCursor(path.first(), path))
	}

	@Test
	fun `a legacy non ice start remains immediately before the route`() {
		val path = listOf(BlockPos(0, 70, 0), BlockPos(1, 70, 0))
		assertEquals(-1, iceFillLaunchCursor(BlockPos(-1, 70, 0), path))
		assertEquals(null, iceFillLaunchCursor(BlockPos(-2, 70, 0), path))
	}

	@Test
	fun `calibration persists all four room relative values`() {
		val file = directory.resolve("ice_fill_calibration.json")
		IceFillCalibrationStore(file).apply {
			setFallY(67.25)
			setFloorStart(0, BlockPos(1, 69, -9))
			setFloorStart(1, BlockPos(1, 70, -4))
			setFloorStart(2, BlockPos(1, 71, 3))
		}
		val loaded = IceFillCalibrationStore(file).current()
		assertTrue(loaded.complete)
		assertEquals(67.25, loaded.fallY)
		assertEquals(BlockPos(1, 71, 3), loaded.floorStarts[2])
	}
}
