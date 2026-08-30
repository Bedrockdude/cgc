package cgc.cgc.client.gui

import cgc.cgc.config.CgcConfigStore
import cgc.cgc.config.CgcSettings
import cgc.cgc.module.CgcModule
import cgc.cgc.module.CgcModules
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ButtonSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.HotbarSwapListSetting
import cgc.cgc.module.setting.HotbarSwapTrigger
import cgc.cgc.module.setting.HotbarSwapType
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.MultiBoolSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.SaveSetting
import cgc.cgc.module.setting.Setting
import cgc.cgc.module.setting.SoundSetting
import cgc.cgc.module.setting.StringSetting
import cgc.cgc.module.setting.group.GroupSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CgcConfigScreen : Screen(Component.literal("CGC Config")) {
	private val panel = RsmStylePanel()
	private val openedAt = System.currentTimeMillis()

	override fun extractRenderState(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		val progress = if (CgcSettings.openAnimation.value) {
			val elapsed = (System.currentTimeMillis() - openedAt).coerceAtLeast(0L)
			easeOutCubic(min(1.0f, elapsed / 250.0f))
		} else {
			1.0f
		}

		panel.render(gfx, width, height, mouseX, mouseY, progress)
		super.extractRenderState(gfx, mouseX, mouseY, partialTick)
	}

	override fun extractBackground(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean =
		panel.mouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled)

	override fun mouseReleased(click: MouseButtonEvent): Boolean =
		panel.mouseReleased(click.x(), click.y(), click.button()) || super.mouseReleased(click)

	override fun mouseScrolled(mouseX: Double, mouseY: Double, hScroll: Double, vScroll: Double): Boolean =
		panel.mouseScrolled(mouseX, mouseY, vScroll) || super.mouseScrolled(mouseX, mouseY, hScroll, vScroll)

	override fun charTyped(event: CharacterEvent): Boolean {
		val typed = event.codepointAsString().firstOrNull() ?: return super.charTyped(event)
		return panel.charTyped(typed, event.codepoint()) || super.charTyped(event)
	}

	override fun keyPressed(input: KeyEvent): Boolean =
		panel.keyPressed(input) || super.keyPressed(input)

	override fun onClose() {
		panel.saveSessionState()
		CgcModules.manager.all().forEach { it.onGuiClosed() }
		CgcConfigStore.saveAll()
		super.onClose()
	}

	override fun isPauseScreen(): Boolean = false

	private fun easeOutCubic(value: Float): Float {
		val t = 1.0f - value
		return 1.0f - t * t * t
	}
}

private class RsmStylePanel {
	private val hitboxes = mutableListOf<Hitbox>()
	private val categoryHover = mutableMapOf<ModuleCategory, Float>()
	private val categoryOpen = mutableMapOf<ModuleCategory, Float>()
	private val moduleHover = mutableMapOf<String, Float>()
	private val moduleSelect = mutableMapOf<String, Float>()
	private val moduleToggle = mutableMapOf<String, Float>()
	private val groupHover = mutableMapOf<String, Float>()
	private val groupSelect = mutableMapOf<String, Float>()
	private val selectedModules = mutableMapOf<ModuleCategory, CgcModule>()
	private val selectedGroups = mutableMapOf<String, GroupSetting<*>>()
	private val settingsScroll = mutableMapOf<String, Double>()
	private val modeDropdownScroll = mutableMapOf<String, Double>()
	private val hotbarTriggerScroll = mutableMapOf<String, Double>()
	private val expandedCategories = linkedSetOf<ModuleCategory>()
	private val modulesByCategory = mutableMapOf<ModuleCategory, List<CgcModule>>()

	private var selectedCategory = ModuleCategory.MOVEMENT
	private var search = ""
	private var cachedSearch = ""
	private var cachedSearchResults: Map<ModuleCategory, List<CgcModule>> = emptyMap()
	private var searchCacheValid = false
	private var leftScroll = 0.0
	private var writingSearch = false
	private var focusedString: StringSetting? = null
	private var focusedSave: SaveSetting<*>? = null
	private var waitingKeybind: KeybindSetting? = null
	private var waitingHotbarSwapKey: WaitingHotbarSwapKey? = null
	private var draggingNumber: NumberSetting? = null
	private var draggingColour: ColourDrag? = null
	private var expandedSettingKey: String? = null
	private var initialized = false
	private var leftBounds = Bounds.ZERO
	private var settingsBounds = Bounds.ZERO
	private var modeDropdownKey: String? = null
	private var modeDropdownBounds = Bounds.ZERO
	private var modeDropdownMaxScroll = 0.0
	private var hotbarTriggerDropdownKey: String? = null
	private var hotbarTriggerDropdownBounds = Bounds.ZERO
	private var hotbarTriggerDropdownMaxScroll = 0.0
	private var panelX = 0
	private var panelY = 0
	private var panelWidthPixels = WIDTH
	private var panelHeightPixels = HEIGHT

	fun render(gfx: GuiGraphicsExtractor, screenWidth: Int, screenHeight: Int, mouseX: Int, mouseY: Int, progress: Float) {
		initializeState()
		hitboxes.clear()
		modeDropdownKey = null
		modeDropdownBounds = Bounds.ZERO
		modeDropdownMaxScroll = 0.0
		hotbarTriggerDropdownKey = null
		hotbarTriggerDropdownBounds = Bounds.ZERO
		hotbarTriggerDropdownMaxScroll = 0.0

		panelWidthPixels = min(WIDTH, max(MIN_WIDTH, screenWidth - 24))
		panelHeightPixels = min(HEIGHT, max(MIN_HEIGHT, screenHeight - 24))
		panelX = (screenWidth - panelWidthPixels) / 2
		panelY = ((screenHeight - panelHeightPixels) / 2 + ((1.0f - progress) * -18.0f)).roundToInt()

		drawShell(gfx, panelX, panelY, panelWidthPixels, panelHeightPixels, progress)
		renderSearch(gfx, panelX, panelY, mouseX, mouseY)
		renderLeftNavigation(gfx, panelX, panelY, panelHeightPixels, mouseX, mouseY)
		renderSelectedModule(gfx, panelX, panelY, mouseX, mouseY)
	}

	fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
		waitingKeybind?.let { setting ->
			if (button != 0) {
				setting.value.keyName = InputConstants.Type.MOUSE.getOrCreate(button).name
				setting.onEdit()
				waitingKeybind = null
				return true
			}
		}
		waitingHotbarSwapKey?.let { waiting ->
			if (button != 0) {
				waiting.swap.keybind.keyName = InputConstants.Type.MOUSE.getOrCreate(button).name
				waiting.setting.onEdit()
				waitingHotbarSwapKey = null
				return true
			}
		}

		for (hitbox in hitboxes.asReversed()) {
			if (hitbox.contains(mouseX, mouseY)) {
				hitbox.onClick(button)
				return true
			}
		}

