package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand

class RecordNode(
	pos: Pos = Pos(),
	val recordSeconds: Double = DEFAULT_RECORD_SECONDS,
	private val frames: MutableList<RecordFrame> = mutableListOf(),
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean =
		context.playRecording(frames)

	override fun name(): String =
		"record"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		30

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("recordSeconds", recordSeconds)
		val array = JsonArray()
		frames.forEach { array.add(it.serialize()) }
		json.add("frames", array)
		return json
	}

	fun addFrame(frame: RecordFrame) {
		frames.add(frame)
	}

	fun addEventToLastFrame(event: RecordEvent): Boolean {
		val frame = frames.lastOrNull() ?: return false
		frame.events.add(event)
		return true
	}

	fun frameCount(): Int =
		frames.size

	fun frames(): List<RecordFrame> =
		frames.toList()

	companion object {
		val COLOUR = Colour(180, 90, 255)
		private const val DEFAULT_RECORD_SECONDS = 3.0

		fun supply(player: LocalPlayer, args: String): RecordNode? {
			val seconds = args.trim().toDoubleOrNull() ?: return null
			if (seconds <= 0.0) {
				return null
			}
			return RecordNode(Pos(player.position()), seconds)
		}
	}
}

data class RecordFrame(
	val yaw: Float = 0.0f,
	val pitch: Float = 0.0f,
	val slot: Int = 0,
	val forward: Boolean = false,
	val back: Boolean = false,
	val left: Boolean = false,
	val right: Boolean = false,
	val jump: Boolean = false,
	val sneak: Boolean = false,
	val sprint: Boolean = false,
	val attack: Boolean = false,
	val lookSamples: MutableList<RecordLookSample> = mutableListOf(),
	val events: MutableList<RecordEvent> = mutableListOf()
) {
	fun serialize(): JsonObject {
		val json = JsonObject()
		json.addProperty("yaw", yaw)
		json.addProperty("pitch", pitch)
		json.addProperty("slot", slot)
		json.addProperty("forward", forward)
		json.addProperty("back", back)
		json.addProperty("left", left)
		json.addProperty("right", right)
		json.addProperty("jump", jump)
		json.addProperty("sneak", sneak)
		json.addProperty("sprint", sprint)
		json.addProperty("attack", attack)
		if (lookSamples.isNotEmpty()) {
			val array = JsonArray()
			lookSamples.forEach { array.add(it.serialize()) }
			json.add("lookSamples", array)
		}
		if (events.isNotEmpty()) {
			val array = JsonArray()
			events.forEach { array.add(it.serialize()) }
			json.add("events", array)
		}
		return json
	}
}

data class RecordLookSample(
	val offset: Float = 0.0f,
	val yaw: Float = 0.0f,
	val pitch: Float = 0.0f
) {
	fun serialize(): JsonObject {
		val json = JsonObject()
		json.addProperty("offset", offset)
		json.addProperty("yaw", yaw)
		json.addProperty("pitch", pitch)
		return json
	}
}

data class RecordEvent(
	val type: RecordEventType = RecordEventType.SWING,
	val hand: InteractionHand = InteractionHand.MAIN_HAND,
	val action: String = "",
	val block: Pos? = null,
	val hit: Pos? = null,
	val direction: Direction = Direction.UP,
	val inside: Boolean = false,
	val worldBorderHit: Boolean = false,
	val yaw: Float = 0.0f,
	val pitch: Float = 0.0f,
	val slot: Int = -1
) {
	fun serialize(): JsonObject {
		val json = JsonObject()
		json.addProperty("type", type.id)
		json.addProperty("hand", hand.name)
		if (action.isNotBlank()) {
			json.addProperty("action", action)
		}
		block?.let { json.add("block", AutoCNodeUtils.writePos(it)) }
		hit?.let { json.add("hit", AutoCNodeUtils.writePos(it)) }
		json.addProperty("direction", direction.name)
		if (inside) {
			json.addProperty("inside", true)
		}
		if (worldBorderHit) {
			json.addProperty("worldBorderHit", true)
		}
		if (type == RecordEventType.USE_ITEM) {
			json.addProperty("yaw", yaw)
			json.addProperty("pitch", pitch)
		}
		if (type == RecordEventType.SET_SLOT) {
			json.addProperty("slot", slot)
		}
		return json
	}
}

enum class RecordEventType(val id: String) {
	USE_ITEM("useItem"),
	USE_BLOCK("useBlock"),
	PLAYER_ACTION("playerAction"),
	SWING("swing"),
	SET_SLOT("setSlot");

	companion object {
		fun parse(value: String?): RecordEventType =
			entries.firstOrNull { it.id.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
				?: SWING
	}
}
