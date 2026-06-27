package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.DungeonClass
import cgc.cgc.data.DungeonPlayer
import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.ChatMessageModule
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.NumberSetting
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
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern
import kotlin.math.abs

class AutoLeap : CgcModule(
	id = "AutoLeap",
	displayName = "Auto leap",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically uses Spirit Leap for selected F7/M7 boss triggers.",
	defaultEnabled = false
), ClientTickModule, ChatMessageModule, PacketReceiveModule, WorldLoadModule {
	private val leapSlot = NumberSetting("Leap Slot", 1.0, 9.0, 8.0, 1.0)
	private val mageCp = ModeSetting("Mage CP", "Healer", CLASS_MODES)
	private val ss = ModeSetting("SS", "Archer", CLASS_MODES)

	private val leapMenu = SpiritLeapMenu("Auto Leap »") { target ->
		modMessage("Leaping to $target.")
	}

	private var pendingLeap: PendingLeap? = null
	private var pendingUseTarget: String? = null
	private var pendingUseTick = 0L
	private var clientTicks = 0L
	private var lastTriggerAt = 0L
	private var ssCompleteWindowUntil = 0L
	private var previousMagePos: Vec3? = null
	private var previousMageInside = false

	init {
		instance = this
		registerProperty(leapSlot, mageCp, ss)
	}

	override fun onClientTick(client: Minecraft) {
		clientTicks++
		if (!areaCheck() || client.player == null || client.level == null) {
			resetRuntime()
			return
		}

		detectMageCp(client)
		runPending(client)
		runPendingUse(client)
		leapMenu.tickTimeout(MENU_TIMEOUT_MS)
	}

	override fun onChatMessage(message: String) {
		if (!areaCheck() || !isAwaitingSsSectionComplete()) {
			return
		}

		val text = ChatFormatting.stripFormatting(message)?.trim() ?: message.trim()
		val matcher = TERMINAL_PATTERN.matcher(text)
		if (!matcher.find() || matcher.group(2) != "device") {
			return
		}

		if (!isOwnCompletionMessage(matcher.group(1))) {
			return
		}

		ssCompleteWindowUntil = 0L
		val completed = matcher.group(3).toInt()
		val total = matcher.group(4).toInt()
		if (completed == total && DungeonUtils.getP3Section() == Phase7.S1) {
			queueLeap(FastLeap.classFromMode(ss.value), "SS")
		}
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

	override fun onWorldLoad() {
		resetRuntime()
	}

	override fun reset() {
		resetRuntime()
	}

	private fun markSimonSaysComplete() {
		if (!areaCheck() || DungeonUtils.getP3Section() != Phase7.S1) {
			return
		}

		ssCompleteWindowUntil = System.currentTimeMillis() + SS_SECTION_COMPLETE_WINDOW_MS
	}

	private fun detectMageCp(client: Minecraft) {
		if (!DungeonUtils.isPhase(Phase7.P2)) {
			previousMagePos = null
			previousMageInside = false
			return
		}

		val local = client.player ?: return
		val mage = DungeonState.getClassPlayer(DungeonClass.MAGE)
		if (mage == null || mage.name.equals(local.name.string, ignoreCase = true)) {
			previousMagePos = null
			previousMageInside = false
			return
		}

		val magePlayer = mage.findPlayer()
		if (magePlayer == null) {
			previousMagePos = null
			previousMageInside = false
			return
		}

		val magePos = magePlayer.position()
		val localPos = local.position()
		val inside = isInsideLocalPlayer(magePos, localPos)
		val teleported = previousMagePos != null && previousMagePos!!.distanceToSqr(magePos) >= TELEPORT_DISTANCE_SQ
		if (inside && teleported && !previousMageInside) {
			queueLeap(FastLeap.classFromMode(mageCp.value), "Mage CP")
		}

		previousMagePos = magePos
		previousMageInside = inside
	}

	private fun queueLeap(targetClass: DungeonClass, reason: String) {
		val now = System.currentTimeMillis()
		if (targetClass == DungeonClass.NONE || now - lastTriggerAt < TRIGGER_COOLDOWN_MS) {
			return
		}

		val delay = ThreadLocalRandom.current().nextLong(MIN_START_DELAY_MS, MAX_START_DELAY_MS + 1L)
		pendingLeap = PendingLeap(targetClass, now + delay, reason)
		lastTriggerAt = now
	}

	private fun runPending(client: Minecraft) {
		val leap = pendingLeap ?: return
		if (System.currentTimeMillis() < leap.runAt) {
			return
		}

		pendingLeap = null
		val target = DungeonState.getClassPlayer(leap.targetClass)
		if (target == null || target.name.equals(client.player?.name?.string, ignoreCase = true)) {
			modMessage("${ChatFormatting.RED}Couldn't find ${leap.targetClass.displayName} for ${leap.reason}.")
			return
		}

		startLeap(client, target.name)
	}

	private fun startLeap(client: Minecraft, targetName: String) {
		val player = client.player ?: return
		if (client.gameMode == null || client.level == null || TerminalContext.inTerminal) {
			return
		}

		val slot = Mth.clamp(leapSlot.value.toInt(), 1, 9) - 1
		val previousSlot = player.inventory.selectedSlot
		if (previousSlot != slot) {
			player.inventory.selectedSlot = slot
		}

		if (!isSpiritLeapHeld(player)) {
			modMessage("${ChatFormatting.RED}Leap slot does not contain a spirit leap.")
			return
		}

		if (previousSlot != slot) {
			pendingUseTarget = targetName
			pendingUseTick = clientTicks + ThreadLocalRandom.current().nextInt(MIN_SLOT_SWITCH_SETTLE_TICKS, MAX_SLOT_SWITCH_SETTLE_TICKS + 1)
			return
		}

		useSelectedLeap(targetName)
	}

	private fun runPendingUse(client: Minecraft) {
		val target = pendingUseTarget ?: return
		if (clientTicks < pendingUseTick) {
			return
		}

		pendingUseTarget = null
		pendingUseTick = 0L
		if (client.player != null && isSpiritLeapHeld(client.player!!)) {
			useSelectedLeap(target)
		}
	}

	private fun useSelectedLeap(targetName: String) {
		leapMenu.start(targetName)
	}

	private fun isSpiritLeapHeld(player: Player): Boolean {
		val itemId = ItemUtils.skyBlockId(player.inventory.selectedItem)
		return itemId == "SPIRIT_LEAP" || itemId == "INFINITE_SPIRIT_LEAP"
	}

	private fun areaCheck(): Boolean =
		Location.area.isArea(Island.DUNGEON)
			&& (Location.floor == Floor.F7 || Location.floor == Floor.M7)
			&& DungeonState.inBoss

	private fun isInsideLocalPlayer(playerPos: Vec3, localPos: Vec3): Boolean {
		val dx = playerPos.x - localPos.x
		val dz = playerPos.z - localPos.z
		return dx * dx + dz * dz <= INSIDE_PLAYER_HORIZONTAL_SQ && abs(playerPos.y - localPos.y) <= INSIDE_PLAYER_Y
	}

	private fun isAwaitingSsSectionComplete(): Boolean {
		if (ssCompleteWindowUntil == 0L) {
			return false
		}

		if (System.currentTimeMillis() > ssCompleteWindowUntil) {
			ssCompleteWindowUntil = 0L
			return false
		}

		return true
	}

	private fun isOwnCompletionMessage(actor: String): Boolean {
		val name = Minecraft.getInstance().player?.name?.string ?: return false
		return actor.equals(name, ignoreCase = true)
			|| actor.lowercase(Locale.ROOT).endsWith(" ${name.lowercase(Locale.ROOT)}")
	}

	private fun resetRuntime() {
		pendingLeap = null
		pendingUseTarget = null
		pendingUseTick = 0L
		ssCompleteWindowUntil = 0L
		previousMagePos = null
		previousMageInside = false
		leapMenu.clear()
	}

	private fun modMessage(message: String) {
		ChatUtils.chat("${ChatFormatting.AQUA}Auto Leap » ${ChatFormatting.RESET}$message")
	}

	private data class PendingLeap(val targetClass: DungeonClass, val runAt: Long, val reason: String)

	companion object {
		private val CLASS_MODES = listOf("Archer", "Mage", "Berserk", "Healer", "Tank")
		private const val MIN_START_DELAY_MS = 130L
		private const val MAX_START_DELAY_MS = 170L
		private const val TRIGGER_COOLDOWN_MS = 2000L
		private const val MENU_TIMEOUT_MS = 1500L
		private const val SS_SECTION_COMPLETE_WINDOW_MS = 1000L
		private const val MIN_SLOT_SWITCH_SETTLE_TICKS = 2
		private const val MAX_SLOT_SWITCH_SETTLE_TICKS = 3
		private const val INSIDE_PLAYER_HORIZONTAL_SQ = 0.9 * 0.9
		private const val INSIDE_PLAYER_Y = 1.6
		private const val TELEPORT_DISTANCE_SQ = 6.0 * 6.0
		private val TERMINAL_PATTERN = Pattern.compile("^(.*?) (?:activated|completed) a (terminal|device|lever)! \\((\\d+)/(\\d+)\\)")

		private var instance: AutoLeap? = null

		@JvmStatic
		fun onSimonSaysComplete() {
			instance?.takeIf { it.enabled }?.markSimonSaysComplete()
		}
	}
}
