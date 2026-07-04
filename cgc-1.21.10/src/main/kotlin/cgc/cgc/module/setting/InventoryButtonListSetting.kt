package cgc.cgc.module.setting

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale
import java.util.UUID

class InventoryButtonListSetting(
	name: String,
	supplier: (() -> Boolean)? = null,
	onEdit: (() -> Unit)? = null
) : Setting<MutableList<InventoryButtonListSetting.Button>>(name, supplier, onEdit) {
	override var value: MutableList<Button> = mutableListOf()

	init {
		defaultValue = mutableListOf()
	}

	fun addButton(): Button {
		val button = createButton(8 + value.size * 34, 8)
		value.add(button)
		onEdit()
		return button
	}

	fun addButtonAt(x: Int, y: Int, maxX: Int, maxY: Int): Button {
		val button = createButton(x.coerceIn(0, maxX.coerceAtLeast(0)), y.coerceIn(0, maxY.coerceAtLeast(0)))
		value.add(button)
		onEdit()
		return button
	}

	private fun createButton(x: Int, y: Int): Button {
		val button = Button(
			label = "Button ${value.size + 1}",
			command = "",
			iconItem = DEFAULT_ICON,
			x = x,
			y = y
		)
		return button
	}

	fun removeButton(button: Button) {
		value.removeAll { it.id == button.id }
		onEdit()
	}

	fun setField(button: Button, field: Field, text: String) {
		when (field) {
			Field.LABEL -> button.label = text.take(MAX_LABEL_LENGTH)
			Field.COMMAND -> button.command = text.take(MAX_COMMAND_LENGTH)
			Field.ICON -> button.iconItem = cleanIcon(text.take(MAX_ICON_LENGTH))
		}
		onEdit()
	}

	fun fieldValue(button: Button, field: Field): String =
		when (field) {
			Field.LABEL -> button.label
			Field.COMMAND -> button.command
			Field.ICON -> button.iconItem
		}

	fun moveButton(button: Button, x: Int, y: Int, maxX: Int, maxY: Int) {
		button.x = x.coerceIn(0, maxX.coerceAtLeast(0))
		button.y = y.coerceIn(0, maxY.coerceAtLeast(0))
		onEdit()
	}

	override fun loadFromJson(obj: JsonObject) {
		val array = obj.getAsJsonArray("value") ?: JsonArray()
		value = mutableListOf()
		for (element in array) {
			val buttonObj = element.asJsonObject
			value.add(
				Button(
					id = buttonObj.get("id")?.asString?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
					label = buttonObj.get("label")?.asString?.take(MAX_LABEL_LENGTH) ?: "Button",
					command = buttonObj.get("command")?.asString?.take(MAX_COMMAND_LENGTH) ?: "",
					iconItem = cleanIcon(buttonObj.get("iconItem")?.asString ?: buttonObj.get("icon_item")?.asString ?: DEFAULT_ICON),
					x = buttonObj.get("x")?.asInt ?: 8,
					y = buttonObj.get("y")?.asInt ?: 8
				)
			)
		}
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		val array = JsonArray()
		for (button in value) {
			val buttonObj = JsonObject()
			buttonObj.addProperty("id", button.id)
			buttonObj.addProperty("label", button.label)
			buttonObj.addProperty("command", button.command)
			buttonObj.addProperty("iconItem", button.iconItem)
			buttonObj.addProperty("x", button.x)
			buttonObj.addProperty("y", button.y)
			array.add(buttonObj)
		}
		obj.add("value", array)
	}

	override val type: String = "inventory_buttons"

	override val displayValue: String
		get() = "${value.size} buttons"

	data class Button(
		var id: String = UUID.randomUUID().toString(),
		var label: String = "Button",
		var command: String = "",
		var iconItem: String = DEFAULT_ICON,
		var x: Int = 8,
		var y: Int = 8
	)

	enum class Field {
		LABEL,
		COMMAND,
		ICON
	}

	companion object {
		const val BUTTON_SIZE = 28
		private const val DEFAULT_ICON = "minecraft:paper"
		private const val MAX_LABEL_LENGTH = 32
		private const val MAX_COMMAND_LENGTH = 256
		private const val MAX_ICON_LENGTH = 96

		fun iconItemId(value: String): String {
			val text = value.trim().lowercase(Locale.ROOT)
			if (text.isBlank()) {
				return DEFAULT_ICON
			}
			return if (":" in text) text else "minecraft:$text"
		}

		private fun cleanIcon(value: String): String =
			value.trim().lowercase(Locale.ROOT)
	}
}
