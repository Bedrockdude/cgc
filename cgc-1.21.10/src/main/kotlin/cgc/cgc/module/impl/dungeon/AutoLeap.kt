package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.DungeonClass
import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.ActionBarMessageModule
import cgc.cgc.module.ChatMessageModule
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.SubModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.group.GroupSetting
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
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom

class AutoLeap : CgcModule(
	id = "AutoLeap",
	displayName = "Auto leap",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically uses Spirit Leap for your selected F7/M7 class route.",
	defaultEnabled = false
), ClientTickModule, ChatMessageModule, ActionBarMessageModule, PacketReceiveModule, WorldLoadModule {
	private val dungeonClass = ModeSetting("Class", "Healer", CLASS_MODES)
	private val i4LeapEnabled = BooleanSetting("I4 Leap Enabled", true)
	private val i4LeapTarget = ModeSetting(
		"I4 Leap Target",
		"Tank",
		CLASS_MODES,
		supplier = { i4LeapEnabled.value }
	)
	private val tankCpEnabled = BooleanSetting("Tank CP Enabled", true)
	private val tankCpTarget = ModeSetting(
		"Tank CP Target",
		"Tank",
		CLASS_MODES,
		supplier = { tankCpEnabled.value }
	)
	private val healCpEnabled = BooleanSetting("Heal CP Enabled", true)
	private val healCpTarget = ModeSetting(
		"Heal CP Target",
		"Healer",
		CLASS_MODES,
		supplier = { healCpEnabled.value }
	)
	private val bloodRushEnabled = BooleanSetting("Blood Rush Enabled", true)
	private val bloodRushTarget = ModeSetting(
		"Blood Rush Target",
		"Healer",
		CLASS_MODES,
		supplier = { bloodRushEnabled.value }
	)
	private val includeBloodKey = BooleanSetting(
		"Include Blood Key",
		false,
		supplier = { bloodRushEnabled.value }
	)
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
	private val overviewGroup = navigationGroup(
		"Overview",
		"Choose the class route used by class-specific checkpoints."
	)
	private val clearGroup = navigationGroup(
		"Clear",
		"Leap after party key pickups before the boss fight."
	)
	private val stormGroup = navigationGroup(
		"Storm",
		"One-time checkpoint leaps during Storm (P2)."
	)
	private val goldorGroup = navigationGroup(
		"Goldor",
		"I4 and section-completion leaps during Goldor (P3)."
	)

	private val leapMenu = SpiritLeapMenu("Auto Leap »") { target ->
		modMessage("Leaping to $target.")
	}
	private val p3Progress = PhaseTrackerState()

	private var pendingLeap: PendingLeap? = null
	private var pendingUseTarget: String? = null
	private var pendingUseOrigin: Phase7? = null
	private var pendingUseDestination: Phase7? = null
	private var pendingUseTick = 0L
	private var clientTicks = 0L
	private var lastTriggerAt = 0L
	private var mageLastFarTick = NO_MAGE_FAR_TICK
	private var mageWasInside = false
	private var i4WindowUntil = 0L
	private var firstLightningHandled = false
	private var firstLightningEndsAtTick = NO_LIGHTNING_END_TICK
	private var firstCrushHandled = false
	private var lastKeyPickupMessage = ""
	private var lastKeyPickupAt = 0L

	init {
		overviewGroup.add(dungeonClass)
		clearGroup.add(
			bloodRushEnabled,
			bloodRushTarget,
			includeBloodKey
		)
		stormGroup.add(
			tankCpEnabled,
			tankCpTarget,
			healCpEnabled,
			healCpTarget,
			mageCpEnabled,
			mageCpTarget
		)
		goldorGroup.add(
			i4LeapEnabled,
			i4LeapTarget,
			s1LeapEnabled,
			s1LeapTarget,
			s2LeapEnabled,
			s2LeapTarget,
			s3LeapEnabled,
			s3LeapTarget,
			s4LeapEnabled,
			s4LeapTarget
		)
		registerProperty(
			overviewGroup,
			clearGroup,
			stormGroup,
			goldorGroup
		)
	}

	private fun navigationGroup(name: String, description: String): GroupSetting<SubModule<AutoLeap>> =
		GroupSetting(
			name,
			SubModule(this, name, defaultEnabled = true),
			description = description,
			toggleable = false
		)

	override fun onClientTick(client: Minecraft) {
		clientTicks++
		if (!dungeonFloorCheck() || client.player == null || client.level == null) {
			resetRuntime()
			return
		}

		if (bossAreaCheck()) {
			tickP3Progress()
			detectHealerMageCp(client)
			tickFirstLightning()
		} else {
			resetMageCpDetection()
		}
		runPending(client)
		runPendingUse(client)
		leapMenu.tickTimeout(MENU_TIMEOUT_MS)
	}

	override fun onChatMessage(message: String) {
		handleP3ProgressMessage(message)
		handleAutoLeapMessage(message)
	}

	override fun onActionBarMessage(message: String) {
		if (!dungeonFloorCheck() || DungeonState.inBoss || !DungeonState.started) {
			return
		}

		val key = AutoLeapSignals.keyPickup(message) ?: return
		val now = System.currentTimeMillis()
		val normalized = AutoLeapSignals.normalize(message)
		if (normalized == lastKeyPickupMessage && now - lastKeyPickupAt < KEY_PICKUP_DUPLICATE_WINDOW_MS) {
			return
		}
		lastKeyPickupMessage = normalized
		lastKeyPickupAt = now

		if (bloodRushEnabled.value && (key == DungeonKeyKind.WITHER || includeBloodKey.value)) {
			queueLeap(DungeonClass.findClassString(bloodRushTarget.value), "Blood Rush")
		}
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		if (packet is ClientboundOpenScreenPacket && leapMenu.handleOpenScreen(packet)) {
			return true
		}

		if (packet is ClientboundContainerSetSlotPacket && leapMenu.handleSetSlot(packet)) {
			return true
		}

		observeLightningPacket(packet)
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

	private fun handleAutoLeapMessage(message: String) {
		if (!bossAreaCheck()) {
			return
		}

		val text = AutoLeapSignals.normalize(message)
		val now = System.currentTimeMillis()
		when {
			text == STORM_DEATH_MESSAGE -> i4WindowUntil = now + I4_WINDOW_MS
			DungeonUtils.isPhase(Phase7.P2) && AutoLeapSignals.isStormCrush(text) && !firstCrushHandled -> {
				firstCrushHandled = true
				if (healCpEnabled.value) {
					queueLeap(DungeonClass.findClassString(healCpTarget.value), "Heal CP")
				}
			}
		}

		val player = Minecraft.getInstance().player ?: return
		if (now <= i4WindowUntil
			&& DungeonState.p3Section == Phase7.S1
			&& AutoLeapSignals.isOnI4(player.position())
			&& AutoLeapSignals.isOwnDeviceCompletion(text, player.name.string)
		) {
			i4WindowUntil = 0L
			if (i4LeapEnabled.value) {
				queueLeap(DungeonClass.findClassString(i4LeapTarget.value), "I4", Phase7.S4)
			}
		}
	}

	private fun observeLightningPacket(packet: Packet<*>) {
		when (packet) {
			is ClientboundBundlePacket -> packet.subPackets().forEach(::observeLightningPacket)
			is ClientboundSetTitleTextPacket -> {
				if (!bossAreaCheck()
					|| !DungeonUtils.isPhase(Phase7.P2)
					|| firstLightningHandled
					|| firstLightningEndsAtTick != NO_LIGHTNING_END_TICK
				) {
					return
				}

				val duration = AutoLeapSignals.lightningDurationTicks(packet.text.string) ?: return
				firstLightningEndsAtTick = clientTicks + duration
			}
		}
	}

	private fun tickFirstLightning() {
		val endTick = firstLightningEndsAtTick
		if (endTick == NO_LIGHTNING_END_TICK || clientTicks < endTick) {
			return
		}

		firstLightningEndsAtTick = NO_LIGHTNING_END_TICK
		firstLightningHandled = true
		if (tankCpEnabled.value) {
			queueLeap(DungeonClass.findClassString(tankCpTarget.value), "Tank CP")
		}
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
			|| !bossAreaCheck()
			|| after.phase != TrackedBossPhase.P3
			|| after.p3State != P3ProgressState.DONE
			|| (before.section == after.section && before.p3State == P3ProgressState.DONE)
		) {
			return
		}

		val setting = p3LeapSetting(after.section) ?: return
		val origin = p3LeapOrigin(after.section) ?: return
		val destination = p3LeapDestination(after.section) ?: return
		if (setting.enabled.value && isValidLeapPosition(origin, destination)) {
			queueLeap(DungeonClass.findClassString(setting.target.value), "Healer S${after.section}", origin, destination)
		}
	}

	private fun p3LeapOrigin(completedSection: Int): Phase7? =
		when (completedSection) {
			1 -> Phase7.S1
			2 -> Phase7.S2
			3 -> Phase7.S3
			4 -> Phase7.S4
			else -> null
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

	private fun isValidLeapPosition(origin: Phase7?, destination: Phase7?): Boolean =
		(origin == null || DungeonUtils.getP3Section() == origin)
			&& !isAlreadyAtLeapDestination(destination)

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

	private fun queueLeap(
		targetClass: DungeonClass,
		reason: String,
		origin: Phase7? = null,
		destination: Phase7? = null
	) {
		val now = System.currentTimeMillis()
		if (targetClass == DungeonClass.NONE
			|| !isValidLeapPosition(origin, destination)
			|| now - lastTriggerAt < TRIGGER_COOLDOWN_MS
		) {
			return
		}

		val delay = ThreadLocalRandom.current().nextLong(MIN_START_DELAY_MS, MAX_START_DELAY_MS + 1L)
		pendingLeap = PendingLeap(targetClass, now + delay, reason, origin, destination)
		lastTriggerAt = now
	}

	private fun runPending(client: Minecraft) {
		val leap = pendingLeap ?: return
		if (System.currentTimeMillis() < leap.runAt) {
			return
		}

		pendingLeap = null
		if (!isValidLeapPosition(leap.origin, leap.destination)) {
			return
		}
		val target = DungeonState.getClassPlayer(leap.targetClass)
		if (target == null || target.name.equals(client.player?.name?.string, ignoreCase = true)) {
			modMessage("${ChatFormatting.RED}Couldn't find ${leap.targetClass.displayName} for ${leap.reason}.")
			return
		}

		startLeap(client, target.name, leap.origin, leap.destination)
	}

	private fun startLeap(client: Minecraft, targetName: String, origin: Phase7?, destination: Phase7?) {
		val player = client.player ?: return
		if (client.gameMode == null
			|| client.level == null
			|| TerminalContext.inTerminal
			|| !isValidLeapPosition(origin, destination)
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
			pendingUseOrigin = origin
			pendingUseDestination = destination
			pendingUseTick = clientTicks + ThreadLocalRandom.current().nextInt(MIN_SLOT_SWITCH_SETTLE_TICKS, MAX_SLOT_SWITCH_SETTLE_TICKS + 1)
			return
		}

		useSelectedLeap(targetName)
	}

	private fun runPendingUse(client: Minecraft) {
		val target = pendingUseTarget ?: return
		val origin = pendingUseOrigin
		val destination = pendingUseDestination
		if (clientTicks < pendingUseTick) {
			return
		}

		pendingUseTarget = null
		pendingUseOrigin = null
		pendingUseDestination = null
		pendingUseTick = 0L
		if (isValidLeapPosition(origin, destination)
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

	private fun dungeonFloorCheck(): Boolean =
		Location.area.isArea(Island.DUNGEON)
			&& (Location.floor == Floor.F7 || Location.floor == Floor.M7)

	private fun bossAreaCheck(): Boolean =
		dungeonFloorCheck() && DungeonState.inBoss

	private fun selectedClass(): DungeonClass =
		DungeonClass.findClassString(dungeonClass.value)

	private fun isHealerSelected(): Boolean =
		selectedClass() == DungeonClass.HEALER

	private fun resetRuntime() {
		pendingLeap = null
		pendingUseTarget = null
		pendingUseOrigin = null
		pendingUseDestination = null
		pendingUseTick = 0L
		resetMageCpDetection()
		p3Progress.reset()
		leapMenu.clear()
		i4WindowUntil = 0L
		firstLightningHandled = false
		firstLightningEndsAtTick = NO_LIGHTNING_END_TICK
		firstCrushHandled = false
		lastKeyPickupMessage = ""
		lastKeyPickupAt = 0L
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
		val origin: Phase7?,
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
		private const val NO_LIGHTNING_END_TICK = -1L
		private const val I4_WINDOW_MS = 30_000L
		private const val KEY_PICKUP_DUPLICATE_WINDOW_MS = 1_000L
		private const val STORM_DEATH_MESSAGE = "[BOSS] Storm: I should have known that I stood no chance."
		private const val MAGE_CP_AREA_RADIUS_SQ = 8.0 * 8.0
		private const val MAGE_INSIDE_RADIUS_SQ = 2.5 * 2.5
		private const val MAGE_FAR_DISTANCE_SQ = 6.0 * 6.0
		private const val MAGE_FAR_TO_INSIDE_WINDOW_TICKS = 10L
		private val MAGE_CP_CENTER = Vec3(56.0, 169.0, 66.0)
		private val LEAP_ITEM_IDS = arrayOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP")
	}
}
