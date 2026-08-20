package cgc.cgc.client.gui

import cgc.cgc.data.Phase7
import cgc.cgc.module.impl.dungeon.AutoC
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeAdapter
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class AutoCEditScreen(
	private val session: AutoC.EditSession
) : Screen(Component.literal("Auto C Edit")) {
	private val hitboxes = mutableListOf<UiHitbox>()
	private val projections = mutableListOf<ProjectedNode>()
	private var selectedId: String? = session.entries.firstOrNull()?.node?.id
	private var focusedField: FieldKey? = null
	private var focusedText = ""
	private var pickMenu: PickMenu? = null
	private var activationReqMenuId: String? = null
	private var phaseStartMenuId: String? = null
	private var panelScroll = 0.0
	private var panelContentHeight = 0
	private var orbitYaw = 35.0
	private var orbitPitch = -25.0
	private var draggingView = false
	private var confirmDeleteId: String? = null
	private var pressX = 0.0
	private var pressY = 0.0
	private var lastDragX = 0.0
	private var lastDragY = 0.0
	private var movedDrag = false

	override fun extractBackground(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
		gfx.fill(0, 0, width, height, BLACK)
	}

	override fun extractRenderState(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		hitboxes.clear()
		projections.clear()
		super.extractRenderState(gfx, mouseX, mouseY, partialTick)
		renderVisualizer(gfx)
		renderPanel(gfx, mouseX, mouseY)
		renderPickMenu(gfx)
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		for (hitbox in hitboxes.asReversed()) {
			if (hitbox.contains(click.x(), click.y())) {
				hitbox.onClick(click.button())
				return true
			}
		}

		focusedField = null
		pickMenu = null
		if (click.button() == 0 && click.x() < viewportWidth()) {
			draggingView = true
			pressX = click.x()
			pressY = click.y()
			lastDragX = click.x()
			lastDragY = click.y()
			movedDrag = false
			return true
		}
		return true
	}

	override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		if (!draggingView || click.button() != 0) {
			return super.mouseDragged(click, dragX, dragY)
		}

		val dx = click.x() - lastDragX
		val dy = click.y() - lastDragY
		if (abs(click.x() - pressX) > DRAG_THRESHOLD || abs(click.y() - pressY) > DRAG_THRESHOLD) {
			movedDrag = true
		}
		if (movedDrag) {
			orbitYaw += dx * ORBIT_SPEED
			orbitPitch = (orbitPitch - dy * ORBIT_SPEED).coerceIn(-85.0, 85.0)
		}
		lastDragX = click.x()
		lastDragY = click.y()
		return true
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean {
		if (draggingView && click.button() == 0) {
			draggingView = false
			if (!movedDrag) {
				selectProjected(click.x(), click.y())
			}
			movedDrag = false
			return true
		}
		return super.mouseReleased(click)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, hScroll: Double, vScroll: Double): Boolean {
		if (mouseX >= viewportWidth()) {
			val maxScroll = max(0.0, panelContentHeight - height + PANEL_PADDING * 2.0)
			panelScroll = (panelScroll - vScroll * SCROLL_STEP).coerceIn(0.0, maxScroll)
			return true
		}
		return super.mouseScrolled(mouseX, mouseY, hScroll, vScroll)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val field = focusedField ?: return super.charTyped(event)
		val typed = event.codepointAsString().firstOrNull() ?: return true
		if (!typed.isISOControl()) {
			focusedText += typed
			applyFieldText(field)
		}
		return true
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		val field = focusedField ?: return super.keyPressed(input)
		return when (input.key()) {
			GLFW.GLFW_KEY_ESCAPE,
			GLFW.GLFW_KEY_ENTER -> {
				applyFieldText(field)
				focusedField = null
				true
			}
			GLFW.GLFW_KEY_BACKSPACE -> {
				focusedText = focusedText.dropLast(1)
				applyFieldText(field)
				true
			}
			GLFW.GLFW_KEY_DELETE -> {
				focusedText = ""
				applyFieldText(field)
				true
			}
			GLFW.GLFW_KEY_TAB -> {
				applyFieldText(field)
				focusNextField(field)
				true
			}
			else -> super.keyPressed(input)
		}
	}

	override fun onClose() {
		focusedField?.let(::applyFieldText)
		focusedField = null
		session.module.saveEditSession(session)
		super.onClose()
	}

	override fun isPauseScreen(): Boolean = false

	private fun renderVisualizer(gfx: GuiGraphicsExtractor) {
		val viewWidth = viewportWidth()
		gfx.fill(0, 0, viewWidth, height, BLACK)
		drawAxes(gfx)
		for (entry in session.entries) {
			val projected = project(entry) ?: continue
			projections.add(projected)
		}
		projections.sortByDescending { it.depth }
		for (projection in projections) {
			drawNode(gfx, projection)
		}
	}

	private fun drawAxes(gfx: GuiGraphicsExtractor) {
		val origin = projectRelative(0.0, 0.0, 0.0) ?: return
		val xAxis = projectRelative(20.0, 0.0, 0.0)
		val yAxis = projectRelative(0.0, 20.0, 0.0)
		val zAxis = projectRelative(0.0, 0.0, 20.0)
		xAxis?.let { drawLine(gfx, origin.x.roundToInt(), origin.y.roundToInt(), it.x.roundToInt(), it.y.roundToInt(), AXIS_X) }
		yAxis?.let { drawLine(gfx, origin.x.roundToInt(), origin.y.roundToInt(), it.x.roundToInt(), it.y.roundToInt(), AXIS_Y) }
		zAxis?.let { drawLine(gfx, origin.x.roundToInt(), origin.y.roundToInt(), it.x.roundToInt(), it.y.roundToInt(), AXIS_Z) }
	}

	private fun drawNode(gfx: GuiGraphicsExtractor, projection: ProjectedNode) {
		val selected = projection.entry.node.id == selectedId
		val radius = if (selected) NODE_SIZE_SELECTED else NODE_SIZE
		val x = projection.x.roundToInt()
		val y = projection.y.roundToInt()
		val colour = projection.entry.node.colour().argb()
		gfx.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, colour)
		outline(gfx, x - radius - 1, y - radius - 1, radius * 2 + 3, radius * 2 + 3, if (selected) SELECTED_OUTLINE else NODE_OUTLINE)
		if (selected) {
			gfx.centeredText(font(), (projection.entry.index + 1).toString(), x, y - 4, TEXT)
		}
	}

	private fun renderPanel(gfx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val x = viewportWidth()
		gfx.fill(x, 0, width, height, PANEL)
		outline(gfx, x, 0, PANEL_WIDTH, height, OUTLINE)
		gfx.enableScissor(x, 0, width, height)
		var y = PANEL_PADDING - panelScroll.roundToInt()
		gfx.text(font(), "Auto C Edit", x + PANEL_PADDING, y, TEXT, false)
		y += 14
		gfx.text(font(), fit("Scope: ${session.scopeLabel}", PANEL_WIDTH - PANEL_PADDING * 2), x + PANEL_PADDING, y, MUTED_TEXT, false)
		y += 18

		val node = selectedNode()
		if (node == null) {
			gfx.text(font(), "No node selected", x + PANEL_PADDING, y, MUTED_TEXT, false)
			panelContentHeight = y + 20 + panelScroll.roundToInt()
			gfx.disableScissor()
			return
		}

		val index = session.sourceNodes.indexOf(node).coerceAtLeast(0)
		gfx.text(font(), fit("#${index + 1} ${node.name()}", PANEL_WIDTH - PANEL_PADDING * 2), x + PANEL_PADDING, y, TEXT, false)
		y += 18
		y = renderDeleteControls(gfx, x, y, node)
		y += 4
		y = renderField(gfx, node, FieldKey.Base(EditField.X), "X", formatNumber(node.pos.x), x, y)
		y = renderField(gfx, node, FieldKey.Base(EditField.Y), "Y", formatNumber(node.pos.y), x, y)
		y = renderField(gfx, node, FieldKey.Base(EditField.Z), "Z", formatNumber(node.pos.z), x, y)
		y = renderField(gfx, node, FieldKey.Base(EditField.RADIUS), "Radius", formatNumber(node.radius.toDouble()), x, y)
		y = renderField(gfx, node, FieldKey.Base(EditField.WAIT), "Wait", formatNumber(node.waitSeconds), x, y)
		y = renderField(gfx, node, FieldKey.Base(EditField.MAX_A), "MaxA", node.maxActivationsPerMinute.toString(), x, y)
		y = renderField(gfx, node, FieldKey.Base(EditField.ORDER), "Order", (index + 1).toString(), x, y)
		y += 4

		y = renderToggle(gfx, x, y, "AS", node.awaitSecret) {
			node.awaitSecret = !node.awaitSecret
		}
		y = renderToggle(gfx, x, y, "NR", node.notStart) {
			node.notStart = !node.notStart
		}
		y += 4
		button(gfx, x + PANEL_PADDING, y, 66, BUTTON_HEIGHT, "Up") { button ->
			if (button == 0) {
				moveSelected(-1)
			}
		}
		button(gfx, x + PANEL_PADDING + 72, y, 66, BUTTON_HEIGHT, "Down") { button ->
			if (button == 0) {
				moveSelected(1)
			}
		}
		y += BUTTON_HEIGHT + 10

		y = renderNodeProperties(gfx, x, y, node)
		y += 6

		gfx.text(font(), "Conditions", x + PANEL_PADDING, y, TEXT, false)
		y += 14
		y = renderToggle(gfx, x, y, "On Terminal Exit", node.onTerminalExit) {
			node.onTerminalExit = !node.onTerminalExit
		}
		y = renderPhaseStartCondition(gfx, x, y, node)
		y = renderActivationRequirements(gfx, x, y, node)

		panelContentHeight = y + panelScroll.roundToInt()
		gfx.disableScissor()
	}

	private fun renderField(gfx: GuiGraphicsExtractor, node: AutoCNode, field: FieldKey, label: String, value: String, panelX: Int, y: Int): Int {
		val labelX = panelX + PANEL_PADDING
		val inputX = panelX + 78
		val inputWidth = PANEL_WIDTH - 78 - PANEL_PADDING
		gfx.text(font(), label, labelX, y + 5, MUTED_TEXT, false)
		val focused = focusedField == field
		gfx.fill(inputX, y, inputX + inputWidth, y + FIELD_HEIGHT, if (focused) FIELD_ACTIVE else FIELD_FILL)
		outline(gfx, inputX, y, inputWidth, FIELD_HEIGHT, if (focused) SELECTED_OUTLINE else OUTLINE)
		val shown = if (focused) focusedText + "|" else value
		gfx.text(font(), fit(shown, inputWidth - 8), inputX + 4, y + 5, TEXT, false)
		hitboxes.add(UiHitbox(inputX, y, inputWidth, FIELD_HEIGHT) { button ->
			if (button == 0) {
				focusedField?.let(::applyFieldText)
				focusedField = field
				focusedText = currentFieldText(node, field)
			}
		})
		return y + FIELD_HEIGHT + 5
	}

	private fun renderNodeProperties(gfx: GuiGraphicsExtractor, panelX: Int, yStart: Int, node: AutoCNode): Int {
		val properties = node.serialize()
			.entrySet()
			.filter { (key, _) -> key !in SHARED_PROPERTY_KEYS }
		if (properties.isEmpty()) {
			return yStart
		}

		var y = yStart
		gfx.text(font(), "Node Properties", panelX + PANEL_PADDING, y, TEXT, false)
		y += 14
		for ((key, value) in properties) {
			y = renderJsonProperty(gfx, panelX, y, node, key, value)
		}
		return y
	}

	private fun renderJsonProperty(
		gfx: GuiGraphicsExtractor,
		panelX: Int,
		y: Int,
		node: AutoCNode,
		key: String,
		value: JsonElement
	): Int =
		when {
			value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> {
				renderToggle(gfx, panelX, y, key, value.asBoolean) {
					updateNodeJson(node) { json -> json.addProperty(key, !value.asBoolean) }
				}
			}
			value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> {
				renderField(gfx, node, FieldKey.Json(key, JsonValueKind.NUMBER), key, formatNumber(value.asDouble), panelX, y)
			}
			value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
				renderField(gfx, node, FieldKey.Json(key, JsonValueKind.STRING), key, value.asString, panelX, y)
			}
			value.isJsonObject && isPosObject(value.asJsonObject) -> {
				var nextY = y
				nextY = renderField(gfx, node, FieldKey.Json("$key.x", JsonValueKind.NUMBER), "$key X", formatNumber(value.asJsonObject.get("x").asDouble), panelX, nextY)
				nextY = renderField(gfx, node, FieldKey.Json("$key.y", JsonValueKind.NUMBER), "$key Y", formatNumber(value.asJsonObject.get("y").asDouble), panelX, nextY)
				renderField(gfx, node, FieldKey.Json("$key.z", JsonValueKind.NUMBER), "$key Z", formatNumber(value.asJsonObject.get("z").asDouble), panelX, nextY)
			}
			value.isJsonArray && key == "blocks" && isPosArray(value.asJsonArray) -> {
				renderField(gfx, node, FieldKey.Json(key, JsonValueKind.POS_ARRAY), key, formatPosArray(value.asJsonArray), panelX, y)
			}
			value.isJsonArray && key == "except" -> {
				renderField(gfx, node, FieldKey.Json(key, JsonValueKind.STRING_ARRAY), key, formatStringArray(value.asJsonArray), panelX, y)
			}
			value.isJsonArray -> {
				gfx.text(font(), "$key: ${value.asJsonArray.size()} entries", panelX + PANEL_PADDING, y + 5, MUTED_TEXT, false)
				y + FIELD_HEIGHT + 5
			}
			else -> y
		}

	private fun renderDeleteControls(gfx: GuiGraphicsExtractor, panelX: Int, y: Int, node: AutoCNode): Int {
		val x = panelX + PANEL_PADDING
		val width = PANEL_WIDTH - PANEL_PADDING * 2
		if (confirmDeleteId != node.id) {
			button(gfx, x, y, width, BUTTON_HEIGHT, "Delete Node", DELETE_FILL) { button ->
				if (button == 0) {
					focusedField = null
					confirmDeleteId = node.id
				}
			}
			return y + BUTTON_HEIGHT + 5
		}

		gfx.fill(x, y, x + width, y + BUTTON_HEIGHT, DELETE_CONFIRM_FILL)
		outline(gfx, x, y, width, BUTTON_HEIGHT, DELETE_OUTLINE)
		gfx.text(font(), "Delete this node?", x + 6, y + 5, TEXT, false)
		val buttonY = y + BUTTON_HEIGHT + 4
		button(gfx, x, buttonY, 72, BUTTON_HEIGHT, "Confirm", DELETE_FILL) { button ->
			if (button == 0) {
				deleteSelectedNode(node)
			}
		}
		button(gfx, x + 78, buttonY, 64, BUTTON_HEIGHT, "Cancel") { button ->
			if (button == 0) {
				confirmDeleteId = null
			}
		}
		return buttonY + BUTTON_HEIGHT + 5
	}

	private fun renderToggle(gfx: GuiGraphicsExtractor, panelX: Int, y: Int, label: String, active: Boolean, toggle: () -> Unit): Int {
		val x = panelX + PANEL_PADDING
		val width = PANEL_WIDTH - PANEL_PADDING * 2
		gfx.fill(x, y, x + width, y + BUTTON_HEIGHT, if (active) TOGGLE_ACTIVE else FIELD_FILL)
		outline(gfx, x, y, width, BUTTON_HEIGHT, if (active) SELECTED_OUTLINE else OUTLINE)
		gfx.text(font(), label, x + 8, y + 5, TEXT, false)
		hitboxes.add(UiHitbox(x, y, width, BUTTON_HEIGHT) { button ->
			if (button == 0) {
				toggle()
			}
		})
		return y + BUTTON_HEIGHT + 5
	}

	private fun renderActivationRequirements(gfx: GuiGraphicsExtractor, panelX: Int, yStart: Int, node: AutoCNode): Int {
		var y = yStart
		val requirementIds = node.requiredNodeIds.toList()
		for (requiredId in requirementIds) {
			y = renderActivationRequirement(gfx, panelX, y, node, requiredId)
		}

		val x = panelX + PANEL_PADDING
		val width = PANEL_WIDTH - PANEL_PADDING * 2
		button(gfx, x, y, width, BUTTON_HEIGHT, "Add Activation Req") { button ->
			if (button == 0) {
				activationReqMenuId = if (activationReqMenuId == node.id) null else node.id
			}
		}
		y += BUTTON_HEIGHT + 5

		if (activationReqMenuId == node.id) {
			y = renderActivationRequirementMenu(gfx, panelX, y, node)
		}
		return y
	}

	private fun renderPhaseStartCondition(gfx: GuiGraphicsExtractor, panelX: Int, yStart: Int, node: AutoCNode): Int {
		var y = yStart
		val x = panelX + PANEL_PADDING
		val width = PANEL_WIDTH - PANEL_PADDING * 2
		val phase = node.onPhaseStart
		if (phase != Phase7.UNKNOWN) {
			val label = "On Phase Start: ${phaseLabel(phase)}"
			gfx.fill(x, y, x + width, y + BUTTON_HEIGHT, TOGGLE_ACTIVE)
			outline(gfx, x, y, width, BUTTON_HEIGHT, SELECTED_OUTLINE)
			gfx.text(font(), fit(label, width - 48), x + 6, y + 5, TEXT, false)
			button(gfx, x + width - 38, y + 2, 34, BUTTON_HEIGHT - 4, "X") { button ->
				if (button == 0) {
					node.onPhaseStart = Phase7.UNKNOWN
					phaseStartMenuId = null
				}
			}
			y += BUTTON_HEIGHT + 4
		}

		button(gfx, x, y, width, BUTTON_HEIGHT, "Add Phase Start") { button ->
			if (button == 0) {
				phaseStartMenuId = if (phaseStartMenuId == node.id) null else node.id
			}
		}
		y += BUTTON_HEIGHT + 5

		if (phaseStartMenuId == node.id) {
			y = renderPhaseStartMenu(gfx, panelX, y, node)
		}
		return y
	}

	private fun renderPhaseStartMenu(gfx: GuiGraphicsExtractor, panelX: Int, yStart: Int, node: AutoCNode): Int {
		var y = yStart
		val x = panelX + PANEL_PADDING
		val width = PANEL_WIDTH - PANEL_PADDING * 2
		for (phase in PHASE_START_CHOICES) {
			gfx.fill(x, y, x + width, y + BUTTON_HEIGHT, PICK_PANEL)
			outline(gfx, x, y, width, BUTTON_HEIGHT, OUTLINE)
			gfx.text(font(), phaseLabel(phase), x + 6, y + 5, TEXT, false)
			hitboxes.add(UiHitbox(x, y, width, BUTTON_HEIGHT) { button ->
				if (button == 0) {
					node.onPhaseStart = phase
					phaseStartMenuId = null
				}
			})
			y += BUTTON_HEIGHT + 4
		}
		return y
	}

	private fun renderActivationRequirement(gfx: GuiGraphicsExtractor, panelX: Int, y: Int, node: AutoCNode, requiredId: String): Int {
		val x = panelX + PANEL_PADDING
		val width = PANEL_WIDTH - PANEL_PADDING * 2
		val requiredNode = session.sourceNodes.firstOrNull { it.id == requiredId }
		val label = requiredNode?.let { other ->
			"Requires #${session.sourceNodes.indexOf(other) + 1} ${other.name()}"
		} ?: "Requires missing node"
		gfx.fill(x, y, x + width, y + BUTTON_HEIGHT, TOGGLE_ACTIVE)
		outline(gfx, x, y, width, BUTTON_HEIGHT, SELECTED_OUTLINE)
		gfx.text(font(), fit(label, width - 48), x + 6, y + 5, TEXT, false)
		button(gfx, x + width - 38, y + 2, 34, BUTTON_HEIGHT - 4, "X") { button ->
			if (button == 0) {
				node.requiredNodeIds.remove(requiredId)
			}
		}
		return y + BUTTON_HEIGHT + 4
	}

	private fun renderActivationRequirementMenu(gfx: GuiGraphicsExtractor, panelX: Int, yStart: Int, node: AutoCNode): Int {
		var y = yStart
		val candidates = session.sourceNodes.filter { it !== node && it.id !in node.requiredNodeIds }
		if (candidates.isEmpty()) {
			gfx.text(font(), "No nodes available", panelX + PANEL_PADDING, y + 5, MUTED_TEXT, false)
			return y + FIELD_HEIGHT + 5
		}
		for (candidate in candidates) {
			val x = panelX + PANEL_PADDING
			val width = PANEL_WIDTH - PANEL_PADDING * 2
			val label = "#${session.sourceNodes.indexOf(candidate) + 1} ${candidate.name()}"
			gfx.fill(x, y, x + width, y + BUTTON_HEIGHT, PICK_PANEL)
			outline(gfx, x, y, width, BUTTON_HEIGHT, OUTLINE)
			gfx.text(font(), fit(label, width - 12), x + 6, y + 5, TEXT, false)
			hitboxes.add(UiHitbox(x, y, width, BUTTON_HEIGHT) { button ->
				if (button == 0) {
					node.requiredNodeIds.add(candidate.id)
					activationReqMenuId = null
				}
			})
			y += BUTTON_HEIGHT + 4
		}
		return y
	}

	private fun button(
		gfx: GuiGraphicsExtractor,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		label: String,
		fill: Int = FIELD_FILL,
		click: (Int) -> Unit
	) {
		gfx.fill(x, y, x + width, y + height, fill)
		outline(gfx, x, y, width, height, OUTLINE)
		gfx.centeredText(font(), label, x + width / 2, y + 5, TEXT)
		hitboxes.add(UiHitbox(x, y, width, height, click))
	}

	private fun renderPickMenu(gfx: GuiGraphicsExtractor) {
		val menu = pickMenu ?: return
		val rows = menu.entries.size
		val menuWidth = 150
		val rowHeight = 18
		val x = menu.x.coerceIn(4, max(4, width - menuWidth - 4))
		val y = menu.y.coerceIn(4, max(4, height - rows * rowHeight - 4))
		gfx.fill(x, y, x + menuWidth, y + rows * rowHeight, PICK_PANEL)
		outline(gfx, x, y, menuWidth, rows * rowHeight, SELECTED_OUTLINE)
		menu.entries.forEachIndexed { index, entry ->
			val rowY = y + index * rowHeight
			val hovered = selectedId == entry.node.id
			gfx.fill(x, rowY, x + menuWidth, rowY + rowHeight, if (hovered) FIELD_ACTIVE else PICK_PANEL)
			gfx.text(font(), fit("#${entry.index + 1} ${entry.node.name()}", menuWidth - 10), x + 5, rowY + 5, TEXT, false)
			hitboxes.add(UiHitbox(x, rowY, menuWidth, rowHeight) { button ->
				if (button == 0) {
					selectNode(entry.node.id)
					pickMenu = null
				}
			})
		}
	}

	private fun selectProjected(mouseX: Double, mouseY: Double) {
		val hits = projections
			.filter { abs(it.x - mouseX) <= CLICK_RADIUS && abs(it.y - mouseY) <= CLICK_RADIUS }
			.sortedWith(compareBy<ProjectedNode> { it.entry.index }.thenBy { it.depth })
			.map { it.entry }
		when (hits.size) {
			0 -> selectNode(null)
			1 -> selectNode(hits.first().node.id)
			else -> pickMenu = PickMenu(mouseX.toInt(), mouseY.toInt(), hits)
		}
	}

	private fun selectNode(id: String?) {
		if (selectedId != id) {
			confirmDeleteId = null
			focusedField = null
			activationReqMenuId = null
			phaseStartMenuId = null
		}
		selectedId = id
	}

	private fun project(entry: AutoC.EditEntry): ProjectedNode? {
		val relX = entry.worldPos.x - session.playerWorldPos.x
		val relY = entry.worldPos.y - session.playerWorldPos.y
		val relZ = entry.worldPos.z - session.playerWorldPos.z
		return projectRelative(relX, relY, relZ)?.let { ProjectedNode(entry, it.x, it.y, it.depth) }
	}

	private fun projectRelative(relX: Double, relY: Double, relZ: Double): ProjectedPoint? {
		val yaw = Math.toRadians(orbitYaw)
		val pitch = Math.toRadians(orbitPitch)
		val cosY = cos(yaw)
		val sinY = sin(yaw)
		val xYaw = relX * cosY - relZ * sinY
		val zYaw = relX * sinY + relZ * cosY
		val cosP = cos(pitch)
		val sinP = sin(pitch)
		val yPitch = relY * cosP - zYaw * sinP
		val zPitch = relY * sinP + zYaw * cosP + CAMERA_DISTANCE
		if (zPitch <= 1.0) {
			return null
		}
		val viewWidth = viewportWidth()
		val scale = min(viewWidth, height).coerceAtLeast(1) * PROJECTION_SCALE / zPitch
		return ProjectedPoint(
			x = viewWidth * 0.5 + xYaw * scale,
			y = height * 0.5 - yPitch * scale,
			depth = zPitch
		)
	}

	private fun selectedNode(): AutoCNode? {
		val id = selectedId ?: return null
		return session.sourceNodes.firstOrNull { it.id == id }
	}

	private fun applyFieldText(field: FieldKey) {
		val node = selectedNode() ?: return
		when (field) {
			is FieldKey.Base -> applyBaseFieldText(node, field.field)
			is FieldKey.Json -> applyJsonFieldText(node, field)
		}
		session.module.refreshEditSession(session)
	}

	private fun applyBaseFieldText(node: AutoCNode, field: EditField) {
		when (field) {
			EditField.X -> focusedText.toDoubleOrNull()?.let { node.pos.x = it }
			EditField.Y -> focusedText.toDoubleOrNull()?.let { node.pos.y = it }
			EditField.Z -> focusedText.toDoubleOrNull()?.let { node.pos.z = it }
			EditField.RADIUS -> focusedText.toFloatOrNull()?.takeIf { it > 0.0f }?.let { node.radius = it }
			EditField.WAIT -> focusedText.toDoubleOrNull()?.let { node.waitSeconds = it.coerceAtLeast(0.0) }
			EditField.MAX_A -> focusedText.toIntOrNull()?.let { node.maxActivationsPerMinute = it.coerceAtLeast(0) }
			EditField.ORDER -> focusedText.toIntOrNull()?.let { moveSelectedTo(it - 1) }
		}
		node.calculate()
	}

	private fun applyJsonFieldText(node: AutoCNode, field: FieldKey.Json) {
		updateNodeJson(node) { json ->
			when (field.kind) {
				JsonValueKind.STRING -> json.addProperty(field.path, focusedText)
				JsonValueKind.NUMBER -> applyJsonNumber(json, field.path, focusedText.toDoubleOrNull() ?: return@updateNodeJson)
				JsonValueKind.POS_ARRAY -> json.add(field.path, parsePosArray(focusedText) ?: return@updateNodeJson)
				JsonValueKind.STRING_ARRAY -> json.add(field.path, parseStringArray(focusedText))
			}
		}
	}

	private fun focusNextField(field: FieldKey) {
		val node = selectedNode() ?: return
		if (field !is FieldKey.Base) {
			focusedField = null
			return
		}
		val next = EditField.entries[(field.field.ordinal + 1) % EditField.entries.size]
		val nextField = FieldKey.Base(next)
		focusedField = nextField
		focusedText = currentFieldText(node, nextField)
	}

	private fun currentFieldText(node: AutoCNode, field: FieldKey): String =
		when (field) {
			is FieldKey.Base -> when (field.field) {
				EditField.X -> formatNumber(node.pos.x)
				EditField.Y -> formatNumber(node.pos.y)
				EditField.Z -> formatNumber(node.pos.z)
				EditField.RADIUS -> formatNumber(node.radius.toDouble())
				EditField.WAIT -> formatNumber(node.waitSeconds)
				EditField.MAX_A -> node.maxActivationsPerMinute.toString()
				EditField.ORDER -> (session.sourceNodes.indexOf(node).coerceAtLeast(0) + 1).toString()
			}
			is FieldKey.Json -> jsonFieldText(node, field)
		}

	private fun moveSelected(delta: Int) {
		val node = selectedNode() ?: return
		val index = session.sourceNodes.indexOf(node)
		if (index == -1) {
			return
		}
		moveSelectedTo(index + delta)
	}

	private fun moveSelectedTo(targetIndexRaw: Int) {
		val node = selectedNode() ?: return
		val index = session.sourceNodes.indexOf(node)
		if (index == -1 || session.sourceNodes.isEmpty()) {
			return
		}
		val targetIndex = targetIndexRaw.coerceIn(0, session.sourceNodes.lastIndex)
		if (targetIndex == index) {
			return
		}
		session.sourceNodes.removeAt(index)
		session.sourceNodes.add(targetIndex, node)
		session.module.refreshEditSession(session)
		focusedField?.let { focusedText = currentFieldText(node, it) }
	}

	private fun deleteSelectedNode(node: AutoCNode) {
		val index = session.sourceNodes.indexOf(node)
		if (index == -1) {
			confirmDeleteId = null
			selectNode(null)
			return
		}
		session.sourceNodes.removeAt(index)
		session.module.refreshEditSession(session)
		val next = session.sourceNodes.getOrNull(index) ?: session.sourceNodes.getOrNull(index - 1)
		confirmDeleteId = null
		selectNode(next?.id)
		panelScroll = 0.0
	}

	private fun updateNodeJson(node: AutoCNode, mutate: (JsonObject) -> Unit) {
		val index = session.sourceNodes.indexOf(node)
		if (index == -1) {
			return
		}
		val json = node.serialize()
		mutate(json)
		val replacement = runCatching {
			NODE_GSON.fromJson(json, AutoCNode::class.java)?.also { it.calculate() }
		}.getOrNull() ?: return
		session.sourceNodes[index] = replacement
		selectedId = replacement.id
		session.module.refreshEditSession(session)
	}

	private fun applyJsonNumber(json: JsonObject, path: String, value: Double) {
		val split = path.split('.')
		if (split.size == 1) {
			json.addProperty(path, value)
			return
		}
		val parent = json.getAsJsonObject(split[0]) ?: return
		parent.addProperty(split[1], value)
	}

	private fun jsonFieldText(node: AutoCNode, field: FieldKey.Json): String {
		val value = jsonValue(node.serialize(), field.path) ?: return ""
		return when (field.kind) {
			JsonValueKind.STRING -> value.asString
			JsonValueKind.NUMBER -> formatNumber(value.asDouble)
			JsonValueKind.POS_ARRAY -> formatPosArray(value.asJsonArray)
			JsonValueKind.STRING_ARRAY -> formatStringArray(value.asJsonArray)
		}
	}

	private fun jsonValue(json: JsonObject, path: String): JsonElement? {
		val split = path.split('.')
		if (split.size == 1) {
			return json.get(path)
		}
		return json.getAsJsonObject(split[0])?.get(split[1])
	}

	private fun isPosObject(obj: JsonObject): Boolean =
		obj.has("x") && obj.has("y") && obj.has("z")

	private fun isPosArray(array: JsonArray): Boolean =
		array.all { it.isJsonObject && isPosObject(it.asJsonObject) }

	private fun formatPosArray(array: JsonArray): String =
		array.joinToString("; ") { element ->
			val obj = element.asJsonObject
			"${formatNumber(obj.get("x").asDouble)},${formatNumber(obj.get("y").asDouble)},${formatNumber(obj.get("z").asDouble)}"
		}

	private fun parsePosArray(raw: String): JsonArray? {
		val array = JsonArray()
		if (raw.isBlank()) {
			return array
		}
		for (entry in raw.split(';')) {
			val parts = entry.split(',').map { it.trim() }
			if (parts.size != 3) {
				return null
			}
			val x = parts[0].toDoubleOrNull() ?: return null
			val y = parts[1].toDoubleOrNull() ?: return null
			val z = parts[2].toDoubleOrNull() ?: return null
			val obj = JsonObject()
			obj.addProperty("x", x)
			obj.addProperty("y", y)
			obj.addProperty("z", z)
			array.add(obj)
		}
		return array
	}

	private fun formatStringArray(array: JsonArray): String =
		array.joinToString(",") { it.asString }

	private fun parseStringArray(raw: String): JsonArray {
		val array = JsonArray()
		raw.split(',', ' ')
			.map { it.trim() }
			.filter { it.isNotBlank() }
			.forEach(array::add)
		return array
	}

	private fun viewportWidth(): Int =
		max(1, width - PANEL_WIDTH)

	private fun drawLine(gfx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, colour: Int) {
		val steps = max(abs(x2 - x1), abs(y2 - y1)).coerceAtLeast(1)
		for (i in 0..steps step 3) {
			val x = x1 + ((x2 - x1) * i / steps.toFloat()).roundToInt()
			val y = y1 + ((y2 - y1) * i / steps.toFloat()).roundToInt()
			gfx.fill(x, y, x + 1, y + 1, colour)
		}
	}

	private fun outline(gfx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, colour: Int) {
		if (width <= 1 || height <= 1) {
			return
		}
		gfx.fill(x, y, x + width, y + 1, colour)
		gfx.fill(x, y + height - 1, x + width, y + height, colour)
		gfx.fill(x, y, x + 1, y + height, colour)
		gfx.fill(x + width - 1, y, x + width, y + height, colour)
	}

	private fun formatNumber(value: Double): String =
		if (abs(value - value.toLong()) < 1.0E-6) value.toLong().toString() else "%.3f".format(value)

	private fun fit(text: String, maxWidth: Int): String {
		val font = font()
		if (font.width(text) <= maxWidth) {
			return text
		}
		if (maxWidth <= font.width("...")) {
			return ""
		}
		return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "..."
	}

	private fun phaseLabel(phase: Phase7): String =
		if (phase == Phase7.P5) "p5" else phase.name.lowercase()

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

	private data class ProjectedPoint(
		val x: Double,
		val y: Double,
		val depth: Double
	)

	private data class ProjectedNode(
		val entry: AutoC.EditEntry,
		val x: Double,
		val y: Double,
		val depth: Double
	)

	private data class PickMenu(
		val x: Int,
		val y: Int,
		val entries: List<AutoC.EditEntry>
	)

	private enum class EditField {
		X,
		Y,
		Z,
		RADIUS,
		WAIT,
		MAX_A,
		ORDER
	}

	private sealed class FieldKey {
		data class Base(val field: EditField) : FieldKey()
		data class Json(val path: String, val kind: JsonValueKind) : FieldKey()
	}

	private enum class JsonValueKind {
		STRING,
		NUMBER,
		POS_ARRAY,
		STRING_ARRAY
	}

	private companion object {
		private val NODE_GSON = GsonBuilder()
			.registerTypeHierarchyAdapter(AutoCNode::class.java, AutoCNodeAdapter())
			.create()
		private val SHARED_PROPERTY_KEYS = setOf(
			"type",
			"id",
			"pos",
			"radius",
			"wait",
			"awaitSecret",
			"notStart",
			"maxA",
			"onTerminalExit",
			"onPhaseStart",
			"requires"
		)
		private val PHASE_START_CHOICES = listOf(
			Phase7.P1,
			Phase7.P2,
			Phase7.S1,
			Phase7.S2,
			Phase7.S3,
			Phase7.S4,
			Phase7.P4,
			Phase7.P5
		)
		private const val PANEL_WIDTH = 238
		private const val PANEL_PADDING = 10
		private const val FIELD_HEIGHT = 18
		private const val BUTTON_HEIGHT = 18
		private const val NODE_SIZE = 4
		private const val NODE_SIZE_SELECTED = 6
		private const val CLICK_RADIUS = 9.0
		private const val CAMERA_DISTANCE = 46.0
		private const val PROJECTION_SCALE = 1.18
		private const val ORBIT_SPEED = 0.35
		private const val DRAG_THRESHOLD = 3.0
		private const val SCROLL_STEP = 24.0
		private const val BLACK = 0xFF000000.toInt()
		private const val PANEL = 0xEE101216.toInt()
		private const val PICK_PANEL = 0xF0181B20.toInt()
		private const val FIELD_FILL = 0xFF1C2026.toInt()
		private const val FIELD_ACTIVE = 0xFF26313D.toInt()
		private const val TOGGLE_ACTIVE = 0xFF264231.toInt()
		private const val DELETE_FILL = 0xFF5A2428.toInt()
		private const val DELETE_CONFIRM_FILL = 0xFF3A2024.toInt()
		private const val DELETE_OUTLINE = 0xFFE45D65.toInt()
		private const val OUTLINE = 0xFF343A42.toInt()
		private const val NODE_OUTLINE = 0xFF111111.toInt()
		private const val SELECTED_OUTLINE = 0xFFE6EDF3.toInt()
		private const val TEXT = 0xFFE8EAED.toInt()
		private const val MUTED_TEXT = 0xFF9AA3AD.toInt()
		private const val AXIS_X = 0xFFCA4A4A.toInt()
		private const val AXIS_Y = 0xFF52A867.toInt()
		private const val AXIS_Z = 0xFF4D7ED8.toInt()
	}
}
