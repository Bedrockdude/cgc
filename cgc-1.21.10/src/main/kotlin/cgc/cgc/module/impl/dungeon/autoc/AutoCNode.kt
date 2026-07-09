package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.runtime.CgcRenderer3D
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class AutoCNode(
	val pos: Pos = Pos(),
	var radius: Float = DEFAULT_RADIUS
) {
	var waitSeconds: Double = 0.0
	var awaitSecret: Boolean = false
	var notStart: Boolean = false
	var id: String = UUID.randomUUID().toString()
	var maxActivationsPerMinute: Int = 0
	var onTerminalExit: Boolean = false
	val requiredNodeIds: MutableSet<String> = linkedSetOf()

	@Transient
	private var triggered = false

	@Transient
	private var lastTickTime = -1

	open fun calculate() {
	}

	abstract fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean

	open fun prepareAwaitSecret(context: AutoCNodeContext) {
	}

	open fun handlesAwaitSecretInRun(): Boolean =
		false

	open fun render(depth: Boolean) {
		CgcRenderer3D.ring(pos.asVec3().add(0.0, RING_Y_OFFSET, 0.0), depth, radius, colour())
	}

	open fun priority(): Int =
		8

	open fun routeDelayTicks(): Int =
		0

	fun isInNode(playerPos: Pos): Boolean {
		val dx = playerPos.x - pos.x
		val dz = playerPos.z - pos.z
		val activationRadius = activationRadius()
		return dx * dx + dz * dz <= activationRadius * activationRadius
			&& abs(playerPos.y - pos.y) <= ACTIVATION_VERTICAL_TOLERANCE
	}

	fun canTrigger(playerPos: Pos, previousPlayerPos: Pos?): Boolean {
		val currentInNode = isInNode(playerPos)
		if (currentInNode && !triggered) {
			return true
		}
		if (previousPlayerPos == null) {
			return currentInNode
		}
		if (isInNode(previousPlayerPos)) {
			return false
		}
		return currentInNode || segmentIntersectsNode(previousPlayerPos.asVec3(), playerPos.asVec3())
	}

	fun hasRanThisTick(tickTime: Int): Boolean =
		tickTime <= lastTickTime

	fun preTrigger(tickTime: Int) {
		lastTickTime = tickTime
		triggered = true
	}

	fun updateNodeState(playerPos: Pos, tickTime: Int): Boolean {
		if (tickTime <= lastTickTime) {
			return false
		}

		val inNode = isInNode(playerPos)
		if (inNode && !triggered) {
			return true
		}

		if (!inNode && triggered) {
			reset()
		}

		return false
	}

	protected fun cancel(): Boolean {
		reset()
		return false
	}

	abstract fun name(): String

	abstract fun colour(): Colour

	open fun serialize(): JsonObject {
		val json = JsonObject()
		json.addProperty("type", name())
		json.addProperty("id", id)
		json.add("pos", writePos(pos))
		json.addProperty("radius", radius)
		if (waitSeconds > 0.0) {
			json.addProperty("wait", waitSeconds)
		}
		if (awaitSecret) {
			json.addProperty("awaitSecret", true)
		}
		if (notStart) {
			json.addProperty("notStart", true)
		}
		if (maxActivationsPerMinute > 0) {
			json.addProperty("maxA", maxActivationsPerMinute)
		}
		if (onTerminalExit) {
			json.addProperty("onTerminalExit", true)
		}
		if (requiredNodeIds.isNotEmpty()) {
			val conditions = JsonArray()
			requiredNodeIds.forEach(conditions::add)
			json.add("requires", conditions)
		}
		return json
	}

	fun reset() {
		triggered = false
	}

	fun isTriggered(): Boolean =
		triggered

	private fun writePos(pos: Pos): JsonObject {
		val obj = JsonObject()
		obj.addProperty("x", pos.x)
		obj.addProperty("y", pos.y)
		obj.addProperty("z", pos.z)
		return obj
	}

	private fun segmentIntersectsNode(start: Vec3, end: Vec3): Boolean {
		val center = pos.asVec3()
		val dx = end.x - start.x
		val dy = end.y - start.y
		val dz = end.z - start.z
		val horizontalLengthSq = dx * dx + dz * dz
		if (horizontalLengthSq <= 1.0E-8) {
			return isInNode(Pos(end))
		}

		val verticalRange = verticalTInterval(start.y, end.y, center.y) ?: return false
		val closestT = (((center.x - start.x) * dx + (center.z - start.z) * dz) / horizontalLengthSq)
			.coerceIn(verticalRange.first, verticalRange.second)
		val closestX = start.x + dx * closestT
		val closestZ = start.z + dz * closestT
		val cx = closestX - center.x
		val cz = closestZ - center.z
		val activationRadius = activationRadius()
		return cx * cx + cz * cz <= activationRadius * activationRadius
	}

	private fun verticalTInterval(startY: Double, endY: Double, centerY: Double): Pair<Double, Double>? {
		val minY = centerY - ACTIVATION_VERTICAL_TOLERANCE
		val maxY = centerY + ACTIVATION_VERTICAL_TOLERANCE
		val dy = endY - startY
		if (abs(dy) <= 1.0E-8) {
			return if (startY in minY..maxY) 0.0 to 1.0 else null
		}

		val t1 = (minY - startY) / dy
		val t2 = (maxY - startY) / dy
		val startT = max(0.0, min(t1, t2))
		val endT = min(1.0, max(t1, t2))
		return if (startT <= endT) startT to endT else null
	}

	private fun activationRadius(): Double =
		radius + PLAYER_HORIZONTAL_RADIUS

	companion object {
		const val DEFAULT_RADIUS = 0.5f
		private const val PLAYER_HORIZONTAL_RADIUS = 0.3
		private const val ACTIVATION_VERTICAL_TOLERANCE = 1.25
		private const val RING_Y_OFFSET = 0.1
	}
}