		writingSearch = false
		focusedString = null
		focusedSave = null
		waitingHotbarSwapKey = null
		if (button == 0) expandedSettingKey = null
		return false
	}

	fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
		if (draggingNumber != null) {
			draggingNumber = null
			return true
		}
		if (draggingColour != null) {
			draggingColour = null
			return true
		}
		return false
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		val delta = -amount * 23.0
		val modeKey = modeDropdownKey
		if (modeKey != null && modeDropdownBounds.contains(mouseX, mouseY)) {
			val current = modeDropdownScroll.getOrDefault(modeKey, 0.0)
			modeDropdownScroll[modeKey] = (current - amount * MODE_OPTION_HEIGHT).coerceIn(0.0, modeDropdownMaxScroll)
			return true
		}

		val dropdownKey = hotbarTriggerDropdownKey
		if (dropdownKey != null && hotbarTriggerDropdownBounds.contains(mouseX, mouseY)) {
			val current = hotbarTriggerScroll.getOrDefault(dropdownKey, 0.0)
			hotbarTriggerScroll[dropdownKey] = (current + delta).coerceIn(0.0, hotbarTriggerDropdownMaxScroll)
			return true
		}

		return when {
			leftBounds.contains(mouseX, mouseY) -> {
				leftScroll = max(0.0, leftScroll + delta)
				true
			}
			settingsBounds.contains(mouseX, mouseY) -> {
				val key = selectedSettingScrollKey()
				val current = settingsScroll.getOrDefault(key, 0.0)
				settingsScroll[key] = max(0.0, current + delta)
				true
			}
			else -> false
		}
	}

	fun charTyped(typedChar: Char, keyCode: Int): Boolean {
		if (writingSearch) {
			if (!typedChar.isISOControl()) search = (search + typedChar).take(42)
			return true
		}

		focusedString?.let { setting ->
			if (!typedChar.isISOControl()) setting.setText(setting.value + typedChar)
			return true
		}

		focusedSave?.let { setting ->
			if (!typedChar.isISOControl()) setting.setFileName(setting.fileName + typedChar)
			return true
		}

		return false
	}

	fun keyPressed(input: KeyEvent): Boolean {
		waitingKeybind?.let { setting ->
			val key = InputConstants.getKey(input)
			setting.value.keyName = if (key.value == 0 || key.value == InputConstants.KEY_ESCAPE) {
				InputConstants.UNKNOWN.name
			} else {
				key.name
			}
			setting.onEdit()
			waitingKeybind = null
			return true
		}
		waitingHotbarSwapKey?.let { waiting ->
			val key = InputConstants.getKey(input)
			waiting.swap.keybind.keyName = if (key.value == 0 || key.value == InputConstants.KEY_ESCAPE) {
				InputConstants.UNKNOWN.name
			} else {
				key.name
			}
			waiting.setting.onEdit()
			waitingHotbarSwapKey = null
			return true
		}

		if (writingSearch) {
			return handleTextKey(input, search, allowBlank = true) { search = it }
		}

		focusedString?.let { setting ->
			return handleTextKey(input, setting.value, allowBlank = setting.allowBlank) { setting.setText(it) }
		}

		focusedSave?.let { setting ->
			return handleTextKey(input, setting.fileName, allowBlank = true) { setting.setFileName(it) }
		}

		return false
	}

	private fun initializeState() {
		if (initialized) return
		initialized = true

		val fallbackCategory = ModuleCategory.entries.firstOrNull { CgcModules.manager.byCategory(it).isNotEmpty() } ?: ModuleCategory.MOVEMENT
		for (category in ModuleCategory.entries) {
			val modules = CgcModules.manager.byCategory(category).sortedBy { it.displayName.lowercase(Locale.ROOT) }
			modulesByCategory[category] = modules
		}
		applySessionState(fallbackCategory)
	}

	fun saveSessionState() {
		SessionState.selectedCategory = selectedCategory
		SessionState.search = search
		SessionState.leftScroll = leftScroll
		SessionState.selectedModules = selectedModules.mapValues { it.value.id }.toMutableMap()
		SessionState.selectedGroups = selectedGroups.mapValues { it.value.name }.toMutableMap()
		SessionState.expandedCategories = expandedCategories.toMutableSet()
		SessionState.settingsScroll = settingsScroll.toMutableMap()
	}

	private fun applySessionState(fallbackCategory: ModuleCategory) {
		selectedCategory = SessionState.selectedCategory
			?.takeIf { modulesByCategory[it].orEmpty().isNotEmpty() }
			?: fallbackCategory
		search = SessionState.search
		leftScroll = SessionState.leftScroll
		settingsScroll.putAll(SessionState.settingsScroll)

		for ((category, modules) in modulesByCategory) {
			val savedId = SessionState.selectedModules[category]
			selectedModules[category] = modules.firstOrNull { it.id == savedId } ?: modules.firstOrNull() ?: continue
		}

		for ((moduleId, groupName) in SessionState.selectedGroups) {
			val module = CgcModules.manager.get(moduleId) ?: continue
			val group = module.getShownSettings().firstOrNull { it.name.equals(groupName, ignoreCase = true) } ?: continue
			selectedGroups[moduleId] = group
		}

		expandedCategories.clear()
		val savedExpanded = SessionState.expandedCategories
			.filterTo(linkedSetOf()) { modulesByCategory[it].orEmpty().isNotEmpty() }
		if (search.isNotBlank()) {
			expandedCategories.addAll(modulesByCategory.keys.filter { modulesByCategory[it].orEmpty().isNotEmpty() })
		} else if (savedExpanded.isNotEmpty()) {
			expandedCategories.addAll(savedExpanded)
		} else {
			expandedCategories.add(selectedCategory)
		}
	}

	private fun drawShell(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, progress: Float) {
		fill(gfx, x, y, x + width, y + height, surface(Colours.BACKGROUND, progress), applyOpacity = false)
		drawRectOutline(gfx, x, y, width, height, Colours.GROUP_OUTLINE)

		val contentY = y + 50
		fill(gfx, x, contentY, x + width, y + height - 25, surface(Colours.PANEL, progress), applyOpacity = false)

		fill(gfx, x, contentY, x + width, contentY + 1, Colours.LINE)
		fill(gfx, x + LEFT_WIDTH - 1, contentY + 1, x + LEFT_WIDTH, y + height - 25, Colours.LINE)
		fill(gfx, x, y + height - 25, x + width, y + height - 24, Colours.LINE)
		gfx.text(font(), "CGC", x + 20, y + 20, Colours.TEXT, false)
	}

	private fun renderSearch(gfx: GuiGraphicsExtractor, x: Int, y: Int, mouseX: Int, mouseY: Int) {
		val searchX = x + 16
		val searchY = y + 67
		val hovered = Bounds(searchX, searchY, 94, 25).contains(mouseX.toDouble(), mouseY.toDouble())
		val fill = if (hovered || writingSearch) Colours.SEARCH_FILL_HOVER else Colours.SEARCH_FILL

		fill(gfx, searchX, searchY, searchX + 94, searchY + 25, fill)
		drawRectOutline(gfx, searchX, searchY, 94, 25, Colours.SEARCH_OUTLINE)

		val display = if (search.isBlank() && !writingSearch) "Search" else search + if (writingSearch) "|" else ""
		gfx.text(font(), fit(font(), display, 80), searchX + 8, searchY + 9, if (search.isBlank() && !writingSearch) Colours.UNSELECTED_TEXT else Colours.TEXT, false)

		hitboxes.add(Hitbox(searchX, searchY, 94, 25) { button ->
			if (button == 0) {
				writingSearch = true
				focusedString = null
				focusedSave = null
				waitingKeybind = null
				waitingHotbarSwapKey = null
			}
		})
	}

	private fun renderLeftNavigation(gfx: GuiGraphicsExtractor, x: Int, y: Int, height: Int, mouseX: Int, mouseY: Int) {
		val navX = x
		val navY = y + 104
		val navBottom = y + height - 70
		leftBounds = Bounds(navX, navY - 8, LEFT_WIDTH, navBottom - navY + 8)

		val filtered = filteredModulesByCategory()
		enableScissor(gfx, navX, navY - 8, navX + LEFT_WIDTH, navBottom)

		var cursorY = navY - leftScroll.roundToInt()
		for (category in ModuleCategory.entries) {
			cursorY = renderCategoryDropdown(gfx, category, filtered[category].orEmpty(), navX, cursorY, navY - 8, navBottom, mouseX, mouseY)
		}

		val contentHeight = cursorY - (navY - leftScroll.roundToInt())
		leftScroll = leftScroll.coerceIn(0.0, max(0, contentHeight - (navBottom - navY)).toDouble())
		gfx.disableScissor()

		val maxScroll = max(0, contentHeight - (navBottom - navY))
		if (maxScroll > 0 && leftBounds.contains(mouseX.toDouble(), mouseY.toDouble())) {
			val trackHeight = navBottom - navY
			val barHeight = max(24, (trackHeight.toDouble() / contentHeight * trackHeight).roundToInt())
			val barY = navY + ((trackHeight - barHeight) * (leftScroll / maxScroll)).roundToInt()
			fill(gfx, x + 7, barY, x + 9, barY + barHeight, Colours.SCROLL_BAR)
		}

		renderSettingsButton(gfx, x, y + height - 55, mouseX, mouseY)
	}

	private fun renderSettingsButton(gfx: GuiGraphicsExtractor, x: Int, y: Int, mouseX: Int, mouseY: Int) {
		val module = CgcModules.manager.get("ClickGUI") ?: return
		val buttonX = x + 16
		val buttonY = y
		val hovered = Bounds(buttonX - 7, buttonY - 6, LEFT_WIDTH - 28, 24).contains(mouseX.toDouble(), mouseY.toDouble())
		val selected = selectedModules[module.category] == module
		val hoverValue = animate(moduleHover, "settings-button", if (hovered || selected) 1.0f else 0.0f)

		if (selected) fill(gfx, buttonX - 8, buttonY - 1, buttonX - 6, buttonY + 15, Colours.SELECTED)
		gfx.text(font(), "Settings", buttonX, buttonY + 2, blend(Colours.UNSELECTED_TEXT, Colours.SELECTED_TEXT, hoverValue), false)

		hitboxes.add(Hitbox(buttonX - 7, buttonY - 6, LEFT_WIDTH - 28, 24) { button ->
			if (button == 0) {
				selectedCategory = module.category
				selectedModules[module.category] = module
				expandedCategories.add(module.category)
				writingSearch = false
				expandedSettingKey = null
			}
		})
	}

	private fun renderCategoryDropdown(
		gfx: GuiGraphicsExtractor,
		category: ModuleCategory,
		modules: List<CgcModule>,
		x: Int,
		startY: Int,
		clipTop: Int,
		clipBottom: Int,
		mouseX: Int,
		mouseY: Int
	): Int {
		val font = font()
		val rowX = x + 16
		val rowWidth = LEFT_WIDTH - 28
		val expanded = category in expandedCategories || search.isNotBlank()
		val selected = selectedCategory == category
		var y = startY

		val hovered = Bounds(rowX - 7, y - 6, rowWidth, CATEGORY_HIT_HEIGHT).contains(mouseX.toDouble(), mouseY.toDouble())
		val hoverValue = animate(categoryHover, category, if (hovered || selected || expanded) 1.0f else 0.0f)
		val openValue = animate(categoryOpen, category, if (expanded) 1.0f else 0.0f)

		if (isVisible(y - 6, CATEGORY_HIT_HEIGHT, clipTop, clipBottom)) {
			if (selected) fill(gfx, rowX - 8, y - 2, rowX - 6, y + 14, Colours.SELECTED)
			gfx.text(font, if (expanded) "v" else ">", rowX + 1, y + 1, Colours.UNSELECTED_TEXT, false)
			gfx.text(font, category.displayName, rowX + 18, y + 1, blend(Colours.UNSELECTED_TEXT, Colours.SELECTED_TEXT, hoverValue), false)
			hitboxes.add(Hitbox(rowX - 7, y - 6, rowWidth, CATEGORY_HIT_HEIGHT) { button ->
				if (button == 0) {
					selectedCategory = category
					if (category in expandedCategories) {
						expandedCategories.remove(category)
					} else {
						expandedCategories.add(category)
						(modules.firstOrNull() ?: modulesByCategory[category]?.firstOrNull())?.let { selectedModules[category] = it }
					}
					expandedSettingKey = null
				}
			})
		}

		y += CATEGORY_STEP
		if (openValue <= 0.02f) return y

		val shownHeight = ((max(1, modules.size) * MODULE_STEP) * openValue).roundToInt()
		if (modules.isEmpty()) {
			if (shownHeight > MODULE_STEP / 2 && isVisible(y, MODULE_HIT_HEIGHT, clipTop, clipBottom)) {
				gfx.text(font, if (search.isBlank()) "No modules" else "No matches", rowX + 20, y + 1, Colours.UNSELECTED_TEXT, false)
			}
		} else {
			val moduleStartY = y
			for (module in modules) {
				val offset = y - moduleStartY
				if (offset + MODULE_HIT_HEIGHT <= shownHeight && isVisible(y, MODULE_HIT_HEIGHT, clipTop, clipBottom)) {
					renderModuleLine(gfx, module, rowX + 18, y, rowWidth - 14, mouseX, mouseY)
				}
				y += MODULE_STEP
			}
		}

		return startY + CATEGORY_STEP + shownHeight + 4
	}

	private fun renderSelectedModule(gfx: GuiGraphicsExtractor, x: Int, y: Int, mouseX: Int, mouseY: Int) {
		val selected = selectedModules[selectedCategory] ?: modulesByCategory[selectedCategory]?.firstOrNull()
		if (selected != null) selectedModules[selectedCategory] = selected
		renderModule(gfx, x, y, selected, mouseX, mouseY)
	}

	private fun renderModuleLine(gfx: GuiGraphicsExtractor, module: CgcModule, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) {
		val font = font()
		val selected = selectedCategory == module.category && selectedModules[module.category] == module
		val textWidth = font.width(module.displayName) + 10
		val hovered = Bounds(x, y - 8, min(width, textWidth + 10), 20).contains(mouseX.toDouble(), mouseY.toDouble())
		val hoverValue = animate(moduleHover, module.id, if (hovered || selected) 1.0f else 0.0f)
		val selectValue = animate(moduleSelect, module.id, if (selected) 1.0f else 0.0f)
		val toggleValue = animate(moduleToggle, module.id, if (module.enabled) 1.0f else 0.0f)

		if (toggleValue > 0.02f) {
			fill(gfx, x - 1, y - 3, x + min(width, textWidth + 2), y + 13, withAlpha(Colours.ENABLED, (120 * toggleValue).roundToInt()))
		}

		gfx.text(
			font,
			fit(font, module.displayName, width - 4),
			x + 6,
			y,
			blend(if (module.enabled) Colours.ENABLED_TEXT else Colours.UNSELECTED_TEXT, Colours.SELECTED_TEXT, hoverValue),
			false
		)

		val markerHeight = (font.lineHeight * selectValue).roundToInt()
		if (markerHeight > 0) fill(gfx, x, y - 1, x + 2, y - 1 + markerHeight, Colours.SELECTED)

		hitboxes.add(Hitbox(x, y - 8, width, 20) { button ->
			if (button == CgcSettings.toggleMouseButton && module.toggleable) {
				module.toggle()
			} else if (module.settings.isNotEmpty()) {
				selectedCategory = module.category
				selectedModules[module.category] = module
				expandedCategories.add(module.category)
				expandedSettingKey = null
			}
		})
	}

	private fun renderModule(gfx: GuiGraphicsExtractor, x: Int, y: Int, module: CgcModule?, mouseX: Int, mouseY: Int) {
		val panelX = x + LEFT_WIDTH
		val panelY = y + 60
		val rightWidth = rightWidth()
		val rightHeight = rightHeight()
		fill(gfx, panelX, panelY, panelX + rightWidth, panelY + rightHeight, Colours.GROUP_FILL)
		drawRectOutline(gfx, panelX, panelY, rightWidth, rightHeight, Colours.GROUP_OUTLINE)
		fill(gfx, panelX + 5, panelY + 39, panelX + rightWidth - 5, panelY + 40, Colours.GROUP_OUTLINE)

		if (module == null) {
			gfx.text(font(), "No module selected", panelX + 18, panelY + 68, Colours.UNSELECTED_TEXT, false)
			return
		}

		val group = selectedGroups[module.id]?.takeIf { module.getShownSettings().contains(it) }
			?: module.getShownSettings().firstOrNull { it.name.equals("General", ignoreCase = true) }
			?: module.getShownSettings().firstOrNull()
		if (group != null) selectedGroups[module.id] = group
		renderGroupTabs(gfx, module, group, x, y, mouseX, mouseY)
		renderGroupSettings(gfx, module, group, x, y, mouseX, mouseY)
	}

	private fun renderGroupTabs(gfx: GuiGraphicsExtractor, module: CgcModule, selectedGroup: GroupSetting<*>?, x: Int, y: Int, mouseX: Int, mouseY: Int) {
		val font = font()
		var cursorX = x + LEFT_WIDTH + 18
		val tabY = y + 76

		for (group in module.getShownSettings()) {
			val key = "${module.id}:${group.name}"
			val selected = selectedGroup == group
			val tabWidth = font.width(group.name) + 12
			val hovered = Bounds(cursorX - 5, y + 68, tabWidth, 27).contains(mouseX.toDouble(), mouseY.toDouble())
			val enabled = group.name.equals("General", ignoreCase = true) || group.value.enabled
			val hoverValue = animate(groupHover, key, if (hovered || selected) 1.0f else 0.0f)
			val selectValue = animate(groupSelect, key, if (selected) 1.0f else 0.0f)

			if (hovered) fill(gfx, cursorX - 5, y + 68, cursorX + tabWidth, y + 94, Colours.SELECTED_BACKGROUND)
			gfx.text(font, group.name, cursorX, tabY, blend(if (enabled) Colours.ENABLED_TEXT else Colours.UNSELECTED_TEXT, Colours.SELECTED_TEXT, hoverValue), false)
			val underline = (font.width(group.name) * selectValue).roundToInt()
			if (underline > 0) fill(gfx, cursorX, y + 91, cursorX + underline, y + 93, Colours.SELECTED)

			hitboxes.add(Hitbox(cursorX - 5, y + 68, tabWidth, 27) { button ->
				if (button == CgcSettings.toggleMouseButton && !group.name.equals("General", ignoreCase = true)) {
					group.value.toggle()
				} else if (group.value.settings.isNotEmpty()) {
					selectedGroups[module.id] = group
					expandedSettingKey = null
				}
			})

			cursorX += font.width(group.name) + 15
		}
	}

	private fun renderGroupSettings(
		gfx: GuiGraphicsExtractor,
		module: CgcModule,
		group: GroupSetting<*>?,
		x: Int,
		y: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val startX = x + LEFT_WIDTH + 18
		val startY = y + 128
		val width = rightWidth() - 36
		val height = rightHeight() - 94
		settingsBounds = Bounds(startX - 8, startY - 12, width, height + 20)

		if (group == null) {
			gfx.text(font(), "No settings", startX, startY, Colours.UNSELECTED_TEXT, false)
			return
		}

		val settings = group.value.getShownSettings()
		val scrollKey = "${module.id}:${group.name}"
		val rows = settings.filter { it !is cgc.cgc.module.setting.DragSetting }
		if (rows.any { it is HotbarSwapListSetting }) {
			renderVariableGroupSettings(gfx, module, group, rows, scrollKey, startX, startY, width, height, mouseX, mouseY)
			return
		}

		val columns = max(1, (rows.size + SETTINGS_PER_COLUMN - 1) / SETTINGS_PER_COLUMN)
		val totalHeight = min(SETTINGS_PER_COLUMN, rows.size) * SETTING_STEP
		val maxScroll = max(0, totalHeight - height)
		val scroll = settingsScroll.getOrDefault(scrollKey, 0.0).coerceIn(0.0, maxScroll.toDouble())
		settingsScroll[scrollKey] = scroll

		if (settings.isEmpty()) {
			gfx.text(font(), "No settings", startX, startY, Colours.UNSELECTED_TEXT, false)
			return
		}

		enableScissor(gfx, startX - 8, startY - 12, startX + width, startY + height)
		val expanded = mutableListOf<SettingRow>()

		for ((index, setting) in rows.withIndex()) {
			val column = index / SETTINGS_PER_COLUMN
			val row = index % SETTINGS_PER_COLUMN
			val rowX = startX + column * COLUMN_STEP
			val rowY = startY + row * SETTING_STEP - scroll.roundToInt()
			if (rowY + SETTING_STEP < startY - 12 || rowY > startY + height) continue

			val rowInfo = SettingRow(module.id, group.name, setting, rowX, rowY, COLUMN_STEP - 16)
			if (isExpanded(setting, rowInfo.key)) {
				expanded.add(rowInfo)
			} else {
				renderSetting(gfx, rowInfo, mouseX, mouseY)
			}
		}
		expanded.forEach { renderSetting(gfx, it, mouseX, mouseY) }
		gfx.disableScissor()

		if (columns > 2) {
			gfx.text(font(), "${rows.size} settings", startX + width - 88, y + panelHeightPixels - 62, Colours.UNSELECTED_TEXT, false)
		}
	}

	private fun renderVariableGroupSettings(
		gfx: GuiGraphicsExtractor,
		module: CgcModule,
		group: GroupSetting<*>,
		rows: List<Setting<*>>,
		scrollKey: String,
		startX: Int,
		startY: Int,
		width: Int,
		height: Int,
		mouseX: Int,
		mouseY: Int
	) {
		if (rows.isEmpty()) {
			gfx.text(font(), "No settings", startX, startY, Colours.UNSELECTED_TEXT, false)
			return
		}

		val totalHeight = rows.sumOf { settingHeight(it) }
		val maxScroll = max(0, totalHeight - height)
		val scroll = settingsScroll.getOrDefault(scrollKey, 0.0).coerceIn(0.0, maxScroll.toDouble())
		settingsScroll[scrollKey] = scroll

		enableScissor(gfx, startX - 8, startY - 12, startX + width, startY + height)
		val expanded = mutableListOf<SettingRow>()
		var rowY = startY - scroll.roundToInt()
		for (setting in rows) {
			val rowHeight = settingHeight(setting)
			if (rowY + rowHeight >= startY - 12 && rowY <= startY + height) {
				val rowInfo = SettingRow(module.id, group.name, setting, startX, rowY, width)
				if (isExpanded(setting, rowInfo.key)) {
					expanded.add(rowInfo)
				} else {
					renderSetting(gfx, rowInfo, mouseX, mouseY)
				}
			}
			rowY += rowHeight
		}
		expanded.forEach { renderSetting(gfx, it, mouseX, mouseY) }
		gfx.disableScissor()
	}

	private fun renderSetting(gfx: GuiGraphicsExtractor, row: SettingRow, mouseX: Int, mouseY: Int) {
		val setting = row.setting
		if (setting is HotbarSwapListSetting) {
			renderHotbarSwaps(gfx, row, setting, mouseX, mouseY)
			return
		}

		gfx.text(font(), fit(font(), setting.name, 110), row.x, row.y, Colours.TEXT, false)

		when (setting) {
			is BooleanSetting -> renderBoolean(gfx, row, setting, mouseX, mouseY)
			is ModeSetting -> renderMode(gfx, row, setting, mouseX, mouseY)
			is MultiBoolSetting -> renderMultiBool(gfx, row, setting, mouseX, mouseY)
			is NumberSetting -> renderNumber(gfx, row, setting, mouseX, mouseY)
			is StringSetting -> renderString(gfx, row, setting)
			is KeybindSetting -> renderKeybind(gfx, row, setting)
			is ButtonSetting -> renderButton(gfx, row, setting)
			is ColourSetting -> renderColour(gfx, row, setting, mouseX, mouseY)
			is SaveSetting<*> -> renderSave(gfx, row, setting)
			is SoundSetting -> gfx.text(font(), fit(font(), "${setting.value} ${setting.volume}x", 170), row.x + CONTROL_X, row.y, Colours.UNSELECTED_TEXT, false)
			else -> gfx.text(font(), fit(font(), setting.displayValue, 170), row.x + CONTROL_X, row.y, Colours.UNSELECTED_TEXT, false)
		}
	}

	private fun renderBoolean(gfx: GuiGraphicsExtractor, row: SettingRow, setting: BooleanSetting, mouseX: Int, mouseY: Int) {
		val boxX = row.x + CONTROL_X + 200 - 14
		val boxY = row.y - 7
		val hovered = Bounds(boxX, boxY, 14, 14).contains(mouseX.toDouble(), mouseY.toDouble())
		fill(gfx, boxX, boxY, boxX + 14, boxY + 14, if (hovered) Colours.HOVERING_TEXT else Colours.PANEL)
		drawRectOutline(gfx, boxX, boxY, 14, 14, Colours.GROUP_OUTLINE)
		if (setting.value) {
			fill(gfx, boxX + 3, boxY + 3, boxX + 11, boxY + 11, Colours.SELECTED)
		}
		hitboxes.add(Hitbox(boxX, boxY, 14, 14) { button ->
			if (button == 0) setting.toggle()
		})
	}

	private fun renderMode(gfx: GuiGraphicsExtractor, row: SettingRow, setting: ModeSetting, mouseX: Int, mouseY: Int) {
		val expanded = expandedSettingKey == row.key
		val boxX = row.x + CONTROL_X
		val boxY = row.y - 10
		drawInputBox(gfx, boxX, boxY, 200, 21, expanded)
		gfx.text(font(), fit(font(), setting.value, 170), boxX + 5, row.y - 2, Colours.UNSELECTED_TEXT, false)
		gfx.text(font(), if (expanded) "v" else ">", boxX + 187, row.y - 2, Colours.TEXT, false)
		hitboxes.add(Hitbox(boxX, boxY, 200, 21) { button ->
			if (button == 0) expandedSettingKey = if (expanded) null else row.key
		})
		if (!expanded) return

		val totalHeight = setting.values.size * MODE_OPTION_HEIGHT
		val topLimit = settingsBounds.y
		val bottomLimit = settingsBounds.y + settingsBounds.height - 8
		val belowY = boxY + 21
		val belowSpace = bottomLimit - belowY
		val aboveSpace = boxY - topLimit
		val openAbove = belowSpace < totalHeight && aboveSpace > belowSpace
		val availableHeight = if (openAbove) aboveSpace else belowSpace
		val visibleHeight = min(totalHeight, max(MODE_OPTION_HEIGHT, availableHeight))
		val listY = if (openAbove) boxY - visibleHeight else belowY
		val maxScroll = max(0, totalHeight - visibleHeight).toDouble()
		val scroll = modeDropdownScroll.getOrDefault(row.key, 0.0).coerceIn(0.0, maxScroll)
		modeDropdownScroll[row.key] = scroll
		modeDropdownKey = row.key
		modeDropdownBounds = Bounds(boxX, listY, 200, visibleHeight)
		modeDropdownMaxScroll = maxScroll

		fill(gfx, boxX, listY, boxX + 200, listY + visibleHeight, Colours.PANEL)
		hitboxes.add(Hitbox(boxX, listY, 200, visibleHeight) {})
		enableScissor(gfx, boxX, listY, boxX + 200, listY + visibleHeight)
		var optionY = listY - scroll.roundToInt()
		for (option in setting.values) {
			val visibleTop = max(optionY, listY)
			val visibleBottom = min(optionY + MODE_OPTION_HEIGHT, listY + visibleHeight)
			if (visibleBottom <= visibleTop) {
				optionY += MODE_OPTION_HEIGHT
				continue
			}
			val hovered = Bounds(boxX, visibleTop, 200, visibleBottom - visibleTop).contains(mouseX.toDouble(), mouseY.toDouble())
			fill(gfx, boxX, optionY, boxX + 200, optionY + MODE_OPTION_HEIGHT, if (hovered) Colours.HOVERING_TEXT else Colours.PANEL)
			gfx.text(font(), fit(font(), option, 188), boxX + 5, optionY + 5, if (option == setting.value) Colours.SELECTED else Colours.TEXT, false)
			hitboxes.add(Hitbox(boxX, visibleTop, 200, visibleBottom - visibleTop) { button ->
				if (button == 0) {
					setting.value = option
					setting.onEdit()
					expandedSettingKey = null
				}
			})
			optionY += MODE_OPTION_HEIGHT
		}
		gfx.disableScissor()
		drawRectOutline(gfx, boxX, listY, 200, visibleHeight, Colours.GROUP_OUTLINE)
		if (maxScroll > 0.0) {
			val trackX = boxX + 196
			val barHeight = max(12, (visibleHeight.toDouble() / totalHeight * visibleHeight).roundToInt())
			val barY = listY + ((visibleHeight - barHeight) * (scroll / maxScroll)).roundToInt()
			fill(gfx, trackX, listY + 2, trackX + 2, listY + visibleHeight - 2, Colours.GROUP_OUTLINE)
			fill(gfx, trackX, barY, trackX + 2, barY + barHeight, Colours.SCROLL_BAR)
		}
	}

	private fun renderMultiBool(gfx: GuiGraphicsExtractor, row: SettingRow, setting: MultiBoolSetting, mouseX: Int, mouseY: Int) {
		val expanded = expandedSettingKey == row.key
		val boxX = row.x + CONTROL_X
		val boxY = row.y - 10
		val text = setting.valuesList().joinToString(", ").ifBlank { "None" }
		drawInputBox(gfx, boxX, boxY, 200, 21, expanded)
		gfx.text(font(), fit(font(), text, 170), boxX + 5, row.y - 2, Colours.UNSELECTED_TEXT, false)
		gfx.text(font(), if (expanded) "v" else ">", boxX + 187, row.y - 2, Colours.TEXT, false)
		hitboxes.add(Hitbox(boxX, boxY, 200, 21) { button ->
			if (button == 0) expandedSettingKey = if (expanded) null else row.key
		})
		if (!expanded) return

		var optionY = boxY + 21
		for ((option, state) in setting.value) {
			val hovered = Bounds(boxX, optionY, 200, 18).contains(mouseX.toDouble(), mouseY.toDouble())
			fill(gfx, boxX, optionY, boxX + 200, optionY + 18, if (hovered) Colours.HOVERING_TEXT else Colours.PANEL)
			gfx.text(font(), fit(font(), option, 174), boxX + 5, optionY + 5, if (state) Colours.SELECTED else Colours.TEXT, false)
			if (state) fill(gfx, boxX + 186, optionY + 6, boxX + 192, optionY + 12, Colours.SELECTED)
			hitboxes.add(Hitbox(boxX, optionY, 200, 18) { button ->
				if (button == 0) setting.toggle(option)
			})
			optionY += 18
		}
	}

	private fun renderNumber(gfx: GuiGraphicsExtractor, row: SettingRow, setting: NumberSetting, mouseX: Int, mouseY: Int) {
		val sliderX = row.x + CONTROL_X
		val sliderY = row.y - 8
		val inputX = sliderX + 150
		if (draggingNumber == setting) {
			updateNumber(setting, mouseX, sliderX, 140)
		}

		val min = setting.min.toDouble()
		val max = setting.max.toDouble()
		val range = (max - min).takeIf { it > 0.0 } ?: 1.0
		val percent = ((setting.value.toDouble() - min) / range).coerceIn(0.0, 1.0)
		val fillWidth = (136 * percent).roundToInt()

		fill(gfx, sliderX, sliderY, sliderX + 140, sliderY + 16, Colours.PANEL)
		fill(gfx, sliderX + 2, sliderY + 2, sliderX + 2 + fillWidth, sliderY + 14, Colours.SELECTED)
		gfx.centeredText(font(), setting.displayValue, sliderX + 70, sliderY + 4, Colours.TEXT)
		drawInputBox(gfx, inputX, sliderY, 50, 16, false)
		gfx.centeredText(font(), fit(font(), setting.valueAsString(), 42), inputX + 25, sliderY + 4, Colours.TEXT)

		hitboxes.add(Hitbox(sliderX, sliderY, 140, 16) { button ->
			if (button == 0) {
				draggingNumber = setting
				updateNumber(setting, mouseX, sliderX, 140)
			}
		})
	}

	private fun renderString(gfx: GuiGraphicsExtractor, row: SettingRow, setting: StringSetting) {
		val boxX = row.x + CONTROL_X
		val boxY = row.y - 10
		val focused = focusedString == setting
		val text = if (setting.secure && !focused) "*".repeat(setting.value.length) else setting.value
		val inputWidth = if (setting.pasteButton) 151 else 200
		drawInputBox(gfx, boxX, boxY, inputWidth, 21, focused)
		gfx.text(font(), fit(font(), text + if (focused) "|" else "", inputWidth - 12), boxX + 5, row.y - 2, Colours.TEXT, false)
		hitboxes.add(Hitbox(boxX, boxY, inputWidth, 21) { button ->
			if (button == 0) {
				focusString(setting)
			}
		})

		if (setting.pasteButton) {
			val pasteX = boxX + inputWidth + 4
			drawInputBox(gfx, pasteX, boxY, 45, 21, false)
			gfx.centeredText(font(), "Paste", pasteX + 22, row.y - 2, Colours.TEXT)
			hitboxes.add(Hitbox(pasteX, boxY, 45, 21) { button ->
				if (button == 0) {
					setting.setText(Minecraft.getInstance().keyboardHandler.clipboard)
					focusString(setting)
				}
			})
		}
	}

	private fun focusString(setting: StringSetting) {
		focusedString = setting
		focusedSave = null
		waitingKeybind = null
		waitingHotbarSwapKey = null
		writingSearch = false
	}

	private fun renderKeybind(gfx: GuiGraphicsExtractor, row: SettingRow, setting: KeybindSetting) {
		val boxX = row.x + CONTROL_X
		val boxY = row.y - 10
		val waiting = waitingKeybind == setting
		drawInputBox(gfx, boxX, boxY, 200, 21, waiting)
		gfx.centeredText(font(), fit(font(), if (waiting) "..." else setting.displayValue, 188), boxX + 100, row.y - 2, Colours.TEXT)
		hitboxes.add(Hitbox(boxX, boxY, 200, 21) { button ->
			if (button == 0) {
				waitingKeybind = setting
				waitingHotbarSwapKey = null
				focusedString = null
				focusedSave = null
				writingSearch = false
			}
		})
	}

	private fun renderButton(gfx: GuiGraphicsExtractor, row: SettingRow, setting: ButtonSetting) {
		val boxX = row.x + CONTROL_X
		val boxY = row.y - 10
		drawInputBox(gfx, boxX, boxY, 200, 21, false)
		gfx.centeredText(font(), fit(font(), setting.value, 188), boxX + 100, row.y - 2, Colours.TEXT)
		hitboxes.add(Hitbox(boxX, boxY, 200, 21) { button ->
			if (button == 0) setting.press()
		})
	}

	private fun renderHotbarSwaps(gfx: GuiGraphicsExtractor, row: SettingRow, setting: HotbarSwapListSetting, mouseX: Int, mouseY: Int) {
		gfx.text(font(), fit(font(), setting.name, 110), row.x, row.y, Colours.TEXT, false)
		if (setting.value.isEmpty()) {
			gfx.text(font(), "No swaps added", row.x + CONTROL_X, row.y, Colours.UNSELECTED_TEXT, false)
			return
		}

		var y = row.y + 18
		val boxWidth = min(row.width, HOTBAR_SWAP_BOX_WIDTH)
		var dropdown: HotbarSwapDropdown? = null
		for ((index, swap) in setting.value.withIndex()) {
			val boxX = row.x
			val boxY = y
			fill(gfx, boxX, boxY, boxX + boxWidth, boxY + HOTBAR_SWAP_BOX_HEIGHT, Colours.PANEL)
			drawRectOutline(gfx, boxX, boxY, boxWidth, HOTBAR_SWAP_BOX_HEIGHT, Colours.GROUP_OUTLINE)
			gfx.text(font(), "Swap ${index + 1}", boxX + 8, boxY + 7, Colours.TEXT, false)

			val eventKey = "${row.key}:${swap.id}:event"
			val eventExpanded = expandedSettingKey == eventKey
			val eventX = boxX + 70
			val eventY = boxY + 5
			drawInputBox(gfx, eventX, eventY, 124, 19, eventExpanded)
			gfx.text(font(), fit(font(), swap.trigger.displayName, 100), eventX + 5, eventY + 6, Colours.TEXT, false)
			gfx.text(font(), if (eventExpanded) "v" else ">", eventX + 112, eventY + 6, Colours.TEXT, false)
			hitboxes.add(Hitbox(eventX, eventY, 124, 19) { button ->
				if (button == 0) {
					expandedSettingKey = if (eventExpanded) null else eventKey
					focusedString = null
					focusedSave = null
					waitingKeybind = null
					waitingHotbarSwapKey = null
					writingSearch = false
				}
			})
			if (eventExpanded) {
				dropdown = HotbarSwapDropdown(setting, swap, eventKey, eventX, eventY, eventY + 19, 124)
			}

			val setupX = eventX + 132
			drawInputBox(gfx, setupX, eventY, 106, 19, false)
			gfx.centeredText(font(), "Setup switch", setupX + 53, eventY + 6, Colours.TEXT)
			hitboxes.add(Hitbox(setupX, eventY, 106, 19) { button ->
				if (button == 0) {
					expandedSettingKey = null
					setting.openSetup(swap)
				}
			})

			val typeX = setupX + 114
			drawInputBox(gfx, typeX, eventY, 76, 19, swap.swapType != HotbarSwapType.SLOT)
			gfx.centeredText(font(), fit(font(), swap.swapType.displayName, 66), typeX + 38, eventY + 6, Colours.TEXT)
			hitboxes.add(Hitbox(typeX, eventY, 76, 19) { button ->
				if (button == 0) {
					swap.swapType = when (swap.swapType) {
						HotbarSwapType.SLOT -> HotbarSwapType.ITEM
						HotbarSwapType.ITEM -> HotbarSwapType.DEV_ONLY
						HotbarSwapType.DEV_ONLY -> HotbarSwapType.SLOT
					}
					if (swap.swapType == HotbarSwapType.ITEM) {
						fillMissingHotbarSwapItems(swap)
					}
					setting.onEdit()
				}
			})

			val removeX = boxX + boxWidth - 25
			drawInputBox(gfx, removeX, eventY, 18, 19, false)
			gfx.centeredText(font(), "X", removeX + 9, eventY + 6, Colours.TEXT)
			hitboxes.add(Hitbox(removeX, eventY, 18, 19) { button ->
				if (button == 0) {
					setting.removeSwap(swap)
					if (expandedSettingKey?.startsWith("${row.key}:${swap.id}") == true) {
						expandedSettingKey = null
					}
				}
			})

			val detailY = boxY + 33
			if (swap.trigger == HotbarSwapTrigger.KEYBIND) {
				val waiting = waitingHotbarSwapKey?.swap?.id == swap.id
				drawInputBox(gfx, boxX + 8, detailY - 3, 136, 18, waiting)
				val keyText = if (waiting) "..." else "Key: ${HotbarSwapListSetting.friendlyKeyName(swap.keybind.keyName)}"
				gfx.text(font(), fit(font(), keyText, 124), boxX + 13, detailY + 3, Colours.TEXT, false)
				hitboxes.add(Hitbox(boxX + 8, detailY - 3, 136, 18) { button ->
					if (button == 0) {
						waitingHotbarSwapKey = WaitingHotbarSwapKey(setting, swap)
						waitingKeybind = null
						focusedString = null
						focusedSave = null
						writingSearch = false
					}
				})
				gfx.text(font(), "${swap.pairs.size} pair(s)", boxX + 154, detailY + 3, Colours.UNSELECTED_TEXT, false)
			} else {
				gfx.text(font(), "${swap.pairs.size} pair(s)", boxX + 8, detailY + 3, Colours.UNSELECTED_TEXT, false)
			}
			renderHotbarSwapAutoClose(gfx, setting, swap, boxX + boxWidth - 104, detailY - 3, mouseX, mouseY)

			y += HOTBAR_SWAP_BOX_HEIGHT + 6
		}

		dropdown?.let { renderHotbarSwapDropdown(gfx, it, mouseX, mouseY) }
	}

	private fun fillMissingHotbarSwapItems(swap: HotbarSwapListSetting.Swap) {
		val inventory = Minecraft.getInstance().player?.inventory ?: return
		for (pair in swap.pairs) {
			if (pair.item == null) {
				pair.item = HotbarSwapListSetting.ItemSelector.fromStack(inventory.getItem(pair.inventorySlot))
			}
		}
	}

	private fun renderHotbarSwapAutoClose(
		gfx: GuiGraphicsExtractor,
		setting: HotbarSwapListSetting,
		swap: HotbarSwapListSetting.Swap,
		x: Int,
		y: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val checkX = x + 78
		val checkY = y + 2
		val hovered = Bounds(x, y, 98, 18).contains(mouseX.toDouble(), mouseY.toDouble())
		gfx.text(font(), "Auto close", x, y + 5, if (swap.autoClose) Colours.TEXT else Colours.UNSELECTED_TEXT, false)
		fill(gfx, checkX, checkY, checkX + 14, checkY + 14, if (hovered) Colours.HOVERING_TEXT else Colours.PANEL)
		drawRectOutline(gfx, checkX, checkY, 14, 14, Colours.GROUP_OUTLINE)
		if (swap.autoClose) {
			fill(gfx, checkX + 3, checkY + 3, checkX + 11, checkY + 11, Colours.SELECTED)
		}
		hitboxes.add(Hitbox(x, y, 98, 18) { button ->
			if (button == 0) {
				swap.autoClose = !swap.autoClose
				setting.onEdit()
			}
		})
	}

	private fun renderHotbarSwapDropdown(gfx: GuiGraphicsExtractor, dropdown: HotbarSwapDropdown, mouseX: Int, mouseY: Int) {
		val triggers = HotbarSwapTrigger.entries
		val totalHeight = triggers.size * HOTBAR_TRIGGER_OPTION_HEIGHT
		val topLimit = if (settingsBounds.height > 0) settingsBounds.y else panelY + 58
		val bottomLimit = if (settingsBounds.height > 0) settingsBounds.y + settingsBounds.height - 8 else panelY + panelHeightPixels - 8
		val belowSpace = bottomLimit - dropdown.y
		val aboveSpace = dropdown.buttonY - topLimit
		val openAbove = belowSpace < totalHeight && aboveSpace > belowSpace
		val availableHeight = if (openAbove) aboveSpace else belowSpace
		val visibleHeight = min(totalHeight, max(HOTBAR_TRIGGER_OPTION_HEIGHT * 3, availableHeight))
			.coerceAtMost(totalHeight)
			.coerceAtLeast(HOTBAR_TRIGGER_OPTION_HEIGHT)
		val listY = if (openAbove) {
			(dropdown.buttonY - visibleHeight).coerceAtLeast(topLimit)
		} else {
			dropdown.y
		}
		val maxScroll = max(0, totalHeight - visibleHeight).toDouble()
		val scroll = hotbarTriggerScroll.getOrDefault(dropdown.key, 0.0).coerceIn(0.0, maxScroll)
		hotbarTriggerScroll[dropdown.key] = scroll
		hotbarTriggerDropdownKey = dropdown.key
		hotbarTriggerDropdownBounds = Bounds(dropdown.x, listY, dropdown.width, visibleHeight)
		hotbarTriggerDropdownMaxScroll = maxScroll

		fill(gfx, dropdown.x, listY, dropdown.x + dropdown.width, listY + visibleHeight, Colours.PANEL)
		hitboxes.add(Hitbox(dropdown.x, listY, dropdown.width, visibleHeight) {})
		enableScissor(gfx, dropdown.x, listY, dropdown.x + dropdown.width, listY + visibleHeight)
		var optionY = listY - scroll.roundToInt()
		for (trigger in triggers) {
			val visibleTop = max(optionY, listY)
			val visibleBottom = min(optionY + HOTBAR_TRIGGER_OPTION_HEIGHT, listY + visibleHeight)
			if (visibleBottom <= visibleTop) {
				optionY += HOTBAR_TRIGGER_OPTION_HEIGHT
				continue
			}

			val hovered = Bounds(dropdown.x, visibleTop, dropdown.width, visibleBottom - visibleTop).contains(mouseX.toDouble(), mouseY.toDouble())
			fill(gfx, dropdown.x, optionY, dropdown.x + dropdown.width, optionY + HOTBAR_TRIGGER_OPTION_HEIGHT, if (hovered) Colours.HOVERING_TEXT else Colours.PANEL)
			drawRectOutline(gfx, dropdown.x, optionY, dropdown.width, HOTBAR_TRIGGER_OPTION_HEIGHT, Colours.GROUP_OUTLINE)
			gfx.text(font(), fit(font(), trigger.displayName, dropdown.width - 12), dropdown.x + 5, optionY + 5, if (trigger == dropdown.swap.trigger) Colours.SELECTED else Colours.TEXT, false)
			hitboxes.add(Hitbox(dropdown.x, visibleTop, dropdown.width, visibleBottom - visibleTop) { button ->
				if (button == 0) {
					dropdown.swap.trigger = trigger
					dropdown.setting.onEdit()
					expandedSettingKey = null
					if (trigger != HotbarSwapTrigger.KEYBIND && waitingHotbarSwapKey?.swap?.id == dropdown.swap.id) {
						waitingHotbarSwapKey = null
					}
				}
			})
			optionY += HOTBAR_TRIGGER_OPTION_HEIGHT
		}
		gfx.disableScissor()
		drawRectOutline(gfx, dropdown.x, listY, dropdown.width, visibleHeight, Colours.GROUP_OUTLINE)

		if (maxScroll > 0.0) {
			val trackX = dropdown.x + dropdown.width - 4
			val barHeight = max(16, (visibleHeight.toDouble() / totalHeight * visibleHeight).roundToInt())
			val barY = listY + ((visibleHeight - barHeight) * (scroll / maxScroll)).roundToInt()
			fill(gfx, trackX, listY + 2, trackX + 2, listY + visibleHeight - 2, Colours.GROUP_OUTLINE)
			fill(gfx, trackX, barY, trackX + 2, barY + barHeight, Colours.SCROLL_BAR)
		}
	}

	private fun renderColour(gfx: GuiGraphicsExtractor, row: SettingRow, setting: ColourSetting, mouseX: Int, mouseY: Int) {
		val boxX = row.x + CONTROL_X + 150
		val boxY = row.y - 10
		val expanded = expandedSettingKey == row.key
		val drag = draggingColour
		if (drag != null && drag.setting == setting) {
			updateColourChannel(setting, drag.channel, mouseX, drag.sliderX, drag.sliderWidth)
		}

		gfx.text(font(), setting.displayValue, row.x + CONTROL_X, row.y - 2, Colours.UNSELECTED_TEXT, false)
		fill(gfx, boxX, boxY, boxX + 50, boxY + 21, setting.value.argb())
		drawRectOutline(gfx, boxX, boxY, 50, 21, if (expanded) Colours.SELECTED else Colours.GROUP_OUTLINE)
		hitboxes.add(Hitbox(boxX, boxY, 50, 21) { button ->
			when (button) {
				0 -> expandedSettingKey = if (expanded) null else row.key
				1 -> setting.resetToDefault()
			}
		})

		if (!expanded) {
			return
		}

		val editorX = row.x + CONTROL_X
		var editorY = row.y + 16
		renderColourChannel(gfx, setting, ColourChannel.RED, "R", setting.value.red, editorX, editorY, mouseX)
		editorY += 16
		renderColourChannel(gfx, setting, ColourChannel.GREEN, "G", setting.value.green, editorX, editorY, mouseX)
		editorY += 16
		renderColourChannel(gfx, setting, ColourChannel.BLUE, "B", setting.value.blue, editorX, editorY, mouseX)
		editorY += 16
		renderColourChannel(gfx, setting, ColourChannel.ALPHA, "A", setting.value.alpha, editorX, editorY, mouseX)
	}

	private fun renderColourChannel(
		gfx: GuiGraphicsExtractor,
		setting: ColourSetting,
		channel: ColourChannel,
		label: String,
		value: Int,
		x: Int,
		y: Int,
		mouseX: Int
	) {
		val sliderX = x + 18
		val sliderY = y
		val sliderWidth = 140
		val fillWidth = (136 * (value / 255.0)).roundToInt()

		gfx.text(font(), label, x, y + 3, Colours.UNSELECTED_TEXT, false)
		fill(gfx, sliderX, sliderY, sliderX + sliderWidth, sliderY + 13, Colours.PANEL)
		fill(gfx, sliderX + 2, sliderY + 2, sliderX + 2 + fillWidth, sliderY + 11, channel.previewColour(value))
		drawInputBox(gfx, sliderX + 150, sliderY, 32, 13, false)
		gfx.centeredText(font(), value.toString(), sliderX + 166, sliderY + 2, Colours.TEXT)

		hitboxes.add(Hitbox(sliderX, sliderY, sliderWidth, 13) { button ->
			if (button == 0) {
				draggingColour = ColourDrag(setting, channel, sliderX, sliderWidth)
				updateColourChannel(setting, channel, mouseX, sliderX, sliderWidth)
			}
		})
	}

	private fun renderSave(gfx: GuiGraphicsExtractor, row: SettingRow, setting: SaveSetting<*>) {
		if (!setting.allowEdits) {
			gfx.text(font(), fit(font(), setting.displayValue, 190), row.x + CONTROL_X, row.y - 2, Colours.UNSELECTED_TEXT, false)
			return
		}

		val boxX = row.x + CONTROL_X
		val boxY = row.y - 10
		val focused = focusedSave == setting
		drawInputBox(gfx, boxX, boxY, 140, 21, focused)
		gfx.text(font(), fit(font(), setting.fileName + if (focused) "|" else "", 128), boxX + 5, row.y - 2, Colours.TEXT, false)
		drawInputBox(gfx, boxX + 150, boxY, 50, 21, false)
		gfx.centeredText(font(), "Load", boxX + 175, row.y - 2, Colours.TEXT)
		hitboxes.add(Hitbox(boxX, boxY, 140, 21) { button ->
			if (button == 0) {
				focusedSave = setting
				focusedString = null
				waitingKeybind = null
				waitingHotbarSwapKey = null
				writingSearch = false
			}
		})
		hitboxes.add(Hitbox(boxX + 150, boxY, 50, 21) { button ->
			if (button == 0) setting.load()
		})
	}

	private fun filteredModulesByCategory(): Map<ModuleCategory, List<CgcModule>> {
		if (searchCacheValid && search == cachedSearch) return cachedSearchResults

		cachedSearch = search
		searchCacheValid = true
		cachedSearchResults = if (search.isBlank()) {
			modulesByCategory
		} else {
			CgcModules.manager.all()
				.mapNotNull { module ->
					val score = scoreModule(search, module)
					if (score > 200) module to score else null
				}
				.groupBy({ it.first.category }, { it })
				.mapValues { (_, scored) ->
					scored.sortedWith(compareByDescending<Pair<CgcModule, Int>> { it.second }.thenBy { it.first.displayName.lowercase(Locale.ROOT) })
						.map { it.first }
				}
		}
		return cachedSearchResults
	}

	private fun scoreModule(query: String, module: CgcModule): Int =
		module.aliases.maxOfOrNull { score(it, query) } ?: 0

	private fun score(candidateInput: String, queryInput: String): Int {
		val candidate = candidateInput.lowercase(Locale.ROOT)
		val query = queryInput.lowercase(Locale.ROOT)
		if (query.isEmpty()) return 0
		if (candidate == query) return 1000
		if (candidate.startsWith(query)) return 850
		if (candidate.split(" ", "_", "-").any { it.startsWith(query) }) return 800
		if (candidate.contains(query)) return 650

		var qi = 0
		for (char in candidate) {
			if (qi < query.length && char == query[qi]) qi++
		}
		if (qi == query.length) return 500
		return max(0, 400 - levenshtein(candidate, query) * 25)
	}

	private fun levenshtein(a: String, b: String): Int {
		val dp = Array(a.length + 1) { IntArray(b.length + 1) }
		for (i in 0..a.length) dp[i][0] = i
		for (j in 0..b.length) dp[0][j] = j
		for (i in 1..a.length) {
			for (j in 1..b.length) {
				val cost = if (a[i - 1] == b[j - 1]) 0 else 1
				dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
			}
		}
		return dp[a.length][b.length]
	}

	private fun isExpanded(setting: Setting<*>, key: String): Boolean =
		expandedSettingKey == key && (setting is ModeSetting || setting is MultiBoolSetting || setting is ColourSetting)

	private fun settingHeight(setting: Setting<*>): Int =
		when (setting) {
			is HotbarSwapListSetting -> if (setting.value.isEmpty()) {
				SETTING_STEP
			} else {
				SETTING_STEP + setting.value.size * (HOTBAR_SWAP_BOX_HEIGHT + 6)
			}
			else -> SETTING_STEP
		}

	private fun selectedSettingScrollKey(): String {
		val module = selectedModules[selectedCategory] ?: return ""
		val group = selectedGroups[module.id] ?: return module.id
		return "${module.id}:${group.name}"
	}

	private fun handleTextKey(input: KeyEvent, currentValue: String, allowBlank: Boolean, update: (String) -> Unit): Boolean {
		return when (input.key()) {
			GLFW.GLFW_KEY_ESCAPE,
			GLFW.GLFW_KEY_ENTER -> {
				writingSearch = false
				focusedString = null
				focusedSave = null
				true
			}
			GLFW.GLFW_KEY_BACKSPACE -> {
				val next = currentValue.dropLast(1)
				if (next.isNotBlank() || allowBlank) update(next)
				true
			}
			else -> false
		}
	}

	private fun updateNumber(setting: NumberSetting, mouseX: Int, sliderX: Int, sliderWidth: Int) {
		val min = setting.min.toDouble()
		val max = setting.max.toDouble()
		val percent = ((mouseX - sliderX).toDouble() / sliderWidth).coerceIn(0.0, 1.0)
		val rawValue = min + (max - min) * percent
		val increment = setting.increment.toDouble()
		val rounded = if (increment > 0.0) {
			Math.round(rawValue / increment) * increment
		} else {
			rawValue
		}
		setting.setValue(rounded)
	}

	private fun updateColourChannel(setting: ColourSetting, channel: ColourChannel, mouseX: Int, sliderX: Int, sliderWidth: Int) {
		val value = (((mouseX - sliderX).toDouble() / sliderWidth).coerceIn(0.0, 1.0) * 255.0).roundToInt()
		when (channel) {
			ColourChannel.RED -> setting.setColour(red = value)
			ColourChannel.GREEN -> setting.setColour(green = value)
			ColourChannel.BLUE -> setting.setColour(blue = value)
			ColourChannel.ALPHA -> setting.setColour(alpha = value)
		}
	}

	private fun drawInputBox(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, active: Boolean) {
		fill(gfx, x, y, x + width, y + height, if (active) Colours.WRITING_TEXT else Colours.PANEL)
		drawRectOutline(gfx, x, y, width, height, if (active) Colours.SELECTED else Colours.GROUP_OUTLINE)
	}

	private fun drawRectOutline(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
		if (width <= 1 || height <= 1) return
		fill(gfx, x, y, x + width, y + 1, color)
		fill(gfx, x, y + height - 1, x + width, y + height, color)
		fill(gfx, x, y, x + 1, y + height, color)
		fill(gfx, x + width - 1, y, x + width, y + height, color)
	}

	private fun fill(
		gfx: GuiGraphicsExtractor,
		x1: Int,
		y1: Int,
		x2: Int,
		y2: Int,
		color: Int,
		applyOpacity: Boolean = true
	) {
		gfx.fill(x1, y1, x2, y2, if (applyOpacity) surface(color) else color)
	}

	private fun enableScissor(gfx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int) {
		gfx.enableScissor(x1, y1, x2, y2)
	}

	private fun drawLineApprox(gfx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
		val steps = max(abs(x2 - x1), abs(y2 - y1)).coerceAtLeast(1)
		for (i in 0..steps step 3) {
			val x = x1 + ((x2 - x1) * i / steps.toFloat()).roundToInt()
			val y = y1 + ((y2 - y1) * i / steps.toFloat()).roundToInt()
			fill(gfx, x, y, x + 1, y + 1, color)
		}
	}

	private fun <K> animate(values: MutableMap<K, Float>, key: K, target: Float): Float {
		val current = values[key] ?: target
		val next = current + (target - current) * 0.24f
		val value = if (abs(next - target) < 0.01f) target else next
		values[key] = value
		return value
	}

	private fun surface(color: Int, progress: Float = 1.0f): Int =
		withAlpha(color, (channel(color, 24) * guiOpacity() * progress).roundToInt())

	private fun guiOpacity(): Float =
		(1.0 - CgcSettings.guiTransparency.value.toDouble()).toFloat().coerceIn(0.0f, 1.0f)

	private fun blend(from: Int, to: Int, progress: Float): Int {
		val t = progress.coerceIn(0.0f, 1.0f)
		return Colours.argb(
			lerp(channel(from, 24), channel(to, 24), t),
			lerp(channel(from, 16), channel(to, 16), t),
			lerp(channel(from, 8), channel(to, 8), t),
			lerp(channel(from, 0), channel(to, 0), t)
		)
	}

	private fun withAlpha(color: Int, alpha: Int): Int =
		(alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

	private fun channel(color: Int, shift: Int): Int =
		color shr shift and 255

	private fun lerp(from: Int, to: Int, progress: Float): Int =
		(from + (to - from) * progress).roundToInt()

	private fun font(): Font = Minecraft.getInstance().font

	private fun fit(font: Font, text: String, maxWidth: Int): String {
		if (font.width(text) <= maxWidth) return text
		if (maxWidth <= font.width("...")) return ""
		return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "..."
	}

	private fun isVisible(y: Int, height: Int, clipTop: Int, clipBottom: Int): Boolean =
		y + height >= clipTop && y <= clipBottom

	private fun rightWidth(): Int =
		panelWidthPixels - LEFT_WIDTH - 14

	private fun rightHeight(): Int =
		panelHeightPixels - 101

	private data class SettingRow(
		val moduleId: String,
		val groupName: String,
		val setting: Setting<*>,
		val x: Int,
		val y: Int,
		val width: Int
	) {
		val key: String = "$moduleId:$groupName:${setting.name}"
	}

	private data class WaitingHotbarSwapKey(
		val setting: HotbarSwapListSetting,
		val swap: HotbarSwapListSetting.Swap
	)

	private data class HotbarSwapDropdown(
		val setting: HotbarSwapListSetting,
		val swap: HotbarSwapListSetting.Swap,
		val key: String,
		val x: Int,
		val buttonY: Int,
		val y: Int,
		val width: Int
	)

	private data class ColourDrag(
		val setting: ColourSetting,
		val channel: ColourChannel,
		val sliderX: Int,
		val sliderWidth: Int
	)

	private companion object {
		private const val WIDTH = 650
		private const val HEIGHT = 470
		private const val MIN_WIDTH = 560
		private const val MIN_HEIGHT = 390
		private const val LEFT_WIDTH = 140
		private const val CATEGORY_STEP = 24
		private const val CATEGORY_HIT_HEIGHT = 20
		private const val MODULE_STEP = 23
		private const val MODULE_HIT_HEIGHT = 18
		private const val SETTING_STEP = 28
		private const val SETTINGS_PER_COLUMN = 15
		private const val COLUMN_STEP = 310
		private const val CONTROL_X = 114
		private const val HOTBAR_SWAP_BOX_WIDTH = 448
		private const val HOTBAR_SWAP_BOX_HEIGHT = 55
		private const val MODE_OPTION_HEIGHT = 18
		private const val HOTBAR_TRIGGER_OPTION_HEIGHT = 18
	}

	private object SessionState {
		var selectedCategory: ModuleCategory? = null
		var search: String = ""
		var leftScroll: Double = 0.0
		var selectedModules: MutableMap<ModuleCategory, String> = mutableMapOf()
		var selectedGroups: MutableMap<String, String> = mutableMapOf()
		var expandedCategories: MutableSet<ModuleCategory> = linkedSetOf()
		var settingsScroll: MutableMap<String, Double> = mutableMapOf()
	}
}

