package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Colour
import cgc.cgc.data.Keybind
import cgc.cgc.data.Phase7
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.ActionBarMessageModule
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
import cgc.cgc.runtime.CgcRenderPrimitives
import cgc.cgc.utils.DungeonUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.rendertype.RenderTypes
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
import kotlin.math.sqrt

class AutoSSSpecsafe : CgcModule(
	id = "AutoSSSpecsafe",
	displayName = "Auto SS",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically solves spectator safe Simon Says.",
	defaultEnabled = false
), ClientTickModule, WorldRenderStartModule, WorldRenderExtractModule, HudRenderModule, WorldLoadModule, ChatMessageModule, ActionBarMessageModule, BlockChangeModule {
	private val resetKey = KeybindSetting("Reset Key", Keybind(action = this::SSR))
	private val autoStart = BooleanSetting("Auto start", true)
	private val autoRestartSs = BooleanSetting("Auto restart ss", false)
	private val forceSkyblock = BooleanSetting("Force Skyblock", false)
	private val autoStartDelay = NumberSetting("Auto start delay (MS)", 10.0, 500.0, 120.0, 10.0)
	private val aimSpeed = NumberSetting("Aim Speed", 0.5, 2.0, 1.0, 0.05, "x")
	private val waitCorrectionAimSpeed = NumberSetting("Wait Correction Aim Speed", 0.1, 2.0, 0.6, 0.05, "x")
	private val practiceAimSpeed = NumberSetting("Practice Aim Speed", 0.1, 2.0, 0.6, 0.05, "x")
	private val practiceAimCycles = BooleanSetting("Practice Aim Cycles", true)
	private val donePopup = BooleanSetting("Done Popup", false)
	private val fillColor = ColourSetting("Button Fill Color", Colour(85, 255, 85))
	private val outlineColor = ColourSetting("Button Outline Color", Colour(0, 170, 0))

	private val clicks = arrayListOf<BlockPos>()
	private val allButtons = arrayListOf<Vec3>()
	private val practiceButtons = arrayListOf<BlockPos>()
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
	private var targetOpeningPreAim = false
	private var targetPractice = false
	private var targetPracticeReturningToStart = false
	private var targetPracticeIndex = 0
	private var targetWaitingForButton = false
	private var targetWaitCorrectionStarted = false
	private var targetWaitCorrectionOnRealButton = false
	private var preAimButton: BlockPos? = null
	private var openingPreAimButton: BlockPos? = null
	private var openingPreAimAt = 0L
	private var startClicksRemaining = 0
	private var nextStartClickAt = 0L
	private var patternSettledAt = 0L
	private var clickedButton: Vec3? = null
	private var donePopupUntil = 0L
	private var completedSequenceCount = 0
	private var practiceUnlocked = false
	private var practicePassesCompleted = 0
	private var practiceMaxPasses = 0
	private var practiceReturnAt = 0L
	private var practiceResumeAt = 0L
	private var autoRestartAt = 0L
	private var previousGridButton: BlockPos? = null
	private var previousGridMotion: GridMotion? = null

	init {
		registerProperty(
			resetKey,
			autoStart,
			autoRestartSs,
			forceSkyblock,
			autoStartDelay,
			aimSpeed,
			waitCorrectionAimSpeed,
			practiceAimSpeed,
			practiceAimCycles,
			donePopup,
			fillColor,
			outlineColor
		)
	}

	override fun onClientTick(client: Minecraft) {
		if (!areaCheck() || client.player == null || client.level == null) {
			clearAutoRestart()
			clearTarget()
			return
		}

		val now = System.currentTimeMillis()
		if (tickAutoRestart(now)) {
			return
		}

		if (targetButton != null && (targetPreAim || targetWaitingForButton) && shouldYieldPreAim(client)) {
			if (clickPreAimedCurrentButtonIfReady(client)) {
				return
			}
			clearTarget()
		}

		if (targetButton != null) {
			tickTarget(client)
			return
		}

		if (!doingSS || client.player!!.distanceToSqr(START_BUTTON) > 25.0) {
			return
		}

		if (startClicksRemaining > 0) {
			if (now >= nextStartClickAt) {
				if (isStartButtonAlreadyAimed(client)) {
					clickStartButtonIfAlreadyAimed(client)
					return
				}
				beginLookClick(startButtonPos(), true)
			}
			return
		}

		if (tickOpeningPreAim(client, now)) {
			return
		}

		if (shouldPracticeCurrentSequence(client)) {
			beginPracticeSequence(client)
			return
		}

		if (state >= clicks.size) {
			beginWaitingPractice(client)
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
			beginWaitingPractice(client)
			return
		}

		val next = clicks[state]
		if (client.level!!.getBlockState(next).`is`(Blocks.STONE_BUTTON)) {
			if (clickCurrentSolveButtonIfAlreadyAimed(client, next)) {
				return
			}
			beginLookClick(next, false)
		}
	}

	override fun onWorldRenderStart() {
		val client = Minecraft.getInstance()
		if (areaCheck() && targetButton != null && client.player != null) {
			val button = targetButton
			if (button != null && targetIsStart && isStartButtonAlreadyAimed(client)) {
				return
			}
			if (button != null && (targetPreAim || targetWaitingForButton) && isCurrentSolveButtonAlreadyAimed(client, button)) {
				return
			}
			updateAimRotation(client)
		}
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		if (!areaCheck() || client.player == null || client.level == null) {
			return
		}

		val clicked = clickedButton
		if (clicked != null && System.currentTimeMillis() - lastClickTime <= currentClickDelayMs) {
			renderButton(context, client.level!!, BlockPos.containing(clicked), fillColor.value, outlineColor.value)
		}
	}

	override fun onHudRender(gfx: GuiGraphicsExtractor) {
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
		gfx.centeredText(client.font, text, centerX, centerY - client.font.lineHeight / 2, 0xFF55FF55.toInt())
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
		handlePossibleSimonSaysFailure(message)
	}

	override fun onActionBarMessage(message: String) {
		handlePossibleSimonSaysFailure(message)
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

			if (targetPreAim && !targetOpeningPreAim && button == preAimButton && !canPracticeCurrentSequence()) {
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
				if (targetPreAim && !targetOpeningPreAim && button == clicks.first()) {
					targetPreAim = false
					targetWaitingForButton = true
				}
			}

			scheduleOpeningPreAim()
			continuePracticeDuringPatternDisplay(Minecraft.getInstance())
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

		if (targetPractice && readyToSolveCurrentPattern(client)) {
			clearTarget()
			return
		}
		if (targetPractice && !practiceAimCycles.value) {
			clearTarget()
			return
		}

		if (!targetPreAim && !targetWaitingForButton && !targetPractice && !client.level!!.getBlockState(button).`is`(Blocks.STONE_BUTTON)) {
			clearTarget()
			return
		}

		if (client.player!!.distanceToSqr(Vec3.atCenterOf(button)) > MAX_BUTTON_DISTANCE_SQ) {
			chat("SS button too far!")
			clearTarget()
			return
		}

		if (targetIsStart && isStartButtonAlreadyAimed(client)) {
			if (clickStartButtonIfAlreadyAimed(client)) {
				return
			}
			return
		}

		if ((targetPreAim || targetWaitingForButton) && isCurrentSolveButtonAlreadyAimed(client, button)) {
			if (clickCurrentSolveButtonIfAlreadyAimed(client, button)) {
				return
			}
			return
		}

		val aimResult = updateAimRotation(client)
		if (aimResult == null) {
			clearTarget()
			return
		}

		if (clickPreAimedCurrentButtonIfReady(client)) {
			return
		}

		if (beginWaitCorrectionIfNeeded(client, button, aimResult)) {
			return
		}

		if (targetPractice) {
			if (shouldAdvancePracticeTarget(aimResult)) {
				beginNextPracticeButton(client)
			}
			return
		}

		if (targetPreAim) {
			// Pre-aim is an idle hold. Keep it alive so the aim controller can
			// apply settle shake, then let the top-level tick interrupt it as soon
			// as solving or practice movement is ready.
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
		targetOpeningPreAim = false
		targetPractice = false
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		targetWaitCorrectionOnRealButton = false
		targetAimPoint = if (startButton && startAimPoint != null) startAimPoint else getAimPoint(level, button)
		val aimPoint = targetAimPoint ?: return
		val mode = modeOverride ?: when {
			startButton -> AimMode.START_BUTTON
			flowingRetarget -> AimMode.CHAINED_RETARGET
			else -> AimMode.NORMAL_BUTTON
		}
		if (startsSolveSequence(mode)) {
			resetGridMotionHistory()
		}
		aimController.start(player, aimPoint, getAimSettings(mode), mode, buildMoveContext(button, mode))
		recordGridTarget(button, mode)
	}

	private fun clickStartButtonIfAlreadyAimed(client: Minecraft): Boolean {
		val level = client.level ?: return false
		val now = System.currentTimeMillis()
		if (now < nextStartClickAt || now - lastClickTime < currentClickDelayMs || !isStartButtonAlreadyAimed(client)) {
			return false
		}

		val button = startButtonPos()
		targetButton = button
		targetIsStart = true
		targetPreAim = false
		targetOpeningPreAim = false
		targetPractice = false
		targetPracticeReturningToStart = false
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		targetWaitCorrectionOnRealButton = false
		preAimButton = null
		targetAimPoint = startAimPoint ?: getAimPoint(level, button)
		clearOpeningPreAim()
		return clickTarget(client)
	}

	private fun isStartButtonAlreadyAimed(client: Minecraft): Boolean {
		val level = client.level ?: return false
		val player = client.player ?: return false
		val button = startButtonPos()
		if (!doingSS || startClicksRemaining <= 0 || client.gameMode == null) {
			return false
		}
		if (!level.getBlockState(button).`is`(Blocks.STONE_BUTTON)) {
			return false
		}
		if (player.distanceToSqr(Vec3.atCenterOf(button)) > MAX_BUTTON_DISTANCE_SQ) {
			return false
		}
		return getLookHit(client, button) != null && isLookingAtPhysicalStartButton(player)
	}

	private fun isLookingAtPhysicalStartButton(player: LocalPlayer): Boolean {
		val eye = player.eyePosition
		val look = player.lookAngle
		if (kotlin.math.abs(look.x) <= START_BUTTON_FACE_RAY_EPSILON) {
			return false
		}

		val distanceToFace = (START_BUTTON.x - eye.x) / look.x
		if (distanceToFace < 0.0 || distanceToFace > RAYCAST_DISTANCE) {
			return false
		}

		val faceHit = eye.add(look.scale(distanceToFace))
		val yOffset = kotlin.math.abs(faceHit.y - START_BUTTON.y)
		val zOffset = kotlin.math.abs(faceHit.z - START_BUTTON.z)
		return yOffset <= START_BUTTON_PHYSICAL_HALF_HEIGHT &&
			zOffset <= START_BUTTON_PHYSICAL_HALF_WIDTH
	}

	private fun clickPreAimedCurrentButtonIfReady(client: Minecraft): Boolean {
		if (!targetPreAim && !targetWaitingForButton) {
			return false
		}

		val button = targetButton ?: return false
		return clickCurrentSolveButtonIfAlreadyAimed(client, button)
	}

	private fun clickCurrentSolveButtonIfAlreadyAimed(client: Minecraft, button: BlockPos): Boolean {
		val level = client.level ?: return false
		if (!readyToSolveCurrentPattern(client) || !isCurrentSolveButtonAlreadyAimed(client, button)) {
			return false
		}

		targetButton = button
		targetIsStart = false
		targetPreAim = false
		targetOpeningPreAim = false
		targetPractice = false
		targetPracticeReturningToStart = false
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		targetWaitCorrectionOnRealButton = false
		preAimButton = null
		targetAimPoint = getAimPoint(level, button)
		clearOpeningPreAim()
		seedSolveSequenceStart(button)
		return clickTarget(client)
	}

	private fun isCurrentSolveButtonAlreadyAimed(client: Minecraft, button: BlockPos): Boolean {
		val level = client.level ?: return false
		val player = client.player ?: return false
		if (!doingSS || startClicksRemaining > 0 || state != 0 || clicks.getOrNull(state) != button) {
			return false
		}
		if (!level.getBlockState(button).`is`(Blocks.STONE_BUTTON)) {
			return false
		}
		if (player.distanceToSqr(Vec3.atCenterOf(button)) > MAX_BUTTON_DISTANCE_SQ) {
			return false
		}
		return getLookHit(client, button) != null
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
		clearAutoRestart()
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
			completedSequenceCount++
			practiceUnlocked = completedSequenceCount >= PRACTICE_UNLOCK_SEQUENCE_COUNT
			if (clicks.size >= FINAL_SEQUENCE_LENGTH) {
				finishSimonSays()
				return true
			}
			if (beginPracticeSequence(client)) {
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
		if (practiceAimCycles.value && practiceUnlocked) {
			return false
		}

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

	private fun scheduleOpeningPreAim() {
		val first = openingFirstButtonCandidate() ?: return
		if (openingPreAimButton == first || targetOpeningPreAim && targetButton == first) {
			return
		}

		if (targetOpeningPreAim) {
			clearTarget()
		}

		openingPreAimButton = first
		openingPreAimAt = System.currentTimeMillis() + FIRST_PATTERN_PRE_AIM_REACTION_MS
	}

	private fun tickOpeningPreAim(client: Minecraft, now: Long): Boolean {
		val pending = openingPreAimButton ?: return false
		val currentFirst = openingFirstButtonCandidate()
		if (currentFirst == null) {
			clearOpeningPreAim()
			return false
		}
		if (currentFirst != pending) {
			scheduleOpeningPreAim()
			return true
		}
		if (targetButton != null) {
			return false
		}
		if (now < openingPreAimAt) {
			return true
		}

		clearOpeningPreAim()
		return beginOpeningPreAim(client, pending)
	}

	private fun beginOpeningPreAim(client: Minecraft, button: BlockPos): Boolean {
		val player = client.player ?: return false
		val level = client.level ?: return false

		targetButton = button
		targetIsStart = false
		targetPreAim = true
		targetOpeningPreAim = true
		targetPractice = false
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		targetWaitCorrectionOnRealButton = false
		preAimButton = button
		targetAimPoint = if (level.getBlockState(button).`is`(Blocks.STONE_BUTTON)) {
			getAimPoint(level, button, PRE_AIM_TOLERANCE)
		} else {
			getPracticeAimPoint(button, PRE_AIM_AIM_OFFSET)
		}
		aimController.start(player, targetAimPoint!!, getAimSettings(AimMode.PRE_AIM), AimMode.PRE_AIM)
		return true
	}

	private fun openingFirstButtonCandidate(): BlockPos? {
		if (!doingSS || completedSequenceCount != 0 || state != 0 || clicks.isEmpty()) {
			return null
		}

		return if (doneFirst) {
			clicks.firstOrNull()
		} else {
			clicks.getOrNull(1)
		}
	}

	private fun clearOpeningPreAim() {
		openingPreAimButton = null
		openingPreAimAt = 0L
	}

	private fun beginPracticeSequence(client: Minecraft): Boolean {
		if (!canPracticeCurrentSequence()) {
			return false
		}

		syncPracticeButtons(resetToStart = true)
		if (practiceButtons.isEmpty()) {
			return false
		}

		practicePassesCompleted = 0
		practiceMaxPasses = desiredPracticePasses()
		practiceResumeAt = 0L
		targetPracticeReturningToStart = false
		return beginPracticeButton(client, flowingRetarget = false)
	}

	private fun beginWaitingPractice(client: Minecraft): Boolean {
		if (!doingSS
			|| startClicksRemaining > 0
			|| clicks.isEmpty()
			|| !canPracticeCurrentSequence()
		) {
			return false
		}

		syncPracticeButtons(resetToStart = false)

		if (targetPracticeIndex !in practiceButtons.indices) {
			targetPracticeIndex = 0
		}

		if (practiceMaxPasses <= 0) {
			practiceMaxPasses = desiredPracticePasses()
		}
		if (practicePassesCompleted >= practiceMaxPasses) {
			return false
		}

		return beginPracticeButton(client, flowingRetarget = false)
	}

	private fun continuePracticeDuringPatternDisplay(client: Minecraft): Boolean {
		if (!doingSS
			|| startClicksRemaining > 0
			|| clicks.isEmpty()
			|| !canPracticeCurrentSequence()
		) {
			return false
		}

		if (targetButton != null && !targetPractice) {
			if (targetPreAim || targetWaitingForButton) {
				clearTarget()
			} else {
				return false
			}
		}

		syncPracticeButtons(resetToStart = false)
		if (targetPractice && targetButton != null) {
			return true
		}

		if (readyToSolveCurrentPattern(client)) {
			return false
		}

		if (practiceMaxPasses <= 0) {
			practiceMaxPasses = desiredPracticePasses()
		}
		if (practicePassesCompleted >= practiceMaxPasses) {
			return false
		}

		return beginPracticeButton(client, flowingRetarget = false)
	}

	private fun shouldPracticeCurrentSequence(client: Minecraft): Boolean {
		return canPracticeCurrentSequence() &&
			(state == 0 || state >= clicks.size) &&
			!readyToSolveCurrentPattern(client)
	}

	private fun shouldYieldPreAim(client: Minecraft): Boolean {
		if (!doingSS || startClicksRemaining > 0) {
			return false
		}

		return shouldPracticeCurrentSequence(client) || readyToSolveCurrentPattern(client)
	}

	private fun canPracticeCurrentSequence(): Boolean =
		practiceAimCycles.value &&
			practiceUnlocked &&
			completedSequenceCount >= PRACTICE_UNLOCK_SEQUENCE_COUNT &&
			clicks.isNotEmpty() &&
			clicks.size <= FINAL_SEQUENCE_LENGTH

	private fun desiredPracticePasses(): Int =
		1

	private fun shouldAdvancePracticeTarget(aimResult: AimUpdateResult): Boolean {
		if (aimResult.finished) {
			return true
		}
		if (targetPracticeReturningToStart || practiceButtons.size <= 1) {
			return false
		}

		val plan = aimController.currentPlan() ?: return false
		if (plan.mode != AimMode.PRACTICE) {
			return false
		}

		val elapsedMs = max(0L, System.currentTimeMillis() - plan.startedAtMs)
		val progress = (elapsedMs.toDouble() / max(1L, plan.durationMs)).coerceIn(0.0, 1.0)
		val finalPatternButton = targetPracticeIndex >= practiceButtons.lastIndex
		return progress >= practicePassThroughProgress(plan, finalPatternButton)
	}

	private fun practicePassThroughProgress(plan: AimPlan, finalPatternButton: Boolean): Double {
		val distanceScale = (plan.angularDistance / MEDIUM_PRACTICE_TURN_DISTANCE).coerceIn(0.0, 1.0)
		val seedJitter = (((plan.seed and Long.MAX_VALUE) % 1000L).toDouble() / 999.0 - 0.5) * 0.06
		val finalButtonHold = if (finalPatternButton) 0.06 else 0.0
		return (0.82 - distanceScale * 0.16 + seedJitter + finalButtonHold).coerceIn(0.62, 0.90)
	}

	private fun beginNextPracticeButton(client: Minecraft): Boolean {
		syncPracticeButtons(resetToStart = false)
		if (practiceButtons.isEmpty()) {
			clearTarget()
			return false
		}

		if (targetPracticeReturningToStart) {
			if (practicePassesCompleted >= practiceMaxPasses) {
				return true
			}

			if (practiceResumeAt <= 0L) {
				practiceResumeAt = System.currentTimeMillis() + randomPracticeResumeDelayMs()
			}
			if (System.currentTimeMillis() < practiceResumeAt) {
				return true
			}

			practiceResumeAt = 0L
			targetPracticeReturningToStart = false
			if (targetPracticeIndex + 1 < practiceButtons.size) {
				targetPracticeIndex++
				return beginPracticeButton(client, flowingRetarget = true)
			}
			return true
		}

		if (targetPracticeIndex + 1 < practiceButtons.size) {
			targetPracticeIndex++
			return beginPracticeButton(client, flowingRetarget = true)
		} else {
			practicePassesCompleted++
			targetPracticeIndex = 0
			practiceResumeAt = 0L
			val firstButton = practiceButtons.first()
			if (targetButton == firstButton) {
				practiceReturnAt = 0L
				targetPracticeReturningToStart = true
				return true
			}
			if (practiceReturnAt <= 0L) {
				practiceReturnAt = System.currentTimeMillis() + PRACTICE_RETURN_PAUSE_MS
				return true
			}
			if (System.currentTimeMillis() < practiceReturnAt) {
				return true
			}

			practiceReturnAt = 0L
			targetPracticeReturningToStart = true
			return beginPracticeButton(client, firstButton, flowingRetarget = true, returnToStart = true)
		}
	}

	private fun beginPracticeButton(client: Minecraft, flowingRetarget: Boolean): Boolean {
		if (practiceButtons.isEmpty()) {
			return false
		}

		val button = practiceButtons[targetPracticeIndex.coerceIn(0, practiceButtons.lastIndex)]
		return beginPracticeButton(client, button, flowingRetarget)
	}

	private fun beginPracticeButton(
		client: Minecraft,
		button: BlockPos,
		flowingRetarget: Boolean,
		returnToStart: Boolean = false
	): Boolean {
		beginPracticeAim(button, flowingRetarget, returnToStart)
		targetPractice = true
		return true
	}

	private fun beginPracticeAim(button: BlockPos, flowingRetarget: Boolean, returnToStart: Boolean) {
		val client = Minecraft.getInstance()
		val player = client.player
		val level = client.level
		if (player == null || level == null) {
			return
		}

		targetButton = button
		targetIsStart = false
		targetPreAim = false
		targetOpeningPreAim = false
		targetPractice = false
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		targetWaitCorrectionOnRealButton = false
		targetAimPoint = if (returnToStart) {
			getPracticeAimPoint(button, PRACTICE_RETURN_AIM_OFFSET)
		} else {
			getPracticeAimPoint(button)
		}
		val mode = if (returnToStart) AimMode.PRACTICE_RETURN else AimMode.PRACTICE
		aimController.start(player, targetAimPoint!!, getAimSettings(mode), mode, buildMoveContext(button, mode))
		recordGridTarget(button, mode)
	}

	private fun beginWaitCorrectionIfNeeded(client: Minecraft, button: BlockPos, aimResult: AimUpdateResult): Boolean {
		if (!targetPreAim && !targetWaitingForButton) {
			return false
		}

		val player = client.player ?: return false
		val level = client.level ?: return false
		val realButton = level.getBlockState(button).`is`(Blocks.STONE_BUTTON)
		if (!realButton) {
			return false
		}
		if (targetWaitCorrectionOnRealButton) {
			return false
		}
		if (!aimResult.finished && !targetWaitCorrectionStarted) {
			return false
		}

		targetAimPoint = getAimPoint(level, button)
		targetWaitCorrectionStarted = true
		targetWaitCorrectionOnRealButton = true
		aimController.start(player, targetAimPoint!!, getAimSettings(AimMode.WAIT_CORRECTION), AimMode.WAIT_CORRECTION)
		return true
	}

	private fun buildMoveContext(button: BlockPos, mode: AimMode): AimMoveContext? {
		if (!tracksGridMotion(mode)) {
			return null
		}

		val previous = previousGridButton ?: return null
		val rowDelta = button.y - previous.y
		val columnDelta = button.z - previous.z
		if (rowDelta == 0 && columnDelta == 0) {
			return null
		}

		val chebyshevDistance = max(kotlin.math.abs(rowDelta), kotlin.math.abs(columnDelta))
		val euclideanDistance = sqrt((rowDelta * rowDelta + columnDelta * columnDelta).toDouble())
		val diagonal = kotlin.math.abs(rowDelta) == kotlin.math.abs(columnDelta) && rowDelta != 0
		val currentMotion = GridMotion(rowDelta, columnDelta)
		val alignment = previousGridMotion?.alignmentWith(currentMotion)
		val nextMotion = nextGridMotion(button, mode)
		val nextChebyshevDistance = if (nextMotion == null) {
			0
		} else {
			max(kotlin.math.abs(nextMotion.row), kotlin.math.abs(nextMotion.column))
		}
		return AimMoveContext(
			rowDelta = rowDelta,
			columnDelta = columnDelta,
			chebyshevDistance = chebyshevDistance,
			euclideanDistance = euclideanDistance,
			diagonal = diagonal,
			continuingDirection = alignment != null && alignment >= 0.72,
			reversingDirection = alignment != null && alignment <= -0.30,
			passesThroughTarget = nextMotion != null && currentMotion.alignmentWith(nextMotion) >= 0.72,
			nextChebyshevDistance = nextChebyshevDistance
		)
	}

	private fun recordGridTarget(button: BlockPos, mode: AimMode) {
		if (!tracksGridMotion(mode)) {
			return
		}

		val previous = previousGridButton
		if (previous != null) {
			val rowDelta = button.y - previous.y
			val columnDelta = button.z - previous.z
			if (rowDelta != 0 || columnDelta != 0) {
				previousGridMotion = GridMotion(rowDelta, columnDelta)
			}
		}
		previousGridButton = button
	}

	private fun seedSolveSequenceStart(button: BlockPos) {
		if (state != 0) {
			return
		}

		resetGridMotionHistory()
		recordGridTarget(button, AimMode.NORMAL_BUTTON)
	}

	private fun resetGridMotionHistory() {
		previousGridButton = null
		previousGridMotion = null
	}

	private fun startsSolveSequence(mode: AimMode): Boolean =
		mode == AimMode.NORMAL_BUTTON && state == 0

	private fun tracksGridMotion(mode: AimMode): Boolean =
		when (mode) {
			AimMode.NORMAL_BUTTON,
			AimMode.CHAINED_RETARGET,
			AimMode.PRACTICE,
			AimMode.PRACTICE_RETURN -> true
			else -> false
		}

	private fun nextGridMotion(button: BlockPos, mode: AimMode): GridMotion? {
		val nextButton = when (mode) {
			AimMode.NORMAL_BUTTON,
			AimMode.CHAINED_RETARGET -> clicks.getOrNull(state + 1)
			AimMode.PRACTICE -> practiceButtons.getOrNull(targetPracticeIndex + 1)
			AimMode.PRACTICE_RETURN -> null
			else -> null
		} ?: return null

		val rowDelta = nextButton.y - button.y
		val columnDelta = nextButton.z - button.z
		if (rowDelta == 0 && columnDelta == 0) {
			return null
		}
		return GridMotion(rowDelta, columnDelta)
	}

	private fun readyToSolveCurrentPattern(client: Minecraft): Boolean {
		val now = System.currentTimeMillis()
		return doingSS &&
			startClicksRemaining <= 0 &&
			now - lastClickTime >= currentClickDelayMs &&
			now >= patternSettledAt &&
			client.level?.getBlockState(DETECT)?.`is`(Blocks.STONE_BUTTON) == true &&
			state < clicks.size
	}

	private fun syncPracticeButtons(resetToStart: Boolean) {
		val currentPracticeButton = if (targetPractice) targetButton else null
		val previousIndexedButton = practiceButtons.getOrNull(targetPracticeIndex)

		practiceButtons.clear()
		practiceButtons.addAll(clicks)

		targetPracticeIndex = when {
			practiceButtons.isEmpty() -> 0
			resetToStart -> 0
			currentPracticeButton != null && practiceButtons.contains(currentPracticeButton) -> practiceButtons.indexOf(currentPracticeButton)
			previousIndexedButton != null && practiceButtons.contains(previousIndexedButton) -> practiceButtons.indexOf(previousIndexedButton)
			else -> targetPracticeIndex.coerceIn(0, practiceButtons.lastIndex)
		}
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

	private fun getAimPoint(level: ClientLevel, pos: BlockPos): Vec3 =
		getAimPoint(level, pos, AIM_TOLERANCE)

	private fun getAimPoint(level: ClientLevel, pos: BlockPos, tolerance: Double): Vec3 {
		val state = level.getBlockState(pos)
		val shape = state.getShape(level, pos)
		val box = if (shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
		val random = ThreadLocalRandom.current()
		val center = box.center
		val xDepth = box.xsize <= box.ysize && box.xsize <= box.zsize
		val yDepth = box.ysize < box.xsize && box.ysize <= box.zsize
		val x = center.x + if (xDepth) 0.0 else middleOffset(random, min(tolerance, max(0.0, box.xsize * 0.35)))
		val y = center.y + if (yDepth) 0.0 else middleOffset(random, min(tolerance, max(0.0, box.ysize * 0.35)))
		val z = center.z + if (!xDepth && !yDepth) 0.0 else middleOffset(random, min(tolerance, max(0.0, box.zsize * 0.35)))
		return Vec3(x, y, z)
	}

	private fun getPracticeAimPoint(pos: BlockPos): Vec3 =
		getPracticeAimPoint(pos, PRACTICE_AIM_OFFSET)

	private fun getPracticeAimPoint(pos: BlockPos, maxOffset: Double): Vec3 {
		val random = ThreadLocalRandom.current()
		return Vec3(
			pos.x + PRACTICE_BUTTON_FACE_X + middleOffset(random, maxOffset),
			pos.y + 0.5 + middleOffset(random, maxOffset),
			pos.z + 0.5 + middleOffset(random, maxOffset)
		)
	}

	private fun renderButton(context: LevelRenderContext, level: ClientLevel, pos: BlockPos, colorFill: Colour, colorOutline: Colour) {
		val state = level.getBlockState(pos)
		val shape: VoxelShape = state.getShape(level, pos)
		if (!shape.isEmpty) {
			val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
			val box = shape.bounds().move(pos)
			val matrices = context.poseStack()
			matrices.pushPose()
			matrices.translate(-camera.x, -camera.y, -camera.z)
			CgcRenderPrimitives.filledBox(
				matrices,
				context.bufferSource().getBuffer(RenderTypes.debugFilledBox()),
				box,
				colorFill.red / 255.0f,
				colorFill.green / 255.0f,
				colorFill.blue / 255.0f,
				colorFill.alpha / 255.0f
			)
			CgcRenderPrimitives.lineBox(
				matrices,
				context.bufferSource().getBuffer(RenderTypes.lines()),
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

	private fun scheduleAutoRestart(): Boolean {
		if (!autoRestartSs.value || !doingSS) {
			return false
		}

		if (autoRestartAt <= 0L) {
			autoRestartAt = System.currentTimeMillis() + randomAutoRestartReactionDelayMs()
		}
		return true
	}

	private fun tickAutoRestart(now: Long): Boolean {
		if (autoRestartAt <= 0L) {
			return false
		}
		if (!autoRestartSs.value || !doingSS) {
			clearAutoRestart()
			return false
		}
		if (now < autoRestartAt) {
			return false
		}

		clearAutoRestart()
		start()
		return true
	}

	private fun clearAutoRestart() {
		autoRestartAt = 0L
	}

	private fun handlePossibleSimonSaysFailure(message: String) {
		if (!doingSS || !areaCheck() || !isSimonSaysFailureMessage(message)) {
			return
		}

		scheduleAutoRestart()
	}

	private fun isSimonSaysFailureMessage(message: String): Boolean {
		val text = stripControlCodes(message).lowercase()
		if (text.isBlank()) {
			return false
		}
		if (text.contains("completed a device") || text.contains("activated a device")) {
			return false
		}

		val mentionsSimonSays = text.contains("simon") ||
			text.contains(" ss") ||
			text.contains("ss ") ||
			text.contains("device") ||
			text.contains("button")
		if (!mentionsSimonSays) {
			return false
		}

		return text.contains("fail") ||
			text.contains("wrong") ||
			text.contains("incorrect") ||
			text.contains("reset") ||
			text.contains("broke")
	}

	private fun stripControlCodes(message: String): String =
		message.replace(CONTROL_CODE_PATTERN, "").trim()

	private fun areaCheck(): Boolean =
		forceSkyblock.value ||
			Location.area.isArea(Island.DUNGEON) &&
			(Location.floor == Floor.F7 || Location.floor == Floor.M7) &&
			DungeonUtils.isPhase(Phase7.P3)

	private fun resetState() {
		allButtons.clear()
		clicks.clear()
		practiceButtons.clear()
		clearTarget()
		startAimPoint = null
		startClicksRemaining = 0
		nextStartClickAt = 0L
		patternSettledAt = 0L
		state = 0
		doneFirst = false
		doingSS = false
		completedSequenceCount = 0
		practiceUnlocked = false
		practicePassesCompleted = 0
		practiceMaxPasses = 0
		practiceReturnAt = 0L
		practiceResumeAt = 0L
		resetGridMotionHistory()
		clearAutoRestart()
		clearOpeningPreAim()
		donePopupUntil = 0L
	}

	private fun finishSimonSays() {
		AutoLeap.onSimonSaysComplete()
		doingSS = false
		donePopupUntil = System.currentTimeMillis() + DONE_POPUP_MS
		clearAutoRestart()
		clearTarget()
	}

	private fun clearTarget() {
		targetButton = null
		targetAimPoint = null
		targetIsStart = false
		targetPreAim = false
		targetOpeningPreAim = false
		targetPractice = false
		targetPracticeReturningToStart = false
		targetPracticeIndex = 0
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		targetWaitCorrectionOnRealButton = false
		preAimButton = null
		practiceReturnAt = 0L
		clearOpeningPreAim()
		aimController.clear()
	}

	private fun middleOffset(random: ThreadLocalRandom, maxOffset: Double): Double {
		if (maxOffset <= 0.0) {
			return 0.0
		}

		return (random.nextDouble(-maxOffset, maxOffset) + random.nextDouble(-maxOffset, maxOffset)) * 0.5
	}

	private fun getAimSettings(mode: AimMode): AimSettings {
		val speedMultiplier = when (mode) {
			AimMode.PRACTICE,
			AimMode.PRACTICE_RETURN -> getDouble(practiceAimSpeed)
			AimMode.WAIT_CORRECTION -> getDouble(waitCorrectionAimSpeed)
			else -> 1.0
		}
		val randomness = when (mode) {
			AimMode.PRE_AIM -> PRE_AIM_RANDOMNESS
			AimMode.PRACTICE_RETURN -> PRACTICE_RETURN_RANDOMNESS
			else -> AIM_RANDOMNESS
		}
		return AimSettings(
			speed = getDouble(aimSpeed) * speedMultiplier,
			randomness = randomness,
			overshootStrength = OVERSHOOT_STRENGTH,
			microCorrection = MICRO_CORRECTION
		)
	}

	private fun getLong(setting: NumberSetting): Long =
		setting.value.toLong()

	private fun getDouble(setting: NumberSetting): Double =
		setting.value.toDouble()

	private fun randomAutoStartDelayMs(): Long {
		val baseDelay = getLong(autoStartDelay)
		return ThreadLocalRandom.current().nextLong(baseDelay, baseDelay + AUTO_START_DELAY_RANDOM_EXTRA_MS + 1L)
	}

	private fun randomClickDelayMs(): Long {
		val minDelay = CLICK_DELAY_MIN_MS.coerceAtLeast(1L)
		val maxDelay = CLICK_DELAY_MAX_MS.coerceAtLeast(1L)
		val low = min(minDelay, maxDelay)
		val configuredHigh = max(minDelay, maxDelay)
		val high = if (configuredHigh <= low) low + MIN_CLICK_DELAY_VARIANCE_MS else configuredHigh
		return ThreadLocalRandom.current().nextLong(low, high + 1L)
	}

	private fun randomPracticeResumeDelayMs(): Long =
		ThreadLocalRandom.current().nextLong(MIN_PRACTICE_RESUME_DELAY_MS, MAX_PRACTICE_RESUME_DELAY_MS + 1L)

	private fun randomAutoRestartReactionDelayMs(): Long =
		ThreadLocalRandom.current().nextLong(MIN_AUTO_RESTART_REACTION_MS, MAX_AUTO_RESTART_REACTION_MS + 1L)

	private fun chat(message: String) {
		Minecraft.getInstance().player?.sendSystemMessage(Component.literal(message))
	}

	private companion object {
		private val START_BUTTON = Vec3(110.875, 121.5, 91.5)
		private val DETECT = BlockPos(110, 123, 92)
		private const val MAX_BUTTON_DISTANCE_SQ = 36.0
		private const val RAYCAST_DISTANCE = 6.0
		private const val START_BUTTON_FACE_RAY_EPSILON = 1.0E-5
		private const val START_BUTTON_PHYSICAL_HALF_HEIGHT = 0.145
		private const val START_BUTTON_PHYSICAL_HALF_WIDTH = 0.205
		private const val FINAL_SEQUENCE_LENGTH = 5
		private const val PRACTICE_UNLOCK_SEQUENCE_COUNT = 2
		private const val PRACTICE_BUTTON_FACE_X = 0.875
		private const val PRACTICE_AIM_OFFSET = 0.44
		private const val PRE_AIM_AIM_OFFSET = 0.16
		private const val PRACTICE_RETURN_AIM_OFFSET = 0.18
		private const val MEDIUM_PRACTICE_TURN_DISTANCE = 32.0
		private const val FIRST_PATTERN_PRE_AIM_REACTION_MS = 180L
		private const val PRACTICE_RETURN_PAUSE_MS = 100L
		private const val MIN_PRACTICE_RESUME_DELAY_MS = 500L
		private const val MAX_PRACTICE_RESUME_DELAY_MS = 580L
		private const val MIN_AUTO_RESTART_REACTION_MS = 180L
		private const val MAX_AUTO_RESTART_REACTION_MS = 200L
		private const val DONE_POPUP_MS = 1500L
		private const val AIM_RANDOMNESS = 0.1
		private const val PRE_AIM_RANDOMNESS = 0.03
		private const val PRACTICE_RETURN_RANDOMNESS = 0.045
		private const val AIM_TOLERANCE = 0.18
		private const val PRE_AIM_TOLERANCE = 0.07
		private const val OVERSHOOT_STRENGTH = 1.0
		private const val MICRO_CORRECTION = 0.55
		private const val CLICK_DELAY_MIN_MS = 8L
		private const val CLICK_DELAY_MAX_MS = 14L
		private const val MIN_CLICK_DELAY_VARIANCE_MS = 4L
		private const val MIN_START_CLICK_DELAY_MS = 105L
		private const val MAX_START_CLICK_DELAY_MS = 130L
		private const val AUTO_START_DELAY_RANDOM_EXTRA_MS = 40L
		private val CONTROL_CODE_PATTERN = Regex("(?i)§[0-9A-FK-OR]")

		private fun startButtonPos(): BlockPos =
			BlockPos.containing(START_BUTTON.x, START_BUTTON.y, START_BUTTON.z)

		private fun randomStartClickDelayMs(): Long =
			ThreadLocalRandom.current().nextLong(MIN_START_CLICK_DELAY_MS, MAX_START_CLICK_DELAY_MS + 1L)
	}

	private data class GridMotion(
		val row: Int,
		val column: Int
	) {
		fun alignmentWith(other: GridMotion): Double {
			val length = sqrt((row * row + column * column).toDouble())
			val otherLength = sqrt((other.row * other.row + other.column * other.column).toDouble())
			if (length <= 0.0 || otherLength <= 0.0) {
				return 0.0
			}

			return ((row * other.row) + (column * other.column)) / (length * otherLength)
		}
	}
}
