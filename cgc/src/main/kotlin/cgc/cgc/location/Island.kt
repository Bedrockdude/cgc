package cgc.cgc.location

enum class Island(val displayName: String) {
	SINGLEPLAYER("Singleplayer"),
	PRIVATE_ISLAND("Private Island"),
	GARDEN("The Garden"),
	SPIDER_DEN("Spider's Den"),
	CRIMSON_ISLE("Crimson Isle"),
	THE_END("The End"),
	GOLD_MINE("Gold Mine"),
	DEEP_CAVERNS("Deep Caverns"),
	DWARVEN_MINES("Dwarven Mines"),
	CRYSTAL_HOLLOWS("Crystal Hollows"),
	FARMING_ISLAND("The Farming Islands"),
	THE_PARK("The Park"),
	DUNGEON("Catacombs"),
	DUNGEON_HUB("Dungeon Hub"),
	HUB("Hub"),
	DARK_AUCTION("Dark Auction"),
	JERRY_WORKSHOP("Jerry's Workshop"),
	KUUDRA("Kuudra"),
	MINESHAFT("Mineshaft"),
	GALATEA("Galatea"),
	UNKNOWN("Unknown");

	fun isArea(island: Island): Boolean =
		this == island

	companion object {
		fun findByName(name: String): Island =
			entries.firstOrNull { name.contains(it.displayName, ignoreCase = true) } ?: UNKNOWN
	}
}
