package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.data.DungeonClass
import cgc.cgc.data.DungeonPlayer
import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.HudRenderModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.DragSetting
import cgc.cgc.module.setting.SaveSetting
import cgc.cgc.runtime.CgcRenderer3D
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.DungeonUtils
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector2d
import java.util.Comparator
import java.util.EnumSet
import java.util.Locale
import kotlin.math.abs

class LeapCounter : CgcModule(
	id = "LeapCounter",
	displayName = "Leap counter",
	category = ModuleCategory.DUNGEONS,
	description = "Counts class leaps into configured dungeon nodes.",
	defaultEnabled = false
), ClientTickModule, HudRenderModule, WorldRenderExtractModule, WorldLoadModule {
	private val hud = DragSetting("Leap Counter", Vector2d(50.0, 80.0), Vector2d(HUD_WIDTH.toDouble(), HUD_HEIGHT.toDouble()))
	private val forceSkyblock = BooleanSetting("Force Skyblock", false)
	private val labelColour = ColourSetting("LC Text Colour", Colour(170, 210, 255, 255))
	private val countColour = ColourSetting("Count Text Colour", Colour(255, 255, 255, 255))
	private val renderNodes = BooleanSetting("Render Nodes", true)
	private val nodeColour = ColourSetting("Node Colour", Colour(85, 170, 255, 180))
	private val data = SaveSetting(
		name = "Nodes",
		path = "dungeon/lc",
		defaultFile = "nodes.json",
		factory = { mutableListOf<LeapNode>() },
		valueType = object : TypeToken<MutableList<LeapNode>>() {}.type,
		allowEdits = true,
		action = this::reload
	)

	private val nodes = arrayListOf<LeapNode>()
	private val snapshots = hashMapOf<String, PlayerSnapshot>()
	private val counted = hashSetOf<String>()
	private var activeNode: LeapNode? = null
	private var activeTicks = 0
	private var tick = 0L

	init {
		registerProperty(hud, forceSkyblock, labelColour, countColour, renderNodes, nodeColour, data)
		reload()
	}

	override fun onClientTick(client: Minecraft) {
		tick++
		val player = client.player
		if (!areaCheck() || player == null || client.level == null) {
			clearRuntime()
			return
		}

		val nextNode = findActiveNode(player)
		if (nextNode !== activeNode) {
			activeNode = nextNode
			activeTicks = 0
			counted.clear()
		}

		if (activeNode == null) {
			activeTicks = 0
			updateSnapshots()
			return
		}

		activeTicks++
		detectLeaps(player, activeNode!!)
		updateSnapshots()
	}

	override fun onHudRender(gfx: GuiGraphicsExtractor) {
		val node = activeNode ?: return
		if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) {
			return
		}

		val x = hud.position.x.toInt()
		val y = hud.position.y.toInt()
		gfx.centeredText(Minecraft.getInstance().font, "LC", x + HUD_WIDTH / 2, y + 5, labelColour.value.argb())
		gfx.centeredText(
			Minecraft.getInstance().font,
			"${counted.size}/${node.classes.size}",
			x + HUD_WIDTH / 2,
			y + 18,
			countColour.value.argb()
		)
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		if (!renderNodes.value || !areaCheck()) {
			return
		}

		val phase = DungeonUtils.getF7Phase()
		synchronized(nodes) {
			for (node in nodes) {
				if (node.phase == phase) {
					CgcRenderer3D.circle(Vec3(node.x, node.y + 0.02, node.z), false, node.radius.toFloat(), nodeColour.value, CIRCLE_SEGMENTS)
				}
			}
		}
	}

	override fun onWorldLoad() {
		clearRuntime()
	}

	override fun reset() {
		clearRuntime()
	}

	fun addNode(phase: Phase7, radius: Double, classes: Set<DungeonClass>) {
		val player = Minecraft.getInstance().player
		if (player == null) {
			ChatUtils.chat("${ChatFormatting.RED}You need to be in-world to add an LC node.")
			return
		}

		val pos = player.position()
		val node = LeapNode(pos.x, pos.y, pos.z, radius, phase, EnumSet.copyOf(classes))
		synchronized(nodes) {
			nodes.add(node)
		}
		save()
		modMessage("Added ${phaseName(phase)} node at %.1f, %.1f, %.1f for %s.", pos.x, pos.y, pos.z, node.classList())
	}

	fun removeIndexed(index: Int): Boolean {
		synchronized(nodes) {
			if (index !in nodes.indices) {
				return false
			}
			nodes.removeAt(index)
		}
		save()
		clearRuntime()
		return true
	}

	fun removeNearest(pos: Vec3): Boolean {
		synchronized(nodes) {
			val index = nodes.indices.minByOrNull { nodes[it].distanceSq(pos) } ?: return false
			nodes.removeAt(index)
		}
		save()
		clearRuntime()
		return true
	}

	fun getNodes(): List<LeapNode> =
		synchronized(nodes) { nodes.toList() }

	private fun detectLeaps(local: LocalPlayer, node: LeapNode) {
		if (activeTicks <= ACTIVE_WARMUP_TICKS) {
			return
		}

		val localPos = local.position()
		for (dungeonPlayer in DungeonState.getPlayers()) {
			if (!isCountCandidate(dungeonPlayer, local, node)) {
				continue
			}

			val player = dungeonPlayer.findPlayer() ?: continue
			val current = player.position()
			if (!isInsideLocalPlayer(current, localPos)) {
				continue
			}

			val key = dungeonPlayer.name.lowercase(Locale.ROOT)
			val previous = snapshots[key]
			val teleported = previous == null || previous.pos.distanceToSqr(current) >= TELEPORT_DISTANCE_SQ
			val wasInside = previous != null && isInsideLocalPlayer(previous.pos, localPos)
			if (teleported && !wasInside) {
				counted.add(dungeonPlayer.name)
			}
		}
	}

	private fun isCountCandidate(dungeonPlayer: DungeonPlayer, local: LocalPlayer, node: LeapNode): Boolean =
		node.classes.contains(dungeonPlayer.dungeonClass)
			&& !counted.contains(dungeonPlayer.name)
			&& !dungeonPlayer.name.equals(local.name.string, ignoreCase = true)

	private fun updateSnapshots() {
		val present = hashSetOf<String>()
		for (dungeonPlayer in DungeonState.getPlayers()) {
			val player = dungeonPlayer.findPlayer()
			val key = dungeonPlayer.name.lowercase(Locale.ROOT)
			present.add(key)
			if (player != null) {
				snapshots[key] = PlayerSnapshot(player.position(), tick)
			}
		}

		snapshots.entries.removeIf { !present.contains(it.key) && tick - it.value.seenTick > 100L }
	}

	private fun findActiveNode(player: LocalPlayer): LeapNode? {
		val phase = DungeonUtils.getF7Phase()
		val pos = player.position()
		synchronized(nodes) {
			return nodes
				.asSequence()
				.filter { it.phase == phase && it.contains(pos) }
				.minWithOrNull(Comparator.comparingDouble { it.distanceSq(pos) })
		}
	}

	private fun areaCheck(): Boolean =
		forceSkyblock.value
			|| (Location.area.isArea(Island.DUNGEON)
				&& (Location.floor == Floor.F7 || Location.floor == Floor.M7)
				&& DungeonState.inBoss)

	private fun isInsideLocalPlayer(playerPos: Vec3, localPos: Vec3): Boolean {
		val dx = playerPos.x - localPos.x
		val dz = playerPos.z - localPos.z
		return dx * dx + dz * dz <= INSIDE_PLAYER_HORIZONTAL_SQ && abs(playerPos.y - localPos.y) <= INSIDE_PLAYER_Y
	}

	private fun reload() {
		synchronized(nodes) {
			nodes.clear()
			nodes.addAll(data.value.filter { it.phase != Phase7.UNKNOWN && it.classes.isNotEmpty() })
		}
	}

	private fun save() {
		data.value = synchronized(nodes) { nodes.toMutableList() }
		data.save()
	}

	private fun clearRuntime() {
		activeNode = null
		activeTicks = 0
		counted.clear()
		snapshots.clear()
	}

	private fun modMessage(message: String, vararg args: Any?) {
		ChatUtils.chat("${ChatFormatting.AQUA}LC » ${ChatFormatting.RESET}${message.format(*args)}")
	}

	data class LeapNode(
		var x: Double = 0.0,
		var y: Double = 0.0,
		var z: Double = 0.0,
		var radius: Double = 1.0,
		var phase: Phase7 = Phase7.UNKNOWN,
		var classes: MutableSet<DungeonClass> = EnumSet.noneOf(DungeonClass::class.java)
	) {
		fun contains(pos: Vec3): Boolean {
			val dx = pos.x - x
			val dz = pos.z - z
			return dx * dx + dz * dz <= radius * radius && abs(pos.y - y) <= 4.0
		}

		fun distanceSq(pos: Vec3): Double {
			val dx = pos.x - x
			val dy = pos.y - y
			val dz = pos.z - z
			return dx * dx + dy * dy + dz * dz
		}

		fun classList(): String =
			classes.joinToString(", ") { it.displayName }
	}

	private data class PlayerSnapshot(val pos: Vec3, val seenTick: Long)

	companion object {
		private const val INSIDE_PLAYER_HORIZONTAL_SQ = 0.9 * 0.9
		private const val INSIDE_PLAYER_Y = 1.6
		private const val TELEPORT_DISTANCE_SQ = 6.0 * 6.0
		private const val ACTIVE_WARMUP_TICKS = 4
		private const val HUD_WIDTH = 96
		private const val HUD_HEIGHT = 34
		private const val CIRCLE_SEGMENTS = 64

		@JvmStatic
		fun parsePhase(value: String): Phase7 =
			when (value.lowercase(Locale.ROOT)) {
				"p1" -> Phase7.P1
				"p2" -> Phase7.P2
				"p3" -> Phase7.P3
				"p4" -> Phase7.P4
				"5p", "p5" -> Phase7.P5
				else -> Phase7.UNKNOWN
			}

		@JvmStatic
		fun parseClasses(raw: String): Set<DungeonClass> {
			val classes = EnumSet.noneOf(DungeonClass::class.java)
			for (token in raw.split(Regex("\\s+"))) {
				val trimmed = token.trim()
				if (trimmed.length > 1 && trimmed.all { it.isLetter() }) {
					for (char in trimmed) {
						val clazz = parseClass(char.toString())
						if (clazz != DungeonClass.NONE) {
							classes.add(clazz)
						}
					}
				} else {
					val clazz = parseClass(trimmed)
					if (clazz != DungeonClass.NONE) {
						classes.add(clazz)
					}
				}
			}
			return classes
		}

		@JvmStatic
		fun parseClass(token: String): DungeonClass =
			when (token.lowercase(Locale.ROOT)) {
				"a", "archer" -> DungeonClass.ARCHER
				"m", "mage" -> DungeonClass.MAGE
				"b", "bers", "berserk", "berserker" -> DungeonClass.BERSERKER
				"t", "tank" -> DungeonClass.TANK
				"h", "heal", "healer" -> DungeonClass.HEALER
				else -> DungeonClass.NONE
			}

		@JvmStatic
		fun phaseName(phase: Phase7): String =
			if (phase == Phase7.P5) "5p" else phase.name.lowercase(Locale.ROOT)
	}
}
