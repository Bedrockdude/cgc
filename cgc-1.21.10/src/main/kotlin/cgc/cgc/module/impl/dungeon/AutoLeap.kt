package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.DungeonClass
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
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.runtime.ItemInteractionUtils
import cgc.cgc.terminal.TerminalContext
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.DungeonUtils
import cgc.cgc.utils.ItemUtils
import cgc.cgc.utils.SpiritLeapMenu
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom

class AutoLeap : CgcModule(
	id = "AutoLeap",
	displayName = "Auto leap",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically uses Spirit Leap for your selected F7/M7 class route.",
	defaultEnabled = false
), ClientTickModule, ChatMessageModule, PacketReceiveModule, WorldLoadModule {
	private val dungeonClass = ModeSetting("Class", "Healer", CLASS_MODES)
	private val mageCpEnabled = BooleanSetting("Mage CP Enabled", true, supplier = { isHealerSelected() })
	private val mageCpTarget = ModeSetting(
		"Mage CP Target",
		"Archer",
		CLASS_MODES,
		supplier = { isHealerSelected() && mageCpEnabled.value }
	)
	private val s1LeapEnabled = BooleanSetting("S1 Leap Enabled", true, supplier = { isHealerSelected() })
	private val s1LeapTarget = ModeSetting(
		"S1 Leap Target",
		"Archer",
		CLASS_MODES,
		supplier = { isHealerSelected() && s1LeapEnabled.value }
	)
	private val s2LeapEnabled = BooleanSetting("S2 Leap Enabled", true, supplier = { isHealerSelected() })
	private val s2LeapTarget = ModeSetting(
		"S2 Leap Target",
		"Archer",
		CLASS_MODES,
		supplier = { isHealerSelected() && s2LeapEnabled.value }
	)
	private val s3LeapEnabled = BooleanSetting("S3 Leap Enabled", true, supplier = { isHealerSelected() })
	private val s3LeapTarget = ModeSetting(
		"S3 Leap Target",
		"Mage",
		CLASS_MODES,
		supplier = { isHealerSelected() && s3LeapEnabled.value }
	)
	private val s4LeapEnabled = BooleanSetting("S4 Leap Enabled", true, supplier = { isHealerSelected() })
	private val s4LeapTarget = ModeSetting(
		"S4 Leap Target",
		"Mage",
		CLASS_MODES,
		supplier = { isHealerSelected() && s4LeapEnabled.value }
	)

	private val leapMenu = SpiritLeapMenu("Auto Leap »") { target ->
		modMessage("Leaping to $target.")
	}
	private val p3Progress = PhaseTrackerState()

	private var pendingLeap: PendingLeap? = null
	private var pendingUseTarget: String? = null
	private var pendingUseDestination: Phase7? = null
	private var pendingUseTick = 0L
	private var clientTicks = 0L
	private var lastTriggerAt = 0L
	private var mageLastFarTick = NO_MAGE_FAR_TICK
	private var mageWasInside = false

	init {
		registerProperty(
			dungeonClass,
			mageCpEnabled,
			mageCpTarget,
			s1LeapEnabled,
			s1LeapTarget,
			s2LeapEnabled,
			s2LeapTarget,
			s3LeapEnabled,
			s3LeapTarget,
			s4LeapEnabled,
			s4LeapTarget
		)
	}

	override fun onClientTick(client: Minecraft) {
		clientTicks++
		if (!areaCheck() || client.player == null || client.level == null) {
			resetRuntime()
			return
		}

		tickP3Progress()
		detectHealerMageCp(client)
		runPending(client)
		runPendingUse(client)
		leapMenu.tickTimeout(MENU_TIMEOUT_MS)
	}

	override fun onChatMessage(message: String) {
		handleP3ProgressMessage(message)
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		if (packet is ClientboundOpenScreenPacket && leapMenu.handleOpenScreen(packet)) {
			return true
		}

		if (packet is ClientboundContainerSetSlotPacket && leapMenu.handleSetSlot(packet)) {
			return true
		}

		observeP3Packet(packet)
		return false
	}

	override fun onWorldLoad() {
		resetRuntime()
	}

	override fun reset() {
		resetRuntime()
	}

	private fun tickP3Progress() {
		val now = System.currentTimeMillis()
		val before = p3Progress.snapshot()
		synchronizeP3Progress()
		p3Progress.tick(now, P3_DONE_HOLD_MS)
		queueCompletedP3Section(before, p3Progress.snapshot())
	}

	private fun handleP3ProgressMessage(message: String) {
		val before = p3Progress.snapshot()
		if (!p3Progress.handleMessage(message, System.currentTimeMillis())) {
			return
		}
		queueCompletedP3Section(before, p3Progress.snapshot())
	}

	private fun observeP3Packet(packet: Packet<*>) {
		when (packet) {
			is ClientboundBundlePacket -> packet.subPackets().forEach(::observeP3Packet)
			is ClientboundSetSubtitleTextPacket -> Minecraft.getInstance().execute {
				handleP3ProgressMessage(packet.text.string)
			}
		}
	}

	private fun synchronizeP3Progress() {
		val phase = when (DungeonState.f7Phase) {
			Phase7.P1 -> TrackedBossPhase.P1
			Phase7.P2 -> TrackedBossPhase.P2
			Phase7.P3, Phase7.S1, Phase7.S2, Phase7.S3, Phase7.S4 -> TrackedBossPhase.P3
			Phase7.P4 -> TrackedBossPhase.P4
			Phase7.P5 -> TrackedBossPhase.P5
			Phase7.UNKNOWN -> TrackedBossPhase.NONE
		}
		val section = when (DungeonState.p3Section) {
			Phase7.S1 -> 1
			Phase7.S2 -> 2
			Phase7.S3 -> 3
			Phase7.S4 -> 4
			else -> 1
		}
		p3Progress.synchronize(phase, section)
	}

	private fun queueCompletedP3Section(before: PhaseTrackerSnapshot, after: PhaseTrackerSnapshot) {
		if (!isHealerSelected()
			|| !areaCheck()
			|| after.phase != TrackedBossPhase.P3
			|| after.p3State != P3ProgressState.DONE
			|| (before.section == after.section && before.p3State == P3ProgressState.DONE)
		) {
			return
		}

		val setting = p3LeapSetting(after.section) ?: return
		val destination = p3LeapDestination(after.section) ?: return
		if (setting.enabled.value && !isAlreadyAtLeapDestination(destination)) {
			queueLeap(DungeonClass.findClassString(setting.target.value), "Healer S${after.section}", destination)
		}
	}

	private fun p3LeapSetting(section: Int): P3LeapSetting? =
		when (section) {
			1 -> P3LeapSetting(s1LeapEnabled, s1LeapTarget)
			2 -> P3LeapSetting(s2LeapEnabled, s2LeapTarget)
			3 -> P3LeapSetting(s3LeapEnabled, s3LeapTarget)
			4 -> P3LeapSetting(s4LeapEnabled, s4LeapTarget)
			else -> null
		}

	private fun p3LeapDestination(completedSection: Int): Phase7? =
		when (completedSection) {
			1 -> Phase7.S2
			2 -> Phase7.S3
			3 -> Phase7.S4
			4 -> Phase7.P4
			else -> null
		}

	private fun isAlreadyAtLeapDestination(destination: Phase7?): Boolean =
		when (destination) {
			Phase7.S1, Phase7.S2, Phase7.S3, Phase7.S4 -> DungeonUtils.getP3Section() == destination
			Phase7.P4 -> DungeonUtils.getF7Phase() == Phase7.P4
			else -> false
		}

	private fun detectHealerMageCp(client: Minecraft) {
		if (!isHealerSelected() || !mageCpEnabled.value || !DungeonUtils.isPhase(Phase7.P2)) {
			resetMageCpDetection()
			return
		}

		val local = client.player ?: return
		if (local.position().distanceToSqr(MAGE_CP_CENTER) > MAGE_CP_AREA_RADIUS_SQ) {
			resetMageCpDetection()
			return
		}

		val mage = DungeonState.getClassPlayer(DungeonClass.MAGE)
		if (mage == null || mage.name.equals(local.name.string, ignoreCase = true)) {
			resetMageCpDetection()
			return
		}

		val magePlayer = mage.findPlayer()
		if (magePlayer == null) {
			resetMageCpDetection()
			return
		}

		val distanceSq = magePlayer.position().distanceToSqr(local.position())
		val inside = distanceSq <= MAGE_INSIDE_RADIUS_SQ

		if (distanceSq >= MAGE_FAR_DISTANCE_SQ) {
			mageLastFarTick = clientTicks
		} else if (inside
			&& !mageWasInside
			&& mageLastFarTick != NO_MAGE_FAR_TICK
			&& clientTicks - mageLastFarTick <= MAGE_FAR_TO_INSIDE_WINDOW_TICKS
		) {
			mageLastFarTick = NO_MAGE_FAR_TICK
			queueLeap(DungeonClass.findClassString(mageCpTarget.value), "Healer Mage CP")
		}

		mageWasInside = inside
	}

	private fun queueLeap(targetClass: DungeonClass, reason: String, destination: Phase7? = null) {
		val now = System.currentTimeMillis()
		if (targetClass == DungeonClass.NONE
			|| isAlreadyAtLeapDestination(destination)
			|| now - lastTriggerAt < TRIGGER_COOLDOWN_MS
		) {
			return
		}

		val delay = ThreadLocalRandom.current().nextLong(MIN_START_DELAY_MS, MAX_START_DELAY_MS + 1L)
		pendingLeap = PendingLeap(targetClass, now + delay, reason, destination)
		lastTriggerAt = now
	}

	private fun runPending(client: Minecraft) {
		val leap = pendingLeap ?: return
		if (System.currentTimeMillis() < leap.runAt) {
			return
		}

		pendingLeap = null
		if (isAlreadyAtLeapDestination(leap.destination)) {
			return
		}
		val target = DungeonState.getClassPlayer(leap.targetClass)
		if (target == null || target.name.equals(client.player?.name?.string, ignoreCase = true)) {
			modMessage("${ChatFormatting.RED}Couldn't find ${leap.targetClass.displayName} for ${leap.reason}.")
			return
		}

		startLeap(client, target.name, leap.destination)
	}

	private fun startLeap(client: Minecraft, targetName: String, destination: Phase7?) {
		val player = client.player ?: return
		if (client.gameMode == null
			|| client.level == null
			|| TerminalContext.inTerminal
			|| isAlreadyAtLeapDestination(destination)
		) {
			return
		}

		val previousSlot = player.inventory.selectedSlot
		if (!ItemInteractionUtils.selectHotbarItem(*LEAP_ITEM_IDS)) {
			modMessage("${ChatFormatting.RED}Couldn't find an Infinileap or Spirit Leap in your hotbar.")
			return
		}

		if (previousSlot != player.inventory.selectedSlot) {
			pendingUseTarget = targetName
			pendingUseDestination = destination
			pendingUseTick = clientTicks + ThreadLocalRandom.current().nextInt(MIN_SLOT_SWITCH_SETTLE_TICKS, MAX_SLOT_SWITCH_SETTLE_TICKS + 1)
			return
		}

		useSelectedLeap(targetName)
	}

	private fun runPendingUse(client: Minecraft) {
		val target = pendingUseTarget ?: return
		val destination = pendingUseDestination
		if (clientTicks < pendingUseTick) {
			return
		}

		pendingUseTarget = null
		pendingUseDestination = null
		pendingUseTick = 0L
		if (!isAlreadyAtLeapDestination(destination)
			&& client.player != null
			&& isSpiritLeapHeld(client.player!!)
		) {
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

	private fun selectedClass(): DungeonClass =
		DungeonClass.findClassString(dungeonClass.value)

	private fun isHealerSelected(): Boolean =
		selectedClass() == DungeonClass.HEALER

	private fun resetRuntime() {
		pendingLeap = null
		pendingUseTarget = null
		pendingUseDestination = null
		pendingUseTick = 0L
		resetMageCpDetection()
		p3Progress.reset()
		leapMenu.clear()
	}

	private fun resetMageCpDetection() {
		mageLastFarTick = NO_MAGE_FAR_TICK
		mageWasInside = false
	}

	private fun modMessage(message: String) {
		ChatUtils.chat("${ChatFormatting.AQUA}Auto Leap » ${ChatFormatting.RESET}$message")
	}

	private data class P3LeapSetting(val enabled: BooleanSetting, val target: ModeSetting)
	private data class PendingLeap(
		val targetClass: DungeonClass,
		val runAt: Long,
		val reason: String,
		val destination: Phase7?
	)

	companion object {
		private val CLASS_MODES = listOf("Archer", "Mage", "Berserk", "Healer", "Tank")
		private const val MIN_START_DELAY_MS = 130L
		private const val MAX_START_DELAY_MS = 170L
		private const val TRIGGER_COOLDOWN_MS = 2000L
		private const val MENU_TIMEOUT_MS = 1500L
		private const val P3_DONE_HOLD_MS = 1L
		private const val MIN_SLOT_SWITCH_SETTLE_TICKS = 2
		private const val MAX_SLOT_SWITCH_SETTLE_TICKS = 3
		private const val NO_MAGE_FAR_TICK = -1L
		private const val MAGE_CP_AREA_RADIUS_SQ = 8.0 * 8.0
		private const val MAGE_INSIDE_RADIUS_SQ = 2.5 * 2.5
		private const val MAGE_FAR_DISTANCE_SQ = 6.0 * 6.0
		private const val MAGE_FAR_TO_INSIDE_WINDOW_TICKS = 10L
		private val MAGE_CP_CENTER = Vec3(56.0, 169.0, 66.0)
		private val LEAP_ITEM_IDS = arrayOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")
	}
}