private data class Hitbox(
	val x: Int,
	val y: Int,
	val width: Int,
	val height: Int,
	val onClick: (button: Int) -> Unit
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

	companion object {
		val ZERO = Bounds(0, 0, 0, 0)
	}
}

private enum class ColourChannel {
	RED,
	GREEN,
	BLUE,
	ALPHA;

	fun previewColour(value: Int): Int =
		when (this) {
			RED -> Colours.argb(255, value, 32, 32)
			GREEN -> Colours.argb(255, 32, value, 32)
			BLUE -> Colours.argb(255, 32, 32, value)
			ALPHA -> Colours.argb(value, 180, 180, 180)
		}
}

private object Colours {
	val BACKGROUND = argb(246, 28, 28, 28)
	val SELECTED_BACKGROUND = argb(255, 35, 35, 35)
	val LINE = argb(255, 38, 38, 38)
	val PANEL = argb(255, 22, 22, 22)
	val PANEL_LINES = argb(255, 20, 20, 20)
	val TEXT = argb(255, 255, 255, 255)
	val UNSELECTED_TEXT = argb(255, 105, 105, 105)
	val SELECTED_TEXT = argb(255, 255, 255, 255)
	val SELECTED = argb(255, 85, 21, 153)
	val GROUP_FILL = argb(255, 28, 28, 28)
	val GROUP_OUTLINE = argb(255, 50, 50, 50)
	val SCROLL_BAR = argb(255, 67, 67, 67)
	val ENABLED = argb(13, 255, 255, 255)
	val ENABLED_TEXT = argb(255, 230, 207, 234)
	val WRITING_TEXT = argb(255, 60, 60, 60)
	val HOVERING_TEXT = argb(255, 50, 50, 50)
	val SEARCH_FILL = argb(255, 50, 50, 50)
	val SEARCH_FILL_HOVER = argb(255, 58, 58, 58)
	val SEARCH_OUTLINE = argb(255, 50, 50, 50)

	fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
		((alpha and 255) shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)
}
