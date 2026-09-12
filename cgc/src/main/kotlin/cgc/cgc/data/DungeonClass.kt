package cgc.cgc.data

enum class DungeonClass(val displayName: String) {
	MAGE("Mage"),
	BERSERKER("Berserk"),
	TANK("Tank"),
	HEALER("Healer"),
	ARCHER("Archer"),
	NONE("EMPTY");

	fun sameClass(other: DungeonClass): Boolean =
		displayName.equals(other.displayName, ignoreCase = true)

	companion object {
		fun findClassString(value: String?): DungeonClass =
			entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) } ?: NONE
	}
}
