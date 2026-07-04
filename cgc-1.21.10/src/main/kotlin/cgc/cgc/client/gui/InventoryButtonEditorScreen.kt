package cgc.cgc.client.gui

import cgc.cgc.config.CgcConfigStore
import cgc.cgc.module.setting.InventoryButtonListSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max

class InventoryButtonEditorScreen(
	private val setting: InventoryButtonListSetting,
	player: Player,
	private val returnScreen: Screen? = null
) : InventoryScreen(player) {
	private val hitboxes = mutableListOf<UiHitbox>()
	private var editingButtonId: String? = null
	private var focusedField: InventoryButtonListSetting.Field? = null
	private var editorX = 0
	private var editorY = 0
	private var draggingButtonId: String? = null
	private var dragOffsetX = 0
	private var dragOffsetY = 0
	private var pressedX = 0.0
	private var pressedY = 0.0
	private var movedDrag = false

	override fun extractRenderState(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		hitboxes.clear()
		super.extractRenderState(gfx, mouseX, mouseY, partialTick)
		renderButtons(gfx, mouseX, mouseY)
		renderEditor(gfx)
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

		return when (click.button()) {
			0 -> {
				val button = buttonAt(mouseX, mouseY)
				if (button != null) {
					draggingButtonId = button.id
					dragOffsetX = (mouseX - button.x).toInt()
					dragOffsetY = (mouseY - button.y).toInt()
					pressedX = mouseX
					pressedY = mouseY
					movedDrag = false
				}
				focusedField = null
				true
			}
			1 -> {
				val button = buttonAt(mouseX, mouseY)
					?: setting.addButtonAt(
						mouseX.toInt(),
						mouseY.toInt(),
						width - InventoryButtonListSetting.BUTTON_SIZE,
						height - InventoryButtonListSetting.BUTTON_SIZE
					)
				openEditor(button, mouseX.toInt(), mouseY.toInt())
				CgcConfigStore.saveAll()
				true
			}
			else -> true
		}
	}

	override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		val button = draggingButton() ?: return true
		if (click.button() != 0) {
			return true
		}

		if (abs(click.x() - pressedX) > DRAG_THRESHOLD || abs(click.y() - pressedY) > DRAG_THRESHOLD) {
			movedDrag = true
		}
		if (movedDrag) {
			setting.moveButton(
				button,
				(click.x() - dragOffsetX).toInt(),
				(click.y() - dragOffsetY).toInt(),
				width - InventoryButtonListSetting.BUTTON_SIZE,
				height - InventoryButtonListSetting.BUTTON_SIZE
			)
			if (editingButtonId == button.id) {
				placeEditor(button.x + InventoryButtonListSetting.BUTTON_SIZE + 6, button.y)
			}
		}
		return true
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean {
		if (draggingButtonId != null) {
			draggingButtonId = null
			if (movedDrag) {
				CgcConfigStore.saveAll()
			}
			movedDrag = false
			return true
		}
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val field = focusedField ?: return super.charTyped(event)
		val typed = event.codepointAsString().firstOrNull() ?: return true
		if (!typed.isISOControl()) {
			editingButton()?.let { button ->
				setting.setField(button, field, setting.fieldValue(button, field) + typed)
			}
		}
		return true
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		val field = focusedField ?: return super.keyPressed(input)
		val button = editingButton() ?: run {
			focusedField = null
			return true
		}

		return when (input.key()) {
			GLFW.GLFW_KEY_ESCAPE,
			GLFW.GLFW_KEY_ENTER -> {
				focusedField = null
				true
			}
			GLFW.GLFW_KEY_TAB -> {
				focusedField = if (field == InventoryButtonListSetting.Field.COMMAND) {
					InventoryButtonListSetting.Field.ICON
				} else {
					InventoryButtonListSetting.Field.COMMAND
				}
				true
			}
			GLFW.GLFW_KEY_BACKSPACE -> {
				setting.setField(button, field, setting.fieldValue(button, field).dropLast(1))
				true
			}
			GLFW.GLFW_KEY_DELETE -> {
				setting.setField(button, field, "")
				true
			}
			else -> super.keyPressed(input)
		}
	}

	override fun onClose() {
		draggingButtonId = null
		focusedField = null
		CgcConfigStore.saveAll()
		val client = Minecraft.getInstance()
		if (returnScreen != null) {
			client.setScreen(returnScreen)
		} else {
			super.onClose()
		}
	}

	private fun renderButtons(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		gfx.nextStratum()
		for (button in setting.value) {
			clampButton(button)
			val hovered = contains(button, mouseX.toDouble(), mouseY.toDouble())
			val editing = editingButtonId == button.id
			val dragging = draggingButtonId == button.id
			val fill = when {
				dragging -> BUTTON_ACTIVE
				hovered || editing -> BUTTON_HOVER
				else -> BUTTON_FILL
			}
			gfx.fill(button.x, button.y, button.x + InventoryButtonListSetting.BUTTON_SIZE, button.y + InventoryButtonListSetting.BUTTON_SIZE, fill)
			outline(
				gfx,
				button.x,
				button.y,
				InventoryButtonListSetting.BUTTON_SIZE,
				InventoryButtonListSetting.BUTTON_SIZE,
				if (hovered || editing || dragging) BUTTON_SELECTED_OUTLINE else BUTTON_OUTLINE
			)

			val stack = iconStack(button.iconItem)
			if (!stack.isEmpty) {
				gfx.item(stack, button.x + 6, button.y + 6)
			}

			if (hovered) {
				val tooltip = button.command.ifBlank { button.label.ifBlank { "Inventory Button" } }
				gfx.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY)
			}
		}
	}

	private fun renderEditor(gfx: GuiGraphicsExtractor) {
		val button = editingButton() ?: return
		placeEditor(editorX, editorY)

		gfx.fill(editorX, editorY, editorX + EDITOR_WIDTH, editorY + EDITOR_HEIGHT, PANEL)
		outline(gfx, editorX, editorY, EDITOR_WIDTH, EDITOR_HEIGHT, BUTTON_SELECTED_OUTLINE)
		hitboxes.add(UiHitbox(editorX, editorY, EDITOR_WIDTH, EDITOR_HEIGHT) {})

		gfx.text(font(), "Button", editorX + 8, editorY + 8, TEXT, false)
		renderField(gfx, button, InventoryButtonListSetting.Field.COMMAND, "Command", editorX + 8, editorY + 26)
		renderField(gfx, button, InventoryButtonListSetting.Field.ICON, "Icon", editorX + 8, editorY + 51)

		button(gfx, editorX + 8, editorY + EDITOR_HEIGHT - 24, 52, 18, "Delete") { mouseButton ->
			if (mouseButton == 0) {
				setting.removeButton(button)
				editingButtonId = null
				focusedField = null
				CgcConfigStore.saveAll()
			}
		}
		button(gfx, editorX + EDITOR_WIDTH - 55, editorY + EDITOR_HEIGHT - 24, 47, 18, "Done") { mouseButton ->
			if (mouseButton == 0) {
				editingButtonId = null
				focusedField = null
				CgcConfigStore.saveAll()
			}
		}
	}

	private fun renderField(
		gfx: GuiGraphicsExtractor,
		button: InventoryButtonListSetting.Button,
		field: InventoryButtonListSetting.Field,
		label: String,
		x: Int,
		y: Int
	) {
		val labelWidth = if (field == InventoryButtonListSetting.Field.COMMAND) 56 else 31
		gfx.text(font(), label, x, y + 6, MUTED_TEXT, false)
		val inputX = x + labelWidth
		val inputWidth = EDITOR_WIDTH - labelWidth - 16
		val focused = focusedField == field
		gfx.fill(inputX, y, inputX + inputWidth, y + FIELD_HEIGHT, if (focused) FIELD_ACTIVE else FIELD_FILL)
		outline(gfx, inputX, y, inputWidth, FIELD_HEIGHT, if (focused) BUTTON_SELECTED_OUTLINE else OUTLINE)
		val value = setting.fieldValue(button, field) + if (focused) "|" else ""
		gfx.text(font(), fit(font(), value, inputWidth - 8), inputX + 4, y + 5, TEXT, false)
		hitboxes.add(UiHitbox(inputX, y, inputWidth, FIELD_HEIGHT) { mouseButton ->
			if (mouseButton == 0) {
				focusedField = field
			}
		})
	}

	private fun button(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, label: String, click: (Int) -> Unit) {
		gfx.fill(x, y, x + width, y + height, FIELD_FILL)
		outline(gfx, x, y, width, height, OUTLINE)
		gfx.centeredText(font(), label, x + width / 2, y + 5, TEXT)
		hitboxes.add(UiHitbox(x, y, width, height, click))
	}

	private fun openEditor(button: InventoryButtonListSetting.Button, mouseX: Int, mouseY: Int) {
		editingButtonId = button.id
		focusedField = InventoryButtonListSetting.Field.COMMAND
		placeEditor(mouseX + 8, mouseY + 8)
	}

	private fun placeEditor(x: Int, y: Int) {
		editorX = x.coerceIn(4, max(4, width - EDITOR_WIDTH - 4))
		editorY = y.coerceIn(4, max(4, height - EDITOR_HEIGHT - 4))
	}

	private fun editingButton(): InventoryButtonListSetting.Button? {
		val id = editingButtonId ?: return null
		return setting.value.firstOrNull { it.id == id }
	}

	private fun draggingButton(): InventoryButtonListSetting.Button? {
		val id = draggingButtonId ?: return null
		return setting.value.firstOrNull { it.id == id }
	}

	private fun buttonAt(mouseX: Double, mouseY: Double): InventoryButtonListSetting.Button? =
		setting.value.asReversed().firstOrNull { contains(it, mouseX, mouseY) }

	private fun contains(button: InventoryButtonListSetting.Button, mouseX: Double, mouseY: Double): Boolean =
		mouseX >= button.x &&
			mouseX <= button.x + InventoryButtonListSetting.BUTTON_SIZE &&
			mouseY >= button.y &&
			mouseY <= button.y + InventoryButtonListSetting.BUTTON_SIZE

	private fun clampButton(button: InventoryButtonListSetting.Button) {
		button.x = button.x.coerceIn(0, max(0, width - InventoryButtonListSetting.BUTTON_SIZE))
		button.y = button.y.coerceIn(0, max(0, height - InventoryButtonListSetting.BUTTON_SIZE))
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

	private fun fit(font: Font, text: String, maxWidth: Int): String {
		if (font.width(text) <= maxWidth) return text
		if (maxWidth <= font.width("...")) return ""
		return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "..."
	}

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

	private companion object {
		private const val FIELD_HEIGHT = 18
		private const val EDITOR_WIDTH = 220
		private const val EDITOR_HEIGHT = 100
		private const val DRAG_THRESHOLD = 3.0

		private const val PANEL = 0xF61C1C1C.toInt()
		private const val FIELD_FILL = 0xFF222222.toInt()
		private const val FIELD_ACTIVE = 0xFF303744.toInt()
		private const val OUTLINE = 0xFF505050.toInt()
		private const val BUTTON_FILL = 0xFF202733.toInt()
		private const val BUTTON_HOVER = 0xFF2D3B4E.toInt()
		private const val BUTTON_ACTIVE = 0xFF3A5877.toInt()
		private const val BUTTON_OUTLINE = 0xFFFFFFFF.toInt()
		private const val BUTTON_SELECTED_OUTLINE = 0xFF55AAFF.toInt()
		private const val TEXT = 0xFFFFFFFF.toInt()
		private const val MUTED_TEXT = 0xFFB8C0CC.toInt()
	}
}
