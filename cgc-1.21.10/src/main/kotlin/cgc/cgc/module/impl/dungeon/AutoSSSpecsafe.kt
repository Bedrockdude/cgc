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
import net.minecraft.util.Mth
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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
	private val lookTime = NumberSetting("Look Time (MS)", 10.0, 500.0, 200.0, 10.0)
	private val aimTolerance = NumberSetting("Aim Tolerance", 0.0, 0.45, 0.08, 0.01)
	private val aimJitter = NumberSetting("Aim Jitter", 0.0, 0.25, 0.06, 0.01, " deg")
	private val aimOvershoot = NumberSetting("Aim Overshoot", 0.0, 2.0, 0.35, 0.05, " blocks")
	private val overshootCorrectionDelay = NumberSetting("Overshoot Correction Delay (S)", 0.0, 2.0, 0.20, 0.05, "s")
	private val overshootCorrectionTime = NumberSetting("Overshoot Correction Time", 0.05, 2.0, 0.35, 0.05, "s")
	private val donePopup = BooleanSetting("Done Popup", true)
	private val fillColor = ColourSetting("Button Fill Color", Colour(85, 255, 85))
	private val outlineColor = ColourSetting("Button Outline Color", Colour(0, 170, 0))

	private val clicks = arrayListOf<BlockPos>()
	private val allButtons = arrayListOf<Vec3>()

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
	private var targetStartedAt = 0L
	private var patternSettledAt = 0L
	private var targetStartYaw = 0.0f
	private var targetStartPitch = 0.0f
	private var targetArcYaw = 0.0f
	private var targetArcPitch = 0.0f
	private var targetOvershootYaw = 0.0f
	private var targetOvershootPitch = 0.0f
	private var targetAimPointAtMs = 0L
	private var targetOvershootAtMs = 0L
	private var targetOvershootCorrectionDelayMs = 0L
	private var targetOvershootCorrectionDurationMs = 0L
	private var targetJitterYaw = 0.0f
	private var targetJitterPitch = 0.0f
	private var targetJitterSpeed = 0.0
	private var targetJitterPhaseA = 0.0
	private var targetJitterPhaseB = 0.0
	private var clickedButton: Vec3? = null
	private var lastClickedButton: BlockPos? = null
	private var donePopupUntil = 0L

	init {
		registerProperty(
			resetKey,
			autoStart,
			forceSkyblock,
			autoStartDelay,
			lookTime,
			aimTolerance,
			aimJitter,
			aimOvershoot,
			overshootCorrectionDelay,
			overshootCorrectionTime,
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
			applyTargetRotation(client)
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

		val progress = getTargetProgress()
		if (targetPreAim) {
			if (progress >= 1.0) {
				clearTarget()
			}
			return
		}

		if (System.currentTimeMillis() - lastClickTime < currentClickDelayMs) {
			return
		}

		if (progress >= 1.0 || shouldClickWhileMoving(progress)) {
			applyTargetRotation(client)
			clickTarget(client)
		}
	}

	private fun applyTargetRotation(client: Minecraft) {
		val aimPoint = targetAimPoint ?: return
		val player = client.player ?: return
		val wanted = rotationTo(player, aimPoint)
		val progress = getTargetProgress()
		val amount = flowEase(progress)
		val arcAmount = getArcAmount()
		val yawDelta = wrapAngleTo180(wanted.yaw - targetStartYaw)
		val pitchDelta = wrapAngleTo180(wanted.pitch - targetStartPitch)
		val overshot = applyOvershoot(yawDelta, pitchDelta)
		val jitter = getAimJitter(amount)
		val nextYaw = targetStartYaw +
			overshot.yaw +
			targetArcYaw * arcAmount +
			jitter.yaw
		val nextPitch = targetStartPitch +
			overshot.pitch +
			targetArcPitch * arcAmount +
			jitter.pitch
		player.yRot = nextYaw
		player.xRot = Mth.clamp(nextPitch, -90.0f, 90.0f)
		player.yHeadRot = nextYaw
	}

	private fun getTargetProgress(): Double {
		val lookDuration = getLookDuration()
		return if (lookDuration <= 0L) {
			1.0
		} else {
			Mth.clamp((System.currentTimeMillis() - targetStartedAt).toDouble() / lookDuration, 0.0, 1.0)
		}
	}

	private fun beginLookClick(button: BlockPos, startButton: Boolean) {
		beginLookClick(button, startButton, false)
	}

	private fun beginLookClick(button: BlockPos, startButton: Boolean, flowingRetarget: Boolean) {
		val client = Minecraft.getInstance()
		if (client.player == null || client.level == null) {
			return
		}

		targetButton = button
		targetIsStart = startButton
		targetPreAim = false
		targetWaitingForButton = false
		targetAimPoint = if (startButton && startAimPoint != null) startAimPoint else getAimPoint(client.level!!, button)
		if (startButton && isCrosshairOn(client, button)) {
			targetStartedAt = System.currentTimeMillis() - 100L
			clickTarget(client)
			return
		}

		restartSmoothLook(flowingRetarget)
	}

	private fun restartSmoothLook(flowingRetarget: Boolean) {
		val client = Minecraft.getInstance()
		val startedAt = System.currentTimeMillis() - if (flowingRetarget) FLOW_RETARGET_HEAD_START_MS else 0L
		val player = client.player
		if (player == null) {
			targetStartedAt = startedAt
			targetStartYaw = 0.0f
			targetStartPitch = 0.0f
			targetArcYaw = 0.0f
			targetArcPitch = 0.0f
			targetOvershootYaw = 0.0f
			targetOvershootPitch = 0.0f
			targetAimPointAtMs = 0L
			targetOvershootAtMs = 0L
			targetOvershootCorrectionDelayMs = 0L
			targetOvershootCorrectionDurationMs = 0L
			targetJitterYaw = 0.0f
			targetJitterPitch = 0.0f
			targetJitterSpeed = 0.0
			targetJitterPhaseA = 0.0
			targetJitterPhaseB = 0.0
			return
		}

		targetStartedAt = startedAt
		targetStartYaw = player.yRot
		targetStartPitch = player.xRot
		calculateAimMotionOffsets()
	}

	private fun calculateAimMotionOffsets() {
		val aimPoint = targetAimPoint
		val player = Minecraft.getInstance().player
		if (aimPoint == null || player == null) {
			clearAimMotionOffsets()
			return
		}

		val wanted = rotationTo(player, aimPoint)
		val yawDelta = wrapAngleTo180(wanted.yaw - targetStartYaw)
		val pitchDelta = wrapAngleTo180(wanted.pitch - targetStartPitch)
		val distance = sqrt((yawDelta * yawDelta + pitchDelta * pitchDelta).toDouble())
		val random = ThreadLocalRandom.current()
		val jitter = getDouble(aimJitter)
		targetJitterYaw = (jitter * random.nextDouble(0.75, 1.2)).toFloat()
		targetJitterPitch = (jitter * random.nextDouble(0.45, 0.95)).toFloat()
		targetJitterSpeed = random.nextDouble(MIN_JITTER_SPEED, MAX_JITTER_SPEED)
		targetJitterPhaseA = random.nextDouble(0.0, Math.PI * 2.0)
		targetJitterPhaseB = random.nextDouble(0.0, Math.PI * 2.0)

		if (distance < MIN_AIM_MOTION_DISTANCE_DEGREES) {
			targetArcYaw = 0.0f
			targetArcPitch = 0.0f
			targetOvershootYaw = 0.0f
			targetOvershootPitch = 0.0f
			targetAimPointAtMs = 0L
			targetOvershootAtMs = 0L
			targetOvershootCorrectionDelayMs = 0L
			targetOvershootCorrectionDurationMs = 0L
			return
		}

		val arc = min(0.22, max(0.04, distance * 0.012)) * if (random.nextBoolean()) 1.0 else -1.0
		targetArcYaw = (-pitchDelta / distance * arc).toFloat()
		targetArcPitch = (yawDelta / distance * arc).toFloat()

		val overshootScale = getOvershootScale(yawDelta, pitchDelta, distance)
		val overshoot = getOvershootDegrees(player, aimPoint, overshootScale, random)
		if (overshoot <= 0.0) {
			targetOvershootYaw = 0.0f
			targetOvershootPitch = 0.0f
			targetAimPointAtMs = 0L
			targetOvershootAtMs = 0L
			targetOvershootCorrectionDelayMs = 0L
			targetOvershootCorrectionDurationMs = 0L
			return
		}

		val baseLookDuration = getBaseLookDuration()
		targetOvershootYaw = (yawDelta / distance * overshoot).toFloat()
		targetOvershootPitch = (pitchDelta / distance * overshoot).toFloat()
		targetAimPointAtMs = max(
			MIN_TARGET_REACH_MS,
			(baseLookDuration * random.nextDouble(MIN_TARGET_REACH_AT, MAX_TARGET_REACH_AT)).toLong()
		)
		targetOvershootAtMs = max(
			targetAimPointAtMs + MIN_OVERSHOOT_MOVE_MS,
			targetAimPointAtMs + (baseLookDuration * random.nextDouble(MIN_OVERSHOOT_MOVE_SCALE, MAX_OVERSHOOT_MOVE_SCALE)).toLong()
		)
		targetOvershootCorrectionDelayMs = getMilliseconds(overshootCorrectionDelay)
		targetOvershootCorrectionDurationMs = max(1L, getMilliseconds(overshootCorrectionTime))
	}

	private fun clearAimMotionOffsets() {
		targetArcYaw = 0.0f
		targetArcPitch = 0.0f
		targetOvershootYaw = 0.0f
		targetOvershootPitch = 0.0f
		targetAimPointAtMs = 0L
		targetOvershootAtMs = 0L
		targetOvershootCorrectionDelayMs = 0L
		targetOvershootCorrectionDurationMs = 0L
		targetJitterYaw = 0.0f
		targetJitterPitch = 0.0f
		targetJitterSpeed = 0.0
		targetJitterPhaseA = 0.0
		targetJitterPhaseB = 0.0
	}

	private fun applyOvershoot(yawDelta: Float, pitchDelta: Float): Rotation {
		if (!hasActiveOvershoot() || targetAimPointAtMs <= 0L) {
			val amount = flowEase(getTargetProgress()).toFloat()
			return Rotation(yawDelta * amount, pitchDelta * amount)
		}

		val elapsed = max(0L, System.currentTimeMillis() - targetStartedAt)
		val overshootYaw = yawDelta + targetOvershootYaw
		val overshootPitch = pitchDelta + targetOvershootPitch
		return if (elapsed < targetAimPointAtMs) {
			val targetProgress = flowEase(elapsed.toDouble() / targetAimPointAtMs).toFloat()
			Rotation(
				yawDelta * targetProgress,
				pitchDelta * targetProgress
			)
		} else if (elapsed < targetOvershootAtMs) {
			val overshootDuration = max(1L, targetOvershootAtMs - targetAimPointAtMs)
			val overshootProgress = easeInOut((elapsed - targetAimPointAtMs).toDouble() / overshootDuration).toFloat()
			Rotation(
				yawDelta + targetOvershootYaw * overshootProgress,
				pitchDelta + targetOvershootPitch * overshootProgress
			)
		} else {
			val correctionStartsAt = targetOvershootAtMs + targetOvershootCorrectionDelayMs
			if (elapsed < correctionStartsAt) {
				return Rotation(overshootYaw, overshootPitch)
			}

			val correctionDuration = max(1L, targetOvershootCorrectionDurationMs)
			val correctionProgress = easeInOut(((elapsed - correctionStartsAt).toDouble() / correctionDuration).coerceIn(0.0, 1.0)).toFloat()
			Rotation(
				yawDelta + targetOvershootYaw * (1.0f - correctionProgress),
				pitchDelta + targetOvershootPitch * (1.0f - correctionProgress)
			)
		}
	}

	private fun getArcAmount(): Float {
		if (!hasActiveOvershoot() || targetAimPointAtMs <= 0L) {
			return sin(Math.PI * flowEase(getTargetProgress())).toFloat()
		}

		val elapsed = max(0L, System.currentTimeMillis() - targetStartedAt)
		val initialProgress = (elapsed.toDouble() / max(1L, targetAimPointAtMs)).coerceIn(0.0, 1.0)
		return sin(Math.PI * flowEase(initialProgress)).toFloat()
	}

	private fun hasActiveOvershoot(): Boolean =
		(targetOvershootYaw != 0.0f || targetOvershootPitch != 0.0f) && targetOvershootAtMs > 0L

	private fun getOvershootScale(yawDelta: Float, pitchDelta: Float, distance: Double): Double {
		val majorAxis = max(abs(yawDelta.toDouble()), abs(pitchDelta.toDouble()))
		if (majorAxis <= 0.0) {
			return 0.0
		}

		val minorAxis = min(abs(yawDelta.toDouble()), abs(pitchDelta.toDouble()))
		val straightness = minorAxis / majorAxis
		val distanceFloor = if (straightness < STRAIGHT_AIM_RATIO) {
			STRAIGHT_OVERSHOOT_DISTANCE_DEGREES
		} else {
			MIN_OVERSHOOT_DISTANCE_DEGREES
		}
		if (distance <= distanceFloor) {
			return 0.0
		}

		val linearScale = ((distance - distanceFloor) / (FULL_OVERSHOOT_DISTANCE_DEGREES - distanceFloor)).coerceIn(0.0, 1.0)
		return linearScale * linearScale
	}

	private fun getOvershootDegrees(player: LocalPlayer, aimPoint: Vec3, overshootScale: Double, random: ThreadLocalRandom): Double {
		val maxOvershootBlocks = getDouble(aimOvershoot)
		if (maxOvershootBlocks <= 0.0 || overshootScale <= 0.0) {
			return 0.0
		}

		val overshootBlocks = (maxOvershootBlocks * overshootScale * random.nextDouble(0.85, 1.05))
			.coerceAtMost(maxOvershootBlocks)
		if (overshootBlocks <= 0.0) {
			return 0.0
		}

		val eyeDistance = max(MIN_OVERSHOOT_EYE_DISTANCE, player.eyePosition.distanceTo(aimPoint))
		return Math.toDegrees(kotlin.math.atan2(overshootBlocks, eyeDistance))
	}

	private fun getAimJitter(amount: Double): Rotation {
		if (targetJitterYaw == 0.0f && targetJitterPitch == 0.0f) {
			return Rotation(0.0f, 0.0f)
		}

		val settle = if (amount < JITTER_SETTLE_START) {
			1.0
		} else {
			1.0 - ((amount - JITTER_SETTLE_START) / (1.0 - JITTER_SETTLE_START))
		}.coerceIn(0.0, 1.0)
		val envelope = sin(Math.PI * amount).coerceAtLeast(0.0) * settle
		if (envelope <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val elapsedSeconds = (System.currentTimeMillis() - targetStartedAt).toDouble() / 1000.0
		val yawNoise = sin(elapsedSeconds * targetJitterSpeed + targetJitterPhaseA) +
			sin(elapsedSeconds * targetJitterSpeed * 1.71 + targetJitterPhaseB) * 0.35
		val pitchNoise = sin(elapsedSeconds * targetJitterSpeed * 1.23 + targetJitterPhaseA + 1.4) +
			sin(elapsedSeconds * targetJitterSpeed * 1.93 + targetJitterPhaseB + 0.6) * 0.3
		return Rotation(
			(yawNoise * targetJitterYaw * envelope).toFloat(),
			(pitchNoise * targetJitterPitch * envelope).toFloat()
		)
	}

	private fun clickTarget(client: Minecraft): Boolean {
		val button = targetButton ?: return false
		val hit = getLookHit(client, button)
		if (hit == null || hit.type == HitResult.Type.MISS || hit.blockPos != button) {
			return false
		}

		val clicked = button
		val clickedStart = targetIsStart
		lastClickTime = System.currentTimeMillis()
		currentClickDelayMs = randomClickDelayMs()
		clickedButton = Vec3.atLowerCornerOf(clicked)
		lastClickedButton = clicked
		val player = client.player ?: return false
		client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hit)
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

		beginLookClick(first, false)
		targetPreAim = true
		preAimButton = first
		return true
	}

	private fun getLookHit(client: Minecraft, expected: BlockPos): BlockHitResult? {
		val crosshairHit = client.hitResult as? BlockHitResult
		if (crosshairHit != null && crosshairHit.type != HitResult.Type.MISS && crosshairHit.blockPos == expected) {
			return crosshairHit
		}

		val player = client.player ?: return null
		val level = client.level ?: return null
		val eye = player.eyePosition
		val look = player.lookAngle
		val end = eye.add(look.scale(6.0))
		val hit = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
		return if (hit.blockPos == expected) hit else null
	}

	private fun isCrosshairOn(client: Minecraft, expected: BlockPos): Boolean {
		val hit = client.hitResult as? BlockHitResult ?: return false
		return hit.type != HitResult.Type.MISS && hit.blockPos == expected
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
		targetStartedAt = 0L
		targetStartYaw = 0.0f
		targetStartPitch = 0.0f
		clearAimMotionOffsets()
	}

	private fun easeInOut(t: Double): Double =
		t * t * t * (t * (t * 6.0 - 15.0) + 10.0)

	private fun flowEase(t: Double): Double =
		FLOW_LINEAR_BLEND * t + (1.0 - FLOW_LINEAR_BLEND) * easeInOut(t)

	private fun shouldClickWhileMoving(progress: Double): Boolean =
		progress >= CHAIN_CLICK_PROGRESS &&
			!hasActiveOvershoot() &&
			!targetIsStart &&
			!targetWaitingForButton &&
			state + 1 < clicks.size

	private fun middleOffset(random: ThreadLocalRandom, maxOffset: Double): Double {
		if (maxOffset <= 0.0) {
			return 0.0
		}

		return (random.nextDouble(-maxOffset, maxOffset) + random.nextDouble(-maxOffset, maxOffset)) * 0.5
	}

	private fun getLong(setting: NumberSetting): Long =
		setting.value.toLong()

	private fun getBaseLookDuration(): Long {
		val delay = if (targetIsStart) 0L else currentClickDelayMs
		return getLong(lookTime) + delay
	}

	private fun getLookDuration(): Long {
		val baseDuration = getBaseLookDuration()
		val overshootDuration = targetOvershootAtMs + targetOvershootCorrectionDelayMs + targetOvershootCorrectionDurationMs
		return max(baseDuration, overshootDuration)
	}

	private fun getDouble(setting: NumberSetting): Double =
		setting.value.toDouble()

	private fun getMilliseconds(setting: NumberSetting): Long =
		(getDouble(setting) * 1000.0).toLong()

	private fun randomAutoStartDelayMs(): Long {
		val baseDelay = getLong(autoStartDelay)
		return ThreadLocalRandom.current().nextLong(baseDelay, baseDelay + AUTO_START_DELAY_RANDOM_EXTRA_MS + 1L)
	}

	private fun rotationTo(player: LocalPlayer, target: Vec3): Rotation {
		val eye = player.eyePosition
		val dx = target.x - eye.x
		val dy = target.y - eye.y
		val dz = target.z - eye.z
		val horizontal = sqrt(dx * dx + dz * dz)
		val yaw = Math.toDegrees(kotlin.math.atan2(dz, dx)).toFloat() - 90.0f
		val pitch = -Math.toDegrees(kotlin.math.atan2(dy, horizontal)).toFloat()
		return Rotation(yaw, pitch)
	}

	private fun wrapAngleTo180(angle: Float): Float =
		Mth.wrapDegrees(angle)

	private fun chat(message: String) {
		Minecraft.getInstance().player?.displayClientMessage(Component.literal(message), false)
	}

	private data class Rotation(val yaw: Float, val pitch: Float)

	private companion object {
		private val START_BUTTON = Vec3(110.875, 121.5, 91.5)
		private val DETECT = BlockPos(110, 123, 92)
		private const val MAX_BUTTON_DISTANCE_SQ = 36.0
		private const val FINAL_SEQUENCE_LENGTH = 5
		private const val DONE_POPUP_MS = 1500L
		private const val MIN_CLICK_DELAY_MS = 10L
		private const val MAX_CLICK_DELAY_MS = 20L
		private const val MIN_START_CLICK_DELAY_MS = 105L
		private const val MAX_START_CLICK_DELAY_MS = 130L
		private const val AUTO_START_DELAY_RANDOM_EXTRA_MS = 40L
		private const val FLOW_RETARGET_HEAD_START_MS = 8L
		private const val CHAIN_CLICK_PROGRESS = 0.86
		private const val FLOW_LINEAR_BLEND = 0.08
		private const val MIN_AIM_MOTION_DISTANCE_DEGREES = 0.5
		private const val MIN_OVERSHOOT_DISTANCE_DEGREES = 2.25
		private const val STRAIGHT_OVERSHOOT_DISTANCE_DEGREES = 5.5
		private const val FULL_OVERSHOOT_DISTANCE_DEGREES = 10.5
		private const val STRAIGHT_AIM_RATIO = 0.18
		private const val MIN_OVERSHOOT_EYE_DISTANCE = 0.5
		private const val MIN_TARGET_REACH_AT = 0.48
		private const val MAX_TARGET_REACH_AT = 0.58
		private const val MIN_TARGET_REACH_MS = 70L
		private const val MIN_OVERSHOOT_MOVE_MS = 45L
		private const val MIN_OVERSHOOT_MOVE_SCALE = 0.18
		private const val MAX_OVERSHOOT_MOVE_SCALE = 0.26
		private const val JITTER_SETTLE_START = 0.9
		private const val MIN_JITTER_SPEED = 18.0
		private const val MAX_JITTER_SPEED = 32.0

		private fun startButtonPos(): BlockPos =
			BlockPos.containing(START_BUTTON.x, START_BUTTON.y, START_BUTTON.z)

		private fun randomClickDelayMs(): Long =
			ThreadLocalRandom.current().nextLong(MIN_CLICK_DELAY_MS, MAX_CLICK_DELAY_MS + 1L)

		private fun randomStartClickDelayMs(): Long =
			ThreadLocalRandom.current().nextLong(MIN_START_CLICK_DELAY_MS, MAX_START_CLICK_DELAY_MS + 1L)
	}
}
