package cgc.cgc.module.setting

import com.google.gson.JsonObject

class ButtonSetting(
	name: String,
	defaultValue: String,
	val action: () -> Unit,
	supplier: (() -> Boolean)? = null
) : Setting<String>(name, supplier, null) {
	override var value: String = defaultValue

	init {
		this.defaultValue = defaultValue
	}

	fun press() {
		action()
		onEdit()
	}

	override fun loadFromJson(obj: JsonObject) {
	}

	override fun saveToJson(obj: JsonObject) {
	}

	override val type: String = "button"

	override fun savesToConfig(): Boolean = false
}
