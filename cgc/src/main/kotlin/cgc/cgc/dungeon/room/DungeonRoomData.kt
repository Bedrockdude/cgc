package cgc.cgc.dungeon.room

data class DungeonRoomData(
	val name: String? = null,
	val type: String? = null,
	val shape: String? = null,
	val cores: List<Int>? = null,
	val crypts: Int = 0,
	val secrets: Int = 0,
	val trappedChests: Int = 0
) {
	val displayName: String
		get() = name?.takeIf { it.isNotBlank() } ?: "Unknown"

	val roomType: String
		get() = type?.takeIf { it.isNotBlank() } ?: "NORMAL"

	val roomShape: String
		get() = shape?.takeIf { it.isNotBlank() } ?: "Unknown"
}
