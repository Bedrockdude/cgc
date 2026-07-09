package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.data.DungeonClass
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.LeapCounter
import cgc.cgc.module.impl.dungeon.autoc.nodes.BreakNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.BonzoNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.CommandNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.CrouchNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.EdgeNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.EtherwarpNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.InteractNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.JumpNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.LeapNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.LookNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordEvent
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordEventType
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordFrame
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordLookSample
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.StopNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.StrafeNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.UseNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.WalkNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.WarpNode
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import java.lang.reflect.Type
import java.util.Locale

class AutoCNodeAdapter : JsonDeserializer<AutoCNode>, JsonSerializer<AutoCNode> {
	override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AutoCNode {
		val obj = json.asJsonObject
		val type = obj.get("type")?.asString?.lowercase(Locale.ROOT)
			?: throw JsonParseException("Auto C node is missing a type.")
		val pos = readPos(obj.get("pos")?.asJsonObject ?: obj.get("localPos")?.asJsonObject)
		val radius = obj.get("radius")?.asFloat ?: AutoCNode.DEFAULT_RADIUS

		val node = when (type) {
			"look" -> LookNode(
				pos = pos,
				yaw = obj.get("yaw")?.asFloat ?: 0.0f,
				pitch = obj.get("pitch")?.asFloat ?: 0.0f,
				radius = radius
			)
			"walk" -> WalkNode(
				pos = pos,
				yaw = obj.get("yaw")?.asFloat ?: 0.0f,
				pitch = obj.get("pitch")?.asFloat ?: 0.0f,
				radius = radius
			)
			"strafe" -> StrafeNode(
				pos = pos,
				direction = AutoCStrafeDirection.parse(obj.get("direction")?.asString ?: "w") ?: AutoCStrafeDirection.W,
				radius = radius
			)
			"etherwarp" -> EtherwarpNode(
				pos = pos,
				yaw = obj.get("yaw")?.asFloat ?: 0.0f,
				pitch = obj.get("pitch")?.asFloat ?: 0.0f,
				block = readPos(obj.get("block")?.asJsonObject),
				target = readPos(obj.get("target")?.asJsonObject ?: obj.get("block")?.asJsonObject),
				radius = radius
			)
			"warp" -> WarpNode(
				pos = pos,
				yaw = obj.get("yaw")?.asFloat ?: 0.0f,
				pitch = obj.get("pitch")?.asFloat ?: 0.0f,
				radius = radius
			)
			"interact" -> InteractNode(
				pos = pos,
				yaw = obj.get("yaw")?.asFloat ?: 0.0f,
				pitch = obj.get("pitch")?.asFloat ?: 0.0f,
				block = readPos(obj.get("block")?.asJsonObject),
				hit = readPos(obj.get("hit")?.asJsonObject ?: obj.get("target")?.asJsonObject ?: obj.get("block")?.asJsonObject),
				await = obj.get("await")?.asBoolean ?: false,
				radius = radius
			)
			"use" -> UseNode(
				pos = pos,
				yaw = obj.get("yaw")?.asFloat ?: 0.0f,
				pitch = obj.get("pitch")?.asFloat ?: 0.0f,
				skyBlockId = obj.get("skyBlockId")?.asString ?: obj.get("itemID")?.asString ?: "",
				itemId = obj.get("itemId")?.asString ?: "",
				displayName = obj.get("displayName")?.asString ?: "",
				radius = radius
			)
			"bonzo" -> BonzoNode(
				pos = pos,
				yaw = obj.get("yaw")?.asFloat ?: 0.0f,
				pitch = obj.get("pitch")?.asFloat ?: 0.0f,
				radius = radius
			)
			"crouch" -> CrouchNode(
				pos = pos,
				seconds = obj.get("seconds")?.asDouble ?: obj.get("durationSeconds")?.asDouble ?: 0.0,
				radius = radius
			)
			"command" -> CommandNode(
				pos = pos,
				command = obj.get("command")?.asString ?: "",
				radius = radius
			)
			"jump" -> JumpNode(pos, radius)
			"edge" -> EdgeNode(pos, radius)
			"leap" -> LeapNode(
				pos = pos,
				clazz = readClass(obj.get("class")?.asString),
				radius = radius
			)
			"stop" -> StopNode(
				pos = pos,
				except = readStringSet(obj.get("except")),
				radius = radius
			)
			"record" -> RecordNode(
				pos = pos,
				recordSeconds = obj.get("recordSeconds")?.asDouble ?: 3.0,
				frames = readRecordFrames(obj),
				radius = radius
			)
			"break" -> BreakNode(
				pos = pos,
				zeroTick = obj.get("zeroTick")?.asBoolean ?: true,
				recordSeconds = obj.get("recordSeconds")?.asDouble ?: 3.0,
				blocks = readPosList(obj),
				radius = radius
			)
			else -> throw JsonParseException("Unexpected Auto C node type: $type")
		}
		node.waitSeconds = (obj.get("wait")?.asDouble ?: obj.get("waitSeconds")?.asDouble ?: 0.0).coerceAtLeast(0.0)
		node.awaitSecret = obj.get("awaitSecret")?.asBoolean ?: obj.get("AS")?.asBoolean ?: false
		node.notStart = obj.get("notStart")?.asBoolean ?: obj.get("nr")?.asBoolean ?: false
		node.id = obj.get("id")?.asStringOrNull()?.takeIf { it.isNotBlank() } ?: node.id
		node.maxActivationsPerMinute = (obj.get("maxA")?.asInt ?: obj.get("maxActivationsPerMinute")?.asInt ?: 0)
			.coerceAtLeast(0)
		node.onTerminalExit = obj.get("onTerminalExit")?.asBoolean
			?: obj.get("terminalExit")?.asBoolean
			?: false
		node.requiredNodeIds.clear()
		node.requiredNodeIds.addAll(readRawStringSet(obj.get("requires") ?: obj.get("conditions")))
		return node
	}

