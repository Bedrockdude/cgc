package cgc.cgc.dungeon.room

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals

class DungeonLayoutTest {
	@Test
	fun `door exposes both adjacent rooms`() {
		val entrance = room("entrance", "ENTRANCE", -185, -185)
		val normal = room("normal", "NORMAL", -153, -185)
		val door = ScannedDungeonDoor(BlockPos(-169, 69, -185), DungeonDoorType.NORMAL, entrance, normal)
		val layout = ScannedDungeonLayout(1L, listOf(entrance, normal), listOf(door), emptyList(), emptyList())

		assertEquals(listOf(normal), layout.adjacentRooms(entrance))
		assertEquals(listOf(entrance), layout.adjacentRooms(normal))
		assertEquals(listOf(door), layout.doorsFor(entrance))
	}

	private fun room(key: String, type: String, x: Int, z: Int) = ScannedDungeonRoom(
		key = key,
		displayName = key,
		type = type,
		shape = "1x1",
		core = key.hashCode(),
		mainX = x,
		mainZ = z,
		rotation = DungeonRoomRotation.TOPLEFT
	)
}
