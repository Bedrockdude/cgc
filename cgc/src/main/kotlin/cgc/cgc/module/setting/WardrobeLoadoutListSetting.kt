package cgc.cgc.module.setting

import cgc.cgc.data.Keybind
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

class WardrobeLoadoutListSetting(
	name: String,
	supplier: (() -> Boolean)? = null,
	onEdit: (() -> Unit)? = null
) : Setting<MutableList<WardrobeLoadoutListSetting.Loadout>>(name, supplier, onEdit) {
	override var value: MutableList<Loadout> = mutableListOf()

	init {
		defaultValue = mutableListOf()
	}

	fun addLoadout(): Loadout {
		val loadout = Loadout()
		value.add(loadout)
		onEdit()
		return loadout
	}

	fun removeLoadout(loadout: Loadout) {
		value.removeAll { it.id == loadout.id }
		onEdit()
	}

	fun setName(loadout: Loadout, name: String) {
		loadout.name = name.filterNot(Char::isISOControl).take(MAX_NAME_LENGTH)
		onEdit()
	}

	fun configuredLoadouts(): List<Loadout> = value.filter { it.name.isNotBlank() }

	override fun loadFromJson(obj: JsonObject) {
		val array = obj.getAsJsonArray("value") ?: JsonArray()
		value = mutableListOf()
		for (element in array) {
			val entry = element.asJsonObject
			value.add(
				Loadout(
					id = entry.get("id")?.asString?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
					name = entry.get("name")?.asString?.filterNot(Char::isISOControl)?.take(MAX_NAME_LENGTH) ?: "",
					autoClose = entry.get("autoClose")?.asBoolean ?: entry.get("auto_close")?.asBoolean ?: false,
					keybind = Keybind(entry.get("keybind")?.asString ?: "key.keyboard.unknown")
				)
			)
		}
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		val array = JsonArray()
		for (loadout in value) {
			val entry = JsonObject()
			entry.addProperty("id", loadout.id)
			entry.addProperty("name", loadout.name)
			entry.addProperty("autoClose", loadout.autoClose)
			entry.addProperty("keybind", loadout.keybind.keyName)
			array.add(entry)
		}
		obj.add("value", array)
	}

	override val type: String = "wardrobe_loadouts"

	override val displayValue: String
		get() = "${configuredLoadouts().size} loadouts"

	data class Loadout(
		var id: String = UUID.randomUUID().toString(),
		var name: String = "",
		var autoClose: Boolean = false,
		val keybind: Keybind = Keybind()
	)

	companion object {
		private const val MAX_NAME_LENGTH = 80

		fun friendlyKeyName(keyName: String): String {
			if (keyName == "key.keyboard.unknown") return "None"
			return keyName
				.removePrefix("key.keyboard.")
				.removePrefix("key.mouse.")
				.replace("mouse.", "Mouse ")
				.replace("_", " ")
				.replaceFirstChar { it.titlecase() }
		}
	}
}
