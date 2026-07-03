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
	PRE_AIM,
	WAIT_CORRECTION,
	PRACTICE,
	PRACTICE_RETURN
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

data class AimMoveContext(
	val rowDelta: Int,
	val columnDelta: Int,
	val chebyshevDistance: Int,
	val euclideanDistance: Double,
	val diagonal: Boolean,
	val continuingDirection: Boolean,
	val reversingDirection: Boolean,
	val passesThroughTarget: Boolean,
	val nextChebyshevDistance: Int
)

data class AimPlan(
	val mode: AimMode,
	val velocityProfile: AimVelocityProfile,
	val moveContext: AimMoveContext?,
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
	val velocityBlendIn: Double,
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
		moveContext: AimMoveContext? = null,
		nowMs: Long = System.currentTimeMillis()
	) {
		val previousPlan = plan
		val start = Rotation(player.yRot, player.xRot.coerceIn(MIN_PITCH, MAX_PITCH))
		val wanted = rotationTo(player, target)
		val yawDelta = wrapAngleTo180(wanted.yaw - start.yaw)
		val pitchDelta = (wanted.pitch.coerceIn(MIN_PITCH, MAX_PITCH) - start.pitch).coerceIn(-180.0f, 180.0f)
		val final = Rotation(start.yaw + yawDelta, (start.pitch + pitchDelta).coerceIn(MIN_PITCH, MAX_PITCH))
		val angularDistance = sqrt((yawDelta * yawDelta + pitchDelta * pitchDelta).toDouble())
		val seed = ThreadLocalRandom.current().nextLong()
		val random = Random(seed)
		val planSettings = settings.coerced()
		val practiceReturnMode = mode == AimMode.PRACTICE_RETURN
		val practiceMode = mode == AimMode.PRACTICE || mode == AimMode.PRACTICE_RETURN
		val preAimMode = mode == AimMode.PRE_AIM
		val waitCorrectionMode = mode == AimMode.WAIT_CORRECTION
		val band = AimDistanceBand.fromDistance(angularDistance, moveContext)
		val velocityProfile = band.velocityProfile()
		val velocityBlendIn = velocityBlendInStrength(previousPlan, velocityProfile, mode, moveContext, nowMs)
		val durationMs = planDurationMs(angularDistance, band, mode, planSettings, random, moveContext)
		val eyeDistance = max(MIN_TARGET_EYE_DISTANCE, player.eyePosition.distanceTo(target))
		val overshootAmount = if (practiceMode || waitCorrectionMode || preAimMode) {
			0.0
		} else {
			overshootAmount(angularDistance, eyeDistance, band, planSettings, random, moveContext)
		}
		val motionDirection = directionOrZero(yawDelta.toDouble(), pitchDelta.toDouble(), angularDistance)
		val perpendicular = perpendicular(motionDirection, random.sign())
		val overshootDirection = overshootDirection(motionDirection, perpendicular, overshootAmount, band, random)
		val curveAmount = when {
			waitCorrectionMode -> 0.0
			preAimMode -> 0.0
			mode == AimMode.PRACTICE -> practiceCurveAmount(angularDistance, band, planSettings, random)
			practiceReturnMode -> returnCurveAmount(angularDistance, band, planSettings, random)
			else -> curveAmount(angularDistance, band, planSettings, random, moveContext)
		}
		val curveDirection = if (curveAmount <= 0.0) Rotation(0.0f, 0.0f) else perpendicular
		val settleDurationMs = if (practiceMode) 0L else settleDurationMs(band, planSettings, random, moveContext)
		val overshootHoldDurationMs = overshootHoldDurationMs(overshootAmount, band, random, moveContext)
		val baseCorrectionDurationMs = correctionDurationMs(durationMs, overshootAmount, band, random, moveContext)
		val correctionDurationMs = if (baseCorrectionDurationMs > 0L) {
			baseCorrectionDurationMs + OVERSHOOT_RECOVERY_EXTRA_MS
		} else {
			0L
		}
		val approachDurationMs = if (practiceMode) {
			durationMs
		} else {
			max(MIN_APPROACH_MS, durationMs - overshootHoldDurationMs - baseCorrectionDurationMs - settleDurationMs)
		}
		val adjustedDurationMs = if (practiceMode) {
			durationMs
		} else {
			approachDurationMs + overshootHoldDurationMs + correctionDurationMs + settleDurationMs
		}
		val clickReadyAtMs = if (practiceMode) {
			adjustedDurationMs
		} else {
			clickReadyAtMs(adjustedDurationMs, correctionDurationMs, settleDurationMs, overshootAmount, moveContext)
		}
		val shake = when {
			waitCorrectionMode -> waitCorrectionShakeAmplitudes(angularDistance, band, planSettings, random)
			preAimMode -> waitCorrectionShakeAmplitudes(angularDistance, band, planSettings, random)
			mode == AimMode.PRACTICE -> practiceShakeAmplitudes(angularDistance, band, planSettings, random)
			practiceReturnMode -> returnShakeAmplitudes(angularDistance, band, planSettings, random)
			else -> shakeAmplitudes(angularDistance, band, planSettings, random)
		}

		plan = AimPlan(
			mode = mode,
			velocityProfile = velocityProfile,
			moveContext = moveContext,
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
			velocityBlendIn = velocityBlendIn,
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
		val readyToClick = activePlan.mode != AimMode.PRACTICE &&
			activePlan.mode != AimMode.PRACTICE_RETURN &&
			elapsedMs >= activePlan.clickReadyAtMs &&
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
		val terminalCoast = holdCoastTerminalOffset(plan)
		val correctionStartRotation = clampPitch(
			Rotation(
				overshootRotation.yaw + terminalCoast.yaw,
				overshootRotation.pitch + terminalCoast.pitch
			)
		)

		// Phase 1: travel toward the planned overshoot/side-pass point. The target
		// of this phase is already beyond the button, so overshoot is part of the
		// path rather than an after-the-fact correction.
		if (elapsedMs <= plan.approachDurationMs) {
			val rawProgress = (elapsedMs.toDouble() / max(1L, plan.approachDurationMs)).coerceIn(0.0, 1.0)
			val progress = applyVelocityBlendIn(plan, rawProgress, movementProgress(plan, rawProgress))
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
			if (plan.overshootHoldDurationMs <= 0L) {
				return overshootRotation
			}

			val holdElapsed = elapsedMs - plan.approachDurationMs
			val holdProgress = (holdElapsed.toDouble() / max(1L, plan.overshootHoldDurationMs)).coerceIn(0.0, 1.0)
			val coast = holdCoastOffset(plan, holdProgress)
			return clampPitch(
				Rotation(
					overshootRotation.yaw + coast.yaw,
					overshootRotation.pitch + coast.pitch
				)
			)
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
					lerp(correctionStartRotation.yaw, plan.final.yaw, progress) + correctionCurve.yaw,
					lerp(correctionStartRotation.pitch, plan.final.pitch, progress) + correctionCurve.pitch
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
		if (plan.mode == AimMode.WAIT_CORRECTION) {
			return lerp(progress, smootherStep(progress), 0.72).coerceIn(0.0, 1.0)
		}
		val moveContext = plan.moveContext
		if (isOneBlockMove(moveContext)) {
			return if (isOneBlockChainFlow(moveContext)) {
				oneBlockChainFlowProgress(progress)
			} else {
				oneBlockVelocityCappedProgress(moveContext!!, progress)
			}
		}
		if (isLongJumpChain(moveContext)) {
			return longJumpChainProgress(plan, moveContext!!, progress)
		}
		if (plan.mode == AimMode.PRACTICE) {
			return lerp(progress, smootherStep(progress), 0.32).coerceIn(0.0, 1.0)
		}
		if (plan.mode == AimMode.PRACTICE_RETURN) {
			val controlled = lerp(progress, smootherStep(progress), 0.58)
			val softCatch = easeOutPower(progress, 1.10)
			return lerp(controlled, softCatch, 0.08).coerceIn(0.0, 1.0)
		}
		if (plan.mode == AimMode.PRE_AIM) {
			val eased = smootherStep(progress)
			val shaped = lerp(progress, eased, 0.90)
			return lerp(shaped, easeOutPower(progress, 1.08), 0.04).coerceIn(0.0, 1.0)
		}
		if (moveContext != null) {
			return when {
				moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> {
					val brake = lerp(progress, smootherStep(progress), 0.90)
					lerp(brake, easeOutPower(progress, 1.08), 0.04)
				}
				moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection -> {
					val controlled = lerp(progress, smootherStep(progress), 0.62)
					val softPass = easeOutPower(progress, 1.18 + plan.flickAggression * 0.05)
					val shaped = lerp(controlled, softPass, 0.06)
					limitInitialLead(shaped, progress, 1.18 + plan.flickAggression * 0.03)
				}
				moveContext.chebyshevDistance <= 1 -> {
					val controlled = lerp(progress, smootherStep(progress), 0.64)
					val direct = easeOutPower(progress, 1.16 + plan.flickAggression * 0.05)
					lerp(controlled, direct, 0.06)
				}
				moveContext.chebyshevDistance == 2 -> {
					if (moveContext.continuingDirection && moveContext.passesThroughTarget) {
						val controlled = lerp(progress, smootherStep(progress), 0.58)
						val flow = easeOutPower(progress, 1.10 + plan.flickAggression * 0.02)
						val shaped = lerp(controlled, flow, 0.05)
						limitInitialLead(shaped, progress, 1.14 + plan.flickAggression * 0.03)
					} else if (moveContext.continuingDirection) {
						val controlled = lerp(progress, smootherStep(progress), 0.64)
						val flow = easeOutPower(progress, 1.13 + plan.flickAggression * 0.03)
						val shaped = lerp(controlled, flow, 0.05)
						limitInitialLead(shaped, progress, 1.18 + plan.flickAggression * 0.03)
					} else {
						val controlled = lerp(progress, smootherStep(progress), 0.70)
						val quick = easeOutPower(progress, 1.20 + plan.flickAggression * 0.05)
						val shaped = lerp(controlled, quick, 0.06)
						limitInitialLead(shaped, progress, 1.20 + plan.flickAggression * 0.04)
					}
				}
				else -> {
					val flick = easeOutPower(progress, 1.62 + plan.flickAggression * 0.26)
					val controlled = lerp(progress, smootherStep(progress), 0.34)
					val decel = easeOutPower(progress, 1.30 + plan.decelerationStrength * 0.28)
					val base = lerp(controlled, decel, 0.32)
					val flickBlend = delayedBlend(progress, 0.12, 0.78, 0.24 + plan.flickAggression * 0.08)
					val shaped = lerp(base, flick, flickBlend)
					limitInitialLead(shaped, progress, 1.84 + plan.flickAggression * 0.16)
				}
			}.coerceIn(0.0, 1.0)
		}

		return when (plan.velocityProfile) {
			AimVelocityProfile.TINY -> {
				lerp(progress, smootherStep(progress), 0.75)
			}
			AimVelocityProfile.SMALL -> {
				val mildSmooth = lerp(progress, smootherStep(progress), 0.34)
				lerp(progress, mildSmooth, 0.78)
			}
			AimVelocityProfile.MEDIUM -> {
				val controlled = lerp(progress, smootherStep(progress), 0.58)
				val flick = easeOutPower(progress, 1.20 + plan.flickAggression * 0.16)
				val shaped = lerp(controlled, flick, 0.11 + plan.decelerationStrength * 0.04)
				limitInitialLead(shaped, progress, 1.32 + plan.flickAggression * 0.08)
			}
			AimVelocityProfile.LARGE -> {
				val controlled = lerp(progress, smootherStep(progress), 0.32)
				val flick = easeOutPower(progress, 1.58 + plan.flickAggression * 0.32)
				val decel = easeOutPower(progress, 1.34 + plan.decelerationStrength * 0.34)
				val base = lerp(controlled, decel, 0.38)
				val flickBlend = delayedBlend(progress, 0.10, 0.76, 0.22 + plan.flickAggression * 0.10)
				val shaped = lerp(base, flick, flickBlend)
				limitInitialLead(shaped, progress, 1.78 + plan.flickAggression * 0.18)
			}
		}.coerceIn(0.0, 1.0)
	}

	private fun correctionProgress(plan: AimPlan, rawProgress: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		val moveContext = plan.moveContext
		if (moveContext != null) {
			return when {
				moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> smootherStep(progress)
				moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection -> easeOutPower(progress, 1.26)
				moveContext.chebyshevDistance <= 1 -> lerp(smootherStep(progress), easeOutPower(progress, 1.42), 0.20)
				moveContext.chebyshevDistance == 2 && moveContext.continuingDirection && moveContext.passesThroughTarget ->
					momentumCorrectionProgress(plan, progress, 0.06, 1.10)
				moveContext.chebyshevDistance == 2 && moveContext.continuingDirection ->
					momentumCorrectionProgress(plan, progress, 0.08, 1.14)
				moveContext.chebyshevDistance == 2 -> momentumCorrectionProgress(plan, progress, 0.12, 1.22)
				else -> momentumCorrectionProgress(plan, progress, 0.18, 1.16)
			}.coerceIn(0.0, 1.0)
		}
		return when (plan.velocityProfile) {
			AimVelocityProfile.TINY,
			AimVelocityProfile.SMALL -> smootherStep(progress)
			AimVelocityProfile.MEDIUM -> lerp(smootherStep(progress), easeOutPower(progress, 1.55), 0.22)
			AimVelocityProfile.LARGE -> momentumCorrectionProgress(plan, progress, 0.10, 1.12)
		}.coerceIn(0.0, 1.0)
	}

	private fun curveOffset(plan: AimPlan, rawProgress: Double): Rotation {
		if (plan.curveAmount <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val envelope = asymmetricCurveEnvelope(plan, rawProgress)
		val amount = plan.curveAmount * CURVE_PEAK_SCALE * envelope
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

	private fun holdCoastOffset(plan: AimPlan, rawProgress: Double): Rotation {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		if (progress <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val flickScale = (0.28 + plan.flickAggression * 0.72).coerceIn(0.0, 1.0)
		val coastEnvelope = sin(progress * PI * 0.86).coerceAtLeast(0.0) * (1.0 - progress * 0.40)
		val lateralEnvelope = sin(progress * PI * 0.94).coerceAtLeast(0.0) * (1.0 - progress * 0.28)
		val carryAmount = plan.overshootAmount * HOLD_COAST_OVERSHOOT_SCALE * flickScale * coastEnvelope
		val lateralAmount = plan.curveAmount * HOLD_COAST_CURVE_SCALE * flickScale * lateralEnvelope
		val tailBlend = smootherStep(progress)
		val terminal = holdCoastTerminalOffset(plan)
		return Rotation(
			(plan.overshootDirection.yaw * carryAmount).toFloat() +
				(plan.curveDirection.yaw * lateralAmount).toFloat() +
				(terminal.yaw * tailBlend.toFloat()),
			(plan.overshootDirection.pitch * carryAmount).toFloat() +
				(plan.curveDirection.pitch * lateralAmount).toFloat() +
				(terminal.pitch * tailBlend.toFloat())
		)
	}

	private fun holdCoastTerminalOffset(plan: AimPlan): Rotation {
		if (plan.overshootAmount <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val momentumScale = when {
			plan.moveContext?.chebyshevDistance ?: 0 >= 3 -> 1.0
			plan.moveContext?.chebyshevDistance == 2 -> 0.72
			plan.velocityProfile == AimVelocityProfile.LARGE -> 0.92
			plan.velocityProfile == AimVelocityProfile.MEDIUM -> 0.46
			else -> 0.0
		}
		val aggressionScale = ((plan.flickAggression - 0.10) / 0.90).coerceIn(0.0, 1.0)
		val tailScale = momentumScale * aggressionScale
		if (tailScale <= 0.0) {
			return Rotation(0.0f, 0.0f)
		}

		val overshootTail = plan.overshootAmount * HOLD_COAST_TERMINAL_OVERSHOOT_SCALE * tailScale
		val lateralTail = plan.curveAmount * HOLD_COAST_TERMINAL_CURVE_SCALE * tailScale
		return Rotation(
			(plan.overshootDirection.yaw * overshootTail).toFloat() + (plan.curveDirection.yaw * lateralTail).toFloat(),
			(plan.overshootDirection.pitch * overshootTail).toFloat() + (plan.curveDirection.pitch * lateralTail).toFloat()
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
			smootherStep(local.pow((1.10 + skew * 0.20).coerceIn(0.92, 1.34)))
		} else {
			val local = ((1.0 - progress) / (1.0 - peak)).coerceIn(0.0, 1.0)
			smootherStep(local.pow((1.02 - skew * 0.16).coerceIn(0.86, 1.18)))
		}
		val entryDamping = lerp(CURVE_ENTRY_FLOOR, 1.0, smootherStep(progress))
		val unevenness = 1.0 + plan.curveBias * 0.13 * sin(PI * progress * 2.0 + plan.shakePhaseA)
		return (base * entryDamping * unevenness).coerceIn(0.0, 1.12)
	}

	private fun shakeAt(plan: AimPlan, elapsedMs: Long): Rotation {
		val travel = travelShakeAt(plan, elapsedMs)
		val hold = holdShakeAt(plan, elapsedMs)
		val arrival = arrivalShakeAt(plan, elapsedMs)
		return Rotation(travel.yaw + hold.yaw + arrival.yaw, travel.pitch + hold.pitch + arrival.pitch)
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
		var amplitude = lerp(plan.settleShakeAmplitude, plan.holdShakeAmplitude, settleBlend) * holdBlend
		if (elapsedMs >= plan.durationMs) {
			val stableProgress = ((elapsedMs - plan.durationMs).toDouble() / ARRIVAL_SHAKE_DURATION_MS)
				.coerceIn(0.0, 1.0)
			if (stableProgress >= 1.0) {
				return Rotation(0.0f, 0.0f)
			}
			amplitude *= 1.0 - smootherStep(stableProgress)
		}
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

	private fun arrivalShakeAt(plan: AimPlan, elapsedMs: Long): Rotation {
		if (elapsedMs < plan.durationMs) {
			return Rotation(0.0f, 0.0f)
		}

		val stableProgress = ((elapsedMs - plan.durationMs).toDouble() / ARRIVAL_SHAKE_DURATION_MS)
			.coerceIn(0.0, 1.0)
		if (stableProgress >= 1.0) {
			return Rotation(0.0f, 0.0f)
		}

		val maxAmplitude = when (plan.mode) {
			AimMode.PRACTICE_RETURN -> MAX_RETURN_ARRIVAL_SHAKE_DEGREES
			AimMode.PRACTICE -> MAX_PRACTICE_ARRIVAL_SHAKE_DEGREES
			AimMode.WAIT_CORRECTION -> MAX_WAIT_CORRECTION_ARRIVAL_SHAKE_DEGREES
			AimMode.PRE_AIM -> MAX_PRE_AIM_ARRIVAL_SHAKE_DEGREES
			else -> MAX_CLICK_ARRIVAL_SHAKE_DEGREES
		}
		val floorAmplitude = when (plan.mode) {
			AimMode.PRACTICE_RETURN -> 0.016
			AimMode.PRACTICE -> 0.012
			AimMode.WAIT_CORRECTION -> 0.004
			AimMode.PRE_AIM -> 0.010
			else -> 0.006
		}
		val travelAmplitude = max(plan.travelShakeYawAmplitude, plan.travelShakePitchAmplitude) * 0.85
		val amplitude = max(floorAmplitude, travelAmplitude)
			.coerceAtMost(maxAmplitude) *
			(1.0 - smootherStep(stableProgress))

		val seconds = elapsedMs.toDouble() / 1000.0
		val yaw = (
			sin(seconds * plan.shakeFrequencyA * 0.62 + plan.shakePhaseB) +
				sin(seconds * plan.shakeFrequencyB * 0.41 + plan.shakePhaseA) * 0.36
			) * amplitude
		val pitch = (
			sin(seconds * plan.shakeFrequencyA * 0.48 + plan.shakePhaseA + 0.7) +
				sin(seconds * plan.shakeFrequencyB * 0.37 + plan.shakePhaseB + 1.4) * 0.28
			) * amplitude * 0.65
		return Rotation(yaw.toFloat(), pitch.toFloat())
	}

	private fun planDurationMs(
		distance: Double,
		band: AimDistanceBand,
		mode: AimMode,
		settings: AimSettings,
		random: Random,
		moveContext: AimMoveContext?
	): Long {
		if (isOneBlockPassThrough(moveContext)) {
			return oneBlockPassThroughDurationMs(mode, moveContext!!, random)
		}

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
			AimMode.PRE_AIM -> 1.18
			AimMode.WAIT_CORRECTION -> 2.35
			AimMode.PRACTICE -> practiceDurationScale(band)
			AimMode.PRACTICE_RETURN -> 0.94
		}
		val contextScale = contextualDurationScale(moveContext)
		val randomScale = 1.0 + random.between(-0.10, 0.12) * settings.randomness
		val scaledDurationMs = (base * modeScale * contextScale * randomScale / settings.speed).toLong()
		return max(scaledDurationMs, contextualMinDurationMs(moveContext, mode))
			.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
	}

	private fun practiceDurationScale(band: AimDistanceBand): Double =
		when (band) {
			AimDistanceBand.TINY -> 0.9
			AimDistanceBand.SMALL -> 0.8
			AimDistanceBand.MEDIUM -> 0.68
			AimDistanceBand.LARGE -> 0.58
		}

	private fun overshootAmount(
		distance: Double,
		eyeDistance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random,
		moveContext: AimMoveContext?
	): Double {
		if (settings.overshootStrength <= 0.0) {
			return 0.0
		}
		if (moveContext != null) {
			val contextAmount = contextualOvershootAmount(eyeDistance, settings, random, moveContext)
			return when {
				moveContext.chebyshevDistance <= 1 -> contextAmount
				moveContext.chebyshevDistance == 2 -> max(contextAmount, defaultOvershootAmount(distance, eyeDistance, band, settings, random) * 0.82)
				else -> max(contextAmount, defaultOvershootAmount(distance, eyeDistance, band, settings, random))
			}
		}
		return defaultOvershootAmount(distance, eyeDistance, band, settings, random)
	}

	private fun defaultOvershootAmount(
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
		random: Random,
		moveContext: AimMoveContext?
	): Double {
		if (moveContext != null) {
			val maxFraction = when {
				moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> 0.014
				moveContext.chebyshevDistance <= 1 -> 0.026
				moveContext.chebyshevDistance == 2 -> 0.052
				else -> 0.072
			}
			val base = when {
				moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> random.between(0.0, 0.010)
				moveContext.chebyshevDistance <= 1 -> random.between(0.004, 0.024)
				moveContext.chebyshevDistance == 2 -> random.between(0.026, 0.072)
				else -> random.between(0.082, 0.18)
			}
			return (base * (0.62 + settings.randomness * 0.38)).coerceAtMost(distance * maxFraction)
		}
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

	private fun returnCurveAmount(
		distance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): Double {
		if (band == AimDistanceBand.TINY) {
			return 0.0
		}

		val base = curveAmount(distance, band, settings, random, null)
		return (base * random.between(0.24, 0.44))
			.coerceAtMost(distance * MAX_RETURN_CURVE_DISTANCE_FRACTION)
	}

	private fun practiceCurveAmount(
		distance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): Double {
		val base = when (band) {
			AimDistanceBand.TINY -> lerp(0.0, 0.010, (distance / TINY_DISTANCE).coerceIn(0.0, 1.0))
			AimDistanceBand.SMALL -> lerp(0.045, 0.110, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(0.118, 0.190, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(0.198, 0.285, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val randomScale = random.between(0.78, 1.22)
		val randomnessScale = 0.64 + settings.randomness * 0.36
		return (base * randomScale * randomnessScale)
			.coerceAtMost(distance * MAX_PRACTICE_CURVE_DISTANCE_FRACTION)
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
			AimDistanceBand.TINY -> lerp(0.002, 0.009, (distance / TINY_DISTANCE).coerceIn(0.0, 1.0))
			AimDistanceBand.SMALL -> lerp(0.012, 0.034, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(0.046, 0.102, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(0.108, 0.19, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val travelYaw = (travelBase * randomnessScale * microScale * random.between(0.82, 1.18))
			.coerceAtMost(MAX_TRAVEL_SHAKE_DEGREES)
		val travelPitch = (travelYaw * random.between(0.55, 0.78)).coerceAtMost(MAX_TRAVEL_SHAKE_DEGREES * 0.75)
		val holdBase = when (band) {
			AimDistanceBand.TINY -> 0.0035
			AimDistanceBand.SMALL -> 0.0065
			AimDistanceBand.MEDIUM -> 0.0115
			AimDistanceBand.LARGE -> 0.0165
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

	private fun practiceShakeAmplitudes(
		distance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): ShakeAmplitudes {
		val randomnessScale = 0.45 + settings.randomness * 0.42
		val base = when (band) {
			AimDistanceBand.TINY -> lerp(0.0, 0.004, (distance / TINY_DISTANCE).coerceIn(0.0, 1.0))
			AimDistanceBand.SMALL -> lerp(0.006, 0.018, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(0.020, 0.040, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(0.042, 0.062, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val travelYaw = (base * randomnessScale * random.between(0.82, 1.18))
			.coerceAtMost(MAX_PRACTICE_TRAVEL_SHAKE_DEGREES)
		val travelPitch = (travelYaw * random.between(0.42, 0.68))
			.coerceAtMost(MAX_PRACTICE_TRAVEL_SHAKE_DEGREES * 0.68)
		return ShakeAmplitudes(travelYaw, travelPitch, 0.0, 0.0)
	}

	private fun returnShakeAmplitudes(
		distance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): ShakeAmplitudes {
		val randomnessScale = 0.55 + settings.randomness * 0.45
		val base = when (band) {
			AimDistanceBand.TINY -> lerp(0.0, 0.007, (distance / TINY_DISTANCE).coerceIn(0.0, 1.0))
			AimDistanceBand.SMALL -> lerp(0.012, 0.030, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(0.038, 0.072, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(0.076, 0.116, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val travelYaw = (base * randomnessScale * random.between(0.82, 1.22))
			.coerceAtMost(MAX_RETURN_TRAVEL_SHAKE_DEGREES)
		val travelPitch = (travelYaw * random.between(0.38, 0.58))
			.coerceAtMost(MAX_RETURN_TRAVEL_SHAKE_DEGREES * 0.58)
		return ShakeAmplitudes(travelYaw, travelPitch, 0.0, 0.0)
	}

	private fun waitCorrectionShakeAmplitudes(
		distance: Double,
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random
	): ShakeAmplitudes {
		val randomnessScale = 0.32 + settings.randomness * 0.28
		val base = when (band) {
			AimDistanceBand.TINY -> lerp(0.0, 0.002, (distance / TINY_DISTANCE).coerceIn(0.0, 1.0))
			AimDistanceBand.SMALL -> lerp(0.003, 0.010, ((distance - TINY_DISTANCE) / (SMALL_DISTANCE - TINY_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.MEDIUM -> lerp(0.012, 0.024, ((distance - SMALL_DISTANCE) / (MEDIUM_DISTANCE - SMALL_DISTANCE)).coerceIn(0.0, 1.0))
			AimDistanceBand.LARGE -> lerp(0.026, 0.040, ((distance - MEDIUM_DISTANCE) / LARGE_DISTANCE_RANGE).coerceIn(0.0, 1.0))
		}
		val travelYaw = (base * randomnessScale * random.between(0.78, 1.12))
			.coerceAtMost(MAX_WAIT_CORRECTION_TRAVEL_SHAKE_DEGREES)
		val travelPitch = (travelYaw * random.between(0.38, 0.58))
			.coerceAtMost(MAX_WAIT_CORRECTION_TRAVEL_SHAKE_DEGREES * 0.58)
		return ShakeAmplitudes(travelYaw, travelPitch, 0.0, 0.0)
	}

	private fun overshootHoldDurationMs(overshootAmount: Double, band: AimDistanceBand, random: Random): Long {
		if (overshootAmount <= 0.0) {
			return 0L
		}

		val hold = when (band) {
			AimDistanceBand.TINY -> 0.0
			AimDistanceBand.SMALL -> 0.0
			AimDistanceBand.MEDIUM -> random.between(6.0, 12.0)
			AimDistanceBand.LARGE -> random.between(14.0, 24.0)
		}
		val aggressionBonus = when (band) {
			AimDistanceBand.TINY,
			AimDistanceBand.SMALL -> 0.0
			AimDistanceBand.MEDIUM -> random.between(1.5, 4.0)
			AimDistanceBand.LARGE -> random.between(8.0, 14.0)
		}
		return (hold + aggressionBonus * (overshootAmount / AGGRESSIVE_FLICK_OVERSHOOT_DEGREES).coerceIn(0.0, 1.0)).toLong()
	}

	private fun overshootHoldDurationMs(
		overshootAmount: Double,
		band: AimDistanceBand,
		random: Random,
		moveContext: AimMoveContext?
	): Long {
		if (moveContext != null) {
			if (overshootAmount <= 0.0 || moveContext.chebyshevDistance <= 1) {
				return 0L
			}
			if (isLongJumpChain(moveContext)) {
				val hold = if (moveContext.reversingDirection) {
					random.between(6.0, 10.0)
				} else {
					random.between(3.0, 7.0)
				}
				val aggressionBonus = random.between(0.5, 2.0) *
					(overshootAmount / AGGRESSIVE_FLICK_OVERSHOOT_DEGREES).coerceIn(0.0, 1.0)
				return (hold + aggressionBonus).toLong()
			}
			if (moveContext.chebyshevDistance == 2) {
				val hold = if (moveContext.continuingDirection && moveContext.passesThroughTarget) {
					random.between(2.0, 5.0)
				} else if (moveContext.continuingDirection) {
					random.between(3.0, 6.0)
				} else {
					random.between(4.0, 8.0)
				}
				val aggressionBonus = random.between(1.0, 3.0) * (overshootAmount / AGGRESSIVE_FLICK_OVERSHOOT_DEGREES).coerceIn(0.0, 1.0)
				return (hold + aggressionBonus).toLong()
			}
		}
		return overshootHoldDurationMs(overshootAmount, band, random)
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

	private fun correctionDurationMs(
		durationMs: Long,
		overshootAmount: Double,
		band: AimDistanceBand,
		random: Random,
		moveContext: AimMoveContext?
	): Long {
		if (moveContext != null) {
			return when {
				overshootAmount <= 0.0 -> 0L
				moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection -> (durationMs * random.between(0.18, 0.24)).toLong().coerceAtLeast(14L)
				moveContext.chebyshevDistance <= 1 -> (durationMs * random.between(0.22, 0.30)).toLong().coerceAtLeast(18L)
				moveContext.chebyshevDistance == 2 && moveContext.continuingDirection && moveContext.passesThroughTarget ->
					(durationMs * random.between(0.14, 0.20)).toLong().coerceAtLeast(16L)
				moveContext.chebyshevDistance == 2 && moveContext.continuingDirection ->
					(durationMs * random.between(0.18, 0.26)).toLong().coerceAtLeast(18L)
				moveContext.chebyshevDistance == 2 -> (durationMs * random.between(0.24, 0.34)).toLong().coerceAtLeast(24L)
				isLongJumpChain(moveContext) && moveContext.reversingDirection ->
					(durationMs * random.between(0.18, 0.26)).toLong().coerceAtLeast(24L)
				isLongJumpChain(moveContext) ->
					(durationMs * random.between(0.10, 0.18)).toLong().coerceAtLeast(20L)
				else -> correctionDurationMs(durationMs, overshootAmount, band, random)
			}
		}
		return correctionDurationMs(durationMs, overshootAmount, band, random)
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

	private fun settleDurationMs(
		band: AimDistanceBand,
		settings: AimSettings,
		random: Random,
		moveContext: AimMoveContext?
	): Long {
		if (isOneBlockPassThrough(moveContext)) {
			return 0L
		}
		if (isLongJumpChain(moveContext)) {
			return LONG_CHAIN_SETTLE_MS
		}
		return settleDurationMs(band, settings, random)
	}

	private fun clickReadyAtMs(
		durationMs: Long,
		correctionDurationMs: Long,
		settleDurationMs: Long,
		overshootAmount: Double,
		moveContext: AimMoveContext?
	): Long {
		if (moveContext != null) {
			return when {
				isOneBlockPassThrough(moveContext) ->
					(durationMs * 0.88).toLong().coerceAtLeast(84L)
				moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection ->
					max(0L, durationMs - max(28L, settleDurationMs))
				moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection ->
					max(0L, durationMs - max(20L, (settleDurationMs * 3L) / 5L))
				moveContext.chebyshevDistance <= 1 ->
					max(0L, durationMs - max(28L, settleDurationMs))
				moveContext.chebyshevDistance == 2 && moveContext.continuingDirection && moveContext.passesThroughTarget ->
					(durationMs * 0.84).toLong().coerceAtLeast(72L)
				moveContext.chebyshevDistance == 2 && moveContext.continuingDirection ->
					max(0L, durationMs - max(18L, (settleDurationMs * 3L) / 5L))
				moveContext.chebyshevDistance == 2 ->
					max(0L, durationMs - min(12L, max(8L, settleDurationMs / 4L)))
				isLongJumpChain(moveContext) && moveContext.reversingDirection ->
					(durationMs * 0.88).toLong().coerceAtLeast(110L)
				isLongJumpChain(moveContext) ->
					(durationMs * 0.80).toLong().coerceAtLeast(104L)
				else ->
					max(0L, durationMs - min(12L, max(8L, settleDurationMs / 3L)))
			}
		}
		if (overshootAmount <= 0.0 && correctionDurationMs <= 0L) {
			return max(0L, durationMs - min(10L, settleDurationMs / 2L))
		}

		return max(0L, durationMs - min(12L, settleDurationMs / 2L))
	}

	private fun clickReadyTolerance(plan: AimPlan): Double =
		when {
			plan.moveContext?.chebyshevDistance ?: 0 <= 1 && plan.moveContext?.continuingDirection == true ->
				(0.16 + plan.angularDistance * 0.020).coerceIn(0.16, 0.50)
			else -> (0.12 + plan.angularDistance * 0.018).coerceIn(0.12, 0.42)
		}

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

	private fun applyVelocityBlendIn(plan: AimPlan, rawProgress: Double, shapedProgress: Double): Double {
		val strength = plan.velocityBlendIn.coerceIn(0.0, 1.0)
		if (strength <= 0.0) {
			return shapedProgress.coerceIn(0.0, 1.0)
		}

		val progress = rawProgress.coerceIn(0.0, 1.0)
		if (progress <= 0.0 || progress >= VELOCITY_BLEND_IN_PROGRESS) {
			return shapedProgress.coerceIn(0.0, 1.0)
		}

		val blendOut = smootherStep((progress / VELOCITY_BLEND_IN_PROGRESS).coerceIn(0.0, 1.0))
		val gentleWeight = lerp(VELOCITY_BLEND_GENTLE_WEIGHT_MIN, VELOCITY_BLEND_GENTLE_WEIGHT_MAX, strength)
		val gentle = lerp(progress, smootherStep(progress), gentleWeight)
		val softened = lerp(gentle, shapedProgress, blendOut)
		return lerp(shapedProgress, softened, strength).coerceIn(0.0, 1.0)
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

	private fun momentumCorrectionProgress(plan: AimPlan, rawProgress: Double, easeOutWeight: Double, catchUpPower: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		val delayed = smootherStep(progress.pow((1.14 + plan.decelerationStrength * 0.18).coerceAtLeast(1.0)))
		val catchUp = easeOutPower(progress, catchUpPower + plan.flickAggression * 0.08)
		return lerp(delayed, catchUp, easeOutWeight.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
	}

	private fun oneBlockVelocityCappedProgress(moveContext: AimMoveContext, rawProgress: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		if (progress <= 0.0 || progress >= 1.0) {
			return progress
		}

		val maxRate = oneBlockMaxProgressRate(moveContext)
		val rampDuration = (1.0 - 1.0 / maxRate).coerceIn(0.04, 0.45)
		val coastStart = rampDuration
		val coastEnd = 1.0 - rampDuration
		return when {
			progress < coastStart -> {
				0.5 * maxRate * progress * progress / rampDuration
			}
			progress <= coastEnd -> {
				0.5 * maxRate * rampDuration + maxRate * (progress - rampDuration)
			}
			else -> {
				val remaining = 1.0 - progress
				1.0 - 0.5 * maxRate * remaining * remaining / rampDuration
			}
		}.coerceIn(0.0, 1.0)
	}

	private fun oneBlockChainFlowProgress(rawProgress: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		if (progress <= 0.0 || progress >= 1.0) {
			return progress
		}

		return (progress - ONE_BLOCK_CHAIN_FLOW_CURVE * sin(PI * 2.0 * progress)).coerceIn(0.0, 1.0)
	}

	private fun longJumpChainProgress(plan: AimPlan, moveContext: AimMoveContext, rawProgress: Double): Double {
		val progress = rawProgress.coerceIn(0.0, 1.0)
		val controlledWeight = if (moveContext.reversingDirection) 0.44 else 0.30
		val controlled = lerp(progress, smootherStep(progress), controlledWeight)
		val flowPower = if (moveContext.reversingDirection) 1.24 else 1.42
		val flow = easeOutPower(progress, flowPower + plan.flickAggression * 0.12)
		val flowBlend = if (moveContext.reversingDirection) 0.12 else 0.22
		val shaped = lerp(controlled, flow, flowBlend)
		val leadLimit = if (moveContext.reversingDirection) 1.36 else 1.58
		return limitInitialLead(shaped, progress, leadLimit + plan.flickAggression * 0.10).coerceIn(0.0, 1.0)
	}

	private fun delayedBlend(rawProgress: Double, start: Double, end: Double, maxBlend: Double): Double {
		if (maxBlend <= 0.0) {
			return 0.0
		}

		val span = (end - start).coerceAtLeast(0.0001)
		val progress = ((rawProgress - start) / span).coerceIn(0.0, 1.0)
		return smootherStep(progress) * maxBlend.coerceIn(0.0, 1.0)
	}

	private fun isOneBlockPassThrough(moveContext: AimMoveContext?): Boolean =
		moveContext != null &&
			moveContext.chebyshevDistance <= 1 &&
			moveContext.passesThroughTarget

	private fun isOneBlockMove(moveContext: AimMoveContext?): Boolean =
		moveContext != null &&
			moveContext.chebyshevDistance <= 1

	private fun isOneBlockChainFlow(moveContext: AimMoveContext?): Boolean =
		moveContext != null &&
			isOneBlockPassThrough(moveContext) &&
			moveContext.continuingDirection &&
			!moveContext.reversingDirection

	private fun isLongJumpChain(moveContext: AimMoveContext?): Boolean =
		moveContext != null &&
			moveContext.chebyshevDistance >= LONG_CHAIN_CURRENT_DISTANCE &&
			moveContext.nextChebyshevDistance >= LONG_CHAIN_NEXT_DISTANCE

	private fun velocityBlendInStrength(
		previousPlan: AimPlan?,
		currentProfile: AimVelocityProfile,
		mode: AimMode,
		moveContext: AimMoveContext?,
		nowMs: Long
	): Double {
		if (previousPlan == null || !allowsVelocityBlendIn(mode)) {
			return 0.0
		}

		val idleMs = nowMs - previousPlan.startedAtMs - previousPlan.durationMs
		if (idleMs > VELOCITY_BLEND_MAX_IDLE_MS) {
			return 0.0
		}

		val previousRank = movementVelocityRank(previousPlan.velocityProfile, previousPlan.moveContext)
		val currentRank = movementVelocityRank(currentProfile, moveContext)
		val rankIncrease = currentRank - previousRank
		val aggressiveCurrent = currentRank >= 3
		if (rankIncrease <= 0 && !aggressiveCurrent) {
			return 0.0
		}

		val base = when {
			rankIncrease >= 3 -> 0.72
			rankIncrease == 2 -> 0.62
			rankIncrease == 1 -> 0.48
			aggressiveCurrent -> 0.34
			else -> 0.0
		}
		val chainBonus = if (mode == AimMode.CHAINED_RETARGET && aggressiveCurrent) 0.10 else 0.0
		val longChainBonus = if (isLongJumpChain(moveContext)) 0.08 else 0.0
		return (base + chainBonus + longChainBonus).coerceIn(0.0, MAX_VELOCITY_BLEND_IN)
	}

	private fun allowsVelocityBlendIn(mode: AimMode): Boolean =
		when (mode) {
			AimMode.NORMAL_BUTTON,
			AimMode.CHAINED_RETARGET,
			AimMode.PRACTICE -> true
			else -> false
		}

	private fun movementVelocityRank(profile: AimVelocityProfile, moveContext: AimMoveContext?): Int =
		when {
			moveContext?.chebyshevDistance ?: 0 >= 3 -> 3
			moveContext?.chebyshevDistance == 2 -> 2
			moveContext != null -> if (moveContext.reversingDirection) 1 else 0
			else -> when (profile) {
				AimVelocityProfile.TINY -> 0
				AimVelocityProfile.SMALL -> 1
				AimVelocityProfile.MEDIUM -> 2
				AimVelocityProfile.LARGE -> 3
			}
		}

	private fun oneBlockMaxProgressRate(moveContext: AimMoveContext): Double =
		when {
			moveContext.reversingDirection -> ONE_BLOCK_REVERSE_MAX_PROGRESS_RATE
			moveContext.passesThroughTarget && !moveContext.continuingDirection -> ONE_BLOCK_FIRST_PASS_MAX_PROGRESS_RATE
			moveContext.passesThroughTarget -> ONE_BLOCK_PASS_MAX_PROGRESS_RATE
			moveContext.continuingDirection -> ONE_BLOCK_CONTINUE_MAX_PROGRESS_RATE
			else -> ONE_BLOCK_MAX_PROGRESS_RATE
		}

	private fun oneBlockPassThroughDurationMs(mode: AimMode, moveContext: AimMoveContext, random: Random): Long {
		if (isOneBlockChainFlow(moveContext)) {
			val practicePadding = if (mode == AimMode.PRACTICE) ONE_BLOCK_PRACTICE_PASS_PADDING_MS else 0L
			return (ONE_BLOCK_CHAIN_FLOW_DURATION_MS + practicePadding).coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
		}

		val range = when {
			moveContext.reversingDirection -> ONE_BLOCK_REVERSE_PASS_DURATION_MIN_MS to ONE_BLOCK_REVERSE_PASS_DURATION_MAX_MS
			!moveContext.continuingDirection -> ONE_BLOCK_FIRST_PASS_DURATION_MIN_MS to ONE_BLOCK_FIRST_PASS_DURATION_MAX_MS
			mode == AimMode.CHAINED_RETARGET -> ONE_BLOCK_CHAIN_PASS_DURATION_MIN_MS to ONE_BLOCK_CHAIN_PASS_DURATION_MAX_MS
			else -> ONE_BLOCK_PASS_DURATION_MIN_MS to ONE_BLOCK_PASS_DURATION_MAX_MS
		}
		val practicePadding = if (mode == AimMode.PRACTICE) ONE_BLOCK_PRACTICE_PASS_PADDING_MS else 0L
		return (random.between(range.first.toDouble(), range.second.toDouble()) + practicePadding)
			.toLong()
			.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
	}

	private operator fun Rotation.plus(other: Rotation): Rotation =
		Rotation(yaw + other.yaw, pitch + other.pitch)

	private fun Random.between(min: Double, max: Double): Double =
		min + (max - min) * nextDouble()

	private fun Random.sign(): Int =
		if (nextBoolean()) 1 else -1

	private fun contextualDurationScale(moveContext: AimMoveContext?): Double {
		if (moveContext == null) {
			return 1.0
		}

		return when {
			moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> 1.54
			isOneBlockPassThrough(moveContext) -> 1.0
			moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection -> 1.42
			moveContext.chebyshevDistance <= 1 -> 1.48
			moveContext.chebyshevDistance == 2 && moveContext.reversingDirection -> 1.24
			moveContext.chebyshevDistance == 2 && moveContext.continuingDirection && moveContext.passesThroughTarget -> 1.14
			moveContext.chebyshevDistance == 2 && moveContext.continuingDirection -> 1.12
			moveContext.chebyshevDistance == 2 -> 1.08
			isLongJumpChain(moveContext) -> lerp(0.98, 0.90, nearMaxGridFactor(moveContext))
			moveContext.chebyshevDistance >= 3 -> lerp(0.94, 0.84, nearMaxGridFactor(moveContext))
			else -> 1.0
		}
	}

	private fun contextualMinDurationMs(moveContext: AimMoveContext?, mode: AimMode): Long {
		if (moveContext == null) {
			return MIN_DURATION_MS
		}

		return when {
			isOneBlockPassThrough(moveContext) ->
				if (mode == AimMode.CHAINED_RETARGET) MIN_CHAINED_ONE_BLOCK_PASS_DURATION_MS else MIN_ONE_BLOCK_PASS_DURATION_MS
			moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection ->
				MIN_ONE_BLOCK_CONTINUE_DURATION_MS
			moveContext.chebyshevDistance <= 1 ->
				MIN_ONE_BLOCK_DURATION_MS
			else -> MIN_DURATION_MS
		}
	}

	private fun contextualOvershootAmount(
		eyeDistance: Double,
		settings: AimSettings,
		random: Random,
		moveContext: AimMoveContext
	): Double {
		val overshootBlocks = when {
			moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> 0.0
			isOneBlockPassThrough(moveContext) -> 0.0
			moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection -> random.between(0.10, 0.18)
			moveContext.chebyshevDistance <= 1 -> random.between(0.04, 0.09)
			moveContext.chebyshevDistance == 2 && moveContext.continuingDirection && moveContext.passesThroughTarget -> random.between(0.04, 0.08)
			moveContext.chebyshevDistance == 2 && moveContext.continuingDirection -> random.between(0.06, 0.11)
			moveContext.chebyshevDistance == 2 -> random.between(0.08, 0.15)
			isLongJumpChain(moveContext) -> lerp(0.10, 0.22, nearMaxGridFactor(moveContext)) * random.between(0.88, 1.04)
			else -> lerp(0.24, 0.48, nearMaxGridFactor(moveContext)) * random.between(0.92, 1.10)
		}
		if (overshootBlocks <= 0.0) {
			return 0.0
		}

		val blockOffset = (overshootBlocks * settings.overshootStrength).coerceIn(0.0, MAX_OVERSHOOT_BLOCKS)
		val logicalDistance = max(1.0, moveContext.chebyshevDistance.toDouble() + moveContext.euclideanDistance * 0.22)
		val amount = Math.toDegrees(kotlin.math.atan2(blockOffset, eyeDistance))
		return amount.coerceIn(0.0, logicalDistance * MAX_CONTEXT_OVERSHOOT_DISTANCE_FRACTION)
	}

	private fun nearMaxGridFactor(moveContext: AimMoveContext): Double =
		((moveContext.euclideanDistance - 3.0) / (MAX_GRID_EUCLIDEAN_DISTANCE - 3.0)).coerceIn(0.0, 1.0)

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
			fun fromDistance(distance: Double, moveContext: AimMoveContext?): AimDistanceBand {
				if (moveContext != null) {
					return when {
						moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> SMALL
						moveContext.chebyshevDistance <= 1 -> TINY
						moveContext.chebyshevDistance == 2 -> MEDIUM
						else -> LARGE
					}
				}

				return when {
					distance < TINY_DISTANCE -> TINY
					distance < SMALL_DISTANCE -> SMALL
					distance < MEDIUM_DISTANCE -> MEDIUM
					else -> LARGE
				}
			}
		}
	}

	private companion object {
		private const val MIN_PITCH = -90.0f
		private const val MAX_PITCH = 90.0f
		private const val MIN_AIM_SPEED = 0.35
		private const val MAX_AIM_SPEED = 2.25
		private const val MAX_OVERSHOOT_STRENGTH = 1.3
		private const val TINY_DISTANCE = 1.2
		private const val SMALL_DISTANCE = 16.0
		private const val MEDIUM_DISTANCE = 32.0
		private const val LARGE_DISTANCE_RANGE = 24.0
		private const val OVERSHOOT_START_DISTANCE = 16.0
		private const val OVERSHOOT_FULL_DISTANCE = 26.0
		private const val MEDIUM_OVERSHOOT_BLOCKS_MIN = 0.14
		private const val MEDIUM_OVERSHOOT_BLOCKS_MAX = 0.24
		private const val LARGE_OVERSHOOT_BLOCKS_MIN = 0.34
		private const val MAX_OVERSHOOT_BLOCKS = 0.40
		private const val MIN_TARGET_EYE_DISTANCE = 0.5
		private const val MAX_OVERSHOOT_DISTANCE_FRACTION = 0.37
		private const val MAX_CONTEXT_OVERSHOOT_DISTANCE_FRACTION = 0.36
		private const val MAX_CURVE_DISTANCE_FRACTION = 0.08
		private const val MAX_RETURN_CURVE_DISTANCE_FRACTION = 0.036
		private const val MAX_PRACTICE_CURVE_DISTANCE_FRACTION = 0.268
		private const val MAX_GRID_EUCLIDEAN_DISTANCE = 4.242640687119285
		private const val MIN_DURATION_MS = 24L
		private const val MIN_ONE_BLOCK_DURATION_MS = 128L
		private const val MIN_ONE_BLOCK_PASS_DURATION_MS = 138L
		private const val MIN_CHAINED_ONE_BLOCK_PASS_DURATION_MS = 150L
		private const val MIN_ONE_BLOCK_CONTINUE_DURATION_MS = 116L
		private const val ONE_BLOCK_PASS_DURATION_MIN_MS = 174L
		private const val ONE_BLOCK_PASS_DURATION_MAX_MS = 206L
		private const val ONE_BLOCK_FIRST_PASS_DURATION_MIN_MS = 184L
		private const val ONE_BLOCK_FIRST_PASS_DURATION_MAX_MS = 222L
		private const val ONE_BLOCK_CHAIN_PASS_DURATION_MIN_MS = 172L
		private const val ONE_BLOCK_CHAIN_PASS_DURATION_MAX_MS = 204L
		private const val ONE_BLOCK_CHAIN_FLOW_DURATION_MS = 196L
		private const val ONE_BLOCK_REVERSE_PASS_DURATION_MIN_MS = 198L
		private const val ONE_BLOCK_REVERSE_PASS_DURATION_MAX_MS = 238L
		private const val ONE_BLOCK_PRACTICE_PASS_PADDING_MS = 8L
		private const val ONE_BLOCK_MAX_PROGRESS_RATE = 1.14
		private const val ONE_BLOCK_CONTINUE_MAX_PROGRESS_RATE = 1.11
		private const val ONE_BLOCK_PASS_MAX_PROGRESS_RATE = 1.09
		private const val ONE_BLOCK_FIRST_PASS_MAX_PROGRESS_RATE = 1.08
		private const val ONE_BLOCK_REVERSE_MAX_PROGRESS_RATE = 1.06
		private const val ONE_BLOCK_CHAIN_FLOW_CURVE = 0.012
		private const val LONG_CHAIN_CURRENT_DISTANCE = 3
		private const val LONG_CHAIN_NEXT_DISTANCE = 2
		private const val LONG_CHAIN_SETTLE_MS = 0L
		private const val VELOCITY_BLEND_IN_PROGRESS = 0.24
		private const val VELOCITY_BLEND_GENTLE_WEIGHT_MIN = 0.40
		private const val VELOCITY_BLEND_GENTLE_WEIGHT_MAX = 0.72
		private const val VELOCITY_BLEND_MAX_IDLE_MS = 90L
		private const val MAX_VELOCITY_BLEND_IN = 0.84
		private const val MAX_DURATION_MS = 360L
		private const val MIN_APPROACH_MS = 22L
		private const val MIN_CORRECTION_MS = 34L
		private const val OVERSHOOT_RECOVERY_EXTRA_MS = 30L
		private const val MIN_SETTLE_MS = 6L
		private const val SETTLE_DRIFT_SCALE = 0.055f
		private const val CORRECTION_CURVE_SCALE = 0.2
		private const val HOLD_COAST_OVERSHOOT_SCALE = 0.18
		private const val HOLD_COAST_CURVE_SCALE = 0.34
		private const val HOLD_COAST_TERMINAL_OVERSHOOT_SCALE = 0.08
		private const val HOLD_COAST_TERMINAL_CURVE_SCALE = 0.18
		private const val CURVE_PEAK_SCALE = 0.92
		private const val CURVE_ENTRY_FLOOR = 0.84
		private const val AGGRESSIVE_FLICK_OVERSHOOT_DEGREES = 0.22
		private const val MAX_TRAVEL_SHAKE_DEGREES = 0.2
		private const val MAX_PRACTICE_TRAVEL_SHAKE_DEGREES = 0.62
		private const val MAX_RETURN_TRAVEL_SHAKE_DEGREES = 0.12
		private const val MAX_WAIT_CORRECTION_TRAVEL_SHAKE_DEGREES = 0.245
		private const val ARRIVAL_SHAKE_DURATION_MS = 290L
		private const val MAX_CLICK_ARRIVAL_SHAKE_DEGREES = 0.321
		private const val MAX_PRE_AIM_ARRIVAL_SHAKE_DEGREES = 0.324
		private const val MAX_WAIT_CORRECTION_ARRIVAL_SHAKE_DEGREES = 0.012
		private const val MAX_PRACTICE_ARRIVAL_SHAKE_DEGREES = 0.195
		private const val MAX_RETURN_ARRIVAL_SHAKE_DEGREES = 0.034
		private const val MAX_SETTLE_SHAKE_DEGREES = 0.046
		private const val MAX_HOLD_SHAKE_DEGREES = 0.024
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
