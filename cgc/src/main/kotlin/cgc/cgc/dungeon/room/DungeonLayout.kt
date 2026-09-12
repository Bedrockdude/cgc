package cgc.cgc.dungeon.room

import net.minecraft.core.BlockPos

enum class DungeonDoorType {
	BLOOD,
	ENTRANCE,
	WITHER,
	NORMAL
}

/** A confirmed physical doorway between two distinct dungeon rooms. */
data class ScannedDungeonDoor(
	val position: BlockPos,
	val type: DungeonDoorType,
	val firstRoom: ScannedDungeonRoom,
	val secondRoom: ScannedDungeonRoom
) {
	fun other(room: ScannedDungeonRoom): ScannedDungeonRoom? = when (room.signature) {
		firstRoom.signature -> secondRoom
		secondRoom.signature -> firstRoom
		else -> null
	}
}

/**
 * Immutable view of the layout that has actually reached this vanilla client.
 * Missing chunks remain absent and appear in a later revision when they load.
 */
data class ScannedDungeonLayout(
	val revision: Long,
	val rooms: List<ScannedDungeonRoom>,
	val doors: List<ScannedDungeonDoor>,
	val bloodRoute: List<ScannedDungeonRoom>,
	val bloodRouteDoors: List<ScannedDungeonDoor>
) {
	fun adjacentRooms(room: ScannedDungeonRoom): List<ScannedDungeonRoom> =
		doors.mapNotNull { it.other(room) }.distinctBy { it.signature }

	fun doorsFor(room: ScannedDungeonRoom): List<ScannedDungeonDoor> =
		doors.filter { it.firstRoom.signature == room.signature || it.secondRoom.signature == room.signature }

	companion object {
		val EMPTY = ScannedDungeonLayout(0L, emptyList(), emptyList(), emptyList(), emptyList())
	}
}
