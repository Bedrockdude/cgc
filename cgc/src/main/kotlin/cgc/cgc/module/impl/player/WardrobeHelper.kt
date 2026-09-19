package cgc.cgc.module.impl.player

import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.ButtonSetting
import cgc.cgc.module.setting.WardrobeLoadoutListSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput

class WardrobeHelper : CgcModule(
	id = "wardrobe-helper",
	displayName = "Wardrobe Helper",
	category = ModuleCategory.PLAYER,
	description = "Opens Loadouts and selects a configured wardrobe slot with a keybind.",
	defaultEnabled = false
), ClientTickModule, WorldLoadModule {
	private val loadouts = WardrobeLoadoutListSetting("Loadouts")
	private val addLoadout = ButtonSetting("Add Loadout", "Add Loadout", { loadouts.addLoadout() })

	private var tick = 0L
	private var pending: PendingSelection? = null

	init {
		registerProperty(addLoadout, loadouts)
	}

	override fun onClientTick(client: Minecraft) {
		tick++
		if (client.player == null || client.level == null || client.connection == null) {
			resetRuntime()
			return
		}

		pollKeybinds(client)
		processPending(client)
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

	private fun pollKeybinds(client: Minecraft) {
		val playing = client.screen == null
		for (loadout in loadouts.configuredLoadouts()) {
			loadout.keybind.setRunnable { beginSelection(client, loadout) }
			loadout.keybind.tick(client.window, allowAction = playing)
		}
	}

	private fun beginSelection(client: Minecraft, loadout: WardrobeLoadoutListSetting.Loadout) {
		if (pending != null) return
		client.connection?.sendCommand(LOADOUT_COMMAND)
		pending = PendingSelection(loadout, System.currentTimeMillis())
	}

	private fun processPending(client: Minecraft) {
		val selection = pending ?: return
		val now = System.currentTimeMillis()
		if (now - selection.startedAtMs > OPEN_TIMEOUT_MS) {
			pending = null
			return
		}

		val clickedAtTick = selection.clickedAtTick
		if (clickedAtTick != null) {
			if (selection.loadout.autoClose && tick - clickedAtTick >= ACTION_DELAY_TICKS) {
				if (client.screen is AbstractContainerScreen<*>) {
					client.player?.closeContainer()
				}
				pending = null
			} else if (!selection.loadout.autoClose) {
				pending = null
			}
			return
		}

		val screen = client.screen as? AbstractContainerScreen<*> ?: return
		if (!isLoadoutsTitle(screen.title.string)) return
		if (selection.openedAtMs == null) {
			selection.openedAtMs = now
			selection.openedAtTick = tick
		}

		val openedAtMs = selection.openedAtMs ?: return
		val openedAtTick = selection.openedAtTick ?: return
		if (now - openedAtMs < MIN_OPEN_DELAY_MS || tick - openedAtTick < ACTION_DELAY_TICKS) return

		val player = client.player ?: return
		val targetSlot = screen.menu.slots.indices.firstOrNull { index ->
			val slot = screen.menu.slots[index]
			slot.container !== player.inventory && slot.hasItem() && namesMatch(slot.item.hoverName.string, selection.loadout.name)
		} ?: return

		val gameMode = client.gameMode ?: return
		gameMode.handleContainerInput(screen.menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player)
		selection.clickedAtTick = tick
	}

	private fun resetRuntime() {
		tick = 0L
		pending = null
	}

	private data class PendingSelection(
		val loadout: WardrobeLoadoutListSetting.Loadout,
		val startedAtMs: Long,
		var openedAtTick: Long? = null,
		var openedAtMs: Long? = null,
		var clickedAtTick: Long? = null
	)

	companion object {
		private const val LOADOUT_COMMAND = "ld"
		private const val LOADOUTS_TITLE = "(1/3) Loadouts"
		private const val ACTION_DELAY_TICKS = 3L
		private const val MIN_OPEN_DELAY_MS = 160L
		private const val OPEN_TIMEOUT_MS = 5_000L

		internal fun isLoadoutsTitle(title: String): Boolean = title == LOADOUTS_TITLE

		internal fun namesMatch(itemName: String, configuredName: String): Boolean =
			itemName.equals(configuredName, ignoreCase = true)
	}
}
