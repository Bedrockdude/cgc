package cgc.cgc.module.impl.dungeon

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class AimMode {
	START_BUTTON,
	NORMAL_BUTTON,
	CHAINED_RETARGET,
	PRE_AIM
}

enum class AimVelocityProfile {
	TINY,
	SMALL,
	MEDIUM,
	LARGE
}

data class Rotation(val yaw: Float, val pitch: Float)

data class AimSettings(
	val speed: Double,
	val randomness: Double,
	val overshootStrength: Double,
	val microCorrection: Double
)

data class AimPlan(
	val mode: AimMode,
	val velocityProfile: AimVelocityProfile,
	val start: Rotation,
	val final: Rotation,
	val angularDistance: Double,
	val startedAtMs: Long,
	val durationMs: Long,
	val approachDurationMs: Long,
	val overshootHoldDurationMs: Long,
	val correctionDurationMs: Long,
	val settleDurationMs: Long,
	val overshootAmount: Double,
	val overshootDirection: Rotation,
	val curveBias: Double,
	val curveSkew: Double,
	val curvePeak: Double,
	val curveAmount: Double,
	val curveDirection: Rotation,
	val flickAggression: Double,
	val decelerationStrength: Double,
	val travelShakeYawAmplitude: Double,
	val travelShakePitchAmplitude: Double,
	val settleShakeAmplitude: Double,
	val holdShakeAmplitude: Double,
	val shakeFrequencyA: Double,
	val shakeFrequencyB: Double,
	val shakePhaseA: Double,
	val shakePhaseB: Double,
	val seed: Long,
	val clickReadyAtMs: Long
)

data class AimUpdateResult(
	val rotation: Rotation,
	val readyToClick: Boolean,
	val finished: Boolean
)

class SimonSaysAimController {
	private var plan: AimPlan? = null

	fun hasTarget(): Boolean =
		plan != null

	fun currentPlan(): AimPlan? =
		plan

	fun start(
		player: LocalPlayer,
		target: Vec3,
		settings: AimSettings,
		mode: AimMode,
		nowMs: Long = System.currentTimeMillis()
	) {
		val start = Rotation(player.yRot, player.xRot.coerceIn(MIN_PITCH, MAX_PITCH))
		val wanted = rotationTo(player, target)
		val yawDelta = wrapAngleTo180(wanted.yaw - start.yaw)
		val pitchDelta = (wanted.pitch.coerceIn(MIN_PITCH, MAX_PITCH) - start.pitch).coerceIn(-180.0f, 180.0f)
		val final = Rotation(start.yaw + yawDelta, (start.pitch + pitchDelta).coerceIn(MIN_PITCH, MAX_PITCH))
		val angularDistance = sqrt((yawDelta * yawDelta + pitchDelta * pitchDelta).toDouble())
		val seed = ThreadLocalRandom.current().nextLong()
		val random = Random(seed)
		val planSettings = settings.coerced()
		val band = AimDistanceBand.fromDistance(angularDistance)
		val velocityProfile = band.velocityProfile()
		val durationMs = planDurationMs(angularDistance, band, mode, planSettings, random)
		val eyeDistance = max(MIN_TARGET_EYE_DISTANCE, player.eyePosition.distanceTo(target))
		val overshootAmount = overshootAmount(angularDistance, eyeDistance, band, planSettings, random)
		val motionDirection = directionOrZero(yawDelta.toDouble(), pitchDelta.toDouble(), angularDistance)
		val perpendicular = perpendicular(motionDirection, random.sign())
		val overshootDirection = overshootDirection(motionDirection, perpendicular, overshootAmount, band, random)
		val curveAmount = curveAmount(angularDistance, band, planSettings, random)
		val curveDirection = if (curveAmount <= 0.0) Rotation(0.0f, 0.0f) else perpendicular
		val settleDurationMs = settleDurationMs(band, planSettings, random)
		val overshootHoldDurationMs = overshootHoldDurationMs(overshootAmount, band, random)
		val correctionDurationMs = correctionDurationMs(durationMs, overshootAmount, band, random)
		val approachDurationMs = max(MIN_APPROACH_MS, durationMs - overshootHoldDurationMs - correctionDurationMs - settleDurationMs)
		val adjustedDurationMs = approachDurationMs + overshootHoldDurationMs + correctionDurationMs + settleDurationMs
		val clickReadyAtMs = clickReadyAtMs(adjustedDurationMs, correctionDurationMs, settleDurationMs, overshootAmount)
		val shake = shakeAmplitudes(angularDistance, band, planSettings, random)

		plan = AimPlan(
			mode = mode,
			velocityProfile = velocityProfile,
			start = start,
			final = final,
			angularDistance = angularDistance,
			startedAtMs = nowMs,
			durationMs = adjustedDurationMs,
			approachDurationMs = approachDurationMs,
			overshootHoldDurationMs = overshootHoldDurationMs,
			correctionDurationMs = correctionDurationMs,
			settleDurationMs = settleDurationMs,
			overshootAmount = overshootAmount,
			overshootDirection = overshootDirection,
			curveBias = random.between(-1.0, 1.0),
			curveSkew = random.between(-0.45, 0.45),
			curvePeak = curvePeak(band, random),
			curveAmount = curveAmount,
			curveDirection = curveDirection,
			flickAggression = flickAggression(band, random),
			decelerationStrength = decelerationStrength(band, random),
			travelShakeYawAmplitude = shake.travelYaw,
			travelShakePitchAmplitude = shake.travelPitch,
			settleShakeAmplitude = shake.settle,
			holdShakeAmplitude = shake.hold,
			shakeFrequencyA = random.between(MIN_SHAKE_FREQUENCY_A, MAX_SHAKE_FREQUENCY_A),
			shakeFrequencyB = random.between(MIN_SHAKE_FREQUENCY_B, MAX_SHAKE_FREQUENCY_B),
			shakePhaseA = random.between(0.0, PI * 2.0),
			shakePhaseB = random.between(0.0, PI * 2.0),
			seed = seed,
			clickReadyAtMs = clickReadyAtMs
		)
	}

