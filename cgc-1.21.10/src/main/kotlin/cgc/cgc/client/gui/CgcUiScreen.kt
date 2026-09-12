package cgc.cgc.client.gui

import cgc.cgc.config.CgcConfigStore
import cgc.cgc.module.CgcModule
import cgc.cgc.module.CgcModules
import cgc.cgc.module.setting.DragSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.joml.Vector2d
import kotlin.math.max
import kotlin.math.roundToInt

class CgcUiScreen : Screen(Component.literal("CGC UI")) {
	private var selected: DragSetting? = null

	override fun extractRenderState(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		updateDragged(mouseX, mouseY)
		renderTargets(gfx, mouseX, mouseY)
		super.extractRenderState(gfx, mouseX, mouseY, partialTick)
	}

	override fun extractBackground(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		if (click.button() != 0) {
			return super.mouseClicked(click, doubled)
		}

		for (target in targets().asReversed()) {
			val bounds = target.bounds()
			if (!bounds.contains(click.x(), click.y())) {
				continue
			}

			selected?.dragging = false
			selected = target.setting
			target.setting.dragging = true
			target.setting.dragPos = Vector2d(click.x() - bounds.x, click.y() - bounds.y)
			return true
		}

		selected?.dragging = false
		selected = null
		return super.mouseClicked(click, doubled)
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean {
		selected?.dragging = false
		selected = null
		return true
	}

	override fun onClose() {
		selected?.dragging = false
		selected = null
		CgcConfigStore.saveAll()
		super.onClose()
	}

	override fun isPauseScreen(): Boolean = false

	private fun updateDragged(mouseX: Int, mouseY: Int) {
		val setting = selected?.takeIf { it.dragging } ?: return
		val width = setting.width()
		val height = setting.height()
		val nextX = (mouseX - setting.dragPos.x).coerceIn(0.0, max(0, this.width - width).toDouble())
		val nextY = (mouseY - setting.dragPos.y).coerceIn(0.0, max(0, this.height - height).toDouble())
		setting.value = Vector2d(nextX, nextY)
	}

	private fun renderTargets(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val font = Minecraft.getInstance().font
		val allTargets = targets()
		if (allTargets.isEmpty()) {
			gfx.centeredText(font, "No movable UI elements", width / 2, height / 2, TEXT)
			return
		}

		for (target in allTargets) {
			val bounds = target.bounds()
			val hovered = bounds.contains(mouseX.toDouble(), mouseY.toDouble())
			val active = target.setting == selected
			val outline = when {
				active -> ACTIVE_OUTLINE
				hovered -> HOVER_OUTLINE
				else -> OUTLINE
			}
			gfx.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, FILL)
			drawOutline(gfx, bounds.x, bounds.y, bounds.width, bounds.height, outline)
			gfx.centeredText(font, fit(font, target.label, bounds.width - 8), bounds.x + bounds.width / 2, bounds.y + 5, TEXT)
		}
	}

	private fun targets(): List<DragTarget> =
		CgcModules.manager.all()
			.filter { it.visibleInGui }
			.flatMap { module -> module.getDragSettings().map { DragTarget(module, it) } }

	private fun drawOutline(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, colour: Int) {
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

	private data class DragTarget(
		val module: CgcModule,
		val setting: DragSetting
	) {
		val label: String = "${module.displayName}: ${setting.name}"

		fun bounds(): UiBounds =
			UiBounds(
				setting.position.x.roundToInt(),
				setting.position.y.roundToInt(),
				setting.width(),
				setting.height()
			)
	}

	private data class UiBounds(
		val x: Int,
		val y: Int,
		val width: Int,
		val height: Int
	) {
		fun contains(mouseX: Double, mouseY: Double): Boolean =
			mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
	}

	private companion object {
		private const val FILL = 0x66000000
		private const val OUTLINE = 0x99FFFFFF.toInt()
		private const val HOVER_OUTLINE = 0xFFFFFFFF.toInt()
		private const val ACTIVE_OUTLINE = 0xFF55AAFF.toInt()
		private const val TEXT = 0xFFFFFFFF.toInt()

		private fun DragSetting.width(): Int =
			max(24, scale.x.roundToInt())

		private fun DragSetting.height(): Int =
			max(18, scale.y.roundToInt())
	}
}
