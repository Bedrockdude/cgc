package cgc.cgc.module.setting

import cgc.cgc.data.Keybind
import com.google.gson.JsonObject

class KeybindSetting(
	name: String,
	keybind: Keybind,
	action: (() -> Unit)? = null,
	val persistent: Boolean = false,
	onEdit: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null
) : Setting<Keybind>(name, supplier, onEdit) {
	override var value: Keybind = keybind

	init {
		if (action != null) {
			value.setRunnable(action)
		}
		defaultValue = keybind
	}

	override fun loadFromJson(obj: JsonObject) {
		value.keyName = obj.get("value")?.asString ?: "key.keyboard.unknown"
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("value", value.keyName)
	}

	override val type: String = "keybind"

	override val displayValue: String
		get() = friendlyKeyName(value.keyName)

	private fun friendlyKeyName(keyName: String): String {
		if (keyName == "key.keyboard.unknown") return "None"
		return keyName
			.removePrefix("key.keyboard.")
			.removePrefix("key.mouse.")
			.replace("mouse.", "Mouse ")
			.replace("_", " ")
			.replaceFirstChar { it.titlecase() }
	}
}