	fun update(nowMs: Long = System.currentTimeMillis()): AimUpdateResult {
		val activePlan = plan ?: return AimUpdateResult(Rotation(0.0f, 0.0f), readyToClick = false, finished = true)
		val elapsedMs = max(0L, nowMs - activePlan.startedAtMs)
		val baseRotation = baseRotationAt(activePlan, elapsedMs)
		val rotation = clampPitch(baseRotation + shakeAt(activePlan, elapsedMs))
		val finished = elapsedMs >= activePlan.durationMs
		val readyToClick = elapsedMs >= activePlan.clickReadyAtMs &&
			angularError(baseRotation, activePlan.final) <= clickReadyTolerance(activePlan)
		return AimUpdateResult(rotation, readyToClick, finished)
	}

	fun clear() {
		plan = null
	}

	private fun baseRotationAt(plan: AimPlan, elapsedMs: Long): Rotation {
		val finalDelta = Rotation(
			wrapAngleTo180(plan.final.yaw - plan.start.yaw),
			(plan.final.pitch - plan.start.pitch).coerceIn(-180.0f, 180.0f)
		)
		val overshootDelta = Rotation(
			finalDelta.yaw + plan.overshootDirection.yaw * plan.overshootAmount.toFloat(),
			finalDelta.pitch + plan.overshootDirection.pitch * plan.overshootAmount.toFloat()
		)
		val overshootRotation = Rotation(
			plan.start.yaw + overshootDelta.yaw,
			(plan.start.pitch + overshootDelta.pitch).coerceIn(MIN_PITCH, MAX_PITCH)
		)

		// Phase 1: travel toward the planned overshoot/side-pass point. The target
		// of this phase is already beyond the button, so overshoot is part of the
		// path rather than an after-the-fact correction.
		if (elapsedMs <= plan.approachDurationMs) {
			val rawProgress = (elapsedMs.toDouble() / max(1L, plan.approachDurationMs)).coerceIn(0.0, 1.0)
			val progress = movementProgress(plan, rawProgress)
			val curve = curveOffset(plan, rawProgress)
			return clampPitch(
				Rotation(
					plan.start.yaw + (overshootDelta.yaw * progress).toFloat() + curve.yaw,
					plan.start.pitch + (overshootDelta.pitch * progress).toFloat() + curve.pitch
				)
			)
		}

		// Phase 2: a tiny reaction/hold at the overshoot point. This makes larger
		// flicks read as momentum without freezing because shake is added later.
		val correctionStartsAt = plan.approachDurationMs + plan.overshootHoldDurationMs
		if (elapsedMs <= correctionStartsAt) {
			return overshootRotation
		}

		// Phase 3: correction back to the clickable point. A small bounded curve is
		// kept during correction so the return path is not a perfect mechanical line.
		val settleStartsAt = correctionStartsAt + plan.correctionDurationMs
		if (elapsedMs <= settleStartsAt) {
			if (plan.correctionDurationMs <= 0L) {
				return plan.final
			}

			val correctionElapsed = elapsedMs - correctionStartsAt
			val rawProgress = (correctionElapsed.toDouble() / plan.correctionDurationMs).coerceIn(0.0, 1.0)
			val progress = correctionProgress(plan, rawProgress)
			val correctionCurve = correctionCurveOffset(plan, rawProgress)
			return clampPitch(
				Rotation(
					lerp(overshootRotation.yaw, plan.final.yaw, progress) + correctionCurve.yaw,
					lerp(overshootRotation.pitch, plan.final.pitch, progress) + correctionCurve.pitch
				)
			)
		}

		// Phase 4: settle onto the final rotation. The base drift fades out, while
		// very small hold shake continues after the plan is finished.
		if (elapsedMs < plan.durationMs) {
			val settleElapsed = elapsedMs - settleStartsAt
			val progress = (settleElapsed.toDouble() / max(1L, plan.settleDurationMs)).coerceIn(0.0, 1.0)
			val settleEnvelope = 1.0 - easeOutCubic(progress)
			return clampPitch(
				Rotation(
					plan.final.yaw + plan.overshootDirection.yaw * plan.overshootAmount.toFloat() * SETTLE_DRIFT_SCALE * settleEnvelope.toFloat(),
					plan.final.pitch + plan.overshootDirection.pitch * plan.overshootAmount.toFloat() * SETTLE_DRIFT_SCALE * settleEnvelope.toFloat()
				)
			)
		}

		return plan.final
	}

