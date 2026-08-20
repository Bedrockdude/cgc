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
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.PacketSendModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.WorldRenderStartModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.runtime.CgcRenderPrimitives
import cgc.cgc.utils.DungeonUtils
import cgc.cgc.mixin.MinecraftAccessor
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
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
), ClientTickModule, WorldRenderStartModule, WorldRenderExtractModule, HudRenderModule, WorldLoadModule, ChatMessageModule, ActionBarMessageModule, BlockChangeModule, PacketSendModule, PacketReceiveModule {
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
	private val patternCapture = SimonSaysPatternCapture(FINAL_SEQUENCE_LENGTH)
	private val aimController = SimonSaysAimController()
	private val mouseMotion = VanillaMouseMotion()

	private var lastClickTime = nowMs()
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
	private var latestAimResult: AimUpdateResult? = null
	private var pendingClick: PendingClick? = null
	private var clickPacketCaptureTarget: BlockPos? = null
	private var capturedClickPacketSequence: Int? = null
	private var smoothedInteractionAckMs = 0L
	private var lastServerGameTime: Long? = null
	private var lastServerTimePacketAtMs = 0L
	private var estimatedServerTickMs: Double? = null
	private var nextServerSafeClickAtMs = 0L

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

		val now = nowMs()
		if (tickAutoRestart(now)) {
			return
		}
		// The vanilla acknowledgement is emitted after the server processes the
		// interaction sequence. Aim may continue, but never queue another SS click
		// ahead of that acknowledgement.
		val pending = pendingClick
		if (pending != null) {
			if (now - pending.sentAtMs >= pendingClickTimeoutMs()) {
				stopSimonSaysSafely("Auto SS stopped: the previous click was never acknowledged.")
			}
			return
		}
		if (startClicksRemaining <= 0) {
			commitCapturedPatternIfReady(client, now)
			if (!doingSS) {
				return
			}
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
			latestAimResult = updateAimRotation(client)
		}
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		if (!areaCheck() || client.player == null || client.level == null) {
			return
		}

		val clicked = clickedButton
		if (clicked != null && nowMs() - lastClickTime <= currentClickDelayMs) {
			renderButton(context, client.level!!, BlockPos.containing(clicked), fillColor.value, outlineColor.value)
		}
	}

	override fun onHudRender(gfx: GuiGraphicsExtractor) {
		if (!donePopup.value || nowMs() > donePopupUntil) {
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
		resetServerTiming()
	}

	override fun onChatMessage(message: String) {
		if (runOnClientThread { handleChatMessage(message) }) {
			return
		}
	}

	private fun handleChatMessage(message: String) {
		if (areaCheck() && autoStart.value && Minecraft.getInstance().player != null) {
			if (message == "[BOSS] Goldor: Who dares trespass into my domain?") {
				start()
			}
		}
		handlePossibleSimonSaysFailure(message)
	}

	override fun onActionBarMessage(message: String) {
		runOnClientThread { handlePossibleSimonSaysFailure(message) }
	}

	override fun onBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		if (runOnClientThread { handleBlockChange(pos.immutable(), oldState, newState) }) {
			return
		}
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		val captureTarget = clickPacketCaptureTarget
		if (captureTarget != null
			&& packet is ServerboundUseItemOnPacket
			&& packet.hitResult.blockPos == captureTarget
		) {
			capturedClickPacketSequence = packet.sequence
		}
		return false
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		val receivedAtMs = nowMs()
		val acknowledgedSequence = newestAcknowledgedPredictionSequence(packet)
		val serverGameTime = newestServerGameTime(packet)
		if (acknowledgedSequence != null || serverGameTime != null) {
			runOnClientThread {
				if (serverGameTime != null) {
					observeServerTime(serverGameTime, receivedAtMs)
				}
				if (acknowledgedSequence != null) {
					acknowledgePendingClickFromPrediction(acknowledgedSequence)
				}
			}
		}
		return false
	}

	private fun newestAcknowledgedPredictionSequence(packet: Packet<*>): Int? =
		when (packet) {
			is ClientboundBlockChangedAckPacket -> packet.sequence
			is ClientboundBundlePacket -> packet.subPackets()
				.mapNotNull(::newestAcknowledgedPredictionSequence)
				.maxOrNull()
			else -> null
		}

	private fun newestServerGameTime(packet: Packet<*>): Long? =
		when (packet) {
			is ClientboundSetTimePacket -> packet.gameTime
			is ClientboundBundlePacket -> packet.subPackets()
				.mapNotNull(::newestServerGameTime)
				.lastOrNull()
			else -> null
		}

	private fun handleBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		acknowledgePendingClickFromBlockUpdate(pos, newState)
		if (!doingSS
			|| !areaCheck()
			|| !isPatternLightUpdate(pos, newState)
			|| oldState?.`is`(Blocks.SEA_LANTERN) == true
		) {
			return
		}

		val button = BlockPos(110, pos.y, pos.z)
		if (!patternCapture.record(button)) {
			return
		}
		markPatternChanged()
		if (startClicksRemaining > 0) {
			return
		}

		if (targetPreAim && !targetOpeningPreAim && button == preAimButton && !canPracticeCurrentSequence()) {
			targetPreAim = false
			targetWaitingForButton = true
		}

		scheduleOpeningPreAim()
		continuePracticeDuringPatternDisplay(Minecraft.getInstance())
	}

	private fun commitCapturedPatternIfReady(client: Minecraft, now: Long): Boolean {
		val level = client.level ?: return false
		if (patternCapture.observationCount <= 0
			|| (doneFirst && state < clicks.size)
			|| now < patternSettledAt
			|| !level.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON)
		) {
			return false
		}

		val capturedPattern = if (!doneFirst) {
			patternCapture.openingPattern()
		} else {
			when (val replay = patternCapture.nextPattern(clicks)) {
				PatternReplayResult.Incomplete -> return false
				PatternReplayResult.Mismatch -> {
					stopSimonSaysSafely("Auto SS stopped: the displayed pattern did not match the previous round.")
					return false
				}
				is PatternReplayResult.Ready -> replay.buttons
			}
		} ?: return false

		clicks.clear()
		clicks.addAll(capturedPattern)
		allButtons.clear()
		allButtons.addAll(capturedPattern.map(Vec3::atLowerCornerOf))
		state = 0
		doneFirst = true
		patternCapture.clear()
		patternSettledAt = 0L
		return true
	}

	private fun runOnClientThread(action: () -> Unit): Boolean {
		val client = Minecraft.getInstance()
		if (client.isSameThread) {
			action()
			return true
		}
		client.execute(action)
		return false
	}

	fun SSR() {
		if (areaCheck()) {
			start()
		}
	}

	override fun onEnable() {
		resetState()
		resetServerTiming()
		resetKey.register()
	}

	override fun onDisable() {
		resetKey.unregister()
		resetState()
		resetServerTiming()
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
		nextStartClickAt = nowMs() + randomAutoStartDelayMs()
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

		val aimResult = latestAimResult ?: return

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
			// Pre-aim is an idle hold. The top-level tick interrupts it as soon as
			// solving or practice movement is ready.
			return
		}

		if (nowMs() - lastClickTime < currentClickDelayMs) {
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
		val updateAt = nowMs()
		val result = aimController.update(updateAt)
		val actual = mouseMotion.apply(client, player, result.rotation)
		val plan = aimController.currentPlan() ?: return null
		val actualError = angularError(actual, plan.final)
		val practiceTargetReached = plan.mode == AimMode.PRACTICE &&
			updateAt - plan.startedAtMs >= MIN_PRACTICE_RETARGET_MS &&
			actualError <= PRACTICE_RETARGET_TOLERANCE
		return AimUpdateResult(
			rotation = actual,
			readyToClick = result.readyToClick && actualError <= ACTUAL_CLICK_READY_TOLERANCE,
			finished = practiceTargetReached || result.finished && actualError <= ACTUAL_FINISH_TOLERANCE
		)
	}

	private fun startAim(
		player: LocalPlayer,
		target: Vec3,
		mode: AimMode,
		moveContext: AimMoveContext? = null
	) {
		latestAimResult = null
		aimController.start(player, target, getAimSettings(mode), mode, moveContext)
	}

	private fun angularError(rotation: Rotation, target: Rotation): Double {
		val yaw = Mth.wrapDegrees(rotation.yaw - target.yaw).toDouble()
		val pitch = (rotation.pitch - target.pitch).toDouble()
		return sqrt(yaw * yaw + pitch * pitch)
	}

	private fun beginLookClick(button: BlockPos, startButton: Boolean) {
		beginLookClick(button, startButton, false)
	}

	private fun beginLookClick(
		button: BlockPos,
		startButton: Boolean,
		flowingRetarget: Boolean,
		modeOverride: AimMode? = null,
		sequenceIndexOverride: Int? = null
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
		startAim(player, aimPoint, mode, buildMoveContext(button, mode, sequenceIndexOverride ?: state))
		recordGridTarget(button, mode)
	}

	private fun clickStartButtonIfAlreadyAimed(client: Minecraft): Boolean {
		val level = client.level ?: return false
		val now = nowMs()
		if (now < nextStartClickAt
			|| now < nextServerSafeClickAtMs
			|| now - lastClickTime < currentClickDelayMs
			|| !isStartButtonAlreadyAimed(client)
		) {
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
		targetAimPoint = targetAimPoint ?: startAimPoint ?: getAimPoint(level, button)
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
		targetAimPoint = targetAimPoint ?: getAimPoint(level, button)
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
		if (pendingClick != null) {
			return true
		}

		val button = targetButton ?: return false
		val clickedStart = targetIsStart
		val packetSequence = when (val sendResult = sendClickInteraction(client, button, allowPowered = clickedStart)) {
			ClickSendResult.NotReady -> return false
			ClickSendResult.LocallySuppressed -> {
				stopSimonSaysSafely("Auto SS stopped: another mod blocked its click before it was sent.")
				return true
			}
			is ClickSendResult.Sent -> sendResult.packetSequence
		}

		val sentAt = nowMs()
		recordClickAttempt(button, sentAt)
		if (clickedStart) {
			startClicksRemaining = max(0, startClicksRemaining - 1)
			nextStartClickAt = if (startClicksRemaining > 0) {
				sentAt + randomStartClickDelayMs()
			} else {
				0L
			}
		}

		val pending = PendingClick(
			button = button,
			startButton = clickedStart,
			solveIndex = if (clickedStart) -1 else state,
			packetSequence = packetSequence,
			sentAtMs = sentAt
		)
		pendingClick = pending
		beginMotionWhileClickPending(client, pending)
		return true
	}

	private fun beginMotionWhileClickPending(client: Minecraft, pending: PendingClick) {
		if (pending.startButton
			|| !doingSS
			|| pending.solveIndex !in clicks.indices
			|| clicks[pending.solveIndex] != pending.button
		) {
			return
		}

		val nextIndex = pending.solveIndex + 1
		if (nextIndex < clicks.size) {
			beginSequenceButton(client, nextIndex, flowingRetarget = true)
			return
		}

		if (clicks.size >= FINAL_SEQUENCE_LENGTH) {
			return
		}
		if (beginPracticeSequence(client, completedSequenceCount + 1)) {
			return
		}
		beginFirstButtonPreAim(client)
	}

	private fun sendClickInteraction(client: Minecraft, button: BlockPos, allowPowered: Boolean = false): ClickSendResult {
		val player = client.player ?: return ClickSendResult.NotReady
		val level = client.level ?: return ClickSendResult.NotReady
		val gameMode = client.gameMode ?: return ClickSendResult.NotReady
		if (nowMs() < nextServerSafeClickAtMs) {
			return ClickSendResult.NotReady
		}
		if (client.screen != null || !client.mouseHandler.isMouseGrabbed || gameMode.isDestroying || player.isHandsBusy) {
			return ClickSendResult.NotReady
		}

		val buttonState = level.getBlockState(button)
		if (!buttonState.`is`(Blocks.STONE_BUTTON) || !allowPowered && isPressedStoneButton(buttonState)) {
			return ClickSendResult.NotReady
		}
		if (player.distanceToSqr(Vec3.atCenterOf(button)) > MAX_BUTTON_DISTANCE_SQ) {
			return ClickSendResult.NotReady
		}
		if (getVerifiedClickHit(client, button) == null) {
			return ClickSendResult.NotReady
		}

		clickPacketCaptureTarget = button
		capturedClickPacketSequence = null
		try {
			(client as MinecraftAccessor).cgcStartUseItem()
		} finally {
			clickPacketCaptureTarget = null
		}
		val packetSequence = capturedClickPacketSequence
		capturedClickPacketSequence = null
		return if (packetSequence == null) {
			ClickSendResult.LocallySuppressed
		} else {
			ClickSendResult.Sent(packetSequence)
		}
	}

	private fun recordClickAttempt(button: BlockPos, sentAt: Long) {
		clearAutoRestart()
		lastClickTime = sentAt
		currentClickDelayMs = randomClickDelayMs()
		nextServerSafeClickAtMs = sentAt + randomServerSafeClickSpacingMs()
		clickedButton = Vec3.atLowerCornerOf(button)
	}

	private fun acknowledgePendingClickFromPrediction(acknowledgedSequence: Int) {
		val pending = pendingClick ?: return
		// Acknowledgements are cumulative, so a newer sequence also proves this
		// click has crossed a server processing boundary.
		if (acknowledgedSequence < pending.packetSequence) {
			return
		}

		observeInteractionAck(nowMs() - pending.sentAtMs)
		completePendingClick(Minecraft.getInstance(), pending)
	}

	private fun observeInteractionAck(sampleMs: Long) {
		val boundedSample = sampleMs.coerceIn(MIN_ACK_SAMPLE_MS, MAX_ACK_SAMPLE_MS)
		smoothedInteractionAckMs = if (smoothedInteractionAckMs <= 0L) {
			boundedSample
		} else {
			(smoothedInteractionAckMs * 2L + boundedSample) / 3L
		}
	}

	private fun observeServerTime(gameTime: Long, receivedAtMs: Long) {
		val previousGameTime = lastServerGameTime
		val previousReceivedAt = lastServerTimePacketAtMs
		lastServerGameTime = gameTime
		lastServerTimePacketAtMs = receivedAtMs
		if (previousGameTime == null || gameTime <= previousGameTime || previousReceivedAt <= 0L) {
			return
		}

		val elapsedTicks = gameTime - previousGameTime
		val elapsedMs = receivedAtMs - previousReceivedAt
		if (elapsedTicks !in MIN_SERVER_TIME_SAMPLE_TICKS..MAX_SERVER_TIME_SAMPLE_TICKS || elapsedMs <= 0L) {
			return
		}

		val sampleMs = elapsedMs.toDouble() / elapsedTicks.toDouble()
		if (sampleMs !in MIN_SERVER_TICK_SAMPLE_MS..MAX_SERVER_TICK_SAMPLE_MS) {
			return
		}
		val previousEstimate = estimatedServerTickMs
		estimatedServerTickMs = if (previousEstimate == null) {
			sampleMs
		} else {
			previousEstimate * SERVER_TICK_SMOOTHING_OLD_WEIGHT + sampleMs * (1.0 - SERVER_TICK_SMOOTHING_OLD_WEIGHT)
		}
	}

	private fun resetServerTiming() {
		lastServerGameTime = null
		lastServerTimePacketAtMs = 0L
		estimatedServerTickMs = null
	}

	private fun acknowledgePendingClickFromBlockUpdate(pos: BlockPos, newState: BlockState) {
		val pending = pendingClick ?: return
		val buttonPressed = !pending.startButton && pos == pending.button && isPressedStoneButton(newState)
		val serverAdvancedPattern = isPatternLightUpdate(pos, newState) &&
			(pending.startButton || pending.solveIndex >= clicks.lastIndex)
		if (!buttonPressed && !serverAdvancedPattern) {
			return
		}

		completePendingClick(Minecraft.getInstance(), pending)
	}

	private fun completePendingClick(client: Minecraft, pending: PendingClick) {
		if (pendingClick?.packetSequence != pending.packetSequence) {
			return
		}
		pendingClick = null
		if (!doingSS) {
			clearTarget()
			return
		}

		if (pending.startButton) {
			clearTarget()
			return
		}

		if (pending.solveIndex !in clicks.indices || clicks[pending.solveIndex] != pending.button) {
			clearTarget()
			return
		}
		state = pending.solveIndex + 1
		if (state < clicks.size) {
			if (isPreparedSequenceMotion(clicks[state])) {
				return
			}
			if (beginNextSequenceButton(client, true)) {
				return
			}
			clearTarget()
			return
		}

		patternCapture.clear()
		patternSettledAt = 0L
		completedSequenceCount++
		practiceUnlocked = completedSequenceCount >= PRACTICE_UNLOCK_SEQUENCE_COUNT
		if (clicks.size >= FINAL_SEQUENCE_LENGTH) {
			finishSimonSays()
			return
		}
		if (isPreparedPracticeMotion() && canPracticeCurrentSequence()) {
			return
		}
		if (beginPracticeSequence(client)) {
			return
		}
		if (isPreparedFirstButtonPreAim()) {
			return
		}
		if (beginFirstButtonPreAim(client)) {
			return
		}
		clearTarget()
	}

	private fun isPreparedSequenceMotion(button: BlockPos): Boolean =
		targetButton == button &&
			targetAimPoint != null &&
			!targetIsStart &&
			!targetPreAim &&
			!targetOpeningPreAim &&
			!targetPractice &&
			!targetWaitingForButton &&
			aimController.hasTarget()

	private fun isPreparedPracticeMotion(): Boolean =
		targetPractice &&
			targetButton in practiceButtons &&
			targetAimPoint != null &&
			aimController.hasTarget()

	private fun isPreparedFirstButtonPreAim(): Boolean =
		targetButton == clicks.firstOrNull() &&
			targetPreAim &&
			!targetOpeningPreAim &&
			!targetPractice &&
			targetAimPoint != null &&
			aimController.hasTarget()

	private fun isPressedStoneButton(state: BlockState): Boolean =
		state.`is`(Blocks.STONE_BUTTON) && state.getValue(ButtonBlock.POWERED)

	private fun isPatternLightUpdate(pos: BlockPos, state: BlockState): Boolean =
		state.`is`(Blocks.SEA_LANTERN) &&
			pos.x == 111 && pos.y in 120..123 && pos.z in 92..95

	private fun beginNextSequenceButton(client: Minecraft, flowingRetarget: Boolean): Boolean {
		return beginSequenceButton(client, state, flowingRetarget)
	}

	private fun beginSequenceButton(client: Minecraft, index: Int, flowingRetarget: Boolean): Boolean {
		if (index !in clicks.indices) {
			return false
		}

		val next = clicks[index]
		if (!client.level!!.getBlockState(next).`is`(Blocks.STONE_BUTTON)) {
			return false
		}

		beginLookClick(next, false, flowingRetarget, sequenceIndexOverride = index)
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
		targetWaitCorrectionOnRealButton = true
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
		openingPreAimAt = nowMs() + FIRST_PATTERN_PRE_AIM_REACTION_MS
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
		val realButton = level.getBlockState(button).`is`(Blocks.STONE_BUTTON)
		targetWaitCorrectionOnRealButton = realButton
		preAimButton = button
		targetAimPoint = if (realButton) {
			getAimPoint(level, button, PRE_AIM_TOLERANCE)
		} else {
			getPracticeAimPoint(button, PRE_AIM_AIM_OFFSET)
		}
		startAim(player, targetAimPoint!!, AimMode.PRE_AIM)
		return true
	}

	private fun openingFirstButtonCandidate(): BlockPos? {
		if (!doingSS || completedSequenceCount != 0 || state != 0) {
			return null
		}

		return if (doneFirst) {
			clicks.firstOrNull()
		} else {
			patternCapture.openingPattern()?.firstOrNull()
		}
	}

	private fun clearOpeningPreAim() {
		openingPreAimButton = null
		openingPreAimAt = 0L
	}

	private fun beginPracticeSequence(
		client: Minecraft,
		completedSequences: Int = completedSequenceCount
	): Boolean {
		if (!canPracticeCurrentSequence(completedSequences)) {
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

	private fun canPracticeCurrentSequence(completedSequences: Int = completedSequenceCount): Boolean =
		practiceAimCycles.value &&
			completedSequences >= PRACTICE_UNLOCK_SEQUENCE_COUNT &&
			clicks.isNotEmpty() &&
			clicks.size <= FINAL_SEQUENCE_LENGTH

	private fun desiredPracticePasses(): Int =
		1

	private fun shouldAdvancePracticeTarget(aimResult: AimUpdateResult): Boolean {
		return aimResult.finished
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
				practiceResumeAt = nowMs() + randomPracticeResumeDelayMs()
			}
			if (nowMs() < practiceResumeAt) {
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
				practiceReturnAt = nowMs() + PRACTICE_RETURN_PAUSE_MS
				return true
			}
			if (nowMs() < practiceReturnAt) {
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
		startAim(player, targetAimPoint!!, mode, buildMoveContext(button, mode))
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
		if (getLookHit(client, button) != null) {
			targetWaitCorrectionOnRealButton = true
			return false
		}
		if (!aimResult.finished && !targetWaitCorrectionStarted) {
			return false
		}

		targetAimPoint = getAimPoint(level, button)
		targetWaitCorrectionStarted = true
		targetWaitCorrectionOnRealButton = true
		startAim(player, targetAimPoint!!, AimMode.WAIT_CORRECTION)
		return true
	}

	private fun buildMoveContext(button: BlockPos, mode: AimMode, sequenceIndex: Int = state): AimMoveContext? {
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
		val nextMotion = nextGridMotion(button, mode, sequenceIndex)
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

	private fun nextGridMotion(button: BlockPos, mode: AimMode, sequenceIndex: Int = state): GridMotion? {
		val nextButton = when (mode) {
			AimMode.NORMAL_BUTTON,
			AimMode.CHAINED_RETARGET -> clicks.getOrNull(sequenceIndex + 1)
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
		val now = nowMs()
		return doingSS &&
			startClicksRemaining <= 0 &&
			now - lastClickTime >= currentClickDelayMs &&
			now >= nextServerSafeClickAtMs &&
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
		return if (hit.type != HitResult.Type.MISS && hit.blockPos == expected && isHitInsideBlockShape(level, expected, hit.location)) {
			hit
		} else {
			null
		}
	}

	private fun getVerifiedClickHit(client: Minecraft, expected: BlockPos): BlockHitResult? {
		val level = client.level ?: return null
		val freshHit = getLookHit(client, expected)
		val crosshairHit = client.hitResult as? BlockHitResult
		if (freshHit == null
			|| crosshairHit == null
			|| crosshairHit.type == HitResult.Type.MISS
			|| crosshairHit.blockPos != expected
			|| !isHitInsideBlockShape(level, expected, crosshairHit.location)
		) {
			return null
		}
		return freshHit
	}

	private fun isHitInsideBlockShape(level: ClientLevel, pos: BlockPos, hitPoint: Vec3): Boolean {
		val shape = level.getBlockState(pos).getShape(level, pos)
		if (shape.isEmpty) {
			return false
		}
		return shape.bounds().move(pos).inflate(BUTTON_HIT_EPSILON).contains(hitPoint)
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
		val verticalOffset = min(maxOffset, VIRTUAL_BUTTON_VERTICAL_OFFSET)
		val horizontalOffset = min(maxOffset * VIRTUAL_BUTTON_HORIZONTAL_SCALE, VIRTUAL_BUTTON_HORIZONTAL_OFFSET)
		return Vec3(
			pos.x + PRACTICE_BUTTON_FACE_X,
			pos.y + 0.5 + middleOffset(random, verticalOffset),
			pos.z + 0.5 + middleOffset(random, horizontalOffset)
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
		patternSettledAt = nowMs() + randomPatternSettleDelayMs()
	}

	private fun scheduleAutoRestart(): Boolean {
		if (!autoRestartSs.value || !doingSS) {
			return false
		}

		if (autoRestartAt <= 0L) {
			autoRestartAt = nowMs() + randomAutoRestartReactionDelayMs()
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
		patternCapture.clear()
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
		smoothedInteractionAckMs = 0L
		nextServerSafeClickAtMs = 0L
		resetGridMotionHistory()
		clearAutoRestart()
		clearOpeningPreAim()
		donePopupUntil = 0L
	}

	private fun finishSimonSays() {
		AutoLeap.onSimonSaysComplete()
		doingSS = false
		donePopupUntil = nowMs() + DONE_POPUP_MS
		patternCapture.clear()
		clearAutoRestart()
		clearTarget()
	}

	private fun stopSimonSaysSafely(message: String) {
		doingSS = false
		patternCapture.clear()
		clearAutoRestart()
		clearTarget()
		chat(message)
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
		latestAimResult = null
		mouseMotion.clear()
		pendingClick = null
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

	private fun randomPatternSettleDelayMs(): Long {
		val ackMinimum = if (smoothedInteractionAckMs <= 0L) {
			PATTERN_SETTLE_MIN_MS
		} else {
			(smoothedInteractionAckMs * PATTERN_SETTLE_ACK_PERCENT / 100L)
				.coerceIn(PATTERN_SETTLE_MIN_MS, PATTERN_SETTLE_ADAPTIVE_MAX_MS)
		}
		val serverTickMinimum = estimatedServerTickMs
			?.times(PATTERN_SETTLE_SERVER_TICK_MULTIPLIER)
			?.toLong()
			?.coerceIn(PATTERN_SETTLE_MIN_MS, PATTERN_SETTLE_ADAPTIVE_MAX_MS)
			?: PATTERN_SETTLE_MIN_MS
		val adaptiveMinimum = max(ackMinimum, serverTickMinimum)
		val adaptiveMaximum = min(PATTERN_SETTLE_MAX_MS, adaptiveMinimum + PATTERN_SETTLE_JITTER_MS)
		return ThreadLocalRandom.current().nextLong(adaptiveMinimum, adaptiveMaximum + 1L)
	}

	private fun randomServerSafeClickSpacingMs(): Long {
		val serverTickSpacing = estimatedServerTickMs
			?.times(SERVER_SAFE_CLICK_TICK_MULTIPLIER)
			?.toLong()
			?: 0L
		val minimum = max(MIN_SERVER_SAFE_CLICK_SPACING_MS, serverTickSpacing)
			.coerceAtMost(MAX_SERVER_SAFE_CLICK_SPACING_MS)
		return minimum + ThreadLocalRandom.current().nextLong(SERVER_SAFE_CLICK_JITTER_MS + 1L)
	}

	private fun pendingClickTimeoutMs(): Long {
		val adaptiveTimeout = if (smoothedInteractionAckMs <= 0L) {
			MIN_PENDING_CLICK_TIMEOUT_MS
		} else {
			smoothedInteractionAckMs * PENDING_CLICK_TIMEOUT_ACK_MULTIPLIER
		}
		return adaptiveTimeout.coerceIn(MIN_PENDING_CLICK_TIMEOUT_MS, MAX_PENDING_CLICK_TIMEOUT_MS)
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
		private const val BUTTON_HIT_EPSILON = 0.03
		private const val FINAL_SEQUENCE_LENGTH = 5
		private const val PRACTICE_UNLOCK_SEQUENCE_COUNT = 2
		private const val PRACTICE_BUTTON_FACE_X = 0.875
		private const val PRACTICE_AIM_OFFSET = 0.13
		private const val PRE_AIM_AIM_OFFSET = 0.055
		private const val PRACTICE_RETURN_AIM_OFFSET = 0.085
		private const val VIRTUAL_BUTTON_VERTICAL_OFFSET = 0.085
		private const val VIRTUAL_BUTTON_HORIZONTAL_OFFSET = 0.135
		private const val VIRTUAL_BUTTON_HORIZONTAL_SCALE = 1.45
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
		private const val ACTUAL_CLICK_READY_TOLERANCE = 0.22
		private const val ACTUAL_FINISH_TOLERANCE = 0.14
		private const val PRACTICE_RETARGET_TOLERANCE = 0.22
		private const val MIN_PRACTICE_RETARGET_MS = 60L
		private const val OVERSHOOT_STRENGTH = 1.0
		private const val MICRO_CORRECTION = 0.55
		private const val CLICK_DELAY_MIN_MS = 8L
		private const val CLICK_DELAY_MAX_MS = 14L
		private const val MIN_CLICK_DELAY_VARIANCE_MS = 4L
		private const val PATTERN_SETTLE_MIN_MS = 90L
		private const val PATTERN_SETTLE_ADAPTIVE_MAX_MS = 220L
		private const val PATTERN_SETTLE_MAX_MS = 260L
		private const val PATTERN_SETTLE_JITTER_MS = 40L
		private const val PATTERN_SETTLE_ACK_PERCENT = 45L
		private const val PATTERN_SETTLE_SERVER_TICK_MULTIPLIER = 1.05
		private const val MIN_ACK_SAMPLE_MS = 1L
		private const val MAX_ACK_SAMPLE_MS = 2000L
		private const val MIN_PENDING_CLICK_TIMEOUT_MS = 6000L
		private const val MAX_PENDING_CLICK_TIMEOUT_MS = 15000L
		private const val PENDING_CLICK_TIMEOUT_ACK_MULTIPLIER = 6L
		private const val MIN_SERVER_SAFE_CLICK_SPACING_MS = 125L
		private const val MAX_SERVER_SAFE_CLICK_SPACING_MS = 350L
		private const val SERVER_SAFE_CLICK_JITTER_MS = 25L
		private const val SERVER_SAFE_CLICK_TICK_MULTIPLIER = 1.15
		private const val MIN_SERVER_TIME_SAMPLE_TICKS = 1L
		private const val MAX_SERVER_TIME_SAMPLE_TICKS = 200L
		private const val MIN_SERVER_TICK_SAMPLE_MS = 20.0
		private const val MAX_SERVER_TICK_SAMPLE_MS = 500.0
		private const val SERVER_TICK_SMOOTHING_OLD_WEIGHT = 0.7
		private const val MIN_START_CLICK_DELAY_MS = 105L
		private const val MAX_START_CLICK_DELAY_MS = 130L
		private const val AUTO_START_DELAY_RANDOM_EXTRA_MS = 40L
		private val CONTROL_CODE_PATTERN = Regex("(?i)§[0-9A-FK-OR]")

		private fun nowMs(): Long =
			System.nanoTime() / 1_000_000L

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

	private data class PendingClick(
		val button: BlockPos,
		val startButton: Boolean,
		val solveIndex: Int,
		val packetSequence: Int,
		val sentAtMs: Long
	)

	private sealed interface ClickSendResult {
		data object NotReady : ClickSendResult
		data object LocallySuppressed : ClickSendResult
		data class Sent(val packetSequence: Int) : ClickSendResult
	}
}
