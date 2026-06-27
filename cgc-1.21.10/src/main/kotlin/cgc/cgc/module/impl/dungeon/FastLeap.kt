package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.DungeonClass
import cgc.cgc.data.DungeonPlayer
import cgc.cgc.data.Keybind
import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.StringSetting
import cgc.cgc.terminal.TerminalContext
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.DungeonUtils
import cgc.cgc.utils.ItemUtils
import cgc.cgc.utils.SpiritLeapMenu
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import java.util.Locale

class FastLeap : CgcModule(
	id = "FastLeap",
	displayName = "Fast leap",
	category = ModuleCategory.DUNGEONS,
	description = "Quickly leaps to the configured class or player for the current dungeon phase.",
	defaultEnabled = false
), PacketReceiveModule, WorldLoadModule {
	private val key = KeybindSetting("Key", Keybind(action = this::doAutoLeap))
	private val cooldown = NumberSetting("Cooldown", 0.0, 5000.0, 2000.0, 50.0, " ms")
	private val chatMessage = BooleanSetting("Chat Message", false)
	private val p3Only = BooleanSetting("P3 Only", true)
	private val s1 = ModeSetting("S1", "Archer", CLASS_MODES)
	private val s1Custom = StringSetting("S1 Custom", "", maxLength = 64, supplier = { s1.isMode("Custom") })
	private val s2 = ModeSetting("S2", "Healer", CLASS_MODES)
	private val s2Custom = StringSetting("S2 Custom", "", maxLength = 64, supplier = { s2.isMode("Custom") })
	private val s3 = ModeSetting("S3", "Mage", CLASS_MODES)
	private val s3Custom = StringSetting("S3 Custom", "", maxLength = 64, supplier = { s3.isMode("Custom") })
	private val s4 = ModeSetting("S4", "Mage", CLASS_MODES)
	private val s4Custom = StringSetting("S4 Custom", "", maxLength = 64, supplier = { s4.isMode("Custom") })
	private val p1 = ModeSetting("P1", "Berserk", CLASS_MODES)
	private val p1Custom = StringSetting("P1 Custom", "", maxLength = 64, supplier = { p1.isMode("Custom") })
	private val p2 = ModeSetting("P2", "Auto", CLASS_MODES_WITH_AUTO)
	private val p2Custom = StringSetting("P2 Custom", "", maxLength = 64, supplier = { p2.isMode("Custom") })
	private val p4 = ModeSetting("P4", "Berserk", CLASS_MODES)
	private val p4Custom = StringSetting("P4 Custom", "", maxLength = 64, supplier = { p4.isMode("Custom") })
	private val p5Orange = ModeSetting("P5 Orange", "Berserk", CLASS_MODES)
	private val p5OrangeCustom = StringSetting("Orange Custom", "", maxLength = 64, supplier = { p5Orange.isMode("Custom") })
	private val p5Red = ModeSetting("P5 Red", "Archer", CLASS_MODES)
	private val p5RedCustom = StringSetting("Red Custom", "", maxLength = 64, supplier = { p5Red.isMode("Custom") })

	private val leapMenu = SpiritLeapMenu("Fast Leap »") { target ->
		if (chatMessage.value) {
			Minecraft.getInstance().connection?.sendCommand("pc Leaping to $target")
		} else {
			modMessage("Leaping to $target")
		}
	}

	private var lastUsed = 0L
	private var queuedLeap = false

	init {
		instance = this
		registerProperty(
			key,
			cooldown,
			chatMessage,
			p3Only,
			s1,
			s1Custom,
			s2,
			s2Custom,
			s3,
			s3Custom,
			s4,
			s4Custom,
			p1,
			p1Custom,
			p2,
			p2Custom,
			p4,
			p4Custom,
			p5Orange,
			p5OrangeCustom,
			p5Red,
			p5RedCustom
		)
	}

	override fun onEnable() {
		key.register()
	}

	override fun onDisable() {
		key.unregister()
		reset()
	}

	override fun onWorldLoad() {
		reset()
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		if (packet is ClientboundOpenScreenPacket && leapMenu.handleOpenScreen(packet)) {
			return true
		}

		if (packet is ClientboundContainerSetSlotPacket && leapMenu.handleSetSlot(packet)) {
			return true
		}

		return false
	}

	override fun reset() {
		leapMenu.clear()
		queuedLeap = false
	}

	private fun doAutoLeap() {
		doAutoLeapInternal()
	}

	private fun doAutoLeapInternal(): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		if ((p3Only.value && !DungeonUtils.isPhase(Phase7.P3))
			|| !Location.area.isArea(Island.DUNGEON)
			|| client.level == null
			|| !isSpiritLeapHeld()
			|| System.currentTimeMillis() - lastUsed < cooldown.value.toLong()
			|| leapMenu.isActive
			|| (!TerminalContext.inTerminal && client.screen != null)
			|| !DungeonState.inBoss
		) {
			return false
		}

		if (TerminalContext.inTerminal) {
			queuedLeap = true
			TerminalContext.runOnClose { doAutoLeapInternal() }
			modMessage("Queued leap")
			return true
		}

		val target = getLeapTarget()
		if (target == null || target.equals("NONE", ignoreCase = true) || target.equals(player.name.string, ignoreCase = true)) {
			modMessage("${ChatFormatting.RED}Couldn't find who to leap to! ($target)")
			return false
		}

		return startLeap(target)
	}

	private fun startLeap(name: String): Boolean {
		lastUsed = System.currentTimeMillis()
		queuedLeap = false
		return leapMenu.start(name)
	}

	private fun isSpiritLeapHeld(): Boolean {
		val id = ItemUtils.skyBlockId(Minecraft.getInstance().player?.inventory?.selectedItem ?: return false)
		return id == "SPIRIT_LEAP" || id == "INFINITE_SPIRIT_LEAP"
	}

	private fun getLeapTarget(): String? =
		getClassPlayer()?.name

	private fun getClassPlayer(): DungeonPlayer? {
		val stage = getStageClass()
		return when (stage) {
			is DungeonPlayer -> stage
			is String -> DungeonState.getPlayer(stage)
			is Int -> DungeonState.getClassPlayer(stage)
			else -> null
		}
	}

	private fun getStageClass(): Any? {
		if (!(Location.floor == Floor.F7 || Location.floor == Floor.M7) || !DungeonState.inBoss) {
			return null
		}

		val me = DungeonState.getMyPlayer()
		return when (DungeonUtils.getF7Phase()) {
			Phase7.P1 -> selectedTarget(p1, p1Custom)
			Phase7.P2 -> {
				if (p2.isMode("Auto")) {
					if (me == null) return null
					val healer = DungeonState.getClassPlayer(DungeonClass.HEALER)
					val mage = DungeonState.getClassPlayer(DungeonClass.MAGE)
					val bers = DungeonState.getClassPlayer(DungeonClass.BERSERKER)
					when {
						me.dungeonClass == DungeonClass.TANK && mage != null && Location.floor == Floor.F7 -> mage
						healer != null && me != healer -> healer
						bers != null && me == healer -> bers
						else -> null
					}
				} else {
					selectedTarget(p2, p2Custom)
				}
			}
			Phase7.P3 -> when (DungeonUtils.getP3Section()) {
				Phase7.S1 -> selectedTarget(s1, s1Custom)
				Phase7.S2 -> selectedTarget(s2, s2Custom)
				Phase7.S3 -> selectedTarget(s3, s3Custom)
				Phase7.S4 -> selectedTarget(s4, s4Custom)
				else -> null
			}
			Phase7.P4 -> selectedTarget(p4, p4Custom)
			Phase7.P5 -> {
				if (me == null) {
					null
				} else if (me.dungeonClass == DungeonClass.HEALER || me.dungeonClass == DungeonClass.MAGE) {
					selectedTarget(p5Orange, p5OrangeCustom)
				} else if (me.dungeonClass == DungeonClass.TANK) {
					selectedTarget(p5Red, p5RedCustom)
				} else {
					null
				}
			}
			else -> null
		}
	}

	private fun selectedTarget(mode: ModeSetting, custom: StringSetting): Any =
		if (mode.isMode("Custom")) custom.value else mode.index

	private fun modMessage(message: String) {
		ChatUtils.chat("${ChatFormatting.BLUE}Fast Leap » ${ChatFormatting.RESET}$message")
	}

	companion object {
		private val CLASS_MODES = listOf("Archer", "Mage", "Berserk", "Healer", "Tank", "Custom")
		private val CLASS_MODES_WITH_AUTO = listOf("Archer", "Mage", "Berserk", "Healer", "Tank", "Custom", "Auto")
		private var instance: FastLeap? = null

		@JvmStatic
		fun doAutoLeap(): Boolean =
			instance?.takeIf { it.enabled }?.doAutoLeapInternal() ?: false

		@JvmStatic
		fun doLeap(player: DungeonPlayer): Boolean =
			doLeap(player.name)

		@JvmStatic
		fun doLeap(name: String): Boolean =
			instance?.takeIf { it.enabled }?.startLeap(name) ?: false

		@JvmStatic
		fun classFromMode(value: String): DungeonClass =
			when (value.lowercase(Locale.ROOT)) {
				"archer" -> DungeonClass.ARCHER
				"mage" -> DungeonClass.MAGE
				"berserk", "berserker" -> DungeonClass.BERSERKER
				"healer" -> DungeonClass.HEALER
				"tank" -> DungeonClass.TANK
				else -> DungeonClass.NONE
			}
	}
}