	private fun movementProgress(plan: AimPlan, rawProgress: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		return when (plan.velocityProfile) {
			AimVelocityProfile.TINY -> {
				lerp(progress, smootherStep(progress), 0.75)
			}
			AimVelocityProfile.SMALL -> {
				val mildSmooth = lerp(progress, smootherStep(progress), 0.34)
				lerp(progress, mildSmooth, 0.78)
			}
			AimVelocityProfile.MEDIUM -> {
				val controlled = lerp(progress, smootherStep(progress), 0.38)
				val flick = easeOutPower(progress, 1.45 + plan.flickAggression * 0.35)
				val shaped = lerp(controlled, flick, 0.26 + plan.decelerationStrength * 0.08)
				limitInitialLead(shaped, progress, 1.72 + plan.flickAggression * 0.18)
			}
			AimVelocityProfile.LARGE -> {
				val controlled = lerp(progress, smootherStep(progress), 0.24)
				val flick = easeOutPower(progress, 1.78 + plan.flickAggression * 0.45)
				val decel = easeOutPower(progress, 1.34 + plan.decelerationStrength * 0.34)
				val shaped = lerp(lerp(controlled, decel, 0.34), flick, 0.32 + plan.flickAggression * 0.12)
				limitInitialLead(shaped, progress, 1.96 + plan.flickAggression * 0.26)
			}
		}.coerceIn(0.0, 1.0)
	}

	private fun correctionProgress(plan: AimPlan, rawProgress: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		return when (plan.velocityProfile) {
			AimVelocityProfile.TINY,
			AimVelocityProfile.SMALL -> smootherStep(progress)
			AimVelocityProfile.MEDIUM -> lerp(smootherStep(progress), easeOutPower(progress, 1.55), 0.22)
			AimVelocityProfile.LARGE -> lerp(smootherStep(progress), easeOutPower(progress, 1.38), 0.32)
		}.coerceIn(0.0, 1.0)
	}

