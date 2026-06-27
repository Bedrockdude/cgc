package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.data.Keybind
import cgc.cgc.data.Pos
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.SaveSetting
import cgc.cgc.runtime.InputCommand
import cgc.cgc.runtime.InputScheduler
import cgc.cgc.runtime.ItemInteractionUtils
import cgc.cgc.runtime.MovementPlayback
import cgc.cgc.runtime.CgcRenderPrimitives
import cgc.cgc.utils.ChatUtils
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class AutoP3 : CgcModule(
	id = "AutoP3",
	displayName = "Auto P3",
	category = ModuleCategory.DUNGEONS,
	description = "Runs configured P3 route rings from the old Auto P3 ring file.",
	defaultEnabled = false
), ClientTickModule, WorldRenderExtractModule, WorldLoadModule {
	private val forceSkyblock = BooleanSetting("Force Skyblock", false)
	private val feedback = BooleanSetting("Feedback", false)
	private val triggerBind = KeybindSetting("Trigger", Keybind(action = this::trigger))
	private val edgeDist = NumberSetting("Edge Dist", 0.0, 0.1, 0.001, 0.001)
	private val depth = BooleanSetting("Depth", false)
	private val strafe45 = BooleanSetting("45", true)
	private val freecamBlink = BooleanSetting("Freecam Blink", false)
	private val data = SaveSetting(
		name = "Rings",
		path = "dungeon/ap3",
		defaultFile = "rings.json",
		factory = { mutableListOf<AutoP3Ring>() },
		valueType = object : TypeToken<MutableList<AutoP3Ring>>() {}.type,
		allowEdits = true,
		action = this::reload
	)

	private val rings = arrayListOf<AutoP3Ring>()
	private val redoList = arrayListOf<AutoP3Ring>()

	init {
		registerProperty(feedback, triggerBind, edgeDist, freecamBlink, depth, strafe45, forceSkyblock, data)
		reload()
	}

	override fun onEnable() {
		triggerBind.register()
	}

	override fun onDisable() {
		triggerBind.unregister()
		reset()
	}

	override fun onClientTick(client: Minecraft) {
		val player = client.player ?: return
		if (!dungeonCheck()) {
			return
		}

		val playerPos = player.position()
		val oldPos = player.oldPosition()
		for (ring in rings.sortedByDescending { it.priority() }) {
			val inNode = ring.isInNode(playerPos, oldPos)
			if (inNode && !ring.triggered) {
				if (ring.requiresGround() && !player.onGround()) {
					continue
				}
				ring.triggered = true
				if (feedback.value) {
					modMessage("Triggered ${ring.type.lowercase(Locale.ROOT)}")
				}
				if (!execute(ring)) {
					break
				}
			} else if (!inNode && ring.triggered) {
				ring.triggered = false
			}
		}
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		if (!dungeonCheck()) {
			return
		}

		for (ring in rings) {
			renderRing(context, ring)
		}
	}

	override fun onWorldLoad() {
		reload()
	}

	override fun reset() {
		rings.forEach { it.triggered = false }
	}

	fun addRing(ring: AutoP3Ring) {
		ring.triggered = true
		rings.add(ring)
		save()
		modMessage("Added ${ring.type.lowercase(Locale.ROOT)}")
	}

	fun insertRing(ring: AutoP3Ring, index: Int): Boolean {
		if (index !in 0..rings.size) {
			return false
		}
		ring.triggered = true
		rings.add(index, ring)
		save()
		return true
	}

	fun removeIndexed(index: Int): Boolean {
		if (index !in rings.indices) {
			return false
		}
		rings.removeAt(index)
		save()
		return true
	}

	fun removeNearest(pos: Vec3) {
		val index = rings.indices.minByOrNull { rings[it].distanceSq(pos) } ?: return
		val ring = rings.removeAt(index)
		redoList.clear()
		redoList.add(ring)
		save()
		modMessage("Removed ${ring.type.lowercase(Locale.ROOT)}")
	}

	fun undo() {
		if (rings.isEmpty()) {
			modMessage("No Rings!")
			return
		}
		redoList.add(rings.removeLast())
		save()
	}

	fun redo() {
		if (redoList.isEmpty()) {
			modMessage("No Rings!")
			return
		}
		rings.add(redoList.removeLast())
		save()
	}

	fun loadConfig(config: String) {
		data.setFileName(config.removeSuffix(".json"))
		data.load()
		reload()
		modMessage("Loaded ${data.displayValue}")
	}

	fun ringCount(): Int =
		rings.size

	private fun trigger() {
		val player = Minecraft.getInstance().player ?: return
		val pos = player.position()
		val old = player.oldPosition()
		rings.filter { it.isInNode(pos, old) }.forEach { execute(it) }
	}

	private fun execute(ring: AutoP3Ring): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		return when (ring.normalizedType()) {
			"JUMP" -> {
				if (!player.onGround()) {
					ring.triggered = false
					return false
				}
				player.jumpFromGround()
				true
			}
			"STOP" -> {
				scheduleStopInput(player)
				true
			}
			"LOOK", "BONZO", "FAST_BONZO" -> {
				applyLook(ring)
				if (ring.normalizedType() == "BONZO" || ring.normalizedType() == "FAST_BONZO") {
					ItemInteractionUtils.useItemBySkyBlockId("BONZO_STAFF")
				}
				true
			}
			"USE" -> {
				applyLook(ring)
				useItem(ring.item)
			}
			"LEAP" -> FastLeap.doAutoLeap()
			"CHAT" -> {
				val message = ring.message ?: return true
				client.connection?.sendChat(message)
				true
			}
			"COMMAND" -> {
				val command = ring.command?.removePrefix("/") ?: return true
				client.connection?.sendCommand(command)
				true
			}
			"WALK" -> {
				scheduleWalkInput(ring, player)
				true
			}
			"ALIGN", "FAST_ALIGN" -> {
				scheduleAlignInput(ring, player, fast = ring.normalizedType() == "FAST_ALIGN")
				true
			}
			"EDGE" -> {
				scheduleEdgeInput(player)
				true
			}
			"BOOM" -> {
				boom(ring)
			}
			"MOVEMENT", "BLINK" -> {
				if (!MovementPlayback.play(ring.route)) {
					if (feedback.value) {
						modMessage("Could not play route ${ring.route ?: "inputs"}.")
					}
				} else if (ring.normalizedType() == "BLINK" && feedback.value) {
					modMessage("Blink ring is using normal movement playback; packet blink is not enabled.")
				}
				true
			}
			else -> true
		}
	}

	private fun scheduleWalkInput(ring: AutoP3Ring, player: net.minecraft.client.player.LocalPlayer) {
		val yaw = ring.yaw ?: player.yRot
		val airborneStrafe = strafe45.value && !player.onGround()
		InputScheduler.schedule(
			InputCommand(
				ticks = 8,
				yaw = if (airborneStrafe) yaw - 45.0f else yaw,
				forward = true,
				right = airborneStrafe,
				sprint = true
			)
		)
	}

	private fun scheduleAlignInput(ring: AutoP3Ring, player: net.minecraft.client.player.LocalPlayer, fast: Boolean) {
		val center = ring.box().center
		val dx = center.x - player.x
		val dz = center.z - player.z
		if (dx * dx + dz * dz < 1.0E-4) {
			return
		}

		val yaw = (-Math.toDegrees(atan2(dx, dz))).toFloat()
		InputScheduler.schedule(
			InputCommand(
				ticks = if (fast) 3 else 5,
				yaw = yaw,
				forward = true,
				sneak = !fast,
				sprint = fast
			)
		)
	}

	private fun scheduleStopInput(player: net.minecraft.client.player.LocalPlayer) {
		val velocity = player.deltaMovement
		if (velocity.horizontalDistanceSqr() < 1.0E-4) {
			return
		}

		val yaw = Math.toRadians(player.yRot.toDouble())
		val forwardX = -sin(yaw)
		val forwardZ = cos(yaw)
		val rightX = cos(yaw)
		val rightZ = sin(yaw)
		val forwardDot = velocity.x * forwardX + velocity.z * forwardZ
		val rightDot = velocity.x * rightX + velocity.z * rightZ
		InputScheduler.schedule(
			InputCommand(
				ticks = 4,
				forward = forwardDot < -0.01,
				back = forwardDot > 0.01,
				left = rightDot > 0.01,
				right = rightDot < -0.01
			)
		)
	}

	private fun scheduleEdgeInput(player: net.minecraft.client.player.LocalPlayer) {
		if (!player.onGround()) {
			return
		}

		if (!hasGroundUnderPlayer(edgeDist.value.toDouble())) {
			InputScheduler.schedule(InputCommand(ticks = 2, jump = true))
		} else {
			InputScheduler.schedule(InputCommand(ticks = 8, forward = true, sneak = true))
		}
	}

	private fun hasGroundUnderPlayer(shrink: Double): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return true
		val level = client.level ?: return true
		val box = player.boundingBox.move(0.0, -0.5, 0.0).inflate(-shrink.coerceAtLeast(0.0), 0.0, -shrink.coerceAtLeast(0.0))
		return level.getBlockCollisions(player, box).iterator().hasNext()
	}

	private fun boom(ring: AutoP3Ring): Boolean {
		if (Minecraft.getInstance().player == null) return false
		val target = ring.target
		if (target == null) {
			if (feedback.value) {
				modMessage("Boom ring has no target.")
			}
			return true
		}

		if (!ItemInteractionUtils.useBlockTargetWithItem(target.asVec3(), "INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")) {
			if (feedback.value) {
				modMessage("No superboom found or target was not usable.")
			}
		}
		return true
	}

	private fun applyLook(ring: AutoP3Ring) {
		val player = Minecraft.getInstance().player ?: return
		val yaw = ring.yaw ?: return
		val pitch = ring.pitch ?: return
		player.yRot = yaw
		player.xRot = pitch.coerceIn(-90.0f, 90.0f)
	}

	private fun useItem(itemId: String?): Boolean {
		if (itemId != null) {
			return ItemInteractionUtils.useItemBySkyBlockId(itemId)
		}
		return ItemInteractionUtils.useHeldAir()
	}

	private fun dungeonCheck(): Boolean =
		forceSkyblock.value || (Minecraft.getInstance().player != null && Location.area.isArea(Island.DUNGEON) && DungeonState.inBoss)

	private fun reload() {
		rings.clear()
		rings.addAll(data.value)
	}

	private fun save() {
		data.value = rings.toMutableList()
		data.save()
	}

	private fun renderRing(context: LevelRenderContext, ring: AutoP3Ring) {
		val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
		val matrices = context.poseStack()
		val color = ring.colour()
		matrices.pushPose()
		matrices.translate(-camera.x, -camera.y, -camera.z)

		val fill = ring.fillBox()
		CgcRenderPrimitives.filledBox(
			matrices,
			context.bufferSource().getBuffer(RenderTypes.debugFilledBox()),
			fill,
			color.red / 255.0f,
			color.green / 255.0f,
			color.blue / 255.0f,
			50.0f / 255.0f
		)

		CgcRenderPrimitives.lineBox(
			matrices,
			context.bufferSource().getBuffer(RenderTypes.lines()),
			ring.inlineBox(),
			color.red / 255.0f,
			color.green / 255.0f,
			color.blue / 255.0f,
			color.alpha / 255.0f
		)

		matrices.popPose()
	}

	private fun modMessage(message: String) {
		ChatUtils.chatClean(message)
	}


	data class AutoP3Ring(
		var type: String = "STOP",
		var min: Pos = Pos(),
		var max: Pos = Pos(),
		var yaw: Float? = null,
		var pitch: Float? = null,
		var route: String? = null,
		var target: Pos? = null,
		var item: String? = null,
		var message: String? = null,
		var command: String? = null,
		var size: Int? = null,
		var args: JsonObject? = null,
		var sub: JsonObject? = null
	) {
		@Transient
		var triggered: Boolean = false

		fun box(): AABB =
			AABB(min.asVec3(), max.asVec3())

		fun fillBox(): AABB =
			AABB(min.x, min.y, min.z, max.x, min.y + 0.05, max.z)

		fun inlineBox(): AABB {
			val box = box()
			val diffX = (box.maxX - box.minX) * 0.15
			val diffZ = (box.maxZ - box.minZ) * 0.15
			return AABB(box.minX + diffX, box.minY, box.minZ + diffZ, box.maxX - diffX, box.minY + 0.05, box.maxZ - diffZ)
		}

		fun isInNode(curr: Vec3, prev: Vec3): Boolean {
			val box = box()
			val feet = AABB(curr.x - 0.2, curr.y, curr.z - 0.2, curr.x + 0.3, curr.y + 0.5, curr.z)
			return box.intersects(curr, prev) || box.intersects(feet)
		}

		fun normalizedType(): String =
			when (type.uppercase(Locale.ROOT).replace("-", "_")) {
				"FASTBONZO" -> "FAST_BONZO"
				"FASTALIGN" -> "FAST_ALIGN"
				else -> type.uppercase(Locale.ROOT).replace("-", "_")
			}

		fun requiresGround(): Boolean =
			when (normalizedType()) {
				"JUMP", "EDGE" -> true
				else -> false
			}

		fun distanceSq(pos: Vec3): Double {
			val box = box()
			val dx = (box.maxX + box.minX) / 2.0 - pos.x
			val dy = (box.maxY + box.minY) / 2.0 - pos.y
			val dz = (box.maxZ + box.minZ) / 2.0 - pos.z
			return dx * dx + dy * dy + dz * dz
		}

		fun priority(): Int =
			when (normalizedType()) {
				"STOP" -> 110
				"ALIGN", "FAST_ALIGN" -> 100
				"BONZO", "FAST_BONZO" -> 75
				"JUMP", "EDGE", "BOOM" -> 60
				"WALK", "MOVEMENT", "LOOK", "USE", "LEAP" -> 50
				else -> 10
			}

		fun colour(): Colour =
			when (normalizedType()) {
				"ALIGN", "FAST_ALIGN", "LOOK" -> Colour(0, 255, 0, 220)
				"STOP", "BOOM" -> Colour(255, 0, 0, 220)
				"WALK" -> Colour(0, 255, 255, 220)
				"JUMP" -> Colour(255, 165, 0, 220)
				"BONZO" -> Colour(255, 0, 255, 220)
				"FAST_BONZO" -> Colour(255, 105, 180, 220)
				"EDGE" -> Colour(0, 0, 0, 220)
				"MOVEMENT" -> Colour(255, 255, 255, 220)
				"USE" -> Colour(160, 160, 160, 220)
				"LEAP" -> Colour(80, 200, 255, 220)
				"CHAT" -> Colour(255, 255, 0, 220)
				"COMMAND" -> Colour(170, 120, 255, 220)
				"BLINK" -> Colour(120, 220, 255, 220)
				else -> Colour(170, 120, 255, 220)
			}
	}
}
