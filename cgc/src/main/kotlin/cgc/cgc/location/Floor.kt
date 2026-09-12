package cgc.cgc.location

enum class Floor(val displayName: String?, val index: Int) {
	E("E", 0),
	F1("F1", 1),
	F2("F2", 2),
	F3("F3", 3),
	F4("F4", 4),
	F5("F5", 5),
	F6("F6", 6),
	F7("F7", 7),
	M1("M1", 8),
	M2("M2", 9),
	M3("M3", 10),
	M4("M4", 11),
	M5("M5", 12),
	M6("M6", 13),
	M7("M7", 14),
	NONE(null, -1);

	companion object {
		fun findByName(name: String): Floor =
			entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: NONE
	}
}
