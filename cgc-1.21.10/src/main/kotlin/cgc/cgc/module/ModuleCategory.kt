package cgc.cgc.module

enum class ModuleCategory(
	val displayName: String,
	val colorRgb: Int
) {
	MOVEMENT("Movement", 0x55AAFF),
	DUNGEONS("Dungeons", 0xFF5555),
	PLAYER("Player", 0xAAFF55),
	RENDER("Render", 0xFFFF55),
	UTILS("Utils", 0x55FFDD),
	FIXIES("Fixies", 0xFFAA55),
	GENERAL("General", 0xAAAAAA),
	OTHER("Other", 0xDD42F5)
}
