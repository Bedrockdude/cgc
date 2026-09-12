package cgc.cgc.module.setting.group

import cgc.cgc.module.SubModule
import cgc.cgc.module.setting.Setting
import com.google.gson.JsonObject

open class GroupSetting<T : SubModule<*>>(
	name: String,
	subModule: T,
	supplier: (() -> Boolean)? = null,
	override val description: String = "",
	val toggleable: Boolean = true
) : Setting<T>(name, supplier, null) {
	override var value: T = subModule

	init {
		defaultValue = subModule
	}

	fun get(setting: String): Setting<*>? =
		value.settings.firstOrNull { it.name == setting }

	fun add(vararg settings: Setting<*>) {
		value.registerProperty(*settings)
	}

	override fun loadFromJson(obj: JsonObject) {
	}

	override fun saveToJson(obj: JsonObject) {
	}

	override val type: String = "group"

	override fun savesToConfig(): Boolean = false
}
