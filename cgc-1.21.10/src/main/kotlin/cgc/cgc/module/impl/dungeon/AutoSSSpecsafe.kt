package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.data.Keybind
import cgc.cgc.data.Phase7
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.BlockChangeModule
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ChatMessageModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.HudRenderModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.WorldRenderStartModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.utils.DungeonUtils
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShapeRenderer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.min

class AutoSSSpecsafe : CgcModule(
	id = "AutoSSSpecsafe",
	displayName = "Auto SS",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically solves spectator safe Simon Says.",
	defaultEnabled = false
), ClientTickModule, WorldRenderStartModule, WorldRenderExtractModule, HudRenderModule, WorldLoadModule, ChatMessageModule, BlockChangeModule {
	private val resetKey = KeybindSetting("Reset Key", Keybind(action = this::SSR))
	private val autoStart = BooleanSetting("Auto start", true)
	private val forceSkyblock = BooleanSetting("Force Skyblock", false)
	private val autoStartDelay = NumberSetting("Auto start delay (MS)", 10.0, 500.0, 120.0, 10.0)
	private val aimSpeed = NumberSetting("Aim Speed", 0.5, 2.0, 1.0, 0.05, "x")
	private val aimRandomness = NumberSetting("Aim Randomness", 0.0, 1.0, 0.45, 0.05)
	private val aimTolerance = NumberSetting("Aim Tolerance", 0.0, 0.45, 0.08, 0.01)
	private val overshootStrength = NumberSetting("Overshoot Strength", 0.0, 1.5, 1.0, 0.05)
	private val microCorrection = NumberSetting("Micro Correction", 0.0, 1.0, 0.45, 0.05)
	private val clickDelayMin = NumberSetting("Click Delay Min", 5.0, 150.0, 24.0, 1.0, " ms")
	private val clickDelayMax = NumberSetting("Click Delay Max", 10.0, 180.0, 44.0, 1.0, " ms")
	private val donePopup = BooleanSetting("Done Popup", true)
	private val fillColor = ColourSetting("Button Fill Color", Colour(85, 255, 85))
	private val outlineColor = ColourSetting("Button Outline Color", Colour(0, 170, 0))

	private val clicks = arrayListOf<BlockPos>()
	private val allButtons = arrayListOf<Vec3>()
	private val aimController = SimonSaysAimController()

	private var lastClickTime = System.currentTimeMillis()
	private var currentClickDelayMs = randomClickDelayMs()
	private var state = 0
	private var doneFirst = false
	private var doingSS = false
	private var targetButton: BlockPos? = null
	private var targetAimPoint: Vec3? = null
	private var startAimPoint: Vec3? = null
	private var targetIsStart = false
	private var targetPreAim = false
	private var targetWaitingForButton = false
	private var preAimButton: BlockPos? = null
	private var startClicksRemaining = 0
	private var nextStartClickAt = 0L
	private var patternSettledAt = 0L
	private var clickedButton: Vec3? = null
	private var donePopupUntil = 0L

	init {
		registerProperty(
			resetKey,
			autoStart,
			forceSkyblock,
			autoStartDelay,
			aimSpeed,
			aimRandomness,
			aimTolerance,
			overshootStrength,
			microCorrection,
			clickDelayMin,
			clickDelayMax,
			donePopup,
			fillColor,
			outlineColor
		)
	}

	override fun onClientTick(client: Minecraft) {
		if (!areaCheck() || client.player == null || client.level == null) {
			clearTarget()
			return
		}

		if (targetButton != null) {
			tickTarget(client)
			return
		}

		if (!doingSS || client.player!!.distanceToSqr(START_BUTTON) > 25.0) {
			return
		}

		val now = System.currentTimeMillis()
		if (startClicksRemaining > 0) {
			if (now >= nextStartClickAt) {
				beginLookClick(startButtonPos(), true)
			}
			return
		}

		if (now - lastClickTime < currentClickDelayMs) {
			return
		}

		if (!client.level!!.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON)) {
			return
		}

		if (now < patternSettledAt) {
			return
		}

		if (!doneFirst && clicks.size == 3) {
			clicks.removeAt(0)
			allButtons.removeAt(0)
		}

		doneFirst = true
		if (state >= clicks.size) {
			return
		}

		val next = clicks[state]
		if (client.level!!.getBlockState(next).`is`(Blocks.STONE_BUTTON)) {
			beginLookClick(next, false)
		}
	}

	override fun onWorldRenderStart() {
		val client = Minecraft.getInstance()
		if (areaCheck() && targetButton != null && client.player != null) {
			updateAimRotation(client)
		}
	}

	override fun onWorldRenderExtract(context: WorldRenderContext) {
		val client = Minecraft.getInstance()
		if (!areaCheck() || client.player == null || client.level == null) {
			return
		}

		val clicked = clickedButton
		if (clicked != null && System.currentTimeMillis() - lastClickTime <= currentClickDelayMs) {
			renderButton(context, client.level!!, BlockPos.containing(clicked), fillColor.value, outlineColor.value)
		}
	}

	override fun onHudRender(gfx: GuiGraphics) {
		if (!donePopup.value || System.currentTimeMillis() > donePopupUntil) {
			return
		}

		val client = Minecraft.getInstance()
		val text = "SS Done"
		val centerX = gfx.guiWidth() / 2
		val centerY = gfx.guiHeight() / 2
		val textWidth = client.font.width(text)
		val boxPaddingX = 12
		val boxPaddingY = 7
		gfx.fill(
			centerX - textWidth / 2 - boxPaddingX,
			centerY - client.font.lineHeight / 2 - boxPaddingY,
			centerX + textWidth / 2 + boxPaddingX,
			centerY + client.font.lineHeight / 2 + boxPaddingY,
			0xAA000000.toInt()
		)
		gfx.drawCenteredString(client.font, text, centerX, centerY - client.font.lineHeight / 2, 0xFF55FF55.toInt())
	}

	override fun onWorldLoad() {
		resetState()
	}

	override fun onChatMessage(message: String) {
		if (areaCheck() && autoStart.value && Minecraft.getInstance().player != null) {
			if (message == "[BOSS] Goldor: Who dares trespass into my domain?") {
				start()
			}
		}
	}

	override fun onBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		if (!doingSS
			|| startClicksRemaining > 0
			|| !areaCheck()
			|| !newState.`is`(Blocks.SEA_LANTERN)
		) {
			return
		}

		if (pos.x == 111 && pos.y >= 120 && pos.y <= 123 && pos.z >= 92 && pos.z <= 95) {
			val button = BlockPos(110, pos.y, pos.z)
			markPatternChanged()
			if (clicks.size == 2 && clicks.first() == button && !doneFirst) {
				doneFirst = true
				clicks.removeAt(0)
				allButtons.removeAt(0)
			}

			if (targetPreAim && button == preAimButton) {
				clicks.clear()
				allButtons.clear()
				clicks.add(button)
				allButtons.add(Vec3.atLowerCornerOf(button))
				state = 0
				doneFirst = true
				targetPreAim = false
				targetWaitingForButton = true
				return
			}

			if (!clicks.contains(button)) {
				state = 0
				clicks.add(button)
				allButtons.add(Vec3.atLowerCornerOf(button))
				if (targetPreAim && button == clicks.first()) {
					targetPreAim = false
					targetWaitingForButton = true
				}
			}
		}
	}

	fun SSR() {
		if (areaCheck()) {
			start()
		}
	}

	override fun onEnable() {
		resetState()
		resetKey.register()
	}

	override fun onDisable() {
		resetKey.unregister()
		resetState()
	}

	override fun reset() {
		resetState()
	}

	private fun start() {
		val client = Minecraft.getInstance()
		val player = client.player
		if (player == null || client.level == null || player.distanceToSqr(START_BUTTON) > 25.0) {
			return
		}

		allButtons.clear()
		chat("Starting spectator safe SS!")
		resetState()
		doingSS = true
		startAimPoint = getAimPoint(client.level!!, startButtonPos())
		startClicksRemaining = 3
		nextStartClickAt = System.currentTimeMillis() + randomAutoStartDelayMs()
	}

	private fun tickTarget(client: Minecraft) {
		val button = targetButton
		if (button == null || targetAimPoint == null || client.player == null || client.level == null || client.gameMode == null) {
			clearTarget()
			return
		}

		if (!targetPreAim && !targetWaitingForButton && !client.level!!.getBlockState(button).`is`(Blocks.STONE_BUTTON)) {
			clearTarget()
			return
		}

		if (client.player!!.distanceToSqr(Vec3.atCenterOf(button)) > MAX_BUTTON_DISTANCE_SQ) {
			chat("SS button too far!")
			clearTarget()
			return
		}

		val aimResult = updateAimRotation(client)
		if (aimResult == null) {
			clearTarget()
			return
		}

		if (targetPreAim) {
			if (aimResult.finished) {
				clearTarget()
			}
			return
		}

		if (System.currentTimeMillis() - lastClickTime < currentClickDelayMs) {
			return
		}

		if (aimResult.readyToClick) {
			clickTarget(client)
		}
	}

	private fun updateAimRotation(client: Minecraft): AimUpdateResult? {
		if (!aimController.hasTarget()) {
			return null
		}

		val player = client.player ?: return null
		val result = aimController.update()
		applyRotation(player, result.rotation)
		return result
	}

	private fun applyRotation(player: LocalPlayer, rotation: Rotation) {
		player.yRot = rotation.yaw
		player.xRot = rotation.pitch.coerceIn(-90.0f, 90.0f)
		player.yHeadRot = rotation.yaw
	}

	private fun beginLookClick(button: BlockPos, startButton: Boolean) {
		beginLookClick(button, startButton, false)
	}

	private fun beginLookClick(
		button: BlockPos,
		startButton: Boolean,
		flowingRetarget: Boolean,
		modeOverride: AimMode? = null
	) {
		val client = Minecraft.getInstance()
		val player = client.player
		val level = client.level
		if (player == null || level == null) {
			return
		}

		targetButton = button
		targetIsStart = startButton
		targetPreAim = false
		targetWaitingForButton = false
		targetAimPoint = if (startButton && startAimPoint != null) startAimPoint else getAimPoint(level, button)
		val aimPoint = targetAimPoint ?: return
		val mode = modeOverride ?: when {
			startButton -> AimMode.START_BUTTON
			flowingRetarget -> AimMode.CHAINED_RETARGET
			else -> AimMode.NORMAL_BUTTON
		}
		aimController.start(player, aimPoint, getAimSettings(), mode)
	}

	private fun clickTarget(client: Minecraft): Boolean {
		val button = targetButton ?: return false
		val player = client.player ?: return false
		val level = client.level ?: return false
		val gameMode = client.gameMode ?: return false
		if (!level.getBlockState(button).`is`(Blocks.STONE_BUTTON)) {
			return false
		}

		if (player.distanceToSqr(Vec3.atCenterOf(button)) > MAX_BUTTON_DISTANCE_SQ) {
			return false
		}

		val hit = getLookHit(client, button)
		if (hit == null || hit.type == HitResult.Type.MISS || hit.blockPos != button) {
			return false
		}

		val clicked = button
		val clickedStart = targetIsStart
		lastClickTime = System.currentTimeMillis()
		currentClickDelayMs = randomClickDelayMs()
		clickedButton = Vec3.atLowerCornerOf(clicked)
		gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
		player.swing(InteractionHand.MAIN_HAND)
		if (clickedStart) {
			startClicksRemaining = max(0, startClicksRemaining - 1)
			nextStartClickAt = if (startClicksRemaining > 0) {
				System.currentTimeMillis() + randomStartClickDelayMs()
			} else {
				0L
			}
		}
		if (!clickedStart) {
			state++
			if (state < clicks.size) {
				if (beginNextSequenceButton(client, true)) {
					return true
				}
				clearTarget()
				return true
			}
			if (clicks.size >= FINAL_SEQUENCE_LENGTH) {
				finishSimonSays()
				return true
			}
			if (beginFirstButtonPreAim(client)) {
				return true
			}
		}

		clearTarget()
		return true
	}

	private fun beginNextSequenceButton(client: Minecraft, flowingRetarget: Boolean): Boolean {
		if (state >= clicks.size) {
			return false
		}

		val next = clicks[state]
		if (!client.level!!.getBlockState(next).`is`(Blocks.STONE_BUTTON)) {
			return false
		}

		beginLookClick(next, false, flowingRetarget)
		return true
	}

	private fun beginFirstButtonPreAim(client: Minecraft): Boolean {
		if (clicks.isEmpty()) {
			return false
		}

		val first = clicks.first()
		if (!client.level!!.getBlockState(first).`is`(Blocks.STONE_BUTTON)) {
			return false
		}

		beginLookClick(first, false, false, AimMode.PRE_AIM)
		targetPreAim = true
		preAimButton = first
		return true
	}

	private fun getLookHit(client: Minecraft, expected: BlockPos): BlockHitResult? {
		val player = client.player ?: return null
		val level = client.level ?: return null
		val eye = player.eyePosition
		val look = player.lookAngle
		val end = eye.add(look.scale(RAYCAST_DISTANCE))
		val hit = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
		return if (hit.type != HitResult.Type.MISS && hit.blockPos == expected) hit else null
	}

	private fun getAimPoint(level: ClientLevel, pos: BlockPos): Vec3 {
		val state = level.getBlockState(pos)
		val shape = state.getShape(level, pos)
		val box = if (shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
		val random = ThreadLocalRandom.current()
		val center = box.center
		val tolerance = getDouble(aimTolerance)
		val xDepth = box.xsize <= box.ysize && box.xsize <= box.zsize
		val yDepth = box.ysize < box.xsize && box.ysize <= box.zsize
		val x = center.x + if (xDepth) 0.0 else middleOffset(random, min(tolerance, max(0.0, box.xsize * 0.35)))
		val y = center.y + if (yDepth) 0.0 else middleOffset(random, min(tolerance, max(0.0, box.ysize * 0.35)))
		val z = center.z + if (!xDepth && !yDepth) 0.0 else middleOffset(random, min(tolerance, max(0.0, box.zsize * 0.35)))
		return Vec3(x, y, z)
	}

	private fun renderButton(context: WorldRenderContext, level: ClientLevel, pos: BlockPos, colorFill: Colour, colorOutline: Colour) {
		val state = level.getBlockState(pos)
		val shape: VoxelShape = state.getShape(level, pos)
		if (!shape.isEmpty) {
			val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
			val box = shape.bounds().move(pos)
			val matrices = context.matrices()
			matrices.pushPose()
			matrices.translate(-camera.x, -camera.y, -camera.z)
			ShapeRenderer.addChainedFilledBoxVertices(
				matrices,
				context.consumers().getBuffer(RenderType.debugFilledBox()),
				box.minX,
				box.minY,
				box.minZ,
				box.maxX,
				box.maxY,
				box.maxZ,
				colorFill.red / 255.0f,
				colorFill.green / 255.0f,
				colorFill.blue / 255.0f,
				colorFill.alpha / 255.0f
			)
			ShapeRenderer.renderLineBox(
				matrices.last(),
				context.consumers().getBuffer(RenderType.lines()),
				box,
				colorOutline.red / 255.0f,
				colorOutline.green / 255.0f,
				colorOutline.blue / 255.0f,
				colorOutline.alpha / 255.0f
			)
			matrices.popPose()
		}
	}

	private fun markPatternChanged() {
		patternSettledAt = System.currentTimeMillis() + randomClickDelayMs()
	}

	private fun areaCheck(): Boolean =
		forceSkyblock.value ||
			Location.area.isArea(Island.DUNGEON) &&
			(Location.floor == Floor.F7 || Location.floor == Floor.M7) &&
			DungeonUtils.isPhase(Phase7.P3)

	private fun resetState() {
		allButtons.clear()
		clicks.clear()
		clearTarget()
		startAimPoint = null
		startClicksRemaining = 0
		nextStartClickAt = 0L
		patternSettledAt = 0L
		state = 0
		doneFirst = false
		doingSS = false
		donePopupUntil = 0L
	}

	private fun finishSimonSays() {
		doingSS = false
		donePopupUntil = System.currentTimeMillis() + DONE_POPUP_MS
		clearTarget()
	}

	private fun clearTarget() {
		targetButton = null
		targetAimPoint = null
		targetIsStart = false
		targetPreAim = false
		targetWaitingForButton = false
		preAimButton = null
		aimController.clear()
	}

	private fun middleOffset(random: ThreadLocalRandom, maxOffset: Double): Double {
		if (maxOffset <= 0.0) {
			return 0.0
		}

		return (random.nextDouble(-maxOffset, maxOffset) + random.nextDouble(-maxOffset, maxOffset)) * 0.5
	}

	private fun getAimSettings(): AimSettings =
		AimSettings(
			speed = getDouble(aimSpeed),
			randomness = getDouble(aimRandomness),
			overshootStrength = getDouble(overshootStrength),
			microCorrection = getDouble(microCorrection)
		)

	private fun getLong(setting: NumberSetting): Long =
		setting.value.toLong()

	private fun getDouble(setting: NumberSetting): Double =
		setting.value.toDouble()

	private fun randomAutoStartDelayMs(): Long {
		val baseDelay = getLong(autoStartDelay)
		return ThreadLocalRandom.current().nextLong(baseDelay, baseDelay + AUTO_START_DELAY_RANDOM_EXTRA_MS + 1L)
	}

	private fun randomClickDelayMs(): Long {
		val minDelay = getLong(clickDelayMin).coerceAtLeast(1L)
		val maxDelay = getLong(clickDelayMax).coerceAtLeast(1L)
		val low = min(minDelay, maxDelay)
		val configuredHigh = max(minDelay, maxDelay)
		val high = if (configuredHigh <= low) low + MIN_CLICK_DELAY_VARIANCE_MS else configuredHigh
		return ThreadLocalRandom.current().nextLong(low, high + 1L)
	}

	private fun chat(message: String) {
		Minecraft.getInstance().player?.displayClientMessage(Component.literal(message), false)
	}

	private companion object {
		private val START_BUTTON = Vec3(110.875, 121.5, 91.5)
		private val DETECT = BlockPos(110, 123, 92)
		private const val MAX_BUTTON_DISTANCE_SQ = 36.0
		private const val RAYCAST_DISTANCE = 6.0
		private const val FINAL_SEQUENCE_LENGTH = 5
		private const val DONE_POPUP_MS = 1500L
		private const val MIN_CLICK_DELAY_VARIANCE_MS = 4L
		private const val MIN_START_CLICK_DELAY_MS = 105L
		private const val MAX_START_CLICK_DELAY_MS = 130L
		private const val AUTO_START_DELAY_RANDOM_EXTRA_MS = 40L

		private fun startButtonPos(): BlockPos =
			BlockPos.containing(START_BUTTON.x, START_BUTTON.y, START_BUTTON.z)

		private fun randomStartClickDelayMs(): Long =
			ThreadLocalRandom.current().nextLong(MIN_START_CLICK_DELAY_MS, MAX_START_CLICK_DELAY_MS + 1L)
	}
}