	override fun serialize(src: AutoCNode, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
		src.serialize()

	private fun readPos(obj: JsonObject?): Pos {
		if (obj == null) {
			throw JsonParseException("Auto C node is missing a position.")
		}

		return Pos(
			obj.get("x")?.asDouble ?: 0.0,
			obj.get("y")?.asDouble ?: 0.0,
			obj.get("z")?.asDouble ?: 0.0
		)
	}

	private fun readPosList(obj: JsonObject): MutableList<Pos> {
		val array = obj.get("blocks")?.asJsonArray ?: return mutableListOf()
		return array
			.mapNotNull { it.asJsonObjectOrNull() }
			.map { readPos(it) }
			.toMutableList()
	}

	private fun readStringSet(element: JsonElement?): Set<String> {
		if (element == null) {
			return emptySet()
		}
		val values = when {
			element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull() }
			element.isJsonPrimitive -> element.asStringOrNull()?.split(Regex("\\s+|,")) ?: emptyList()
			else -> emptyList()
		}
		return values
			.asSequence()
			.map { it.trim().lowercase(Locale.ROOT) }
			.filter { it.isNotBlank() }
			.toSet()
	}

	private fun readRawStringSet(element: JsonElement?): Set<String> {
		if (element == null) {
			return emptySet()
		}
		val values = when {
			element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull() }
			element.isJsonPrimitive -> element.asStringOrNull()?.split(Regex("\\s+|,")) ?: emptyList()
			else -> emptyList()
		}
		return values
			.asSequence()
			.map { it.trim() }
			.filter { it.isNotBlank() }
			.toSet()
	}

	private fun readRecordFrames(obj: JsonObject): MutableList<RecordFrame> {
		val array = obj.get("frames")?.asJsonArray ?: return mutableListOf()
		var inferredAttackHeld = false
		return array
			.mapNotNull { it.asJsonObjectOrNull() }
			.map { frame ->
				val events = readRecordEvents(frame)
				val hasStartDestroy = events.any { it.isPlayerAction("START_DESTROY_BLOCK") }
				val hasEndDestroy = events.any { it.isPlayerAction("STOP_DESTROY_BLOCK", "ABORT_DESTROY_BLOCK") }
				val inferredAttack = inferredAttackHeld || hasStartDestroy || hasEndDestroy
				if (hasStartDestroy) {
					inferredAttackHeld = true
				}
				if (hasEndDestroy) {
					inferredAttackHeld = false
				}
				RecordFrame(
					yaw = frame.get("yaw")?.asFloat ?: 0.0f,
					pitch = frame.get("pitch")?.asFloat ?: 0.0f,
					slot = frame.get("slot")?.asInt ?: 0,
					forward = frame.get("forward")?.asBoolean ?: false,
					back = frame.get("back")?.asBoolean ?: frame.get("backward")?.asBoolean ?: false,
					left = frame.get("left")?.asBoolean ?: false,
					right = frame.get("right")?.asBoolean ?: false,
					jump = frame.get("jump")?.asBoolean ?: false,
					sneak = frame.get("sneak")?.asBoolean ?: false,
					sprint = frame.get("sprint")?.asBoolean ?: false,
					attack = frame.get("attack")?.asBoolean ?: inferredAttack,
					lookSamples = readRecordLookSamples(frame),
					events = events
				)
			}
			.toMutableList()
	}

	private fun RecordEvent.isPlayerAction(vararg actions: String): Boolean =
		type == RecordEventType.PLAYER_ACTION && action in actions

	private fun readRecordLookSamples(frame: JsonObject): MutableList<RecordLookSample> {
		val array = frame.get("lookSamples")?.asJsonArray ?: return mutableListOf()
		return array
			.mapNotNull { it.asJsonObjectOrNull() }
			.map { sample ->
				RecordLookSample(
					offset = (sample.get("offset")?.asFloat ?: 0.0f).coerceIn(0.0f, 1.0f),
					yaw = sample.get("yaw")?.asFloat ?: 0.0f,
					pitch = sample.get("pitch")?.asFloat ?: 0.0f
				)
			}
			.sortedBy { it.offset }
			.toMutableList()
	}

	private fun readRecordEvents(frame: JsonObject): MutableList<RecordEvent> {
		val array = frame.get("events")?.asJsonArray ?: return mutableListOf()
		return array
			.mapNotNull { it.asJsonObjectOrNull() }
			.map { event ->
				RecordEvent(
					type = RecordEventType.parse(event.get("type")?.asString),
					hand = readEnum(event.get("hand")?.asString, InteractionHand.MAIN_HAND),
					action = event.get("action")?.asString ?: "",
					block = event.get("block")?.asJsonObjectOrNull()?.let(::readPos),
					hit = event.get("hit")?.asJsonObjectOrNull()?.let(::readPos),
					direction = readEnum(event.get("direction")?.asString, Direction.UP),
					inside = event.get("inside")?.asBoolean ?: false,
					worldBorderHit = event.get("worldBorderHit")?.asBoolean ?: false,
					yaw = event.get("yaw")?.asFloat ?: 0.0f,
					pitch = event.get("pitch")?.asFloat ?: 0.0f,
					slot = event.get("slot")?.asInt ?: -1
				)
			}
			.toMutableList()
	}

	private inline fun <reified T : Enum<T>> readEnum(value: String?, default: T): T =
		if (value.isNullOrBlank()) default else runCatching { enumValueOf<T>(value.uppercase(Locale.ROOT)) }.getOrDefault(default)

	private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
		if (isJsonObject) asJsonObject else null

	private fun JsonElement.asStringOrNull(): String? =
		if (isJsonPrimitive && asJsonPrimitive.isString) asString else null

	private fun readClass(value: String?): DungeonClass {
		if (value.isNullOrBlank()) {
			return DungeonClass.NONE
		}
		val parsed = LeapCounter.parseClass(value)
		if (parsed != DungeonClass.NONE) {
			return parsed
		}
		return runCatching { DungeonClass.valueOf(value.uppercase(Locale.ROOT)) }.getOrDefault(DungeonClass.NONE)
	}
}
