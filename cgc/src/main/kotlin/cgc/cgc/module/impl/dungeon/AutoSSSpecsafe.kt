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
import cgc.cgc.runtime.PacketOrderManager
import cgc.cgc.runtime.PhysicalInputTracker
import cgc.cgc.utils.DungeonUtils
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
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import net.fabricmc.loader.api.FabricLoader
import java.util.Locale
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
	private val donePopup = BooleanSetting("Done Popup", false)
	private val fillColor = ColourSetting("Button Fill Color", Colour(85, 255, 85))
	private val outlineColor = ColourSetting("Button Outline Color", Colour(0, 170, 0))

	private val clicks = arrayListOf<BlockPos>()
	private val patternCapture = SimonSaysPatternCapture(FINAL_SEQUENCE_LENGTH)
	private val inputPhaseTracker = SimonSaysInputPhaseTracker()
	private val aimController = SimonSaysAimController()
	private val mouseMotion = VanillaMouseMotion()
	private val safetyInterlock = AutoSSSafetyInterlock()
	private val debugRecorder = AutoSSDebugRecorder()

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
	private var targetWaitingForButton = false
	private var targetWaitCorrectionStarted = false
	private var targetWaitCorrectionOnRealButton = false
	private var preAimButton: BlockPos? = null
	private var openingPreAimButton: BlockPos? = null
	private var openingPreAimAt = 0L
	private var startClicksRemaining = 0
	private var nextStartClickAt = 0L
	private var lastPatternLightAtMs = 0L
	private var clickedButton: Vec3? = null
	private var donePopupUntil = 0L
	private var completedSequenceCount = 0
	private var autoRestartAt = 0L
	private var pendingAutoStartAtMs = 0L
	private var lastServerPacketAtMs = 0L
	private var previousGridButton: BlockPos? = null
	private var previousGridMotion: GridMotion? = null
	private var latestAimResult: AimUpdateResult? = null
	private var pendingClick: PendingClick? = null
	private var clickPacketIntent: ClickIntent? = null
	private var capturedClickPacketSequence: Int? = null
	private var clickPacketRejectionReason: String? = null
	private var smoothedInteractionAckMs = 0L
	private var lastServerGameTime: Long? = null
	private var lastServerTimePacketAtMs = 0L
	private var estimatedServerTickMs: Double? = null
	private var nextServerSafeClickAtMs = 0L
	private var manualRestartRequired = false

	init {
		registerProperty(
			resetKey,
			autoStart,
			autoRestartSs,
			forceSkyblock,
			autoStartDelay,
			aimSpeed,
			waitCorrectionAimSpeed,
			donePopup,
			fillColor,
			outlineColor
		)
	}

	override fun onClientTick(client: Minecraft) {
		val now = nowMs()
		observeDebugTick(client, now)
		if (!areaCheck() || client.player == null || client.level == null) {
			saveDebugFailure("The dungeon area, player, or world became unavailable while Auto SS was running.", client, now)
			pendingAutoStartAtMs = 0L
			clearAutoRestart()
			clearTarget()
			return
		}
		if (!checkSafetyInterlock(client, now, "client_tick")) {
			return
		}
		if (tickPendingAutoStart(client, now)) {
			return
		}
		inputPhaseTracker.observe(client.level!!.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON), now)

		reportDebugStallIfNeeded(client, now)
		if (tickAutoRestart(now)) {
			return
		}
		// Solve clicks wait for the vanilla acknowledgement before logical state
		// advances. Startup is intentionally separate: the SS skip requires the
		// reference solver's fixed three-tick cadence rather than ping-gated clicks.
		val pending = pendingClick
		if (pending != null) {
			if (now - pending.sentAtMs >= pendingClickTimeoutMs()) {
				stopSimonSaysSafely("Auto SS stopped: the previous click was never acknowledged.")
			}
			return
		}
		if (startClicksRemaining <= 0) {
			if (!commitCapturedPatternIfReady(client, now) && stopIfPatternEndedIncomplete(client)) {
				return
			}
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

		if (!doingSS) {
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

		if (state >= clicks.size) {
			return
		}

		if (now - lastClickTime < currentClickDelayMs) {
			return
		}

		if (!client.level!!.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON)) {
			return
		}

		if (state >= clicks.size) {
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
		if (!areaCheck() || client.player == null) {
			return
		}
		if (!checkSafetyInterlock(client, nowMs(), "world_render_start")) {
			return
		}
		val button = targetButton ?: return
		if (targetIsStart && isStartButtonAlreadyAimed(client)) {
			return
		}
		if ((targetPreAim || targetWaitingForButton) && isCurrentSolveButtonAlreadyAimed(client, button)) {
			return
		}
		latestAimResult = updateAimRotation(client)
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
		saveDebugFailure("The world changed before Auto SS completed.")
		resetState()
		manualRestartRequired = false
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
				scheduleAutoStart()
			}
		}
		debugEvent("chat_message", stripControlCodes(message))
		handlePossibleSimonSaysFailure(message, "chat")
	}

	override fun onActionBarMessage(message: String) {
		runOnClientThread {
			debugEvent("action_bar_message", stripControlCodes(message))
			handlePossibleSimonSaysFailure(message, "action_bar")
		}
	}

	override fun onBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		if (runOnClientThread { handleBlockChange(pos.immutable(), oldState, newState) }) {
			return
		}
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		val intent = clickPacketIntent ?: return false
		if (packet is ServerboundUseItemPacket) {
			val rejection = "vanilla emitted an item-use packet instead of a block interaction"
			clickPacketRejectionReason = rejection
			debugEvent(
				"click_packet_blocked",
				"button=${debugPos(intent.button)} start_button=${intent.startButton} solve_index=${intent.solveIndex} reason=$rejection"
			)
			return true
		}
		if (packet !is ServerboundUseItemOnPacket) {
			return false
		}

		val actualButton = packet.hitResult.blockPos
		val rejection = when {
			actualButton != intent.button ->
				"outgoing packet targeted ${debugPos(actualButton)} instead of ${debugPos(intent.button)}"
			else -> validateLogicalClickIntent(Minecraft.getInstance(), intent)
		}
		if (rejection != null) {
			clickPacketRejectionReason = rejection
			debugEvent(
				"click_packet_blocked",
				"button=${debugPos(intent.button)} start_button=${intent.startButton} solve_index=${intent.solveIndex} reason=$rejection packet_hit=${debugHit(packet.hitResult)}"
			)
			return true
		}

		capturedClickPacketSequence = packet.sequence
		debugEvent(
			"click_packet_captured",
			"button=${debugPos(intent.button)} sequence=${packet.sequence} packet_hit=${debugHit(packet.hitResult)}",
			progress = true
		)
		return false
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		val receivedAtMs = nowMs()
		val acknowledgedSequence = newestAcknowledgedPredictionSequence(packet)
		val serverGameTime = newestServerGameTime(packet)
		runOnClientThread {
			lastServerPacketAtMs = receivedAtMs
			if (serverGameTime != null) {
				observeServerTime(serverGameTime, receivedAtMs)
			}
			if (acknowledgedSequence != null) {
				acknowledgePendingClickFromPrediction(acknowledgedSequence)
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
		if (doingSS && areaCheck() && pos == DETECT) {
			val observedAt = nowMs()
			inputPhaseTracker.observe(newState.`is`(Blocks.STONE_BUTTON), observedAt)
			debugEvent(
				"input_phase_marker",
				"old=$oldState new=$newState display_seen=${inputPhaseTracker.displayPhaseObserved} input_phase_at_ms=${inputPhaseTracker.inputPhaseObservedAtMs}",
				progress = newState.`is`(Blocks.STONE_BUTTON),
				now = observedAt
			)
		}
		if (!doingSS
			|| !areaCheck()
			|| !isPatternLightUpdate(pos, newState)
			|| oldState?.`is`(Blocks.SEA_LANTERN) == true
		) {
			return
		}
		if (doneFirst && state < clicks.size) {
			stopSimonSaysSafely(
				"Auto SS stopped: the server displayed an extra light after the expected pattern was complete."
			)
			return
		}

		val observedAt = nowMs()
		val button = BlockPos(110, pos.y, pos.z)
		patternCapture.record(button)
		lastPatternLightAtMs = observedAt
		debugEvent(
			"pattern_light_recorded",
			"light=${debugPos(pos)} old=$oldState new=$newState button=${debugPos(button)} observation=${patternCapture.observationCount} expected=${expectedPatternObservationCount()} observations=${debugPattern(patternCapture.observations())}",
			progress = true
		)
		if (startClicksRemaining > 0) {
			return
		}

		if (targetPreAim && !targetOpeningPreAim && button == preAimButton) {
			targetPreAim = false
			targetWaitingForButton = true
		}

		scheduleOpeningPreAim()
	}

	private fun commitCapturedPatternIfReady(client: Minecraft, now: Long): Boolean {
		if (patternCapture.observationCount <= 0
			|| (doneFirst && state < clicks.size)
		) {
			return false
		}

		val inputPhaseConfirmsPattern = inputPhaseTracker.confirmsPattern(lastPatternLightAtMs)
		val openingTwoTransitionGraceMs = openingTwoTransitionGraceMs()
		val openingTwoTransitionTimedOut = client.level?.getBlockState(DETECT)?.`is`(Blocks.STONE_BUTTON) == true &&
			now >= lastPatternLightAtMs + openingTwoTransitionGraceMs
		val acceptTwoTransitionOpening = !doneFirst &&
			patternCapture.observationCount == 2 &&
			(inputPhaseConfirmsPattern || openingTwoTransitionTimedOut)
		val capturedPattern = if (!doneFirst) {
			patternCapture.openingSkipPattern(acceptTwoTransitionOpening)
		} else {
			when (val replay = patternCapture.nextPattern(clicks)) {
				PatternReplayResult.Incomplete -> return false
				PatternReplayResult.Mismatch -> {
					debugEvent(
						"pattern_mismatch",
						"expected=${debugPattern(clicks)} observations=${debugPattern(patternCapture.observations())}"
					)
					stopSimonSaysSafely("Auto SS stopped: the displayed pattern did not match the previous round.")
					return false
				}
				is PatternReplayResult.Ready -> replay.buttons
			}
		} ?: return false

		clicks.clear()
		clicks.addAll(capturedPattern)
		state = 0
		doneFirst = true
		patternCapture.clear()
		debugEvent(
			"pattern_committed",
			"length=${clicks.size} buttons=${debugPattern(clicks)} opening_two_transition_fallback=$acceptTwoTransitionOpening" +
				if (acceptTwoTransitionOpening) {
					" decision=${if (inputPhaseConfirmsPattern) "input_phase_marker" else "timeout"} grace_ms=$openingTwoTransitionGraceMs"
				} else "",
			progress = true,
			now = now
		)
		return true
	}

	private fun stopIfPatternEndedIncomplete(client: Minecraft): Boolean {
		if (!doingSS
			|| inputPhaseTracker.inputPhaseObservedAtMs <= 0L
			|| client.level?.getBlockState(DETECT)?.`is`(Blocks.STONE_BUTTON) != true
			|| doneFirst && state < clicks.size
		) {
			return false
		}

		val observed = patternCapture.observationCount
		val expected = expectedPatternObservationCount()
		if (observed >= expected || !doneFirst && observed == 2) {
			return false
		}

		stopSimonSaysSafely(
			"Auto SS stopped: the pattern ended after $observed of $expected expected light transitions."
		)
		return true
	}

	private fun expectedPatternObservationCount(): Int =
		if (doneFirst) clicks.size + 1 else OPENING_SKIP_OBSERVATION_COUNT

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
			start(START_TRIGGER_RESET_KEY)
		}
	}

	override fun onEnable() {
		resetState()
		manualRestartRequired = false
		resetServerTiming()
		resetKey.register()
	}

	override fun onDisable() {
		saveDebugFailure("The Auto SS module was disabled before the run completed.")
		resetKey.unregister()
		resetState()
		manualRestartRequired = false
		resetServerTiming()
	}

	override fun reset() {
		saveDebugFailure("The Auto SS module state was reset before the run completed.")
		resetState()
		manualRestartRequired = false
	}

	private fun scheduleAutoStart() {
		val client = Minecraft.getInstance()
		val player = client.player
		if (player == null
			|| client.level == null
			|| player.distanceToSqr(AUTO_SS_ACTIVATION_CENTER) > AUTO_SS_ACTIVATION_RADIUS_SQ
			|| manualRestartRequired
			|| doingSS
			|| pendingAutoStartAtMs > 0L
		) {
			return
		}

		pendingAutoStartAtMs = nowMs() + randomAutoStartDelayMs()
	}

	private fun tickPendingAutoStart(client: Minecraft, now: Long): Boolean {
		val startAt = pendingAutoStartAtMs
		if (startAt <= 0L) {
			return false
		}
		val player = client.player
		if (player == null
			|| client.level == null
			|| player.distanceToSqr(AUTO_SS_ACTIVATION_CENTER) > AUTO_SS_ACTIVATION_RADIUS_SQ
			|| manualRestartRequired
			|| doingSS
		) {
			pendingAutoStartAtMs = 0L
			return false
		}
		if (now < startAt || lastServerPacketAtMs < startAt) {
			return true
		}

		pendingAutoStartAtMs = 0L
		start(START_TRIGGER_BOSS_CHAT, initialDelayMs = 0L)
		return true
	}

	private fun start(trigger: String, initialDelayMs: Long = randomAutoStartDelayMs()) {
		val client = Minecraft.getInstance()
		val player = client.player
		if (player == null || client.level == null || player.distanceToSqr(AUTO_SS_ACTIVATION_CENTER) > AUTO_SS_ACTIVATION_RADIUS_SQ) {
			return
		}
		if (manualRestartRequired && trigger != START_TRIGGER_RESET_KEY) {
			return
		}
		if (doingSS && trigger == START_TRIGGER_BOSS_CHAT) {
			return
		}

		if (debugRecorder.isActive) {
			saveDebugFailure("A new Auto SS run started before the previous run completed (trigger=$trigger).", client)
		}
		resetState()
		if (trigger == START_TRIGGER_RESET_KEY) {
			manualRestartRequired = false
		}
		doingSS = true
		inputPhaseTracker.arm(client.level!!.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON))
		safetyInterlock.arm(
			position = player.position(),
			rotation = Rotation(player.yRot, player.xRot),
			input = PhysicalInputTracker.snapshot()
		)
		startAimPoint = getAimPoint(client.level!!, startButtonPos())
		startClicksRemaining = 3
		val startedAt = nowMs()
		nextStartClickAt = startedAt + initialDelayMs
		beginDebugSession(client, trigger, startedAt)
		debugEvent(
			"start_clicks_scheduled",
			"remaining=$startClicksRemaining first_click_in_ms=${(nextStartClickAt - startedAt).coerceAtLeast(0L)}",
			progress = true,
			now = startedAt
		)
		chat("Starting spectator safe SS!")
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

		if (targetPreAim) {
			// Pre-aim is an idle hold. The top-level tick interrupts it as soon as
			// solving is ready.
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
		safetyInterlock.recordSolverRotation(actual)
		val plan = aimController.currentPlan() ?: return null
		val actualError = angularError(actual, plan.final)
		return AimUpdateResult(
			rotation = actual,
			readyToClick = result.readyToClick && actualError <= ACTUAL_CLICK_READY_TOLERANCE,
			finished = result.finished && actualError <= ACTUAL_FINISH_TOLERANCE
		)
	}

	private fun startAim(
		player: LocalPlayer,
		target: Vec3,
		mode: AimMode,
		moveContext: AimMoveContext? = null
	) {
		latestAimResult = null
		aimController.start(
			player = player,
			target = target,
			settings = getAimSettings(mode),
			mode = mode,
			moveContext = moveContext,
			motion = AUTO_SS_AIM_MOTION_PROFILE
		)
		val plan = aimController.currentPlan()
		if (plan != null) {
			debugEvent(
				"aim_started",
				"mode=$mode target=${debugVec(target)} start=${debugRotation(plan.start)} final=${debugRotation(plan.final)} distance_deg=${debugDouble(plan.angularDistance)} duration_ms=${plan.durationMs} seed=${plan.seed} move_context=${moveContext ?: "none"}",
				progress = true,
				now = plan.startedAtMs
			)
		}
	}

	private fun angularError(rotation: Rotation, target: Rotation): Double {
		val yaw = Mth.wrapDegrees(rotation.yaw - target.yaw).toDouble()
		val pitch = (rotation.pitch - target.pitch).toDouble()
		return sqrt(yaw * yaw + pitch * pitch)
	}

	private fun checkSafetyInterlock(client: Minecraft, now: Long, source: String): Boolean {
		if (!doingSS) {
			return true
		}
		val player = client.player ?: return false
		val violation = safetyInterlock.check(
			position = player.position(),
			rotation = Rotation(player.yRot, player.xRot),
			input = PhysicalInputTracker.snapshot(),
			nowMs = now
		) ?: return true

		val detail = when (violation) {
			AutoSSSafetyViolation.PLAYER_MOVED ->
				"the player moved from ${safetyInterlock.startingPosition?.let(::debugVec) ?: "unknown"} to ${debugVec(player.position())}"
			AutoSSSafetyViolation.UNAUTHORIZED_CAMERA_MOVEMENT ->
				"the camera moved outside physical mouse input and the Auto SS rotator " +
					"(expected=${safetyInterlock.expectedRotation?.let(::debugRotation) ?: "unknown"}, actual=${debugRotation(Rotation(player.yRot, player.xRot))})"
		}
		debugEvent("safety_interlock", "source=$source violation=$violation detail=$detail")
		manualRestartRequired = true
		stopSimonSaysSafely("Auto SS stopped: $detail. Use the Auto SS reset key to restart it.")
		return false
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
		val solveIndex = if (clickedStart) -1 else state
		val packetSequence = when (
			val sendResult = sendClickInteraction(
				client = client,
				intent = ClickIntent(button, clickedStart, solveIndex),
				allowPowered = clickedStart
			)
		) {
			is ClickSendResult.NotReady -> {
				debugEvent(
					"click_not_sent",
					"button=${debugPos(button)} start_button=$clickedStart reason=${sendResult.reason}"
				)
				return false
			}
			is ClickSendResult.GuardRejected -> {
				debugEvent(
					"click_guard_rejected",
					"button=${debugPos(button)} start_button=$clickedStart solve_index=$solveIndex reason=${sendResult.reason}"
				)
				clearTarget()
				return true
			}
			ClickSendResult.LocallySuppressed -> {
				debugEvent(
					"click_locally_suppressed",
					"button=${debugPos(button)} start_button=$clickedStart no matching outgoing interaction packet was observed"
				)
				stopSimonSaysSafely("Auto SS stopped: its verified click did not produce an outgoing block-interaction packet.")
				return true
			}
			is ClickSendResult.Sent -> sendResult.packetSequence
		}

		val sentAt = nowMs()
		recordClickAttempt(button, sentAt, clickedStart)
		if (clickedStart) {
			startClicksRemaining = max(0, startClicksRemaining - 1)
			nextStartClickAt = if (startClicksRemaining > 0) {
				sentAt + START_CLICK_INTERVAL_MS
			} else {
				0L
			}
			debugEvent(
				"start_click_sent",
				"button=${debugPos(button)} sequence=$packetSequence remaining=$startClicksRemaining next_click_in_ms=${(nextStartClickAt - sentAt).coerceAtLeast(0L)}",
				progress = true,
				now = sentAt
			)
			clearTarget()
			if (startClicksRemaining <= 0) {
				scheduleOpeningPreAim()
			}
			return true
		}

		val pending = PendingClick(
			button = button,
			solveIndex = solveIndex,
			packetSequence = packetSequence,
			sentAtMs = sentAt
		)
		pendingClick = pending
		debugEvent(
			"click_pending",
			"button=${debugPos(button)} start_button=$clickedStart solve_index=${pending.solveIndex} packet_sequence=$packetSequence timeout_ms=${pendingClickTimeoutMs()}",
			progress = true,
			now = sentAt
		)
		beginMotionWhileClickPending(client, pending)
		return true
	}

	private fun beginMotionWhileClickPending(client: Minecraft, pending: PendingClick) {
		if (!doingSS
			|| pending.solveIndex !in clicks.indices
			|| clicks[pending.solveIndex] != pending.button
		) {
			return
		}

		val nextIndex = pending.solveIndex + 1
		if (nextIndex < clicks.size) {
			if (clicks[nextIndex] == pending.button) {
				debugEvent(
					"same_button_held",
					"button=${debugPos(pending.button)} next_index=$nextIndex",
					progress = true
				)
				return
			}
			beginSequenceButton(client, nextIndex, flowingRetarget = true)
			return
		}

		if (clicks.size >= FINAL_SEQUENCE_LENGTH) {
			return
		}
		beginFirstButtonPreAim(client)
	}

	private fun validateLogicalClickIntent(client: Minecraft, intent: ClickIntent): String? {
		val level = client.level ?: return "world unavailable during logical verification"
		if (!doingSS) {
			return "solver is no longer running"
		}
		if (!areaCheck()) {
			return "player is no longer in the Simon Says area"
		}
		if (targetButton != intent.button || targetIsStart != intent.startButton) {
			return "active target changed before packet emission"
		}

		if (intent.startButton) {
			if (intent.button != startButtonPos() || intent.solveIndex != -1) {
				return "invalid start-button intent"
			}
			if (startClicksRemaining <= 0) {
				return "all scheduled start clicks were already emitted"
			}
			if (pendingClick != null) {
				return "a solve click is still awaiting acknowledgement"
			}
			return null
		}

		if (startClicksRemaining > 0) {
			return "$startClicksRemaining start clicks remain"
		}
		if (!doneFirst) {
			return "opening SS-skip pattern is not committed"
		}
		if (pendingClick != null) {
			return "another solve click is still awaiting acknowledgement"
		}
		if (state != intent.solveIndex) {
			return "solve index changed from ${intent.solveIndex} to $state"
		}
		val expected = clicks.getOrNull(state)
			?: return "captured pattern has no button at solve index $state"
		if (expected != intent.button) {
			return "captured pattern expects ${debugPos(expected)} at index $state, not ${debugPos(intent.button)}"
		}
		if (patternCapture.observationCount > 0) {
			return "a newer pattern is still being captured: ${debugPattern(patternCapture.observations())}"
		}
		if (!level.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON)) {
			return "input-phase marker is not a stone button"
		}
		val missingButtons = INPUT_BUTTONS.filterNot { level.getBlockState(it).`is`(Blocks.STONE_BUTTON) }
		if (missingButtons.isNotEmpty()) {
			return "input grid is incomplete (${missingButtons.size} buttons unavailable)"
		}
		return null
	}

	private fun sendClickInteraction(
		client: Minecraft,
		intent: ClickIntent,
		allowPowered: Boolean = false
	): ClickSendResult {
		val button = intent.button
		val player = client.player ?: return ClickSendResult.NotReady("player unavailable")
		val level = client.level ?: return ClickSendResult.NotReady("world unavailable")
		val gameMode = client.gameMode ?: return ClickSendResult.NotReady("game mode unavailable")
		val now = nowMs()
		if (now < nextServerSafeClickAtMs) {
			return ClickSendResult.NotReady("server-safe spacing has ${(nextServerSafeClickAtMs - now).coerceAtLeast(0L)}ms remaining")
		}
		if (client.screen != null) {
			return ClickSendResult.NotReady("screen open: ${client.screen?.javaClass?.simpleName}")
		}
		if (!client.mouseHandler.isMouseGrabbed) {
			return ClickSendResult.NotReady("mouse is not grabbed")
		}
		if (gameMode.isDestroying) {
			return ClickSendResult.NotReady("player is destroying a block")
		}
		if (player.isHandsBusy) {
			return ClickSendResult.NotReady("player hands are busy")
		}
		if (!intent.startButton) {
			if (patternCapture.observationCount > 0) {
				return ClickSendResult.NotReady("pattern capture is still changing")
			}
			if (!level.getBlockState(DETECT).`is`(Blocks.STONE_BUTTON)) {
				return ClickSendResult.NotReady("input-phase marker is not a stone button")
			}
			val unavailableCount = INPUT_BUTTONS.count { !level.getBlockState(it).`is`(Blocks.STONE_BUTTON) }
			if (unavailableCount > 0) {
				return ClickSendResult.NotReady("input grid is incomplete ($unavailableCount buttons unavailable)")
			}
		}
		validateLogicalClickIntent(client, intent)?.let {
			return ClickSendResult.GuardRejected(it)
		}

		val buttonState = level.getBlockState(button)
		if (!buttonState.`is`(Blocks.STONE_BUTTON)) {
			return ClickSendResult.NotReady("target block is not a stone button: $buttonState")
		}
		if (!allowPowered && isPressedStoneButton(buttonState)) {
			return ClickSendResult.NotReady("target stone button is already powered")
		}
		val distanceSq = player.distanceToSqr(Vec3.atCenterOf(button))
		if (distanceSq > MAX_BUTTON_DISTANCE_SQ) {
			return ClickSendResult.NotReady("target distance squared ${debugDouble(distanceSq)} exceeds $MAX_BUTTON_DISTANCE_SQ")
		}
		val verifiedHit = getVerifiedClickHit(client, button)
		if (verifiedHit == null) {
			val freshHit = getLookHit(client, button)
			val crosshairHit = client.hitResult as? BlockHitResult
			return ClickSendResult.NotReady(
				"click ray verification failed; fresh_hit=${freshHit?.let(::debugHit) ?: "none"}; crosshair_hit=${crosshairHit?.let(::debugHit) ?: client.hitResult?.type ?: "none"}"
			)
		}
		debugEvent(
			"click_preflight_verified",
			"button=${debugPos(button)} allow_powered=$allowPowered distance_sq=${debugDouble(distanceSq)} " +
				"verified_hit=${debugHit(verifiedHit)} crosshair_hit=${debugHit(client.hitResult)} " +
				"selected_slot=${player.inventory.selectedSlot} main_hand=${player.mainHandItem}"
		)

		clickPacketIntent = intent
		capturedClickPacketSequence = null
		clickPacketRejectionReason = null
		var localInteractionResult = "not_dispatched"
		var immediateSlotAcquired = false
		try {
			immediateSlotAcquired = PacketOrderManager.tryRunProtectedActionImmediately {
				// Dispatch the exact hit that passed both ray checks. Minecraft.startUseItem()
				// reads Minecraft.hitResult again, allowing a mutable/stale crosshair result
				// (or another interaction hook) to turn this into an air/item use instead.
				val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, verifiedHit)
				localInteractionResult = "${result.javaClass.simpleName}(consumes_action=${result.consumesAction()})"
				player.swing(InteractionHand.MAIN_HAND)
			}
		} finally {
			clickPacketIntent = null
		}
		if (!immediateSlotAcquired) {
			capturedClickPacketSequence = null
			clickPacketRejectionReason = null
			return ClickSendResult.NotReady("CGC protected-action slot is busy for this client tick")
		}
		val packetSequence = capturedClickPacketSequence
		val rejectionReason = clickPacketRejectionReason
		capturedClickPacketSequence = null
		clickPacketRejectionReason = null
		if (rejectionReason != null) {
			return ClickSendResult.GuardRejected(rejectionReason)
		}
		return if (packetSequence == null) {
			debugEvent(
				"click_dispatch_suppressed",
				"button=${debugPos(button)} verified_hit=${debugHit(verifiedHit)} local_result=$localInteractionResult " +
					"selected_slot=${player.inventory.selectedSlot} main_hand=${player.mainHandItem}"
			)
			ClickSendResult.LocallySuppressed
		} else {
			ClickSendResult.Sent(packetSequence)
		}
	}

	private fun recordClickAttempt(button: BlockPos, sentAt: Long, startButton: Boolean) {
		clearAutoRestart()
		lastClickTime = sentAt
		currentClickDelayMs = randomClickDelayMs()
		nextServerSafeClickAtMs = sentAt + if (startButton) START_CLICK_INTERVAL_MS else randomServerSafeClickSpacingMs()
		clickedButton = Vec3.atLowerCornerOf(button)
		debugEvent(
			"click_attempt_recorded",
			"button=${debugPos(button)} click_delay_ms=$currentClickDelayMs server_safe_spacing_ms=${(nextServerSafeClickAtMs - sentAt).coerceAtLeast(0L)}",
			progress = true,
			now = sentAt
		)
	}

	private fun acknowledgePendingClickFromPrediction(acknowledgedSequence: Int) {
		val pending = pendingClick ?: return
		// Acknowledgements are cumulative, so a newer sequence also proves this
		// click has crossed a server processing boundary.
		if (acknowledgedSequence < pending.packetSequence) {
			debugEvent(
				"click_ack_ignored",
				"acknowledged_sequence=$acknowledgedSequence pending_sequence=${pending.packetSequence} reason=older_sequence"
			)
			return
		}
		val targetState = Minecraft.getInstance().level?.getBlockState(pending.button)
		if (pending.solveIndex < clicks.lastIndex && targetState?.`is`(Blocks.STONE_BUTTON) != true) {
			debugEvent(
				"click_prediction_ack_not_confirming",
				"button=${debugPos(pending.button)} pending_sequence=${pending.packetSequence} acknowledged_sequence=$acknowledgedSequence target_state=${targetState ?: "unavailable"} reason=intermediate_button_disappeared"
			)
			return
		}

		val acknowledgedAt = nowMs()
		val latencyMs = (acknowledgedAt - pending.sentAtMs).coerceAtLeast(0L)
		debugEvent(
			"click_acknowledged",
			"source=prediction_ack button=${debugPos(pending.button)} pending_sequence=${pending.packetSequence} acknowledged_sequence=$acknowledgedSequence latency_ms=$latencyMs",
			progress = true,
			now = acknowledgedAt
		)
		observeInteractionAck(latencyMs)
		completePendingClick(Minecraft.getInstance(), pending)
	}

	private fun observeInteractionAck(sampleMs: Long) {
		val boundedSample = sampleMs.coerceIn(MIN_ACK_SAMPLE_MS, MAX_ACK_SAMPLE_MS)
		smoothedInteractionAckMs = if (smoothedInteractionAckMs <= 0L) {
			boundedSample
		} else {
			(smoothedInteractionAckMs * 2L + boundedSample) / 3L
		}
		debugEvent(
			"interaction_ack_timing",
			"sample_ms=$sampleMs bounded_sample_ms=$boundedSample smoothed_ms=$smoothedInteractionAckMs"
		)
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
			debugEvent(
				"server_tick_sample_rejected",
				"game_ticks=$elapsedTicks elapsed_ms=$elapsedMs sample_ms=${debugDouble(sampleMs)}"
			)
			return
		}
		val previousEstimate = estimatedServerTickMs
		estimatedServerTickMs = if (previousEstimate == null) {
			sampleMs
		} else {
			previousEstimate * SERVER_TICK_SMOOTHING_OLD_WEIGHT + sampleMs * (1.0 - SERVER_TICK_SMOOTHING_OLD_WEIGHT)
		}
		debugEvent(
			"server_tick_timing",
			"game_ticks=$elapsedTicks elapsed_ms=$elapsedMs sample_ms=${debugDouble(sampleMs)} estimated_ms=${estimatedServerTickMs?.let(::debugDouble) ?: "unknown"}"
		)
	}

	private fun resetServerTiming() {
		lastServerGameTime = null
		lastServerTimePacketAtMs = 0L
		estimatedServerTickMs = null
	}

	private fun acknowledgePendingClickFromBlockUpdate(pos: BlockPos, newState: BlockState) {
		val pending = pendingClick ?: return
		val buttonPressed = pos == pending.button && isPressedStoneButton(newState)
		val serverAdvancedPattern = isPatternLightUpdate(pos, newState) &&
			pending.solveIndex >= clicks.lastIndex
		if (!buttonPressed && !serverAdvancedPattern) {
			if (pos == pending.button || isPatternLightUpdate(pos, newState)) {
				debugEvent(
					"click_block_update_not_confirming",
					"position=${debugPos(pos)} state=$newState pending_button=${debugPos(pending.button)}"
				)
			}
			return
		}

		val confirmedAt = nowMs()
		debugEvent(
			"click_acknowledged",
			"source=block_update button=${debugPos(pending.button)} update_position=${debugPos(pos)} update_state=$newState button_pressed=$buttonPressed pattern_advanced=$serverAdvancedPattern latency_ms=${(confirmedAt - pending.sentAtMs).coerceAtLeast(0L)}",
			progress = true,
			now = confirmedAt
		)
		completePendingClick(Minecraft.getInstance(), pending)
	}

	private fun completePendingClick(client: Minecraft, pending: PendingClick) {
		if (pendingClick?.packetSequence != pending.packetSequence) {
			return
		}
		pendingClick = null
		if (!doingSS) {
			debugEvent(
				"click_completion_ignored",
				"button=${debugPos(pending.button)} sequence=${pending.packetSequence} reason=solver_not_running"
			)
			clearTarget()
			return
		}

		if (pending.solveIndex !in clicks.indices || clicks[pending.solveIndex] != pending.button) {
			saveDebugFailure(
				"A confirmed click no longer matched the captured pattern (index=${pending.solveIndex}, button=${debugPos(pending.button)}).",
				client
			)
			clearTarget()
			return
		}
		state = pending.solveIndex + 1
		debugEvent(
			"solve_click_completed",
			"button=${debugPos(pending.button)} sequence=${pending.packetSequence} completed_index=${pending.solveIndex} next_state=$state pattern_length=${clicks.size}",
			progress = true
		)
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
		lastPatternLightAtMs = 0L
		inputPhaseTracker.arm(client.level?.getBlockState(DETECT)?.`is`(Blocks.STONE_BUTTON) == true)
		completedSequenceCount++
		debugEvent(
			"sequence_completed",
			"sequence_length=${clicks.size} completed_sequences=$completedSequenceCount",
			progress = true
		)
		if (clicks.size >= FINAL_SEQUENCE_LENGTH) {
			finishSimonSays()
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
			!targetWaitingForButton &&
			aimController.hasTarget()

	private fun isPreparedFirstButtonPreAim(): Boolean =
		targetButton == clicks.firstOrNull() &&
			targetPreAim &&
			!targetOpeningPreAim &&
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
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		val realButton = level.getBlockState(button).`is`(Blocks.STONE_BUTTON)
		targetWaitCorrectionOnRealButton = realButton
		preAimButton = button
		targetAimPoint = if (realButton) {
			getAimPoint(level, button, PRE_AIM_TOLERANCE)
		} else {
			getVirtualButtonAimPoint(button, PRE_AIM_AIM_OFFSET)
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
			patternCapture.openingSkipFirstCandidate()
		}
	}

	private fun clearOpeningPreAim() {
		openingPreAimButton = null
		openingPreAimAt = 0L
	}

	private fun shouldYieldPreAim(client: Minecraft): Boolean {
		if (!doingSS || startClicksRemaining > 0) {
			return false
		}

		return readyToSolveCurrentPattern(client)
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
		val currentMotion = GridMotion(rowDelta, columnDelta)
		val alignment = previousGridMotion?.alignmentWith(currentMotion)
		val nextMotion = nextGridMotion(button, mode, sequenceIndex)
		return AimMoveContext(
			chebyshevDistance = chebyshevDistance,
			continuingDirection = alignment != null && alignment >= 0.72,
			reversingDirection = alignment != null && alignment <= -0.30,
			passesThroughTarget = nextMotion != null && currentMotion.alignmentWith(nextMotion) >= 0.72
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
			AimMode.CHAINED_RETARGET -> true
			else -> false
		}

	private fun nextGridMotion(button: BlockPos, mode: AimMode, sequenceIndex: Int = state): GridMotion? {
		val nextButton = when (mode) {
			AimMode.NORMAL_BUTTON,
			AimMode.CHAINED_RETARGET -> clicks.getOrNull(sequenceIndex + 1)
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
			client.level?.getBlockState(DETECT)?.`is`(Blocks.STONE_BUTTON) == true &&
			state < clicks.size
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

	private fun getVirtualButtonAimPoint(pos: BlockPos, maxOffset: Double): Vec3 {
		val random = ThreadLocalRandom.current()
		val verticalOffset = min(maxOffset, VIRTUAL_BUTTON_VERTICAL_OFFSET)
		val horizontalOffset = min(maxOffset * VIRTUAL_BUTTON_HORIZONTAL_SCALE, VIRTUAL_BUTTON_HORIZONTAL_OFFSET)
		return Vec3(
			pos.x + VIRTUAL_BUTTON_FACE_X,
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

	private fun scheduleAutoRestart(): Boolean {
		if (!autoRestartSs.value || !doingSS) {
			return false
		}

		if (autoRestartAt <= 0L) {
			autoRestartAt = nowMs() + randomAutoRestartReactionDelayMs()
			debugEvent(
				"auto_restart_scheduled",
				"restart_in_ms=${(autoRestartAt - nowMs()).coerceAtLeast(0L)}",
				progress = true
			)
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
		start("auto_restart")
		return true
	}

	private fun clearAutoRestart() {
		autoRestartAt = 0L
	}

	private fun handlePossibleSimonSaysFailure(message: String, source: String) {
		if (!doingSS || !areaCheck() || !isSimonSaysFailureMessage(message)) {
			return
		}

		val cleanMessage = stripControlCodes(message)
		debugEvent(
			"failure_signal_detected",
			"source=$source message=$cleanMessage",
			progress = false
		)
		val restarting = scheduleAutoRestart()
		saveDebugFailure(
			"A Simon Says failure was reported via $source: $cleanMessage${if (restarting) " (auto restart scheduled)" else ""}."
		)
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

	private fun beginDebugSession(client: Minecraft, trigger: String, now: Long) {
		debugRecorder.begin(
			trigger = trigger,
			metadata = debugMetadata(),
			phase = debugPhase(now),
			initialSnapshot = debugSnapshot(client, now),
			nowMs = now
		)
	}

	private fun observeDebugTick(client: Minecraft, now: Long) {
		if (!debugRecorder.isActive) {
			return
		}
		debugRecorder.observeTick(now, debugPhase(now), debugSnapshot(client, now))
	}

	private fun reportDebugStallIfNeeded(client: Minecraft, now: Long) {
		val stalledForMs = debugRecorder.stalledForMs(now) ?: return
		val pendingTimeoutWithGrace = pendingClick
			?.let { pendingClickTimeoutMs() + PENDING_WATCHDOG_GRACE_MS }
			?: 0L
		val thresholdMs = max(STALL_REPORT_AFTER_MS, pendingTimeoutWithGrace)
		if (stalledForMs < thresholdMs) {
			return
		}

		val phase = debugPhase(now)
		debugEvent(
			"watchdog_stall",
			"phase=$phase no_progress_for_ms=$stalledForMs threshold_ms=$thresholdMs"
		)
		saveDebugFailure(
			"Auto SS made no logical progress for ${stalledForMs}ms while in phase '$phase'.",
			client,
			now
		)
	}

	private fun debugEvent(
		category: String,
		details: String,
		progress: Boolean = false,
		now: Long = nowMs()
	) {
		debugRecorder.event(category, details, now, progress)
	}

	private fun saveDebugFailure(
		reason: String,
		client: Minecraft = Minecraft.getInstance(),
		now: Long = nowMs()
	) {
		if (!debugRecorder.isActive) {
			return
		}
		val result = debugRecorder.fail(reason, debugSnapshot(client, now), now) ?: return
		when {
			result.path != null -> chat("Auto SS debug report saved: config/cgc/reports/${result.path.fileName}")
			result.error != null -> chat("Auto SS debug report could not be saved: ${result.error}")
		}
	}

	private fun debugMetadata(): Map<String, String> =
		linkedMapOf(
			"cgc_version" to debugModVersion("cgc"),
			"minecraft_version" to debugModVersion("minecraft"),
			"java_version" to (System.getProperty("java.version") ?: "unknown"),
			"os" to listOfNotNull(
				System.getProperty("os.name"),
				System.getProperty("os.version"),
				System.getProperty("os.arch")
			).joinToString(" "),
			"auto_start" to autoStart.value.toString(),
			"auto_restart_ss" to autoRestartSs.value.toString(),
			"force_skyblock" to forceSkyblock.value.toString(),
			"auto_start_delay_ms" to getDouble(autoStartDelay).toString(),
			"aim_speed" to getDouble(aimSpeed).toString(),
			"wait_correction_aim_speed" to getDouble(waitCorrectionAimSpeed).toString(),
			"location_area" to Location.area.toString(),
			"location_floor" to Location.floor.toString(),
			"phase_p3" to DungeonUtils.isPhase(Phase7.P3).toString()
		)

	private fun debugModVersion(modId: String): String =
		runCatching {
			FabricLoader.getInstance()
				.getModContainer(modId)
				.map { it.metadata.version.friendlyString }
				.orElse("unknown")
		}.getOrDefault("unknown")

	private fun debugPhase(now: Long): String {
		val pending = pendingClick
		val target = targetButton
		return when {
			!doingSS -> "inactive"
			pending != null -> "waiting_click_ack:${debugPos(pending.button)}:sequence=${pending.packetSequence}"
			startClicksRemaining > 0 && target != null -> "aiming_start_button:remaining=$startClicksRemaining"
			startClicksRemaining > 0 -> "waiting_start_click:remaining=$startClicksRemaining"
			targetWaitingForButton -> "waiting_for_real_button:${target?.let(::debugPos) ?: "none"}"
			targetPreAim && targetOpeningPreAim -> "opening_pre_aim:${target?.let(::debugPos) ?: "none"}"
			targetPreAim -> "pre_aim:${target?.let(::debugPos) ?: "none"}"
			target != null -> "solve_aim:${debugPos(target)}:index=$state"
			openingPreAimButton != null && now < openingPreAimAt -> "opening_pre_aim_delay:${debugPos(openingPreAimButton!!)}"
			clicks.isEmpty() -> "waiting_for_pattern_capture:observations=${patternCapture.observationCount}"
			state >= clicks.size -> "waiting_for_next_pattern:completed=$completedSequenceCount:length=${clicks.size}"
			now - lastClickTime < currentClickDelayMs -> "waiting_click_delay:index=$state"
			now < nextServerSafeClickAtMs -> "waiting_server_safe_spacing:index=$state"
			else -> "ready_to_solve:index=$state:length=${clicks.size}"
		}
	}

	private fun debugSnapshot(client: Minecraft, now: Long): Map<String, String> {
		val player = client.player
		val level = client.level
		val gameMode = client.gameMode
		val target = targetButton
		val pending = pendingClick
		val plan = aimController.currentPlan()
		val aimResult = latestAimResult
		val freshTargetHit = target?.let { getLookHit(client, it) }
		val targetDistanceSq = if (player != null && target != null) {
			player.distanceToSqr(Vec3.atCenterOf(target))
		} else {
			null
		}
		val actualAimError = if (plan != null && aimResult != null) {
			angularError(aimResult.rotation, plan.final)
		} else {
			null
		}
		return linkedMapOf(
			"phase" to debugPhase(now),
			"doing_ss" to doingSS.toString(),
			"manual_restart_required" to manualRestartRequired.toString(),
			"safety_start_position" to (safetyInterlock.startingPosition?.let(::debugVec) ?: "none"),
			"safety_expected_rotation" to (safetyInterlock.expectedRotation?.let(::debugRotation) ?: "none"),
			"area_check" to areaCheck().toString(),
			"player_present" to (player != null).toString(),
			"world_present" to (level != null).toString(),
			"game_mode_present" to (gameMode != null).toString(),
			"player_position" to (player?.let { debugVec(it.position()) } ?: "unavailable"),
			"player_eye_position" to (player?.let { debugVec(it.eyePosition) } ?: "unavailable"),
			"player_rotation" to (player?.let { debugRotation(Rotation(it.yRot, it.xRot)) } ?: "unavailable"),
			"player_look" to (player?.let { debugVec(it.lookAngle) } ?: "unavailable"),
			"distance_to_start_squared" to (player?.distanceToSqr(START_BUTTON)?.let(::debugDouble) ?: "unavailable"),
			"screen" to (client.screen?.javaClass?.name ?: "none"),
			"mouse_grabbed" to client.mouseHandler.isMouseGrabbed.toString(),
			"destroying_block" to (gameMode?.isDestroying?.toString() ?: "unavailable"),
			"hands_busy" to (player?.isHandsBusy?.toString() ?: "unavailable"),
			"selected_hotbar_slot" to (player?.inventory?.selectedSlot?.toString() ?: "unavailable"),
			"main_hand_item" to (player?.mainHandItem?.toString() ?: "unavailable"),
			"off_hand_item" to (player?.offhandItem?.toString() ?: "unavailable"),
			"crosshair_hit" to debugHit(client.hitResult),
			"target_button" to (target?.let(::debugPos) ?: "none"),
			"target_block_state" to (if (level != null && target != null) level.getBlockState(target).toString() else "unavailable"),
			"fresh_target_raycast" to (freshTargetHit?.let(::debugHit) ?: "none"),
			"target_distance_squared" to (targetDistanceSq?.let(::debugDouble) ?: "unavailable"),
			"target_flags" to "start=$targetIsStart pre_aim=$targetPreAim opening_pre_aim=$targetOpeningPreAim waiting_for_button=$targetWaitingForButton wait_correction_started=$targetWaitCorrectionStarted wait_correction_on_real_button=$targetWaitCorrectionOnRealButton",
			"target_aim_point" to (targetAimPoint?.let(::debugVec) ?: "none"),
			"aim_plan" to (plan?.let {
				"mode=${it.mode} start=${debugRotation(it.start)} final=${debugRotation(it.final)} distance_deg=${debugDouble(it.angularDistance)} elapsed_ms=${(now - it.startedAtMs).coerceAtLeast(0L)} duration_ms=${it.durationMs} click_ready_at_ms=${it.clickReadyAtMs} seed=${it.seed}"
			} ?: "none"),
			"aim_result" to (aimResult?.let { "rotation=${debugRotation(it.rotation)} ready_to_click=${it.readyToClick} finished=${it.finished}" } ?: "none"),
			"actual_aim_error_degrees" to (actualAimError?.let(::debugDouble) ?: "unavailable"),
			"captured_pattern" to debugPattern(clicks),
			"pattern_observations" to patternCapture.observationCount.toString(),
			"pattern_observation_buttons" to debugPattern(patternCapture.observations()),
			"solve_state" to "$state/${clicks.size}",
			"expected_solve_button" to (clicks.getOrNull(state)?.let(::debugPos) ?: "none"),
			"completed_sequences" to completedSequenceCount.toString(),
			"start_clicks_remaining" to startClicksRemaining.toString(),
			"pending_click" to (pending?.let {
				"button=${debugPos(it.button)} start=false solve_index=${it.solveIndex} packet_sequence=${it.packetSequence} age_ms=${(now - it.sentAtMs).coerceAtLeast(0L)} timeout_ms=${pendingClickTimeoutMs()}"
			} ?: "none"),
			"last_click_age_ms" to (now - lastClickTime).coerceAtLeast(0L).toString(),
			"current_click_delay_ms" to currentClickDelayMs.toString(),
			"click_delay_remaining_ms" to (currentClickDelayMs - (now - lastClickTime)).coerceAtLeast(0L).toString(),
			"server_safe_spacing_remaining_ms" to (nextServerSafeClickAtMs - now).coerceAtLeast(0L).toString(),
			"last_pattern_light_age_ms" to if (lastPatternLightAtMs > 0L) (now - lastPatternLightAtMs).coerceAtLeast(0L).toString() else "none",
			"input_phase_display_seen" to inputPhaseTracker.displayPhaseObserved.toString(),
			"input_phase_marker_age_ms" to if (inputPhaseTracker.inputPhaseObservedAtMs > 0L) (now - inputPhaseTracker.inputPhaseObservedAtMs).coerceAtLeast(0L).toString() else "none",
			"next_start_click_remaining_ms" to (nextStartClickAt - now).coerceAtLeast(0L).toString(),
			"pending_auto_start_remaining_ms" to (pendingAutoStartAtMs - now).coerceAtLeast(0L).toString(),
			"last_server_packet_age_ms" to if (lastServerPacketAtMs > 0L) (now - lastServerPacketAtMs).coerceAtLeast(0L).toString() else "unknown",
			"auto_restart_remaining_ms" to (autoRestartAt - now).coerceAtLeast(0L).toString(),
			"detect_block_state" to (level?.getBlockState(DETECT)?.toString() ?: "unavailable"),
			"smoothed_interaction_ack_ms" to smoothedInteractionAckMs.toString(),
			"estimated_server_tick_ms" to (estimatedServerTickMs?.let(::debugDouble) ?: "unknown"),
			"last_server_game_time" to (lastServerGameTime?.toString() ?: "unknown"),
			"last_server_time_packet_age_ms" to (if (lastServerTimePacketAtMs > 0L) (now - lastServerTimePacketAtMs).coerceAtLeast(0L).toString() else "unknown"),
			"thread" to Thread.currentThread().name
		)
	}

	private fun debugPattern(pattern: List<BlockPos>): String =
		if (pattern.isEmpty()) "[]" else pattern.joinToString(prefix = "[", postfix = "]", transform = ::debugPos)

	private fun debugPos(pos: BlockPos): String =
		"(${pos.x},${pos.y},${pos.z})"

	private fun debugVec(vec: Vec3): String =
		"(${debugDouble(vec.x)},${debugDouble(vec.y)},${debugDouble(vec.z)})"

	private fun debugRotation(rotation: Rotation): String =
		"(yaw=${debugDouble(rotation.yaw.toDouble())},pitch=${debugDouble(rotation.pitch.toDouble())})"

	private fun debugHit(hit: HitResult?): String =
		when (hit) {
			is BlockHitResult -> "${hit.type}:block=${debugPos(hit.blockPos)} face=${hit.direction} location=${debugVec(hit.location)}"
			null -> "none"
			else -> "${hit.type}:location=${debugVec(hit.location)}"
		}

	private fun debugDouble(value: Double): String =
		String.format(Locale.ROOT, "%.3f", value)

	private fun areaCheck(): Boolean =
		forceSkyblock.value ||
			Location.area.isArea(Island.DUNGEON) &&
			(Location.floor == Floor.F7 || Location.floor == Floor.M7) &&
			DungeonUtils.isPhase(Phase7.P3)

	private fun resetState() {
		clicks.clear()
		patternCapture.clear()
		inputPhaseTracker.clear()
		clearTarget()
		safetyInterlock.clear()
		startAimPoint = null
		startClicksRemaining = 0
		nextStartClickAt = 0L
		pendingAutoStartAtMs = 0L
		lastPatternLightAtMs = 0L
		state = 0
		doneFirst = false
		doingSS = false
		completedSequenceCount = 0
		smoothedInteractionAckMs = 0L
		nextServerSafeClickAtMs = 0L
		resetGridMotionHistory()
		clearAutoRestart()
		clearOpeningPreAim()
		donePopupUntil = 0L
	}

	private fun finishSimonSays() {
		val client = Minecraft.getInstance()
		val finishedAt = nowMs()
		debugEvent("solver_completed", "final_sequence_length=${clicks.size}", progress = true, now = finishedAt)
		debugRecorder.complete(debugSnapshot(client, finishedAt), finishedAt)
		doingSS = false
		donePopupUntil = finishedAt + DONE_POPUP_MS
		patternCapture.clear()
		clearAutoRestart()
		clearTarget()
	}

	private fun stopSimonSaysSafely(message: String) {
		saveDebugFailure(message)
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
		targetWaitingForButton = false
		targetWaitCorrectionStarted = false
		targetWaitCorrectionOnRealButton = false
		preAimButton = null
		clearOpeningPreAim()
		aimController.clear()
		latestAimResult = null
		mouseMotion.clear()
		pendingClick = null
		clickPacketIntent = null
		capturedClickPacketSequence = null
		clickPacketRejectionReason = null
	}

	private fun middleOffset(random: ThreadLocalRandom, maxOffset: Double): Double {
		if (maxOffset <= 0.0) {
			return 0.0
		}

		return (random.nextDouble(-maxOffset, maxOffset) + random.nextDouble(-maxOffset, maxOffset)) * 0.5
	}

	private fun getAimSettings(mode: AimMode): AimSettings {
		val speedMultiplier = when (mode) {
			AimMode.WAIT_CORRECTION -> getDouble(waitCorrectionAimSpeed)
			else -> 1.0
		}
		val randomness = when (mode) {
			AimMode.PRE_AIM -> PRE_AIM_RANDOMNESS
			else -> AIM_RANDOMNESS
		}
		return AimSettings(
			speed = getDouble(aimSpeed) * speedMultiplier,
			randomness = randomness,
			overshootStrength = OVERSHOOT_STRENGTH
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

	private fun openingTwoTransitionGraceMs(): Long =
		((estimatedServerTickMs ?: DEFAULT_SERVER_TICK_MS) * OPENING_TWO_TRANSITION_GRACE_SERVER_TICKS)
			.toLong()
			.coerceIn(MIN_OPENING_TWO_TRANSITION_GRACE_MS, MAX_OPENING_TWO_TRANSITION_GRACE_MS)

	private fun randomServerSafeClickSpacingMs(): Long =
		ThreadLocalRandom.current().nextLong(
			MIN_SERVER_SAFE_CLICK_SPACING_MS,
			MAX_SERVER_SAFE_CLICK_SPACING_MS + 1L
		)

	private fun pendingClickTimeoutMs(): Long {
		val adaptiveTimeout = if (smoothedInteractionAckMs <= 0L) {
			MIN_PENDING_CLICK_TIMEOUT_MS
		} else {
			smoothedInteractionAckMs * PENDING_CLICK_TIMEOUT_ACK_MULTIPLIER
		}
		return adaptiveTimeout.coerceIn(MIN_PENDING_CLICK_TIMEOUT_MS, MAX_PENDING_CLICK_TIMEOUT_MS)
	}

	private fun randomAutoRestartReactionDelayMs(): Long =
		ThreadLocalRandom.current().nextLong(MIN_AUTO_RESTART_REACTION_MS, MAX_AUTO_RESTART_REACTION_MS + 1L)

	private fun chat(message: String) {
		Minecraft.getInstance().player?.sendSystemMessage(Component.literal(message))
	}

	private companion object {
		private val START_BUTTON = Vec3(110.875, 121.5, 91.5)
		private val AUTO_SS_ACTIVATION_CENTER = Vec3(107.0, 120.0, 94.0)
		private val DETECT = BlockPos(110, 120, 93)
		private val INPUT_BUTTONS = (120..123).flatMap { y ->
			(92..95).map { z -> BlockPos(110, y, z) }
		}
		private val AUTO_SS_AIM_MOTION_PROFILE = AimMotionProfile(
			durationScale = 0.90,
			startVelocityFactor = 0.60,
			passThroughVelocityFactor = 0.50,
			stabilizeRetargetDirection = true
		)
		private const val MAX_BUTTON_DISTANCE_SQ = 36.0
		private const val AUTO_SS_ACTIVATION_RADIUS_SQ = 9.0
		private const val START_TRIGGER_BOSS_CHAT = "boss_chat"
		private const val START_TRIGGER_RESET_KEY = "reset_key"
		private const val RAYCAST_DISTANCE = 6.0
		private const val START_BUTTON_FACE_RAY_EPSILON = 1.0E-5
		private const val START_BUTTON_PHYSICAL_HALF_HEIGHT = 0.145
		private const val START_BUTTON_PHYSICAL_HALF_WIDTH = 0.205
		private const val BUTTON_HIT_EPSILON = 0.03
		private const val FINAL_SEQUENCE_LENGTH = 5
		private const val OPENING_SKIP_OBSERVATION_COUNT = 3
		private const val VIRTUAL_BUTTON_FACE_X = 0.875
		private const val PRE_AIM_AIM_OFFSET = 0.055
		private const val VIRTUAL_BUTTON_VERTICAL_OFFSET = 0.085
		private const val VIRTUAL_BUTTON_HORIZONTAL_OFFSET = 0.135
		private const val VIRTUAL_BUTTON_HORIZONTAL_SCALE = 1.45
		private const val FIRST_PATTERN_PRE_AIM_REACTION_MS = 180L
		private const val MIN_AUTO_RESTART_REACTION_MS = 180L
		private const val MAX_AUTO_RESTART_REACTION_MS = 200L
		private const val DONE_POPUP_MS = 1500L
		private const val AIM_RANDOMNESS = 0.1
		private const val PRE_AIM_RANDOMNESS = 0.03
		private const val AIM_TOLERANCE = 0.18
		private const val PRE_AIM_TOLERANCE = 0.07
		private const val ACTUAL_CLICK_READY_TOLERANCE = 0.22
		private const val ACTUAL_FINISH_TOLERANCE = 0.14
		private const val OVERSHOOT_STRENGTH = 1.0
		private const val CLICK_DELAY_MIN_MS = 8L
		private const val CLICK_DELAY_MAX_MS = 14L
		private const val MIN_CLICK_DELAY_VARIANCE_MS = 4L
		private const val DEFAULT_SERVER_TICK_MS = 50.0
		private const val OPENING_TWO_TRANSITION_GRACE_SERVER_TICKS = 30.0
		private const val MIN_OPENING_TWO_TRANSITION_GRACE_MS = 1_500L
		private const val MAX_OPENING_TWO_TRANSITION_GRACE_MS = 3_000L
		private const val MIN_ACK_SAMPLE_MS = 1L
		private const val MAX_ACK_SAMPLE_MS = 2000L
		private const val MIN_PENDING_CLICK_TIMEOUT_MS = 6000L
		private const val MAX_PENDING_CLICK_TIMEOUT_MS = 15000L
		private const val PENDING_CLICK_TIMEOUT_ACK_MULTIPLIER = 6L
		private const val STALL_REPORT_AFTER_MS = 12_000L
		private const val PENDING_WATCHDOG_GRACE_MS = 1_000L
		private const val MIN_SERVER_SAFE_CLICK_SPACING_MS = 100L
		private const val MAX_SERVER_SAFE_CLICK_SPACING_MS = 150L
		private const val MIN_SERVER_TIME_SAMPLE_TICKS = 1L
		private const val MAX_SERVER_TIME_SAMPLE_TICKS = 200L
		private const val MIN_SERVER_TICK_SAMPLE_MS = 20.0
		private const val MAX_SERVER_TICK_SAMPLE_MS = 500.0
		private const val SERVER_TICK_SMOOTHING_OLD_WEIGHT = 0.7
		private const val START_CLICK_INTERVAL_MS = 125L
		private const val AUTO_START_DELAY_RANDOM_EXTRA_MS = 40L
		private val CONTROL_CODE_PATTERN = Regex("(?i)§[0-9A-FK-OR]")

		private fun nowMs(): Long =
			System.nanoTime() / 1_000_000L

		private fun startButtonPos(): BlockPos =
			BlockPos.containing(START_BUTTON.x, START_BUTTON.y, START_BUTTON.z)
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
		val solveIndex: Int,
		val packetSequence: Int,
		val sentAtMs: Long
	)

	private data class ClickIntent(
		val button: BlockPos,
		val startButton: Boolean,
		val solveIndex: Int
	)

	private sealed interface ClickSendResult {
		data class NotReady(val reason: String) : ClickSendResult
		data class GuardRejected(val reason: String) : ClickSendResult
		data object LocallySuppressed : ClickSendResult
		data class Sent(val packetSequence: Int) : ClickSendResult
	}
}
