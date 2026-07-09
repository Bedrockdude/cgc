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
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.StringSetting
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.DungeonUtils
import cgc.cgc.utils.ItemUtils
import cgc.cgc.utils.SpiritLeapMenu
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.Locale
import java.util.regex.Pattern

class Relics : CgcModule(
	id = "Relics",
	displayName = "Relics",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically picks up, places, and leaps after M7 relics.",
	defaultEnabled = false
), ClientTickModule, ChatMessageModule, PacketReceiveModule, WorldLoadModule {
	private val autoPickup = BooleanSetting("Auto Pickup", true)
	private val autoPlace = BooleanSetting("Auto Place", true)
	private val autoLeap = BooleanSetting("Auto Leap", true)
	private val range = NumberSetting("Range", 1.0, 6.0, 4.5, 0.1)
	private val clickDelay = NumberSetting("Click Delay", 0.0, 1000.0, 150.0, 25.0, " ms")
	private val relicSlot = NumberSetting("Relic Slot", 1.0, 9.0, 9.0, 1.0)
	private val leapSlot = NumberSetting("Leap Slot", 1.0, 9.0, 8.0, 1.0, supplier = { autoLeap.value })
	private val leapDelay = NumberSetting("Leap Delay", 0.0, 2000.0, 250.0, 25.0, " ms", supplier = { autoLeap.value })
	private val redLeap = ModeSetting("Red Leap", "None", LEAP_MODES, supplier = { autoLeap.value })
	private val redCustom = StringSetting("Red Custom", "", maxLength = 64, supplier = { autoLeap.value && redLeap.isMode("Custom") })
	private val orangeLeap = ModeSetting("Orange Leap", "None", LEAP_MODES, supplier = { autoLeap.value })
	private val orangeCustom = StringSetting("Orange Custom", "", maxLength = 64, supplier = { autoLeap.value && orangeLeap.isMode("Custom") })
	private val greenLeap = ModeSetting("Green Leap", "Archer", LEAP_MODES, supplier = { autoLeap.value })
	private val greenCustom = StringSetting("Green Custom", "", maxLength = 64, supplier = { autoLeap.value && greenLeap.isMode("Custom") })
	private val blueLeap = ModeSetting("Blue Leap", "Berserk", LEAP_MODES, supplier = { autoLeap.value })
	private val blueCustom = StringSetting("Blue Custom", "", maxLength = 64, supplier = { autoLeap.value && blueLeap.isMode("Custom") })
	private val purpleLeap = ModeSetting("Purple Leap", "Berserk", LEAP_MODES, supplier = { autoLeap.value })
	private val purpleCustom = StringSetting("Purple Custom", "", maxLength = 64, supplier = { autoLeap.value && purpleLeap.isMode("Custom") })

	private val leapMenu = SpiritLeapMenu("Relics >")

	private var clientTicks = 0L
	private var lastInteractionAt = 0L
	private var lastHeldRelic: RelicType? = null
	private var lastPickupRelic: RelicType? = null
	private var lastPickupAt = 0L
	private var pendingLeap: PendingLeap? = null
	private var pendingLeapUse: PendingLeapUse? = null
	private var pendingPlace: PendingPlace? = null

	init {
		registerProperty(
			autoPickup,
			autoPlace,
			autoLeap,
			range,
			clickDelay,
			relicSlot,
			leapSlot,
			leapDelay,
			redLeap,
			redCustom,
			orangeLeap,
			orangeCustom,
			greenLeap,
			greenCustom,
			blueLeap,
			blueCustom,
			purpleLeap,
			purpleCustom
		)
	}

	override fun onClientTick(client: Minecraft) {
		clientTicks++
		leapMenu.tickTimeout(MENU_TIMEOUT_MS)

		if (!areaCheck() || client.player == null || client.level == null) {
			resetRuntime()
			return
		}

		val heldRelic = currentRelic(client)
		if (heldRelic?.type != lastHeldRelic) {
			heldRelic?.type?.let { scheduleLeap(it) }
			lastHeldRelic = heldRelic?.type
		}

		runPendingLeap(client)
		runPendingLeapUse(client)
		runPendingPlace(client)

		if (autoPlace.value && heldRelic != null) {
			tryPlaceRelic(client, heldRelic)
			return
		}

		if (autoPickup.value && heldRelic == null) {
			tryPickupRelic(client)
		}
	}

	override fun onChatMessage(message: String) {
		if (!areaCheck()) {
			return
		}

		val text = ChatFormatting.stripFormatting(message)?.trim() ?: message.trim()
		val matcher = PICKUP_PATTERN.matcher(text)
		if (!matcher.find() || !isOwnPickup(matcher.group(1))) {
			return
		}

		RelicType.fromName("Corrupted ${matcher.group(2)} Relic")?.let { scheduleLeap(it) }
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

	override fun onDisable() {
		resetRuntime()
	}

	override fun reset() {
		resetRuntime()
	}

	private fun tryPickupRelic(client: Minecraft) {
		val player = client.player ?: return
		val gameMode = client.gameMode ?: return
		if (!canInteractNow() || leapMenu.isActive) {
			return
		}

		val hit = client.hitResult as? EntityHitResult ?: return
		val stand = hit.entity as? ArmorStand ?: return
		RelicType.fromName(stand.getItemBySlot(EquipmentSlot.HEAD).hoverName.string) ?: return
		if (player.distanceToSqr(stand) > rangeSq()) {
			return
		}

		gameMode.interact(
			player,
			stand,
			hit,
			InteractionHand.MAIN_HAND
		)
		player.swing(InteractionHand.MAIN_HAND)
		lastInteractionAt = System.currentTimeMillis()
	}

	private fun tryPlaceRelic(client: Minecraft, heldRelic: HeldRelic) {
		val player = client.player ?: return
		if (!canInteractNow() || pendingPlace != null || leapMenu.isActive) {
			return
		}

		if (currentCauldronHit(client, heldRelic.type) == null) {
			return
		}

		if (player.inventory.selectedSlot != heldRelic.slot) {
			player.inventory.selectedSlot = heldRelic.slot
			pendingPlace = PendingPlace(heldRelic.type, heldRelic.slot, clientTicks + SLOT_SETTLE_TICKS)
			return
		}

		placeSelectedRelic(client, heldRelic.type, heldRelic.slot)
	}

	private fun runPendingPlace(client: Minecraft) {
		val place = pendingPlace ?: return
		if (clientTicks < place.useAtTick) {
			return
		}

		pendingPlace = null
		val held = currentRelic(client)
		if (held == null || held.type != place.type || held.slot != place.slot) {
			return
		}

		if (client.player?.distanceToSqr(held.type.place) ?: Double.MAX_VALUE <= rangeSq()) {
			placeSelectedRelic(client, held.type, held.slot)
		}
	}

	private fun placeSelectedRelic(client: Minecraft, type: RelicType, slot: Int) {
		val player = client.player ?: return
		val gameMode = client.gameMode ?: return
		val hit = currentCauldronHit(client, type) ?: return
		player.inventory.selectedSlot = slot
		gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
		player.swing(InteractionHand.MAIN_HAND)
		lastInteractionAt = System.currentTimeMillis()
	}

	private fun scheduleLeap(type: RelicType) {
		if (!autoLeap.value) {
			return
		}

		val now = System.currentTimeMillis()
		if (lastPickupRelic == type && now - lastPickupAt < DUPLICATE_PICKUP_WINDOW_MS) {
			return
		}

		if (leapTarget(type) == null) {
			return
		}

		lastPickupRelic = type
		lastPickupAt = now
		pendingLeap = PendingLeap(type, now + leapDelay.value.toLong(), now)
	}

	private fun runPendingLeap(client: Minecraft) {
		val leap = pendingLeap ?: return
		val now = System.currentTimeMillis()
		if (now < leap.runAt) {
			return
		}

		if (now - leap.createdAt > PENDING_LEAP_TIMEOUT_MS) {
			pendingLeap = null
			return
		}

		if (client.screen != null && !leapMenu.isActive) {
			return
		}

		val target = leapTarget(leap.type) ?: run {
			pendingLeap = null
			return
		}

		pendingLeap = null
		startLeap(client, target)
	}

	private fun runPendingLeapUse(client: Minecraft) {
		val use = pendingLeapUse ?: return
		if (clientTicks < use.useAtTick) {
			return
		}

		pendingLeapUse = null
		val player = client.player ?: return
		if (!isSpiritLeap(player)) {
			modMessage("${ChatFormatting.RED}Leap slot does not contain a spirit leap.")
			return
		}

		leapMenu.start(use.target)
	}

	private fun startLeap(client: Minecraft, target: String) {
		val player = client.player ?: return
		if (client.gameMode == null || leapMenu.isActive) {
			return
		}

		val slot = hotbarIndex(leapSlot)
		if (player.inventory.selectedSlot != slot) {
			player.inventory.selectedSlot = slot
			pendingLeapUse = PendingLeapUse(target, clientTicks + SLOT_SETTLE_TICKS)
			return
		}

		if (!isSpiritLeap(player)) {
			modMessage("${ChatFormatting.RED}Leap slot does not contain a spirit leap.")
			return
		}

		leapMenu.start(target)
	}

	private fun leapTarget(type: RelicType): String? {
		val (mode, custom) = when (type) {
			RelicType.RED -> redLeap to redCustom
			RelicType.ORANGE -> orangeLeap to orangeCustom
			RelicType.GREEN -> greenLeap to greenCustom
			RelicType.BLUE -> blueLeap to blueCustom
			RelicType.PURPLE -> purpleLeap to purpleCustom
		}

		if (mode.isMode("None")) {
			return null
		}

		if (mode.isMode("Custom")) {
			return custom.value.trim().takeIf { it.isNotBlank() }
		}

		val clazz = classFromMode(mode.value)
		return DungeonState.getClassPlayer(clazz)?.name
	}

	private fun currentRelic(client: Minecraft): HeldRelic? {
		val player = client.player ?: return null
		val preferredSlot = hotbarIndex(relicSlot)
		RelicType.fromName(player.inventory.getItem(preferredSlot).hoverName.string)?.let {
			return HeldRelic(it, preferredSlot)
		}

		val selectedSlot = player.inventory.selectedSlot
		RelicType.fromName(player.inventory.getItem(selectedSlot).hoverName.string)?.let {
			return HeldRelic(it, selectedSlot)
		}

		for (slot in 0..8) {
			if (slot == preferredSlot || slot == selectedSlot) {
				continue
			}

			RelicType.fromName(player.inventory.getItem(slot).hoverName.string)?.let {
				return HeldRelic(it, slot)
			}
		}

		return null
	}

	private fun currentCauldronHit(client: Minecraft, type: RelicType): BlockHitResult? {
		val player = client.player ?: return null
		val hit = client.hitResult as? BlockHitResult ?: return null
		if (hit.type != HitResult.Type.BLOCK || hit.blockPos != BlockPos.containing(type.place)) {
			return null
		}

		return if (player.distanceToSqr(hit.location) <= rangeSq()) hit else null
	}

	private fun canInteractNow(): Boolean =
		System.currentTimeMillis() - lastInteractionAt >= clickDelay.value.toLong()

	private fun rangeSq(): Double {
		val range = range.value.toDouble()
		return range * range
	}

	private fun hotbarIndex(setting: NumberSetting): Int =
		Mth.clamp(setting.value.toInt(), 1, 9) - 1

	private fun isSpiritLeap(player: Player): Boolean {
		val itemId = ItemUtils.skyBlockId(player.inventory.selectedItem)
		return itemId == "SPIRIT_LEAP" || itemId == "INFINITE_SPIRIT_LEAP"
	}

	private fun isOwnPickup(actor: String): Boolean {
		val name = Minecraft.getInstance().player?.name?.string ?: return false
		return actor.equals(name, ignoreCase = true)
			|| actor.lowercase(Locale.ROOT).endsWith(" ${name.lowercase(Locale.ROOT)}")
	}

	private fun areaCheck(): Boolean =
		Location.area.isArea(Island.DUNGEON)
			&& (Location.floor == Floor.F7 || Location.floor == Floor.M7)
			&& DungeonState.inBoss
			&& DungeonUtils.isPhase(Phase7.P5)

	private fun resetRuntime() {
		leapMenu.clear()
		lastHeldRelic = null
		lastPickupRelic = null
		lastPickupAt = 0L
		pendingLeap = null
		pendingLeapUse = null
		pendingPlace = null
	}

	private fun modMessage(message: String) {
		ChatUtils.chat("${ChatFormatting.LIGHT_PURPLE}Relics > ${ChatFormatting.RESET}$message")
	}

	private data class HeldRelic(val type: RelicType, val slot: Int)
	private data class PendingLeap(val type: RelicType, val runAt: Long, val createdAt: Long)
	private data class PendingLeapUse(val target: String, val useAtTick: Long)
	private data class PendingPlace(val type: RelicType, val slot: Int, val useAtTick: Long)

	private enum class RelicType(val place: Vec3) {
		RED(Vec3(51.5, 7.5, 42.5)),
		ORANGE(Vec3(57.5, 7.5, 42.5)),
		GREEN(Vec3(49.5, 7.5, 44.5)),
		BLUE(Vec3(59.5, 7.5, 44.5)),
		PURPLE(Vec3(54.5, 7.5, 41.5));

		companion object {
			fun fromName(itemName: String?): RelicType? {
				val name = ChatFormatting.stripFormatting(itemName ?: "")
					?.lowercase(Locale.ROOT)
					?: return null
				if (!name.contains("corrupted") || !name.contains("relic")) {
					return null
				}

				return entries.firstOrNull { name.contains(it.name.lowercase(Locale.ROOT)) }
			}
		}
	}

	private companion object {
		private val LEAP_MODES = listOf("None", "Archer", "Mage", "Berserk", "Healer", "Tank", "Custom")
		private val PICKUP_PATTERN = Pattern.compile("^(\\w{3,16}) picked the Corrupted (\\w{3,6}) Relic!$")
		private const val SLOT_SETTLE_TICKS = 2L
		private const val MENU_TIMEOUT_MS = 1500L
		private const val PENDING_LEAP_TIMEOUT_MS = 3000L
		private const val DUPLICATE_PICKUP_WINDOW_MS = 1500L

		private fun classFromMode(value: String): DungeonClass =
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
