package cgc.cgc.dungeon.room

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DungeonLayoutTest {
	@Test
	fun `later partial scans retain rooms already received by the client`() {
		val entrance = room("entrance", "ENTRANCE", -185, -185)
		val blood = room("blood", "BLOOD", -25, -25)
		val rescannedEntrance = entrance.copy(rotation = DungeonRoomRotation.TOPRIGHT)

		val retained = mergeRetainedRooms(
			mapOf((-185 to -185) to entrance, (-25 to -25) to blood),
			mapOf((-185 to -185) to rescannedEntrance)
		)

		assertSame(rescannedEntrance, retained[-185 to -185])
		assertSame(blood, retained[-25 to -25])
	}

	@Test
	fun `room identity remains stable when rotation finishes loading`() {
		val partial = room("long_room", "NORMAL", -185, -185)
		val complete = partial.copy(rotation = DungeonRoomRotation.BOTRIGHT)

		assertEquals(partial.signature, complete.signature)
	}

	@Test
	fun `refining one tile refreshes every retained tile of that room`() {
		val partial = room("long_room", "NORMAL", -185, -185)
		val complete = partial.copy(rotation = DungeonRoomRotation.TOPRIGHT)
		val retained = mergeRetainedRooms(
			mapOf((-185 to -185) to partial, (-153 to -185) to partial),
			mapOf((-185 to -185) to complete)
		)

		assertSame(complete, retained[-185 to -185])
		assertSame(complete, retained[-153 to -185])
	}

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
