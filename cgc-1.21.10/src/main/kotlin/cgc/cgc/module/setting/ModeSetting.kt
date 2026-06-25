package cgc.cgc.module.setting

import com.google.gson.JsonObject

class ModeSetting(
	name: String,
	defaultValue: String,
	modes: List<String>,
	onEdit: (() -> Unit)? = null,
	supplier: (() -> Boolean)? = null
) : Setting<String>(name, supplier, onEdit) {
	val values: ArrayList<String> = ArrayList(modes)
	override var value: String = defaultValue

	init {
		require(values.isNotEmpty()) { "ModeSetting requires at least one mode" }
		require(defaultValue in values) { "Default mode must exist in modes" }
		this.defaultValue = defaultValue
	}

	fun cycle(direction: Int = 1) {
		val current = values.indexOf(value).takeIf { it >= 0 } ?: 0
		value = values[Math.floorMod(current + direction, values.size)]
		onEdit()
	}

	fun setByIndex(index: Int) {
		if (index !in values.indices) return
		value = values[index]
		onEdit()
	}

	val index: Int
		get() = values.indexOf(value)

	fun inRange(min: Int, max: Int): Boolean =
		index in (min + 1) until max

	fun inRangeInclusive(min: Int, max: Int): Boolean =
		index in min..max

	fun cycleBackwards() {
		cycle(-1)
	}

	fun isMode(other: String): Boolean =
		value.equals(other, ignoreCase = true)

	override fun loadFromJson(obj: JsonObject) {
		value = obj.get("value").asString
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("value", value)
	}

	override val type: String = "mode"

	override val displayValue: String
		get() = value
}
