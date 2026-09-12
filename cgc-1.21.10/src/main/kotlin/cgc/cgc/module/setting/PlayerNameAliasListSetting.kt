package cgc.cgc.module.setting

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

class PlayerNameAliasListSetting(
	name: String,
	supplier: (() -> Boolean)? = null,
	onEdit: (() -> Unit)? = null
) : Setting<MutableList<PlayerNameAliasListSetting.NameAlias>>(name, supplier, onEdit) {
	override var value: MutableList<NameAlias> = mutableListOf()

	init {
		defaultValue = mutableListOf()
	}

	fun addAlias(): NameAlias {
		val entry = NameAlias()
		value.add(entry)
		onEdit()
		return entry
	}

	fun removeAlias(entry: NameAlias) {
		value.removeAll { it.id == entry.id }
		onEdit()
	}

	fun setField(entry: NameAlias, field: Field, text: String) {
		val cleaned = clean(text)
		when (field) {
			Field.ALIAS -> entry.alias = cleaned
			Field.USERNAME -> entry.username = cleaned
		}
		onEdit()
	}

	fun fieldValue(entry: NameAlias, field: Field): String =
		when (field) {
			Field.ALIAS -> entry.alias
			Field.USERNAME -> entry.username
		}

	fun configuredAliases(): List<NameAlias> {
		val seen = hashSetOf<String>()
		return value.filter { entry ->
			entry.alias.isNotBlank() && entry.username.isNotBlank() && seen.add(entry.alias.lowercase())
		}
	}

	override fun loadFromJson(obj: JsonObject) {
		val array = obj.getAsJsonArray("value") ?: JsonArray()
		value = mutableListOf()
		for (element in array) {
			val entry = element.asJsonObject
			val alias = clean(entry.get("alias")?.asString ?: "")
			val username = clean(entry.get("username")?.asString ?: entry.get("ign")?.asString ?: "")
			value.add(
				NameAlias(
					id = entry.get("id")?.asString?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
					alias = alias,
					username = username
				)
			)
		}
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		val array = JsonArray()
		for (entry in value) {
			val entryObj = JsonObject()
			entryObj.addProperty("id", entry.id)
			entryObj.addProperty("alias", entry.alias)
			entryObj.addProperty("username", entry.username)
			array.add(entryObj)
		}
		obj.add("value", array)
	}

	override val type: String = "player_name_aliases"

	override val displayValue: String
		get() = "${configuredAliases().size} names"

	data class NameAlias(
		var id: String = UUID.randomUUID().toString(),
		var alias: String = "",
		var username: String = ""
	)

	enum class Field {
		ALIAS,
		USERNAME
	}

	companion object {
		private const val MAX_NAME_LENGTH = 16

		private fun clean(value: String): String =
			value.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(MAX_NAME_LENGTH)
	}
}
