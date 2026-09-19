package cgc.cgc.module.impl.dungeon.autopuzzles.icefill

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
	fun `long lateral runs are promoted to the forward Etherwarp direction`() {
		val path = listOf(BlockPos(0, 70, 0), BlockPos(1, 70, 0), BlockPos(2, 70, 0))
		assertTrue(IceFillMovementPlanner.shouldPromoteLateralRun(path, 0, Direction.NORTH))
	}

	@Test
	fun `single lateral step remains a strafe when the old forward direction follows`() {
		val path = listOf(BlockPos(0, 70, 0), BlockPos(1, 70, 0), BlockPos(1, 70, -1))
		assertFalse(IceFillMovementPlanner.shouldPromoteLateralRun(path, 0, Direction.NORTH))
	}

	@Test
	fun `single lateral step rotates when that avoids a following reversal`() {
		val path = listOf(BlockPos(0, 70, 0), BlockPos(1, 70, 0), BlockPos(1, 70, 1))
		assertTrue(IceFillMovementPlanner.shouldPromoteLateralRun(path, 0, Direction.NORTH))
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

	@Test
	fun `recorded routes persist independently for different floor layouts`() {
		val file = directory.resolve("ice_fill_routes.json")
		val firstCells = listOf(BlockPos(0, 69, 0), BlockPos(1, 69, 0), BlockPos(1, 69, 1))
		val secondCells = listOf(BlockPos(0, 70, 0), BlockPos(0, 70, 1))
		val firstSignature = IceFillRouteStore.layoutSignature(firstCells.reversed())
		val secondSignature = IceFillRouteStore.layoutSignature(secondCells)
		assertNotEquals(firstSignature, secondSignature)

		IceFillRouteStore(file).apply {
			save(firstSignature, IceFillRouteStore.Route(
				firstCells,
				listOf(Direction.EAST, Direction.SOUTH, Direction.SOUTH),
				movements = listOf(
					IceFillRouteStore.MovementInput.FORWARD,
					IceFillRouteStore.MovementInput.LEFT,
					null
				)
			))
			save(secondSignature, IceFillRouteStore.Route(secondCells, listOf(Direction.SOUTH, Direction.SOUTH)))
		}
		val loaded = IceFillRouteStore(file)
		assertEquals(firstCells, loaded.route(firstSignature)?.cells)
		assertEquals(listOf(Direction.SOUTH, Direction.SOUTH), loaded.route(secondSignature)?.facings)
		assertTrue(loaded.route(firstSignature)?.roomRelativeFacings == true)
		assertEquals(IceFillRouteStore.MovementInput.LEFT, loaded.route(firstSignature)?.movements?.get(1))
		assertEquals(firstSignature, IceFillRouteStore.layoutSignature(firstCells))
	}

	@Test
	fun `legacy routes keep their cell order but mark unrecoverable world facings`() {
		val file = directory.resolve("legacy_ice_fill_routes.json")
		Files.writeString(file, """
			{"version":1,"routes":{"legacy":[
				{"x":0,"y":70,"z":0,"facing":"EAST"},
				{"x":1,"y":70,"z":0,"facing":"SOUTH"}
			]}}
		""".trimIndent())

		val route = IceFillRouteStore(file).route("legacy")
		assertEquals(listOf(BlockPos(0, 70, 0), BlockPos(1, 70, 0)), route?.cells)
		assertFalse(route?.roomRelativeFacings ?: true)
	}
}
