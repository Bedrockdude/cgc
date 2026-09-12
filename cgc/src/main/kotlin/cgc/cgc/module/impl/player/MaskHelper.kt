package cgc.cgc.module.impl.player

import cgc.cgc.data.Keybind
import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.utils.DungeonUtils
import cgc.cgc.utils.ItemUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

class MaskHelper : CgcModule(
	id = "mask-helper",
	displayName = "Mask Helper",
	category = ModuleCategory.PLAYER,
	description = "Clicks a Spirit, Bonzo, or P5 Cow Hat from the Equipment screen while the configured key is held.",
	defaultEnabled = false
), ClientTickModule {
	private val equipmentKey = KeybindSetting("Equipment Key", Keybind())
	private val delay = NumberSetting("Delay", 0.0, 2000.0, 300.0, 25.0, " ms")

	private var heldSince = 0L
	private var handled = false

	init {
		registerProperty(equipmentKey, delay)
	}

	override fun onEnable() {
		equipmentKey.register()
	}

	override fun onDisable() {
		equipmentKey.unregister()
		reset()
	}

	override fun onClientTick(client: Minecraft) {
		if (client.player == null || client.level == null || client.connection == null) {
			reset()
			return
		}

		if (!equipmentKey.value.isDown(client.window)) {
			reset()
			return
		}

		val now = System.currentTimeMillis()
		if (heldSince == 0L) {
			heldSince = now
		}

		if (!handled && now - heldSince >= delay.value.toLong()) {
			handled = tryClickMask(client)
		}
	}

	override fun reset() {
		heldSince = 0L
		handled = false
	}

	private fun tryClickMask(client: Minecraft): Boolean {
		val screen = client.screen
		if (!isEquipmentScreen(screen) || screen !is AbstractContainerScreen<*>) {
			return false
		}

		val menu = screen.menu
		val targetSlot = findMaskSlot(client, menu)
		if (targetSlot == -1) {
			return true
		}

		val player = client.player ?: return false
		client.gameMode?.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player)
		return true
	}

	private fun isEquipmentScreen(screen: Screen?): Boolean {
		val title = screen?.title?.string ?: return false
		return title.contains(STATS_TITLE_WORD, ignoreCase = true) &&
			title.contains(EQUIPMENT_TITLE_WORD, ignoreCase = true)
	}

	private fun findMaskSlot(client: Minecraft, menu: AbstractContainerMenu): Int {
		val player = client.player ?: return -1
		val inventory = player.inventory
		if (isP5BossFight()) {
			val wearingCowHat = ItemUtils.skyBlockId(player.getItemBySlot(EquipmentSlot.HEAD)) == COW_HAT_ID
			val targetId = if (wearingCowHat) SPIRIT_MASK_ID else COW_HAT_ID
			return findInventorySlot(menu, inventory, targetId)
		}

		var bonzoSlot = -1

		for (i in menu.slots.indices) {
			val slot = menu.slots[i]
			if (slot.container !== inventory || !slot.hasItem()) {
				continue
			}

			when (ItemUtils.skyBlockId(slot.item)) {
				SPIRIT_MASK_ID -> return i
				BONZO_MASK_ID -> bonzoSlot = i
			}
		}

		return bonzoSlot
	}

	private fun findInventorySlot(
		menu: AbstractContainerMenu,
		inventory: Inventory,
		targetId: String
	): Int {
		for (i in menu.slots.indices) {
			val slot = menu.slots[i]
			if (slot.container === inventory && slot.hasItem() && ItemUtils.skyBlockId(slot.item) == targetId) {
				return i
			}
		}
		return -1
	}

	private fun isP5BossFight(): Boolean =
		(Location.floor == Floor.F7 || Location.floor == Floor.M7) &&
			DungeonState.inBoss &&
			DungeonUtils.isPhase(Phase7.P5)

	private companion object {
		private const val STATS_TITLE_WORD = "stats"
		private const val EQUIPMENT_TITLE_WORD = "equipment"
		private const val SPIRIT_MASK_ID = "STARRED_SPIRIT_MASK"
		private const val BONZO_MASK_ID = "BONZO_MASK"
		private const val COW_HAT_ID = "COW_HEAD"
	}
}