	private fun curveOffset(plan: AimPlan, rawProgress: Double): Rotation {
		if (plan.curveAmount <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val envelope = asymmetricCurveEnvelope(plan, rawProgress)
		val amount = plan.curveAmount * envelope
		return Rotation(
			(plan.curveDirection.yaw * amount).toFloat(),
			(plan.curveDirection.pitch * amount).toFloat()
		)
	}

	private fun correctionCurveOffset(plan: AimPlan, rawProgress: Double): Rotation {
		if (plan.curveAmount <= 0.0 || plan.correctionDurationMs <= 0L) {
			return Rotation(0.0f, 0.0f)
		}

		val progress = rawProgress.coerceIn(0.0, 1.0)
		val envelope = sin(PI * progress).coerceAtLeast(0.0) * (1.0 - progress * 0.35)
		val amount = plan.curveAmount * CORRECTION_CURVE_SCALE * envelope
		return Rotation(
			(-plan.curveDirection.yaw * amount).toFloat(),
			(-plan.curveDirection.pitch * amount).toFloat()
		)
	}

	private fun asymmetricCurveEnvelope(plan: AimPlan, rawProgress: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		if (progress <= 0.0 || progress >= 1.0) {
			return 0.0
		}

		val peak = plan.curvePeak.coerceIn(0.22, 0.78)
		val skew = plan.curveSkew
		val base = if (progress <= peak) {
			val local = (progress / peak).coerceIn(0.0, 1.0)
			smootherStep(local.pow((1.0 + skew * 0.28).coerceIn(0.72, 1.28)))
		} else {
			val local = ((1.0 - progress) / (1.0 - peak)).coerceIn(0.0, 1.0)
			smootherStep(local.pow((1.0 - skew * 0.28).coerceIn(0.72, 1.28)))
		}
		val unevenness = 1.0 + plan.curveBias * 0.13 * sin(PI * progress * 2.0 + plan.shakePhaseA)
		return (base * unevenness).coerceIn(0.0, 1.18)
	}

	private fun shakeAt(plan: AimPlan, elapsedMs: Long): Rotation {
		val travel = travelShakeAt(plan, elapsedMs)
		val hold = holdShakeAt(plan, elapsedMs)
		return Rotation(travel.yaw + hold.yaw, travel.pitch + hold.pitch)
	}

	private fun travelShakeAt(plan: AimPlan, elapsedMs: Long): Rotation {
		if (plan.travelShakeYawAmplitude <= 0.0 && plan.travelShakePitchAmplitude <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val fadeDuration = max(1L, plan.clickReadyAtMs)
		val progress = (elapsedMs.toDouble() / fadeDuration).coerceIn(0.0, 1.0)
		if (progress >= 1.0) {
			return Rotation(0.0f, 0.0f)
		}

		val fadeOut = if (progress < TRAVEL_SHAKE_FADE_START) {
			1.0
		} else {
			1.0 - smootherStep((progress - TRAVEL_SHAKE_FADE_START) / (1.0 - TRAVEL_SHAKE_FADE_START))
		}
		val envelope = sin(PI * progress).coerceAtLeast(0.0) * fadeOut.coerceIn(0.0, 1.0)
		if (envelope <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val seconds = elapsedMs.toDouble() / 1000.0
		val yaw = (
			sin(seconds * plan.shakeFrequencyA + plan.shakePhaseA) +
				sin(seconds * plan.shakeFrequencyB * 0.73 + plan.shakePhaseB) * 0.36 +
				sin(seconds * (plan.shakeFrequencyA + plan.shakeFrequencyB) * 0.42 + plan.shakePhaseA + plan.shakePhaseB) * 0.14
			) * plan.travelShakeYawAmplitude * envelope
		val pitch = (
			sin(seconds * plan.shakeFrequencyA * 0.81 + plan.shakePhaseB + 1.1) +
				sin(seconds * plan.shakeFrequencyB * 0.67 + plan.shakePhaseA + 0.4) * 0.32
			) * plan.travelShakePitchAmplitude * envelope
		return Rotation(yaw.toFloat(), pitch.toFloat())
	}

	private fun holdShakeAt(plan: AimPlan, elapsedMs: Long): Rotation {
		if (plan.settleShakeAmplitude <= 0.0 && plan.holdShakeAmplitude <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val correctionStartsAt = plan.approachDurationMs + plan.overshootHoldDurationMs
		val holdStartsAt = correctionStartsAt + (plan.correctionDurationMs * HOLD_SHAKE_CORRECTION_START).toLong()
		if (elapsedMs <= holdStartsAt) {
			return Rotation(0.0f, 0.0f)
		}

		val settleStartsAt = correctionStartsAt + plan.correctionDurationMs
		val holdBlend = smootherStep(
			((elapsedMs - holdStartsAt).toDouble() / max(1L, plan.durationMs - holdStartsAt)).coerceIn(0.0, 1.0)
		)
		val settleBlend = smootherStep(
			((elapsedMs - settleStartsAt).toDouble() / max(1L, plan.settleDurationMs)).coerceIn(0.0, 1.0)
		)
		val amplitude = lerp(plan.settleShakeAmplitude, plan.holdShakeAmplitude, settleBlend) * holdBlend
		if (amplitude <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val seconds = elapsedMs.toDouble() / 1000.0
		val yaw = (
			sin(seconds * plan.shakeFrequencyA * 0.34 + plan.shakePhaseA) +
				sin(seconds * plan.shakeFrequencyB * 0.27 + plan.shakePhaseB) * 0.42
			) * amplitude
		val pitch = (
			sin(seconds * plan.shakeFrequencyA * 0.29 + plan.shakePhaseB + 0.8) +
				sin(seconds * plan.shakeFrequencyB * 0.23 + plan.shakePhaseA + 1.6) * 0.34
			) * amplitude * HOLD_SHAKE_PITCH_SCALE
		return Rotation(yaw.toFloat(), pitch.toFloat())
	}

	private fun planDurationMs(
		distance: Double,
		band: AimDistanceBand,
		mode: AimMode,
		settings: AimSettings,
		random: Random
	): Long {
		val base = when (band) {
			AimDistanceBand.TINY -> lerp(34.0, 58.0, (distance / TINY_DISTANCE).coerceIn(0.0, 1.0))
			AimDistanceBand.SMALL -> lerp(76.0, 132.0, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(138.0, 220.0, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(224.0, 340.0, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val modeScale = when (mode) {
			AimMode.START_BUTTON -> 0.86
			AimMode.NORMAL_BUTTON -> 1.0
			AimMode.CHAINED_RETARGET -> 0.9
			AimMode.PRE_AIM -> 1.03
		}
		val randomScale = 1.0 + random.between(-0.10, 0.12) * settings.randomness
		return (base * modeScale * randomScale / settings.speed)
			.toLong()
			.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
	}

	private fun overshootAmount(
		distance: Double,
		eyeDistance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): Double {
		if (settings.overshootStrength <= 0.0 || band == AimDistanceBand.TINY || band == AimDistanceBand.SMALL) {
			return 0.0
		}

		val boardScale = smootherStep(((distance - OVERSHOOT_START_DISTANCE) / OVERSHOOT_FULL_DISTANCE).coerceIn(0.0, 1.0))
		val overshootBlocks = when (band) {
			AimDistanceBand.TINY -> 0.0
			AimDistanceBand.SMALL -> 0.0
			AimDistanceBand.MEDIUM -> lerp(MEDIUM_OVERSHOOT_BLOCKS_MIN, MEDIUM_OVERSHOOT_BLOCKS_MAX, boardScale)
			AimDistanceBand.LARGE -> lerp(LARGE_OVERSHOOT_BLOCKS_MIN, MAX_OVERSHOOT_BLOCKS, boardScale)
		}
		val randomScale = random.between(0.86, 1.12)
		val blockOffset = (overshootBlocks * settings.overshootStrength * randomScale).coerceIn(0.0, MAX_OVERSHOOT_BLOCKS)
		val amount = Math.toDegrees(kotlin.math.atan2(blockOffset, eyeDistance))
		return amount.coerceIn(0.0, distance * MAX_OVERSHOOT_DISTANCE_FRACTION)
	}

	private fun overshootDirection(
		motionDirection: Rotation,
		perpendicular: Rotation,
		overshootAmount: Double,
		band: AimDistanceBand,
		random: Random
	): Rotation {
		if (overshootAmount <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val sideWeight = when (band) {
			AimDistanceBand.TINY -> 0.0
			AimDistanceBand.SMALL -> 0.0
			AimDistanceBand.MEDIUM -> random.between(0.18, 0.34)
			AimDistanceBand.LARGE -> random.between(0.28, 0.48)
		}
		val forwardWeight = when (band) {
			AimDistanceBand.TINY -> 0.0
			AimDistanceBand.SMALL -> 0.0
			AimDistanceBand.MEDIUM -> random.between(0.86, 0.96)
			AimDistanceBand.LARGE -> random.between(0.78, 0.92)
		}
		return normalize(
			motionDirection.yaw * forwardWeight.toFloat() + perpendicular.yaw * sideWeight.toFloat(),
			motionDirection.pitch * forwardWeight.toFloat() + perpendicular.pitch * sideWeight.toFloat()
		)
	}

	private fun curveAmount(
		distance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): Double {
		if (band == AimDistanceBand.TINY) {
			return 0.0
		}

		val base = when (band) {
			AimDistanceBand.TINY -> 0.0
			AimDistanceBand.SMALL -> lerp(0.006, 0.026, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(0.04, 0.13, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(0.14, 0.30, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val randomScale = random.between(0.72, 1.24)
		val randomnessScale = 0.58 + settings.randomness * 0.42
		return (base * randomScale * randomnessScale).coerceAtMost(distance * MAX_CURVE_DISTANCE_FRACTION)
	}

	private fun curvePeak(band: AimDistanceBand, random: Random): Double =
		when (band) {
			AimDistanceBand.TINY -> 0.5
			AimDistanceBand.SMALL -> random.between(0.43, 0.58)
			AimDistanceBand.MEDIUM -> random.between(0.38, 0.57)
			AimDistanceBand.LARGE -> random.between(0.32, 0.54)
		}

	private fun flickAggression(band: AimDistanceBand, random: Random): Double =
		when (band) {
			AimDistanceBand.TINY -> random.between(0.0, 0.08)
			AimDistanceBand.SMALL -> random.between(0.06, 0.16)
			AimDistanceBand.MEDIUM -> random.between(0.22, 0.42)
			AimDistanceBand.LARGE -> random.between(0.42, 0.68)
		}

	private fun decelerationStrength(band: AimDistanceBand, random: Random): Double =
		when (band) {
			AimDistanceBand.TINY -> random.between(0.0, 0.1)
			AimDistanceBand.SMALL -> random.between(0.08, 0.2)
			AimDistanceBand.MEDIUM -> random.between(0.28, 0.54)
			AimDistanceBand.LARGE -> random.between(0.48, 0.78)
		}

	private fun shakeAmplitudes(
		distance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): ShakeAmplitudes {
		val micro = settings.microCorrection
		val randomnessScale = 0.55 + settings.randomness * 0.45
		val microScale = 0.35 + micro * 0.65
		val travelBase = when (band) {
			AimDistanceBand.TINY -> lerp(0.001, 0.006, (distance / TINY_DISTANCE).coerceIn(0.0, 1.0))
			AimDistanceBand.SMALL -> lerp(0.008, 0.022, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(0.034, 0.078, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(0.082, 0.15, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val travelYaw = (travelBase * randomnessScale * microScale * random.between(0.82, 1.18))
			.coerceAtMost(MAX_TRAVEL_SHAKE_DEGREES)
		val travelPitch = (travelYaw * random.between(0.55, 0.78)).coerceAtMost(MAX_TRAVEL_SHAKE_DEGREES * 0.75)
		val holdBase = when (band) {
			AimDistanceBand.TINY -> 0.0025
			AimDistanceBand.SMALL -> 0.0045
			AimDistanceBand.MEDIUM -> 0.008
			AimDistanceBand.LARGE -> 0.0115
		}
		val hold = (holdBase * (0.55 + micro * 0.45) * random.between(0.75, 1.18))
			.coerceAtMost(MAX_HOLD_SHAKE_DEGREES)
		val settle = (hold * when (band) {
			AimDistanceBand.TINY -> 1.35
			AimDistanceBand.SMALL -> 1.55
			AimDistanceBand.MEDIUM -> 2.05
			AimDistanceBand.LARGE -> 2.35
		}).coerceAtMost(MAX_SETTLE_SHAKE_DEGREES)
		return ShakeAmplitudes(travelYaw, travelPitch, settle, hold)
	}

	private fun overshootHoldDurationMs(overshootAmount: Double, band: AimDistanceBand, random: Random): Long {
		if (overshootAmount <= 0.0) {
			return 0L
		}

		val hold = when (band) {
			AimDistanceBand.TINY -> 0.0
			AimDistanceBand.SMALL -> 0.0
			AimDistanceBand.MEDIUM -> random.between(6.0, 12.0)
			AimDistanceBand.LARGE -> random.between(10.0, 18.0)
		}
		return hold.toLong()
	}

	private fun correctionDurationMs(durationMs: Long, overshootAmount: Double, band: AimDistanceBand, random: Random): Long {
		if (overshootAmount <= 0.0) {
			return 0L
		}

		val ratio = when (band) {
			AimDistanceBand.TINY -> 0.0
			AimDistanceBand.SMALL -> 0.0
			AimDistanceBand.MEDIUM -> random.between(0.30, 0.38)
			AimDistanceBand.LARGE -> random.between(0.34, 0.44)
		}
		return (durationMs * ratio).toLong().coerceAtLeast(MIN_CORRECTION_MS)
	}

	private fun settleDurationMs(band: AimDistanceBand, settings: AimSettings, random: Random): Long {
		val base = when (band) {
			AimDistanceBand.TINY -> random.between(8.0, 14.0)
			AimDistanceBand.SMALL -> random.between(14.0, 24.0)
			AimDistanceBand.MEDIUM -> random.between(22.0, 38.0)
			AimDistanceBand.LARGE -> random.between(30.0, 48.0)
		}
		return (base * (0.84 + settings.microCorrection * 0.32)).toLong().coerceAtLeast(MIN_SETTLE_MS)
	}

	private fun clickReadyAtMs(
		durationMs: Long,
		correctionDurationMs: Long,
		settleDurationMs: Long,
		overshootAmount: Double
	): Long {
		if (overshootAmount <= 0.0 && correctionDurationMs <= 0L) {
			return max(0L, durationMs - min(10L, settleDurationMs / 2L))
		}

		return max(0L, durationMs - min(12L, settleDurationMs / 2L))
	}

	private fun clickReadyTolerance(plan: AimPlan): Double =
		(0.12 + plan.angularDistance * 0.018).coerceIn(0.12, 0.42)

	private fun angularError(rotation: Rotation, target: Rotation): Double {
		val yaw = wrapAngleTo180(rotation.yaw - target.yaw)
		val pitch = rotation.pitch - target.pitch
		return sqrt((yaw * yaw + pitch * pitch).toDouble())
	}

	private fun rotationTo(player: LocalPlayer, target: Vec3): Rotation {
		val eye = player.eyePosition
		val dx = target.x - eye.x
		val dy = target.y - eye.y
		val dz = target.z - eye.z
		val horizontal = sqrt(dx * dx + dz * dz)
		val yaw = Math.toDegrees(kotlin.math.atan2(dz, dx)).toFloat() - 90.0f
		val pitch = -Math.toDegrees(kotlin.math.atan2(dy, horizontal)).toFloat()
		return Rotation(yaw, pitch.coerceIn(MIN_PITCH, MAX_PITCH))
	}

	private fun AimSettings.coerced(): AimSettings =
		AimSettings(
			speed = speed.coerceIn(MIN_AIM_SPEED, MAX_AIM_SPEED),
			randomness = randomness.coerceIn(0.0, 1.0),
			overshootStrength = overshootStrength.coerceIn(0.0, MAX_OVERSHOOT_STRENGTH),
			microCorrection = microCorrection.coerceIn(0.0, 1.0)
		)

	private fun directionOrZero(yaw: Double, pitch: Double, distance: Double): Rotation {
		if (distance <= 0.0001) {
			return Rotation(0.0f, 0.0f)
		}

		return Rotation((yaw / distance).toFloat(), (pitch / distance).toFloat())
	}

	private fun perpendicular(direction: Rotation, sign: Int): Rotation =
		Rotation(-direction.pitch * sign, direction.yaw * sign)

	private fun normalize(yaw: Float, pitch: Float): Rotation {
		val length = sqrt((yaw * yaw + pitch * pitch).toDouble())
		if (length <= 0.0001) {
			return Rotation(0.0f, 0.0f)
		}

		return Rotation((yaw / length).toFloat(), (pitch / length).toFloat())
	}

	private fun clampPitch(rotation: Rotation): Rotation =
		Rotation(rotation.yaw, rotation.pitch.coerceIn(MIN_PITCH, MAX_PITCH))

	private fun wrapAngleTo180(angle: Float): Float =
		Mth.wrapDegrees(angle)

	private fun lerp(start: Float, end: Float, amount: Double): Float =
		(start + (end - start) * amount).toFloat()

	private fun lerp(start: Double, end: Double, amount: Double): Double =
		start + (end - start) * amount

	private fun easeOutCubic(t: Double): Double {
		val inverse = 1.0 - t.coerceIn(0.0, 1.0)
		return 1.0 - inverse * inverse * inverse
	}

	private fun easeOutPower(t: Double, power: Double): Double {
		val progress = t.coerceIn(0.0, 1.0)
		return 1.0 - (1.0 - progress).pow(power.coerceAtLeast(1.0))
	}

	private fun limitInitialLead(shapedProgress: Double, rawProgress: Double, maxLead: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		if (progress >= 0.48) {
			return shapedProgress
		}

		return min(shapedProgress, progress * maxLead + INITIAL_FLICK_PROGRESS_ALLOWANCE)
	}

	private fun smootherStep(t: Double): Double {
		val value = t.coerceIn(0.0, 1.0)
		return value * value * value * (value * (value * 6.0 - 15.0) + 10.0)
	}

	private operator fun Rotation.plus(other: Rotation): Rotation =
		Rotation(yaw + other.yaw, pitch + other.pitch)

	private fun Random.between(min: Double, max: Double): Double =
		min + (max - min) * nextDouble()

	private fun Random.sign(): Int =
		if (nextBoolean()) 1 else -1

	private data class ShakeAmplitudes(
		val travelYaw: Double,
		val travelPitch: Double,
		val settle: Double,
		val hold: Double
	)

	private enum class AimDistanceBand {
		TINY,
		SMALL,
		MEDIUM,
		LARGE;

		fun velocityProfile(): AimVelocityProfile =
			when (this) {
				TINY -> AimVelocityProfile.TINY
				SMALL -> AimVelocityProfile.SMALL
				MEDIUM -> AimVelocityProfile.MEDIUM
				LARGE -> AimVelocityProfile.LARGE
			}

		companion object {
			fun fromDistance(distance: Double): AimDistanceBand =
				when {
					distance < TINY_DISTANCE -> TINY
					distance < SMALL_DISTANCE -> SMALL
					distance < MEDIUM_DISTANCE -> MEDIUM
					else -> LARGE
				}
		}
	}

	private companion object {
		private const val MIN_PITCH = -90.0f
		private const val MAX_PITCH = 90.0f
		private const val MIN_AIM_SPEED = 0.35
		private const val MAX_AIM_SPEED = 2.25
		private const val MAX_OVERSHOOT_STRENGTH = 1.5
		private const val TINY_DISTANCE = 1.2
		private const val SMALL_DISTANCE = 16.0
		private const val MEDIUM_DISTANCE = 32.0
		private const val LARGE_DISTANCE_RANGE = 24.0
		private const val OVERSHOOT_START_DISTANCE = 16.0
		private const val OVERSHOOT_FULL_DISTANCE = 26.0
		private const val MEDIUM_OVERSHOOT_BLOCKS_MIN = 0.07
		private const val MEDIUM_OVERSHOOT_BLOCKS_MAX = 0.20
		private const val LARGE_OVERSHOOT_BLOCKS_MIN = 0.24
		private const val MAX_OVERSHOOT_BLOCKS = 0.50
		private const val MIN_TARGET_EYE_DISTANCE = 0.5
		private const val MAX_OVERSHOOT_DISTANCE_FRACTION = 0.34
		private const val MAX_CURVE_DISTANCE_FRACTION = 0.08
		private const val MIN_DURATION_MS = 24L
		private const val MAX_DURATION_MS = 360L
		private const val MIN_APPROACH_MS = 22L
		private const val MIN_CORRECTION_MS = 34L
		private const val MIN_SETTLE_MS = 6L
		private const val SETTLE_DRIFT_SCALE = 0.055f
		private const val CORRECTION_CURVE_SCALE = 0.2
		private const val MAX_TRAVEL_SHAKE_DEGREES = 0.16
		private const val MAX_SETTLE_SHAKE_DEGREES = 0.032
		private const val MAX_HOLD_SHAKE_DEGREES = 0.016
		private const val HOLD_SHAKE_PITCH_SCALE = 0.72
		private const val HOLD_SHAKE_CORRECTION_START = 0.62
		private const val TRAVEL_SHAKE_FADE_START = 0.68
		private const val INITIAL_FLICK_PROGRESS_ALLOWANCE = 0.018
		private const val MIN_SHAKE_FREQUENCY_A = 8.5
		private const val MAX_SHAKE_FREQUENCY_A = 16.0
		private const val MIN_SHAKE_FREQUENCY_B = 5.0
		private const val MAX_SHAKE_FREQUENCY_B = 11.5
	}
}
