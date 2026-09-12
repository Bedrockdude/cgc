package cgc.cgc.client.gui

import cgc.cgc.config.CgcConfigStore
import cgc.cgc.module.setting.HotbarSwapListSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class HotbarSwitchSetupScreen(
	private val setting: HotbarSwapListSetting,
	private val swap: HotbarSwapListSetting.Swap,
	private val returnScreen: Screen? = null
) : Screen(Component.literal("Hotbar Switch Setup")) {
	private val hitboxes = mutableListOf<UiHitbox>()
	private val slotHitboxes = mutableListOf<SlotHitbox>()
	private var draggingSlot: Int? = null
	private var gridX = 0
	private var gridY = 0

	override fun extractRenderState(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		hitboxes.clear()
		slotHitboxes.clear()

		val panelWidth = GRID_WIDTH + 42
		val panelHeight = GRID_HEIGHT + 78
		val panelX = (width - panelWidth) / 2
		val panelY = (height - panelHeight) / 2
		gridX = panelX + 21
		gridY = panelY + 42

		gfx.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Colours.BACKGROUND)
		outline(gfx, panelX, panelY, panelWidth, panelHeight, Colours.OUTLINE)
		gfx.centeredText(font(), "Hotbar Switch Setup", panelX + panelWidth / 2, panelY + 16, Colours.TEXT)

		renderPairLines(gfx)
		renderInventory(gfx, mouseX, mouseY)
		renderButtons(gfx, panelX, panelY, panelWidth, panelHeight)
		super.extractRenderState(gfx, mouseX, mouseY, partialTick)
	}

	override fun extractBackground(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val mouseX = click.x()
		val mouseY = click.y()
		for (hitbox in hitboxes.asReversed()) {
			if (hitbox.contains(mouseX, mouseY)) {
				hitbox.onClick(click.button())
				return true
			}
		}

		val slot = slotAt(mouseX, mouseY)
		if (slot != null) {
			when (click.button()) {
				0 -> {
					draggingSlot = slot
					return true
				}
				1 -> {
					if (swap.removePairContaining(slot)) {
						setting.onEdit()
						CgcConfigStore.saveAll()
					}
					return true
				}
			}
		}

		return super.mouseClicked(click, doubled)
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean {
		val start = draggingSlot ?: return super.mouseReleased(click)
		draggingSlot = null
		if (click.button() != 0) {
			return true
		}

		val target = slotAt(click.x(), click.y())
		if (target != null && target != start && swap.addOrReplacePair(start, target, itemForSlot(start))) {
			setting.onEdit()
			CgcConfigStore.saveAll()
		}
		return true
	}

	override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean =
		draggingSlot != null || super.mouseDragged(click, dragX, dragY)

	override fun onClose() {
		CgcConfigStore.saveAll()
		Minecraft.getInstance().setScreen(returnScreen ?: CgcConfigScreen())
	}

	override fun isPauseScreen(): Boolean = false

	private fun renderInventory(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		for (row in 0 until 3) {
			for (column in 0 until 9) {
				val slot = 9 + row * 9 + column
				renderSlot(gfx, slot, gridX + column * SLOT_STEP, gridY + row * SLOT_STEP, mouseX, mouseY)
			}
		}

		val hotbarY = gridY + 3 * SLOT_STEP + HOTBAR_GAP
		for (column in 0 until 9) {
			renderSlot(gfx, column, gridX + column * SLOT_STEP, hotbarY, mouseX, mouseY)
		}
	}

	private fun renderSlot(gfx: GuiGraphicsExtractor, slot: Int, x: Int, y: Int, mouseX: Int, mouseY: Int) {
		val hovered = Bounds(x, y, SLOT_SIZE, SLOT_SIZE).contains(mouseX.toDouble(), mouseY.toDouble())
		val paired = swap.pairForSlot(slot) != null
		val dragging = draggingSlot == slot
		val fill = when {
			dragging -> Colours.SELECTED_FILL
			hovered -> Colours.HOVER_FILL
			else -> Colours.SLOT_FILL
		}

		gfx.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, fill)
		outline(gfx, x, y, SLOT_SIZE, SLOT_SIZE, if (paired) Colours.SELECTED else Colours.OUTLINE)

		val stack = itemForSlot(slot)
		if (!stack.isEmpty) {
			gfx.item(stack, x + 2, y + 2)
			gfx.itemDecorations(font(), stack, x + 2, y + 2)
			if (hovered) {
				gfx.setTooltipForNextFrame(font(), stack, mouseX, mouseY)
			}
		}

		swap.pairForSlot(slot)?.let { pair ->
			val number = swap.pairs.indexOf(pair) + 1
			gfx.centeredText(font(), number.toString(), x + SLOT_SIZE - 5, y + 2, Colours.TEXT)
		}

		slotHitboxes.add(SlotHitbox(slot, x, y, SLOT_SIZE, SLOT_SIZE))
	}

	private fun renderPairLines(gfx: GuiGraphicsExtractor) {
		for ((index, pair) in swap.pairs.withIndex()) {
			val first = slotCenter(pair.hotbarSlot)
			val second = slotCenter(pair.inventorySlot)
			drawLine(gfx, first.first, first.second, second.first, second.second, lineColour(index))
		}
	}

	private fun renderButtons(gfx: GuiGraphicsExtractor, panelX: Int, panelY: Int, panelWidth: Int, panelHeight: Int) {
		val buttonY = panelY + panelHeight - 28
		val clearX = panelX + 21
		val doneX = panelX + panelWidth - 72

		button(gfx, clearX, buttonY, 54, 18, "Clear") { button ->
			if (button == 0) {
				swap.pairs.clear()
				setting.onEdit()
				CgcConfigStore.saveAll()
			}
		}
		button(gfx, doneX, buttonY, 51, 18, "Done") { button ->
			if (button == 0) onClose()
		}
	}

	private fun button(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, label: String, click: (Int) -> Unit) {
		gfx.fill(x, y, x + width, y + height, Colours.BUTTON_FILL)
		outline(gfx, x, y, width, height, Colours.OUTLINE)
		gfx.centeredText(font(), label, x + width / 2, y + 5, Colours.TEXT)
		hitboxes.add(UiHitbox(x, y, width, height, click))
	}

	private fun itemForSlot(slot: Int): ItemStack =
		Minecraft.getInstance().player?.inventory?.getItem(slot) ?: ItemStack.EMPTY

	private fun slotAt(mouseX: Double, mouseY: Double): Int? =
		slotHitboxes.firstOrNull { it.contains(mouseX, mouseY) }?.slot

	private fun slotCenter(slot: Int): Pair<Int, Int> {
		val row = if (slot in 0..8) 3 else (slot - 9) / 9
		val column = if (slot in 0..8) slot else (slot - 9) % 9
		val y = if (slot in 0..8) gridY + 3 * SLOT_STEP + HOTBAR_GAP else gridY + row * SLOT_STEP
		return gridX + column * SLOT_STEP + SLOT_SIZE / 2 to y + SLOT_SIZE / 2
	}

	private fun drawLine(gfx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, colour: Int) {
		val steps = max(abs(x2 - x1), abs(y2 - y1)).coerceAtLeast(1)
		for (i in 0..steps step 2) {
			val x = x1 + ((x2 - x1) * i / steps.toFloat()).roundToInt()
			val y = y1 + ((y2 - y1) * i / steps.toFloat()).roundToInt()
			gfx.fill(x, y, x + 1, y + 1, colour)
		}
	}

	private fun outline(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, colour: Int) {
		gfx.fill(x, y, x + width, y + 1, colour)
		gfx.fill(x, y + height - 1, x + width, y + height, colour)
		gfx.fill(x, y, x + 1, y + height, colour)
		gfx.fill(x + width - 1, y, x + width, y + height, colour)
	}

	private fun lineColour(index: Int): Int =
		LINE_COLOURS[index % LINE_COLOURS.size]

	private fun font(): Font = Minecraft.getInstance().font

	private data class UiHitbox(
		val x: Int,
		val y: Int,
		val width: Int,
		val height: Int,
		val onClick: (button: Int) -> Unit
	) {
		fun contains(mouseX: Double, mouseY: Double): Boolean =
			mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
	}

	private data class SlotHitbox(
		val slot: Int,
		val x: Int,
		val y: Int,
		val width: Int,
		val height: Int
	) {
		fun contains(mouseX: Double, mouseY: Double): Boolean =
			mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
	}

	private data class Bounds(
		val x: Int,
		val y: Int,
		val width: Int,
		val height: Int
	) {
		fun contains(mouseX: Double, mouseY: Double): Boolean =
			mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
	}

	private object Colours {
		const val BACKGROUND = 0xF61C1C1C.toInt()
		const val SLOT_FILL = 0xFF161616.toInt()
		const val HOVER_FILL = 0xFF303030.toInt()
		const val SELECTED_FILL = 0xFF3C3C3C.toInt()
		const val BUTTON_FILL = 0xFF222222.toInt()
		const val OUTLINE = 0xFF505050.toInt()
		const val SELECTED = 0xFF7B3FC4.toInt()
		const val TEXT = 0xFFFFFFFF.toInt()
	}

	private companion object {
		private const val SLOT_SIZE = 20
		private const val SLOT_STEP = 22
		private const val HOTBAR_GAP = 10
		private const val GRID_WIDTH = 9 * SLOT_STEP - (SLOT_STEP - SLOT_SIZE)
		private const val GRID_HEIGHT = 4 * SLOT_SIZE + 3 * (SLOT_STEP - SLOT_SIZE) + HOTBAR_GAP
		private val LINE_COLOURS = intArrayOf(
			0xFF72C7FF.toInt(),
			0xFFFFD166.toInt(),
			0xFFFF6B8A.toInt(),
			0xFF7AE582.toInt(),
			0xFFC792EA.toInt()
		)
	}
}
