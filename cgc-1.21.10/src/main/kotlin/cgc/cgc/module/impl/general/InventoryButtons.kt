package cgc.cgc.module.impl.general

import cgc.cgc.client.CgcCommandRegistry
import cgc.cgc.config.CgcConfigStore
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ButtonSetting
import cgc.cgc.module.setting.InventoryButtonListSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.abs

class InventoryButtons : CgcModule(
	id = "InventoryButtons",
	displayName = "Inventory Buttons",
	category = ModuleCategory.GENERAL,
	description = "Shows movable command buttons on the player inventory screen.",
	defaultEnabled = false
) {
	private val doubleClick = BooleanSetting("Double Click", false)
	private val buttons = InventoryButtonListSetting("Buttons")
	private val addButton = ButtonSetting("Add Button", "Add Button", { buttons.addButton() })

	private var activeButtonId: String? = null
	private var activeOffsetX = 0
	private var activeOffsetY = 0
	private var pressedX = 0.0
	private var pressedY = 0.0
	private var pressedDoubleClick = false
	private var movedActiveButton = false

	init {
		instance = this
		registerProperty(doubleClick, addButton, buttons)
	}

	private fun render(screen: AbstractContainerScreen<*>, gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		if (!isOwnInventory(screen)) {
			clearDrag()
			return
		}

		for (button in buttons.value) {
			val maxX = screen.width - InventoryButtonListSetting.BUTTON_SIZE
			val maxY = screen.height - InventoryButtonListSetting.BUTTON_SIZE
			if (button.x > maxX || button.y > maxY) {
				button.x = button.x.coerceIn(0, maxX.coerceAtLeast(0))
				button.y = button.y.coerceIn(0, maxY.coerceAtLeast(0))
			}

			val hovered = contains(button, mouseX.toDouble(), mouseY.toDouble())
			val active = activeButtonId == button.id
			val fill = when {
				active -> ACTIVE_FILL
				hovered -> HOVER_FILL
				else -> FILL
			}
			gfx.fill(button.x, button.y, button.x + InventoryButtonListSetting.BUTTON_SIZE, button.y + InventoryButtonListSetting.BUTTON_SIZE, fill)
			outline(gfx, button.x, button.y, InventoryButtonListSetting.BUTTON_SIZE, InventoryButtonListSetting.BUTTON_SIZE, if (hovered || active) ACTIVE_OUTLINE else OUTLINE)

			val stack = iconStack(button.iconItem)
			if (!stack.isEmpty) {
				gfx.item(stack, button.x + 6, button.y + 6)
			} else if (button.label.isNotBlank()) {
				gfx.centeredText(Minecraft.getInstance().font, button.label.take(2), button.x + 14, button.y + 10, TEXT)
			}

			if (hovered) {
				val label = button.label.ifBlank { button.command.ifBlank { "Inventory Button" } }
				gfx.setTooltipForNextFrame(Component.literal(label), mouseX, mouseY)
			}
		}
	}

	private fun mouseClicked(screen: AbstractContainerScreen<*>, click: MouseButtonEvent, doubled: Boolean): Boolean {
		if (!isOwnInventory(screen) || click.button() != 0) {
			return false
		}

		val button = buttonAt(click.x(), click.y()) ?: return false
		activeButtonId = button.id
		activeOffsetX = (click.x() - button.x).toInt()
		activeOffsetY = (click.y() - button.y).toInt()
		pressedX = click.x()
		pressedY = click.y()
		pressedDoubleClick = doubled
		movedActiveButton = false
		return true
	}

	private fun mouseDragged(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): Boolean {
		val button = activeButton() ?: return false
		if (!isOwnInventory(screen) || click.button() != 0) {
			return false
		}

		if (abs(click.x() - pressedX) > DRAG_THRESHOLD || abs(click.y() - pressedY) > DRAG_THRESHOLD) {
			movedActiveButton = true
		}
		if (!movedActiveButton) {
			return true
		}

		buttons.moveButton(
			button,
			(click.x() - activeOffsetX).toInt(),
			(click.y() - activeOffsetY).toInt(),
			screen.width - InventoryButtonListSetting.BUTTON_SIZE,
			screen.height - InventoryButtonListSetting.BUTTON_SIZE
		)
		return true
	}

	private fun mouseReleased(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): Boolean {
		val button = activeButton() ?: return false
		if (!isOwnInventory(screen) || click.button() != 0) {
			clearDrag()
			return false
		}

		val moved = movedActiveButton
		val shouldRun = !moved && (!doubleClick.value || pressedDoubleClick)
		clearDrag()
		if (moved) {
			CgcConfigStore.saveAll()
			return true
		}

		if (shouldRun) {
			runCommand(button.command)
		}
		return true
	}

	private fun runCommand(rawCommand: String) {
		val command = rawCommand.trim()
		if (command.isBlank()) {
			return
		}

		if (CgcCommandRegistry.tryExecutePrefixed(command)) {
			return
		}

		val connection = Minecraft.getInstance().connection ?: return
		connection.sendCommand(command.removePrefix("/"))
	}

	private fun activeButton(): InventoryButtonListSetting.Button? {
		val id = activeButtonId ?: return null
		return buttons.value.firstOrNull { it.id == id }
	}

	private fun buttonAt(mouseX: Double, mouseY: Double): InventoryButtonListSetting.Button? =
		buttons.value.asReversed().firstOrNull { contains(it, mouseX, mouseY) }

	private fun contains(button: InventoryButtonListSetting.Button, mouseX: Double, mouseY: Double): Boolean =
		mouseX >= button.x &&
			mouseX <= button.x + InventoryButtonListSetting.BUTTON_SIZE &&
			mouseY >= button.y &&
			mouseY <= button.y + InventoryButtonListSetting.BUTTON_SIZE

	private fun clearDrag() {
		activeButtonId = null
		activeOffsetX = 0
		activeOffsetY = 0
		pressedX = 0.0
		pressedY = 0.0
		pressedDoubleClick = false
		movedActiveButton = false
	}

	private fun isOwnInventory(screen: AbstractContainerScreen<*>): Boolean =
		screen is InventoryScreen

	override fun reset() {
		clearDrag()
	}

	private fun iconStack(iconItem: String): ItemStack {
		val id = runCatching { Identifier.parse(InventoryButtonListSetting.iconItemId(iconItem)) }.getOrNull() ?: return ItemStack(Items.PAPER)
		val item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER)
		return ItemStack(item)
	}

	private fun outline(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, colour: Int) {
		gfx.fill(x, y, x + width, y + 1, colour)
		gfx.fill(x, y + height - 1, x + width, y + height, colour)
		gfx.fill(x, y, x + 1, y + height, colour)
		gfx.fill(x + width - 1, y, x + width, y + height, colour)
	}

	companion object {
		private var instance: InventoryButtons? = null

		private const val DRAG_THRESHOLD = 3.0
		private const val FILL = 0xAA10141B.toInt()
		private const val HOVER_FILL = 0xCC1D2733.toInt()
		private const val ACTIVE_FILL = 0xCC24364A.toInt()
		private const val OUTLINE = 0x88FFFFFF.toInt()
		private const val ACTIVE_OUTLINE = 0xFF55AAFF.toInt()
		private const val TEXT = 0xFFFFFFFF.toInt()

		@JvmStatic
		fun renderInventoryButtons(screen: AbstractContainerScreen<*>, gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
			instance?.takeIf { it.enabled }?.render(screen, gfx, mouseX, mouseY)
		}

		@JvmStatic
		fun handleMouseClicked(screen: AbstractContainerScreen<*>, click: MouseButtonEvent, doubled: Boolean): Boolean =
			instance?.takeIf { it.enabled }?.mouseClicked(screen, click, doubled) ?: false

		@JvmStatic
		fun handleMouseDragged(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): Boolean =
			instance?.takeIf { it.enabled }?.mouseDragged(screen, click) ?: false

		@JvmStatic
		fun handleMouseReleased(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): Boolean =
			instance?.takeIf { it.enabled }?.mouseReleased(screen, click) ?: false
	}
}
