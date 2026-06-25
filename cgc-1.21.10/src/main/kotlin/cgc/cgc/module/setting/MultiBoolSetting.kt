package cgc.cgc.module.setting

import com.google.gson.JsonArray
import com.google.gson.JsonObject

class MultiBoolSetting(
	name: String,
	options: List<String>,
	enabledOptions: List<String> = emptyList(),
	onEdit: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null
) : Setting<LinkedHashMap<String, Boolean>>(name, supplier, onEdit) {
	override var value: LinkedHashMap<String, Boolean> = linkedMapOf()

	init {
		for (option in options) {
			value[option] = enabledOptions.contains(option)
		}
		defaultValue = LinkedHashMap(value)
	}

	operator fun get(key: String): Boolean =
		value.getOrDefault(key, false)

	fun set(key: String, state: Boolean) {
		if (!value.containsKey(key)) return
		value[key] = state
		onEdit()
	}

	fun toggle(key: String) {
		if (!value.containsKey(key)) return
		value[key] = !value.getOrDefault(key, false)
		onEdit()
	}

	fun valuesArray(): Array<String> =
		value.filterValues { it }.keys.toTypedArray()

	fun valuesList(): List<String> =
		value.filterValues { it }.keys.toList()

	override fun loadFromJson(obj: JsonObject) {
		val values = obj.getAsJsonArray("values") ?: return
		for (element in values) {
			val entry = element.asJsonObject
			set(entry.get("name").asString, entry.get("value").asBoolean)
		}
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)

		val array = JsonArray()
		for ((key, state) in value) {
			val entry = JsonObject()
			entry.addProperty("name", key)
			entry.addProperty("value", state)
			array.add(entry)
		}
		obj.add("values", array)
	}

	override val type: String = "multibool"

	override fun toString(): String =
		value.toString()
}
