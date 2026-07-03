package cgc.cgc.module.impl.player

import cgc.cgc.client.gui.CgcConfigScreen
import cgc.cgc.client.gui.HotbarSwitchSetupScreen
import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.ActionBarMessageModule
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ChatMessageModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ButtonSetting
import cgc.cgc.module.setting.HotbarSwapListSetting
import cgc.cgc.module.setting.HotbarSwapTrigger
import cgc.cgc.module.setting.HotbarSwapType
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.utils.DungeonUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.Container
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkAccess
import java.util.EnumMap
import kotlin.math.roundToInt

class HotbarSwitcher : CgcModule(
	id = "hotbar-switcher",
	displayName = "Hotbar Switcher",
	category = ModuleCategory.PLAYER,
	description = "Swaps configured inventory slots into the hotbar when selected events trigger.",
	defaultEnabled = false
), ClientTickModule, ChatMessageModule, ActionBarMessageModule, WorldLoadModule {
	private val autoClose = BooleanSetting("Auto Close", true)
	private val switchDelay = NumberSetting("Switch Delay", 0.0, 1000.0, 50.0, 5.0, " ms")
	private val swaps = HotbarSwapListSetting("Swaps", setupAction = this::openSetup)
	private val addSwap = ButtonSetting("Add Swap", "Add Swap", { swaps.addSwap() })

	private val armedSwaps = linkedMapOf<String, HotbarSwapListSetting.Swap>()
	private val trapFollowUpSwaps = linkedMapOf<String, HotbarSwapListSetting.Swap>()
	private val pendingClicks = ArrayDeque<PendingClick>()
	private val lastEventTriggerAt = EnumMap<HotbarSwapTrigger, Long>(HotbarSwapTrigger::class.java)
	private val lastSwapTriggerAt = hashMapOf<String, Long>()
	private var nextClickAt = 0L
	private var closeWhenDone = false
	private var inventoryWasOpen = false
	private var lastGeneralSwapAt = 0L
	private var inTrapRoom = false
	private var trapExitBufferUntil = 0L
	private var lastTrapRoomKey: String? = null
	private var lastStartRoomActive = false
	private var lastPhaseTrigger: HotbarSwapTrigger? = null

	init {
		registerProperty(autoClose, switchDelay, addSwap, swaps)
	}

	override fun onClientTick(client: Minecraft) {
		if (client.player == null || client.level == null) {
			cancelPendingSwap()
			clearEventState()
			return
		}

		pollSwapKeybinds(client)
		detectStartRoom()
		detectTrapRoom(client)
		detectF7Phase()
		expireTrapExitBuffer()
		handleInventoryActivation(client)
		processPendingClicks(client)
	}

	override fun onChatMessage(message: String) {
		val text = ChatFormatting.stripFormatting(message)?.trim() ?: message.trim()
		if (text.contains("Trap", ignoreCase = true)) {
			triggerEvent(HotbarSwapTrigger.TRAP)
		}
	}

	override fun onActionBarMessage(message: String) {
		val text = ChatFormatting.stripFormatting(message)?.trim() ?: message.trim()
		if (text.contains("Trap", ignoreCase = true)) {
			triggerEvent(HotbarSwapTrigger.TRAP)
		}
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

	private fun openSetup(setting: HotbarSwapListSetting, swap: HotbarSwapListSetting.Swap) {
		val client = Minecraft.getInstance()
		client.setScreen(HotbarSwitchSetupScreen(setting, swap, client.screen))
	}

	private fun pollSwapKeybinds(client: Minecraft) {
		val screen = client.screen
		if (screen is CgcConfigScreen || screen is HotbarSwitchSetupScreen) {
			return
		}

		for (swap in swaps.value) {
			if (swap.trigger != HotbarSwapTrigger.KEYBIND) {
				continue
			}
			swap.keybind.setRunnable { activateKeybindSwap(client, swap) }
			swap.keybind.tick(client.window)
		}
	}

	private fun detectStartRoom() {
		val active = Location.area.isArea(Island.DUNGEON) && !DungeonState.started && !DungeonState.inBoss
		if (active && !lastStartRoomActive) {
			triggerEvent(HotbarSwapTrigger.START_ROOM)
		}
		lastStartRoomActive = active
	}

	private fun detectTrapRoom(client: Minecraft) {
		if (!Location.area.isArea(Island.DUNGEON) || DungeonState.inBoss) {
			markOutsideTrapRoom()
			return
		}

		val room = scanCurrentRoom(client)
		if (room == null || room.trapName == null) {
			markOutsideTrapRoom()
			return
		}

		inTrapRoom = true
		trapExitBufferUntil = 0L
		if (lastTrapRoomKey != room.key) {
			lastTrapRoomKey = room.key
			triggerEvent(HotbarSwapTrigger.TRAP)
		}
	}

	private fun detectF7Phase() {
		val current = currentF7Trigger()
		if (current != null && current != lastPhaseTrigger) {
			triggerEvent(current)
		}
		lastPhaseTrigger = current
	}

	private fun currentF7Trigger(): HotbarSwapTrigger? {
		if (!(Location.floor == Floor.F7 || Location.floor == Floor.M7) || !DungeonState.inBoss) {
			return null
		}

		return when (DungeonUtils.getF7Phase()) {
			Phase7.P1 -> HotbarSwapTrigger.P1
			Phase7.P2 -> HotbarSwapTrigger.P2
			Phase7.P3 -> when (DungeonUtils.getP3Section()) {
				Phase7.S1 -> HotbarSwapTrigger.S1
				Phase7.S2 -> HotbarSwapTrigger.S2
				Phase7.S3 -> HotbarSwapTrigger.S3
				Phase7.S4 -> HotbarSwapTrigger.S4
				else -> null
			}
			Phase7.P4 -> HotbarSwapTrigger.P4
			Phase7.P5 -> HotbarSwapTrigger.P5
			else -> null
		}
	}

	private fun scanCurrentRoom(client: Minecraft): ScannedRoom? {
		val level = client.level ?: return null
		val player = client.player ?: return null
		val center = roomCenter(player.blockX, player.blockZ) ?: return null
		val loadedPos = BlockPos(center.first, SCAN_Y, center.second)
		if (!level.isLoaded(loadedPos)) {
			return null
		}

		val chunk = level.getChunk(loadedPos)
		val roofHeight = roofHeight(center.first, center.second, chunk)
		if (roofHeight <= 0) {
			return null
		}

		val core = roomCore(center.first, center.second, roofHeight, chunk)
		val trapName = TRAP_ROOM_CORES[core]
		return ScannedRoom(center.first, center.second, core, trapName)
	}

	private fun roomCenter(x: Int, z: Int): Pair<Int, Int>? {
		if (x !in DUNGEON_MIN_COORD..DUNGEON_MAX_COORD || z !in DUNGEON_MIN_COORD..DUNGEON_MAX_COORD) {
			return null
		}

		val centerX = ((x - DUNGEON_START_X) / ROOM_SIZE.toFloat()).roundToInt() * ROOM_SIZE + DUNGEON_START_X
		val centerZ = ((z - DUNGEON_START_Z) / ROOM_SIZE.toFloat()).roundToInt() * ROOM_SIZE + DUNGEON_START_Z
		if (centerX !in DUNGEON_START_X..DUNGEON_LAST_ROOM_CENTER || centerZ !in DUNGEON_START_Z..DUNGEON_LAST_ROOM_CENTER) {
			return null
		}
		return centerX to centerZ
	}

	private fun roofHeight(x: Int, z: Int, chunk: ChunkAccess): Int {
		val mutable = BlockPos.MutableBlockPos(x, SCAN_Y, z)
		for (y in MAX_SCAN_Y downTo MIN_SCAN_Y) {
			mutable.set(x, y, z)
			val block = chunk.getBlockState(mutable).block
			if (block != Blocks.AIR) {
				return if (block == Blocks.GOLD_BLOCK) y - 1 else y
			}
		}
		return -1
	}

	private fun roomCore(x: Int, z: Int, roomHeight: Int, chunk: ChunkAccess): Int {
		val mutable = BlockPos.MutableBlockPos()
		val clampedHeight = roomHeight.coerceIn(MIN_CORE_SCAN_Y - 1, MAX_CORE_SCAN_Y)
		val builder = StringBuilder(150)
		builder.append("0".repeat(MAX_CORE_SCAN_Y - clampedHeight))

		var bedrock = 0
		for (y in clampedHeight downTo MIN_CORE_SCAN_Y) {
			mutable.set(x, y, z)
			val block = chunk.getBlockState(mutable).block
			if (block == Blocks.AIR && bedrock >= 2 && y < AIR_BREAK_Y) {
				builder.append("0".repeat(y - MIN_CORE_SCAN_Y + 1))
				break
			}

			if (block == Blocks.BEDROCK) {
				bedrock++
			} else {
				bedrock = 0
				if (IGNORED_CORE_BLOCKS.contains(block)) {
					continue
				}
			}
			builder.append(block)
		}
		return builder.toString().hashCode()
	}

	private fun triggerEvent(trigger: HotbarSwapTrigger) {
		val now = System.currentTimeMillis()
		val last = lastEventTriggerAt[trigger] ?: 0L
		if (now - last < EVENT_COOLDOWN_MS) {
			return
		}

		if (trigger != HotbarSwapTrigger.TRAP) {
			clearTrapSwapState()
		}

		lastEventTriggerAt[trigger] = now
		for (swap in swaps.value) {
			if (swap.trigger == trigger) {
				armSwap(swap)
			}
		}
	}

	private fun armSwap(swap: HotbarSwapListSetting.Swap) {
		if (swap.pairs.isEmpty()) {
			return
		}

		val now = System.currentTimeMillis()
		val last = lastSwapTriggerAt[swap.id] ?: 0L
		if (now - last < SWAP_COOLDOWN_MS) {
			return
		}

		lastSwapTriggerAt[swap.id] = now
		armedSwaps[swap.id] = swap
	}

	private fun activateKeybindSwap(client: Minecraft, swap: HotbarSwapListSetting.Swap) {
		if (client.screen !is InventoryScreen) {
			return
		}
		if (queueSwapNow(swap, delayMs = 0L)) {
			clearTrapSwapState()
		}
	}

	private fun handleInventoryActivation(client: Minecraft) {
		val inventoryOpen = client.screen is InventoryScreen
		if (inventoryOpen && !inventoryWasOpen) {
			queueInventoryOpenSwaps()
		}
		inventoryWasOpen = inventoryOpen
	}

	private fun queueInventoryOpenSwaps() {
		val now = System.currentTimeMillis()
		val devOnlySwaps = swaps.value
			.filter {
				it.swapType == HotbarSwapType.DEV_ONLY &&
					it.pairs.isNotEmpty() &&
					now - (lastSwapTriggerAt[it.id] ?: 0L) >= SWAP_COOLDOWN_MS
			}

		val armed = armedSwaps.values.toList()
		val trapFollowUp = if (armed.isEmpty() && shouldRunTrapFollowUp()) {
			trapFollowUpSwaps.values.toList()
		} else {
			emptyList()
		}
		val eventSwaps = armed.ifEmpty { trapFollowUp }
		val queuedSwaps = (devOnlySwaps + eventSwaps).distinctBy { it.id }
		if (queuedSwaps.isEmpty()) {
			return
		}

		val clicks = queuedSwaps.flatMap(::pendingClicksFor)
		if (clicks.isEmpty()) {
			if (armed.isNotEmpty()) {
				armedSwaps.clear()
			}
			if (trapFollowUp.isNotEmpty() && !inTrapRoom) {
				clearTrapSwapState()
			}
			return
		}
		if (!tryStartGeneralCooldown(now)) {
			return
		}

		for (swap in devOnlySwaps) {
			lastSwapTriggerAt[swap.id] = now
		}

		val trapSwaps = eventSwaps.filter { it.trigger == HotbarSwapTrigger.TRAP }
		if (trapSwaps.isNotEmpty()) {
			trapFollowUpSwaps.clear()
			for (swap in trapSwaps) {
				trapFollowUpSwaps[swap.id] = swap
			}
			if (inTrapRoom) {
				trapExitBufferUntil = 0L
			} else if (trapExitBufferUntil == 0L) {
				trapExitBufferUntil = now + TRAP_EXIT_BUFFER_MS
			}
		}

		pendingClicks.addAll(clicks)
		armedSwaps.clear()
		nextClickAt = now + switchDelay.value.toLong().coerceAtLeast(0L)
		closeWhenDone = closeWhenDone || queuedSwaps.any(::shouldAutoClose)

		if (trapFollowUp.isNotEmpty() && !inTrapRoom) {
			clearTrapSwapState()
		}
	}

	private fun queueSwapNow(swap: HotbarSwapListSetting.Swap, delayMs: Long): Boolean {
		if (swap.pairs.isEmpty()) {
			return false
		}

		val now = System.currentTimeMillis()
		val last = lastSwapTriggerAt[swap.id] ?: 0L
		if (now - last < SWAP_COOLDOWN_MS) {
			return false
		}
		val clicks = pendingClicksFor(swap)
		if (clicks.isEmpty()) {
			return false
		}
		if (!tryStartGeneralCooldown(now)) {
			return false
		}

		lastSwapTriggerAt[swap.id] = now
		pendingClicks.addAll(clicks)
		nextClickAt = now + delayMs.coerceAtLeast(0L)
		closeWhenDone = closeWhenDone || shouldAutoClose(swap)
		return true
	}

	private fun processPendingClicks(client: Minecraft) {
		if (pendingClicks.isEmpty()) {
			return
		}

		val inventoryScreen = client.screen as? InventoryScreen
		if (inventoryScreen == null) {
			cancelPendingSwap()
			return
		}

		val now = System.currentTimeMillis()
		if (now < nextClickAt) {
			return
		}

		val player = client.player ?: run {
			cancelPendingSwap()
			return
		}
		val gameMode = client.gameMode ?: run {
			cancelPendingSwap()
			return
		}

		while (pendingClicks.isNotEmpty()) {
			val currentInventoryScreen = client.screen as? InventoryScreen
			if (currentInventoryScreen !== inventoryScreen) {
				cancelPendingSwap()
				return
			}

			val click = pendingClicks.removeFirst()
			val sourceSlot = sourceSlotForClick(click, player.inventory) ?: continue
			if (sourceSlot == click.hotbarSlot) {
				continue
			}

			val slotNumber = menuSlotForInventorySlot(currentInventoryScreen.menu, player.inventory, sourceSlot) ?: continue
			gameMode.handleContainerInput(currentInventoryScreen.menu.containerId, slotNumber, click.hotbarSlot, ContainerInput.SWAP, player)
		}

		if (pendingClicks.isEmpty() && closeWhenDone) {
			closeWhenDone = false
			if (client.screen is InventoryScreen) {
				client.setScreen(null)
			}
		}
	}

	private fun menuSlotForInventorySlot(menu: AbstractContainerMenu, inventory: Container, inventorySlot: Int): Int? {
		for (index in menu.slots.indices) {
			val slot = menu.slots[index]
			if (slot.container === inventory && slot.getContainerSlot() == inventorySlot) {
				return index
			}
		}
		return null
	}

	private fun cancelPendingSwap() {
		pendingClicks.clear()
		nextClickAt = 0L
		closeWhenDone = false
	}

	private fun clearEventState() {
		lastStartRoomActive = false
		inTrapRoom = false
		trapExitBufferUntil = 0L
		lastTrapRoomKey = null
		lastPhaseTrigger = null
	}

	private fun resetRuntime() {
		armedSwaps.clear()
		trapFollowUpSwaps.clear()
		pendingClicks.clear()
		lastEventTriggerAt.clear()
		lastSwapTriggerAt.clear()
		nextClickAt = 0L
		closeWhenDone = false
		inventoryWasOpen = false
		lastGeneralSwapAt = 0L
		clearEventState()
	}

	private fun markOutsideTrapRoom() {
		if (inTrapRoom) {
			trapExitBufferUntil = if (hasTrapSwapState()) {
				System.currentTimeMillis() + TRAP_EXIT_BUFFER_MS
			} else {
				0L
			}
		}
		inTrapRoom = false
		lastTrapRoomKey = null
	}

	private fun shouldRunTrapFollowUp(): Boolean {
		if (trapFollowUpSwaps.isEmpty()) {
			return false
		}
		return inTrapRoom || (trapExitBufferUntil > 0L && System.currentTimeMillis() <= trapExitBufferUntil)
	}

	private fun expireTrapExitBuffer() {
		val bufferUntil = trapExitBufferUntil
		if (bufferUntil > 0L && System.currentTimeMillis() > bufferUntil) {
			clearTrapSwapState()
		}
	}

	private fun clearTrapSwapState() {
		val trapSwapIds = armedSwaps.values
			.filter { it.trigger == HotbarSwapTrigger.TRAP }
			.map { it.id }
		for (id in trapSwapIds) {
			armedSwaps.remove(id)
		}
		trapFollowUpSwaps.clear()
		trapExitBufferUntil = 0L
	}

	private fun hasTrapSwapState(): Boolean =
		trapFollowUpSwaps.isNotEmpty() || armedSwaps.values.any { it.trigger == HotbarSwapTrigger.TRAP }

	private fun tryStartGeneralCooldown(now: Long): Boolean {
		if (now - lastGeneralSwapAt < GENERAL_COOLDOWN_MS) {
			return false
		}

		lastGeneralSwapAt = now
		return true
	}

	private fun shouldAutoClose(swap: HotbarSwapListSetting.Swap): Boolean =
		autoClose.value || swap.autoClose

	private fun pendingClicksFor(swap: HotbarSwapListSetting.Swap): List<PendingClick> =
		swap.pairs.mapNotNull { pair ->
			when (swap.swapType) {
				HotbarSwapType.SLOT -> PendingClick(pair.hotbarSlot, pair.inventorySlot, null)
				HotbarSwapType.ITEM -> pair.item?.let { PendingClick(pair.hotbarSlot, pair.inventorySlot, it) }
				HotbarSwapType.DEV_ONLY -> PendingClick(pair.hotbarSlot, pair.inventorySlot, null)
			}
		}

	private fun sourceSlotForClick(click: PendingClick, inventory: Container): Int? {
		val item = click.item ?: return click.inventorySlot
		for (slot in 0..35) {
			if (item.matches(inventory.getItem(slot))) {
				return slot
			}
		}
		return null
	}

	private data class PendingClick(
		val hotbarSlot: Int,
		val inventorySlot: Int,
		val item: HotbarSwapListSetting.ItemSelector?
	)

	private data class ScannedRoom(
		val centerX: Int,
		val centerZ: Int,
		val core: Int,
		val trapName: String?
	) {
		val key: String = "$centerX:$centerZ:$core"
	}

	private companion object {
		private const val EVENT_COOLDOWN_MS = 1000L
		private const val SWAP_COOLDOWN_MS = 250L
		private const val GENERAL_COOLDOWN_MS = 2_000L
		private const val TRAP_EXIT_BUFFER_MS = 15_000L
		private const val DUNGEON_START_X = -185
		private const val DUNGEON_START_Z = -185
		private const val DUNGEON_LAST_ROOM_CENTER = -25
		private const val DUNGEON_MIN_COORD = -200
		private const val DUNGEON_MAX_COORD = -10
		private const val ROOM_SIZE = 32
		private const val SCAN_Y = 67
		private const val MIN_SCAN_Y = 12
		private const val MAX_SCAN_Y = 160
		private const val MIN_CORE_SCAN_Y = 12
		private const val MAX_CORE_SCAN_Y = 140
		private const val AIR_BREAK_Y = 69
		private val IGNORED_CORE_BLOCKS: Set<Block> = setOf(Blocks.OAK_PLANKS, Blocks.TRAPPED_CHEST, Blocks.CHEST)
		private val TRAP_ROOM_CORES = mapOf(
			-1471076910 to "New Trap",
			1043953833 to "New Trap",
			-686376510 to "New Trap",
			1851993785 to "New Trap",
			1590699551 to "Old Trap",
			-85564220 to "Old Trap"
		)
	}
}
