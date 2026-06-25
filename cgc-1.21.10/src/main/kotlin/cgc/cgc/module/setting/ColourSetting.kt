package cgc.cgc.module.setting

import cgc.cgc.data.Colour
import com.google.gson.JsonObject

class ColourSetting(
	name: String,
	defaultValue: Colour,
	onEdit: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null
) : Setting<Colour>(name, supplier, onEdit) {
	override var value: Colour = defaultValue
	private val originalDefault = defaultValue

	init {
		this.defaultValue = defaultValue
	}

	fun resetToDefault() {
		value = originalDefault.copyColour()
		onEdit()
	}

	override fun loadFromJson(obj: JsonObject) {
		value = Colour(
			obj.get("red").asInt,
			obj.get("green").asInt,
			obj.get("blue").asInt,
			obj.get("alpha").asInt
		)
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("red", value.red)
		obj.addProperty("green", value.green)
		obj.addProperty("blue", value.blue)
		obj.addProperty("alpha", value.alpha)
	}

	override val type: String = "colour"

	override val displayValue: String
		get() = "#%02X%02X%02X".format(value.red, value.green, value.blue)
}
