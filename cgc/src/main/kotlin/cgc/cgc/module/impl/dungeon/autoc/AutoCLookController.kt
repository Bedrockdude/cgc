package cgc.cgc.module.impl.dungeon.autoc

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AutoCLookController(
	private val speedMultiplier: () -> Double = { 1.0 }
) {
	private var plan: LookPlan? = null
	private var queuedPlan: QueuedLookPlan? = null
	private var movingPlan: MovingLookPlan? = null

	fun start(player: LocalPlayer, yaw: Float, pitch: Float, nowMs: Long = nowMs()) {
		start(player, yaw, pitch, nowMs, NORMAL_PROFILE)
	}

	fun startFast(player: LocalPlayer, yaw: Float, pitch: Float, nowMs: Long = nowMs()) {
		start(player, yaw, pitch, nowMs, FAST_PROFILE)
	}

	fun startEtherwarp(player: LocalPlayer, yaw: Float, pitch: Float, targetDistance: Double, nowMs: Long = nowMs()) {
		val startYaw = player.yRot
		val startPitch = player.xRot.coerceIn(MIN_PITCH, MAX_PITCH)
		val yawDelta = Mth.wrapDegrees(yaw - startYaw)
		val pitchDelta = pitch.coerceIn(MIN_PITCH, MAX_PITCH) - startPitch
		val distance = sqrt((yawDelta * yawDelta + pitchDelta * pitchDelta).toDouble())
		val correctionScale = etherwarpCorrectionScale(targetDistance)
		if (distance <= ETHERWARP_MICRO_MIN_DISTANCE || correctionScale <= 0.0) {
			start(player, yaw, pitch, nowMs, ETHERWARP_PROFILE)
			return
		}

		val random = ThreadLocalRandom.current()
		val correctionSize = (random.nextDouble(ETHERWARP_MICRO_MIN_DEGREES, ETHERWARP_MICRO_MAX_DEGREES) * correctionScale)
			.coerceAtMost(distance * ETHERWARP_MICRO_DISTANCE_FRACTION)
		val yawShare = if (abs(yawDelta) >= abs(pitchDelta)) correctionSize * if (yawDelta >= 0.0f) 1.0 else -1.0 else 0.0
		val pitchShare = if (yawShare == 0.0) correctionSize * if (pitchDelta >= 0.0f) 1.0 else -1.0 else correctionSize * ETHERWARP_MICRO_PITCH_SHARE * if (pitchDelta >= 0.0f) 1.0 else -1.0
		queuedPlan = QueuedLookPlan(yaw, pitch.coerceIn(MIN_PITCH, MAX_PITCH), ETHERWARP_CORRECTION_PROFILE)
		start(player, (yaw - yawShare).toFloat(), (pitch - pitchShare).toFloat(), nowMs, ETHERWARP_PROFILE, preserveQueue = true)
	}

	fun startWarp(player: LocalPlayer, yaw: Float, pitch: Float, nowMs: Long = nowMs()) {
		start(player, yaw, pitch, nowMs, NORMAL_PROFILE, forceDuration = true)
	}

	fun startMovingEtherwarp(player: LocalPlayer, yaw: Float, pitch: Float, nowMs: Long = nowMs()) {
		movingPlan = MovingLookPlan(yaw, pitch.coerceIn(MIN_PITCH, MAX_PITCH))
		queuedPlan = null
		start(player, yaw, pitch, nowMs, ETHERWARP_PROFILE, preserveMoving = true)
	}

	fun retargetMovingEtherwarp(yaw: Float, pitch: Float) {
		val active = movingPlan ?: return
		active.yaw = yaw
		active.pitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
	}

	private fun start(
		player: LocalPlayer,
		yaw: Float,
		pitch: Float,
		nowMs: Long,
		profile: LookProfile,
		preserveQueue: Boolean = false,
		preserveMoving: Boolean = false,
		forceDuration: Boolean = false
	) {
		if (!preserveMoving) movingPlan = null
		if (!preserveQueue) queuedPlan = null
		val start = LookRotation(player.yRot, player.xRot.coerceIn(MIN_PITCH, MAX_PITCH))
		val target = LookRotation(yaw, pitch.coerceIn(MIN_PITCH, MAX_PITCH))
		val yawDelta = Mth.wrapDegrees(target.yaw - start.yaw)
		val pitchDelta = (target.pitch - start.pitch).coerceIn(-180.0f, 180.0f)
		val distance = sqrt((yawDelta * yawDelta + pitchDelta * pitchDelta).toDouble())
		if (distance <= profile.directApplyDistance && (!forceDuration || distance <= ZERO_ROTATION_EPSILON)) {
			setRotation(player, LookRotation(start.yaw + yawDelta, start.pitch + pitchDelta))
			plan = null
			queuedPlan = null
			if (!preserveMoving) movingPlan = null
			return
		}

		val random = ThreadLocalRandom.current()
		val curve = curveFor(yawDelta, pitchDelta, distance, random)
		plan = LookPlan(
			start = start,
			final = LookRotation(start.yaw + yawDelta, (start.pitch + pitchDelta).coerceIn(MIN_PITCH, MAX_PITCH)),
			yawDelta = yawDelta,
			pitchDelta = pitchDelta,
			distance = distance,
			startedAtMs = nowMs,
			durationMs = durationFor(distance, random, profile),
			curveYaw = curve.yaw,
			curvePitch = curve.pitch,
			profile = profile
		)
	}

	fun update(player: LocalPlayer, nowMs: Long = nowMs()) {
		val moving = movingPlan
		if (plan == null && moving != null) {
			start(player, moving.yaw, moving.pitch, nowMs, ETHERWARP_PROFILE, preserveMoving = true)
			return
		}
		val active = plan ?: return
		if (moving != null) {
			active.final = LookRotation(moving.yaw, moving.pitch)
			active.yawDelta = Mth.wrapDegrees(moving.yaw - active.start.yaw)
			active.pitchDelta = (moving.pitch - active.start.pitch).coerceIn(-180.0f, 180.0f)
			active.distance = sqrt((active.yawDelta * active.yawDelta + active.pitchDelta * active.pitchDelta).toDouble())
		}
		val elapsed = (nowMs - active.startedAtMs).coerceAtLeast(0L)
		if (elapsed >= active.durationMs) {
			setRotation(player, active.final)
			plan = null
			val next = if (moving == null) queuedPlan else null
			queuedPlan = null
			if (next != null) {
				start(player, next.yaw, next.pitch, nowMs, next.profile)
			}
			return
		}

		val rawProgress = (elapsed.toDouble() / max(1L, active.durationMs)).coerceIn(0.0, 1.0)
		val progress = humanProgress(rawProgress, active.distance, active.profile)
		val curveEnvelope = curveEnvelope(rawProgress)
		setRotation(
			player,
			LookRotation(
				active.start.yaw + (active.yawDelta * progress).toFloat() + (active.curveYaw * curveEnvelope).toFloat(),
				active.start.pitch + (active.pitchDelta * progress).toFloat() + (active.curvePitch * curveEnvelope).toFloat()
			)
		)
	}

	fun hasPlan(): Boolean =
		plan != null || movingPlan != null

	fun clear() {
		plan = null
		queuedPlan = null
		movingPlan = null
	}

	private fun setRotation(player: LocalPlayer, rotation: LookRotation) {
		player.yRot = rotation.yaw
		player.xRot = rotation.pitch.coerceIn(MIN_PITCH, MAX_PITCH)
		player.yHeadRot = rotation.yaw
	}

	private fun durationFor(distance: Double, random: ThreadLocalRandom, profile: LookProfile): Long {
		val distanceFactor = (distance / LARGE_DISTANCE).coerceIn(0.0, 1.0)
		val base = profile.minDurationMs + distance * profile.msPerDegree + distanceFactor * profile.largeTurnExtraMs
		val variance = random.nextDouble(-profile.durationVarianceMs, profile.durationVarianceMs)
		val speed = speedMultiplier().takeIf { it.isFinite() }?.coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER) ?: 1.0
		return ((base + variance) / speed).toLong().coerceIn(
			(profile.minDurationMs / speed).toLong().coerceAtLeast(1L),
			(profile.maxDurationMs / speed).toLong().coerceAtLeast(1L)
		)
	}

	private fun humanProgress(rawProgress: Double, distance: Double, profile: LookProfile): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		val distanceFactor = ((distance - SMALL_DISTANCE) / (LARGE_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0)
		val smooth = smootherStep(progress)
		val fast = easeOutPower(progress, profile.easeOutPowerBase + distanceFactor * profile.easeOutPowerScale)
		val fastBlend = (profile.fastBlendMin + distanceFactor * (profile.fastBlendMax - profile.fastBlendMin))
			.coerceIn(profile.fastBlendMin, profile.fastBlendMax)
		val shaped = lerp(smooth, fast, fastBlend)
		val maxEarlyLead = progress * (profile.earlyLeadBase + distanceFactor * profile.earlyLeadScale) + INITIAL_LEAD_ALLOWANCE
		return if (progress < EARLY_LEAD_LIMIT_UNTIL) {
			min(shaped, maxEarlyLead)
		} else {
			shaped
		}.coerceIn(0.0, 1.0)
	}

	private fun curveFor(
		yawDelta: Float,
		pitchDelta: Float,
		distance: Double,
		random: ThreadLocalRandom
	): LookCurve {
		if (distance <= SMALL_DISTANCE) {
			return LookCurve(0.0, 0.0)
		}

		val sign = if (random.nextBoolean()) 1.0 else -1.0
		val amount = (distance * random.nextDouble(MIN_CURVE_FRACTION, MAX_CURVE_FRACTION))
			.coerceAtMost(MAX_CURVE_DEGREES)
		return LookCurve(
			yaw = (-pitchDelta / distance) * amount * sign,
			pitch = (yawDelta / distance) * amount * sign * PITCH_CURVE_SCALE
		)
	}

	private fun curveEnvelope(progress: Double): Double {
		val t = progress.coerceIn(0.0, 1.0)
		return sin(PI * t).coerceAtLeast(0.0) * (1.0 - t * CURVE_EXIT_FADE)
	}

	private fun smootherStep(t: Double): Double {
		val value = t.coerceIn(0.0, 1.0)
		return value * value * value * (value * (value * 6.0 - 15.0) + 10.0)
	}

	private fun easeOutPower(t: Double, power: Double): Double {
		val value = t.coerceIn(0.0, 1.0)
		return 1.0 - (1.0 - value).pow(power.coerceAtLeast(1.0))
	}

	private fun lerp(start: Double, end: Double, amount: Double): Double =
		start + (end - start) * amount

	private fun nowMs(): Long =
		System.nanoTime() / 1_000_000L

	private data class LookRotation(val yaw: Float, val pitch: Float)

	private data class LookPlan(
		val start: LookRotation,
		var final: LookRotation,
		var yawDelta: Float,
		var pitchDelta: Float,
		var distance: Double,
		val startedAtMs: Long,
		val durationMs: Long,
		val curveYaw: Double,
		val curvePitch: Double,
		val profile: LookProfile
	)

	private data class LookCurve(val yaw: Double, val pitch: Double)
	private data class QueuedLookPlan(val yaw: Float, val pitch: Float, val profile: LookProfile)
	private data class MovingLookPlan(var yaw: Float, var pitch: Float)

	private data class LookProfile(
		val directApplyDistance: Double,
		val minDurationMs: Double,
		val maxDurationMs: Double,
		val msPerDegree: Double,
		val largeTurnExtraMs: Double,
		val durationVarianceMs: Double,
		val fastBlendMin: Double,
		val fastBlendMax: Double,
		val easeOutPowerBase: Double,
		val easeOutPowerScale: Double,
		val earlyLeadBase: Double,
		val earlyLeadScale: Double
	)

	private companion object {
		private const val MIN_PITCH = -90.0f
		private const val MAX_PITCH = 90.0f
		private const val SMALL_DISTANCE = 8.0
		private const val LARGE_DISTANCE = 80.0
		private const val MIN_DURATION_MS = 42.0
		private const val MAX_DURATION_MS = 190.0
		private const val MS_PER_DEGREE = 1.38
		private const val LARGE_TURN_EXTRA_MS = 28.0
		private const val DURATION_VARIANCE_MS = 5.0
		private const val DIRECT_APPLY_DISTANCE = 0.08
		private const val FAST_DIRECT_APPLY_DISTANCE = 0.03
		private const val FAST_MIN_DURATION_MS = 26.0
		private const val FAST_MAX_DURATION_MS = 82.0
		private const val FAST_MS_PER_DEGREE = 0.55
		private const val FAST_LARGE_TURN_EXTRA_MS = 9.0
		private const val FAST_DURATION_VARIANCE_MS = 3.0
		private const val ETHERWARP_DIRECT_APPLY_DISTANCE = 0.02
		private const val ETHERWARP_MIN_DURATION_MS = 50.0
		private const val ETHERWARP_MAX_DURATION_MS = 230.0
		private const val ETHERWARP_MS_PER_DEGREE = 1.52
		private const val ETHERWARP_LARGE_TURN_EXTRA_MS = 36.0
		private const val ETHERWARP_DURATION_VARIANCE_MS = 4.0
		private const val ETHERWARP_MICRO_MIN_DISTANCE = 0.35
		private const val ETHERWARP_MICRO_MIN_DEGREES = 0.12
		private const val ETHERWARP_MICRO_MAX_DEGREES = 0.28
		private const val ETHERWARP_MICRO_DISTANCE_FRACTION = 0.18
		private const val ETHERWARP_MICRO_PITCH_SHARE = 0.35
		private const val ZERO_ROTATION_EPSILON = 1.0E-4
		private const val MIN_SPEED_MULTIPLIER = 0.25
		private const val MAX_SPEED_MULTIPLIER = 3.0
		private const val INITIAL_LEAD_ALLOWANCE = 0.012
		private const val EARLY_LEAD_LIMIT_UNTIL = 0.44
		private const val MIN_CURVE_FRACTION = 0.004
		private const val MAX_CURVE_FRACTION = 0.012
		private const val MAX_CURVE_DEGREES = 0.55
		private const val PITCH_CURVE_SCALE = 0.72
		private const val CURVE_EXIT_FADE = 0.22
		private val NORMAL_PROFILE = LookProfile(
			directApplyDistance = DIRECT_APPLY_DISTANCE,
			minDurationMs = MIN_DURATION_MS,
			maxDurationMs = MAX_DURATION_MS,
			msPerDegree = MS_PER_DEGREE,
			largeTurnExtraMs = LARGE_TURN_EXTRA_MS,
			durationVarianceMs = DURATION_VARIANCE_MS,
			fastBlendMin = 0.18,
			fastBlendMax = 0.40,
			easeOutPowerBase = 1.18,
			easeOutPowerScale = 0.22,
			earlyLeadBase = 1.18,
			earlyLeadScale = 0.30
		)
		private val FAST_PROFILE = LookProfile(
			directApplyDistance = FAST_DIRECT_APPLY_DISTANCE,
			minDurationMs = FAST_MIN_DURATION_MS,
			maxDurationMs = FAST_MAX_DURATION_MS,
			msPerDegree = FAST_MS_PER_DEGREE,
			largeTurnExtraMs = FAST_LARGE_TURN_EXTRA_MS,
			durationVarianceMs = FAST_DURATION_VARIANCE_MS,
			fastBlendMin = 0.18,
			fastBlendMax = 0.40,
			easeOutPowerBase = 1.18,
			easeOutPowerScale = 0.22,
			earlyLeadBase = 1.18,
			earlyLeadScale = 0.30
		)
		private val ETHERWARP_PROFILE = LookProfile(
			directApplyDistance = ETHERWARP_DIRECT_APPLY_DISTANCE,
			minDurationMs = ETHERWARP_MIN_DURATION_MS,
			maxDurationMs = ETHERWARP_MAX_DURATION_MS,
			msPerDegree = ETHERWARP_MS_PER_DEGREE,
			largeTurnExtraMs = ETHERWARP_LARGE_TURN_EXTRA_MS,
			durationVarianceMs = ETHERWARP_DURATION_VARIANCE_MS,
			fastBlendMin = 0.08,
			fastBlendMax = 0.54,
			easeOutPowerBase = 1.08,
			easeOutPowerScale = 0.66,
			earlyLeadBase = 1.08,
			earlyLeadScale = 0.42
		)
		private val ETHERWARP_CORRECTION_PROFILE = LookProfile(
			directApplyDistance = 0.015,
			minDurationMs = 145.0,
			maxDurationMs = 230.0,
			msPerDegree = 120.0,
			largeTurnExtraMs = 0.0,
			durationVarianceMs = 12.0,
			fastBlendMin = 0.04,
			fastBlendMax = 0.08,
			easeOutPowerBase = 1.04,
			easeOutPowerScale = 0.0,
			earlyLeadBase = 1.04,
			earlyLeadScale = 0.0
		)
	}
}

internal fun etherwarpCorrectionScale(targetDistance: Double): Double {
	if (!targetDistance.isFinite()) return 1.0
	return ((targetDistance - ETHERWARP_CORRECTION_START_DISTANCE) /
		(ETHERWARP_CORRECTION_FULL_DISTANCE - ETHERWARP_CORRECTION_START_DISTANCE)).coerceIn(0.0, 1.0)
}

private const val ETHERWARP_CORRECTION_START_DISTANCE = 8.0
private const val ETHERWARP_CORRECTION_FULL_DISTANCE = 48.0
