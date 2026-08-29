package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.dungeon.room.DungeonRoomRotation
import cgc.cgc.dungeon.room.ScannedDungeonRoom
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoPuzzleRoomCoordinatesTest {
	@Test
	fun `fixed block transforms round trip through every rotation`() {
		for (rotation in DungeonRoomRotation.entries.filter { it != DungeonRoomRotation.UNKNOWN }) {
			val room = room(rotation)
			val local = BlockPos(-6, 97, 1)
			val world = AutoPuzzleRoomCoordinates.worldBlock(room, local.x, local.y, local.z)
			assertEquals(local, AutoPuzzleRoomCoordinates.relativeBlock(room, world), rotation.name)
		}
	}

	@Test
	fun `legacy Waterboard origin is subtracted exactly once`() {
		val room = room(DungeonRoomRotation.TOPLEFT)
		assertEquals(BlockPos(105, 61, 205), AutoPuzzleRoomCoordinates.legacyWaterBlock(room, 20, 61, 20))
		assertEquals(BlockPos(100, 56, 204), AutoPuzzleRoomCoordinates.worldBlock(room, 0, 56, 4))
	}

	@Test
	fun `fractional Waterboard Etherwarp supports transform before block flooring`() {
		val expected = mapOf(
			DungeonRoomRotation.TOPLEFT to BlockPos(104, 60, 205),
			DungeonRoomRotation.TOPRIGHT to BlockPos(94, 60, 204),
			DungeonRoomRotation.BOTRIGHT to BlockPos(95, 60, 194),
			DungeonRoomRotation.BOTLEFT to BlockPos(105, 60, 195)
		)
		for ((rotation, block) in expected) {
			val point = AutoPuzzleRoomCoordinates.legacyWaterPoint(room(rotation), 19.5, 60.0, 20.5)
			assertEquals(block, BlockPos.containing(point), rotation.name)
		}
	}

	@Test
	fun `Blaze player positions use the non-fixed room transform`() {
		val room = room(DungeonRoomRotation.TOPRIGHT)
		val local = Vec3(-6.5, 97.0, 0.5)
		val world = AutoPuzzleRoomCoordinates.worldPosition(room, local.x, local.y, local.z)
		assertEquals(Vec3(100.5, 97.0, 193.5), world)
		assertEquals(local, AutoPuzzleRoomCoordinates.relativePosition(room, world))
		assertEquals(Vec3(99.5, 97.0, 193.5), AutoPuzzleRoomCoordinates.worldPoint(room, local.x, local.y, local.z))
	}

	private fun room(rotation: DungeonRoomRotation) = ScannedDungeonRoom(
		key = "test",
		displayName = "test",
		type = "PUZZLE",
		shape = "1x1",
		core = 1,
		mainX = 100,
		mainZ = 200,
		rotation = rotation
	)
}
