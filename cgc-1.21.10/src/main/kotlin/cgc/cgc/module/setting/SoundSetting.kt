package cgc.cgc.module.setting

import com.google.gson.JsonObject

class SoundSetting(
	name: String,
	sound: String,
	var pitch: Float,
	var volume: Float,
	supplier: (() -> Boolean)? = null
) : Setting<String>(name, supplier, null) {
	override var value: String = sound

	init {
		defaultValue = sound
	}

	override fun loadFromJson(obj: JsonObject) {
		value = obj.get("value").asString
		pitch = obj.get("pitch").asFloat
		volume = obj.get("volume").asFloat
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("value", value)
		obj.addProperty("pitch", pitch)
		obj.addProperty("volume", volume)
	}

	override val type: String = "sound"
}
