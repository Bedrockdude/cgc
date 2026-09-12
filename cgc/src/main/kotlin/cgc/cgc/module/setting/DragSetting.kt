package cgc.cgc.module.setting

import com.google.gson.JsonObject
import org.joml.Vector2d

class DragSetting(
	name: String,
	defaultPos: Vector2d,
	defaultScale: Vector2d,
	supplier: (() -> Boolean)? = null
) : Setting<Vector2d>(name, supplier, null) {
	var position: Vector2d = Vector2d(defaultPos)
	var dragPos: Vector2d = Vector2d()
	var scale: Vector2d = Vector2d(defaultScale)
	var dragging: Boolean = false

	override var value: Vector2d
		get() = position
		set(value) {
			position = Vector2d(value)
			onEdit()
		}

	init {
		defaultValue = Vector2d(defaultPos)
	}

	override fun loadFromJson(obj: JsonObject) {
		position = Vector2d(obj.get("x").asDouble, obj.get("y").asDouble)
		scale = Vector2d(obj.get("scaleX").asDouble, obj.get("scaleY").asDouble)
	}

	override fun saveToJson(obj: JsonObject) {
		obj.addProperty("name", name)
		obj.addProperty("type", type)
		obj.addProperty("x", position.x)
		obj.addProperty("y", position.y)
		obj.addProperty("scaleX", scale.x)
		obj.addProperty("scaleY", scale.y)
	}

	override val type: String = "drag"

	override val displayValue: String
		get() = "${position.x.toInt()}, ${position.y.toInt()}"
}
