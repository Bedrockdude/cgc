package cgc.cgc.module.impl.dungeon.autoc

import java.util.Locale

enum class AutoCStrafeDirection(val commandName: String) {
	W("w"),
	A("a"),
	S("s"),
	D("d");

	companion object {
		fun parse(value: String): AutoCStrafeDirection? =
			entries.firstOrNull { it.commandName == value.lowercase(Locale.ROOT) || it.name.equals(value, ignoreCase = true) }
	}
}
