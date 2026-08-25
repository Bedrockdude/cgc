package cgc.cgc.module.setting

import com.google.gson.JsonObject

class StringSetting(
	name: String,
	defaultValue: String,
	val allowBlank: Boolean = true,
	val secure: Boolean = false,
	val maxLength: Int = 32,
	val pasteButton: Boolean = false,
	onEdit: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null
) : Setting<String>(name, supplier, onEdit) {
	override var value: String = defaultValue

	init {
		this.defaultValue = defaultValue
	}

	fun setText(text: String) {
		if (!allowBlank && text.isBlank()) return
		value = text.take(maxLength)
		onEdit()
	}

	override fun loadFromJson(obj: JsonObject) {
		setText(obj.get("value").asString)
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("value", value)
	}

	override val type: String = "string"
}
