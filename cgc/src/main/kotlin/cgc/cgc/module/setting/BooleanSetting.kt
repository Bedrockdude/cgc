package cgc.cgc.module.setting

import com.google.gson.JsonObject

class BooleanSetting(
	name: String,
	defaultValue: Boolean,
	onEdit: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null
) : Setting<Boolean>(name, supplier, onEdit) {
	override var value: Boolean = defaultValue

	init {
		this.defaultValue = defaultValue
	}

	fun toggle() {
		value = !value
		onEdit()
	}

	override fun loadFromJson(obj: JsonObject) {
		value = obj.get("value").asBoolean
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("value", value)
	}

	override val type: String = "boolean"

	override val displayValue: String
		get() = if (value) "On" else "Off"
}
