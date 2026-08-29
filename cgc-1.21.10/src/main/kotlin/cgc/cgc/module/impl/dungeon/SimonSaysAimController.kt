package cgc.cgc.module.impl.dungeon

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.sqrt

internal enum class AimMode {
	START_BUTTON,
	NORMAL_BUTTON,
	CHAINED_RETARGET,
	PRE_AIM,
	WAIT_CORRECTION,
	PRACTICE,
	PRACTICE_RETURN
}

internal data class Rotation(val yaw: Float, val pitch: Float)

internal data class AimSettings(
	val speed: Double,
	val randomness: Double,
	val overshootStrength: Double,
	val microCorrection: Double
)

internal data class AimTimingProfile(
	val minimumSpeed: Double = 0.35,
	val maximumSpeed: Double = 2.25,
	val maximumDurationMs: Long? = null
) {
	init {
		require(minimumSpeed > 0.0 && maximumSpeed >= minimumSpeed)
		require(maximumDurationMs == null || maximumDurationMs > 0L)
	}
}

internal data class AimMoveContext(
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

internal data class MotionAxis(
	val c0: Double,
	val c1: Double,
	val c2: Double,
	val c3: Double,
	val c4: Double,
	val c5: Double
)

internal data class AimPlan(
	val mode: AimMode,
	val moveContext: AimMoveContext?,
	val start: Rotation,
	val final: Rotation,
	val angularDistance: Double,
	val startedAtMs: Long,
	val durationMs: Long,
	val seed: Long,
	val clickReadyAtMs: Long,
	val yawMotion: MotionAxis,
	val pitchMotion: MotionAxis,
	val curveDirection: Rotation,
	val curveAmount: Double
)

internal data class AimUpdateResult(
	val rotation: Rotation,
	val readyToClick: Boolean,
	val finished: Boolean
)

/**
 * Builds a time-based mouse path while preserving velocity and acceleration at
 * chained retargets. The controller deliberately produces only the desired
 * rotation; [VanillaMouseMotion] converts that path into sensitivity-correct
 * mouse counts before it reaches the player.
 */
internal class SimonSaysAimController {
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
		nowMs: Long = monotonicNowMs(),
		timing: AimTimingProfile = AimTimingProfile()
	) {
		start(
			start = Rotation(player.yRot, player.xRot.coerceIn(MIN_PITCH, MAX_PITCH)),
			target = rotationTo(player, target),
			settings = settings,
			mode = mode,
			moveContext = moveContext,
			nowMs = nowMs,
			timing = timing
		)
	}

	internal fun start(
		start: Rotation,
		target: Rotation,
		settings: AimSettings,
		mode: AimMode,
		moveContext: AimMoveContext? = null,
		nowMs: Long = monotonicNowMs(),
		seed: Long = ThreadLocalRandom.current().nextLong(),
		timing: AimTimingProfile = AimTimingProfile()
	) {
		val previousPlan = plan
		val previousMotion = previousPlan
			?.takeIf { carriesMotion(it, mode, nowMs) }
			?.let { sampleMotion(it, nowMs - it.startedAtMs) }
		val initialVelocity = clampMagnitude(previousMotion?.velocity ?: ZERO_ROTATION, MAX_ANGULAR_SPEED)
		val initialAcceleration = clampMagnitude(previousMotion?.acceleration ?: ZERO_ROTATION, MAX_ANGULAR_ACCELERATION)
		val final = Rotation(
			start.yaw + Mth.wrapDegrees(target.yaw - start.yaw),
			target.pitch.coerceIn(MIN_PITCH, MAX_PITCH)
		)
		val yawDelta = Mth.wrapDegrees(final.yaw - start.yaw).toDouble()
		val pitchDelta = (final.pitch - start.pitch).toDouble()
		val angularDistance = sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta)
		val random = Random(seed)
		val safeSettings = settings.coerced(timing)
		val durationMs = planDurationMs(angularDistance, mode, safeSettings, moveContext, random, timing)
		val curveDirection = perpendicularDirection(yawDelta, pitchDelta, angularDistance, random)
		val curveAmount = curveAmount(angularDistance, mode, safeSettings, random)

		plan = AimPlan(
			mode = mode,
			moveContext = moveContext,
			start = start,
			final = final,
			angularDistance = angularDistance,
			startedAtMs = nowMs,
			durationMs = durationMs,
			seed = seed,
			clickReadyAtMs = (durationMs * CLICK_READY_PROGRESS).toLong(),
			yawMotion = quinticAxis(
				start.yaw.toDouble(),
				final.yaw.toDouble(),
				initialVelocity.yaw.toDouble(),
				0.0,
				initialAcceleration.yaw.toDouble(),
				0.0,
				durationMs
			),
			pitchMotion = quinticAxis(
				start.pitch.toDouble(),
				final.pitch.toDouble(),
				initialVelocity.pitch.toDouble(),
				0.0,
				initialAcceleration.pitch.toDouble(),
				0.0,
				durationMs
			),
			curveDirection = curveDirection,
			curveAmount = curveAmount
		)
	}

	fun update(nowMs: Long = monotonicNowMs()): AimUpdateResult {
		val activePlan = plan
			?: return AimUpdateResult(ZERO_ROTATION, readyToClick = false, finished = true)
		val elapsedMs = max(0L, nowMs - activePlan.startedAtMs)
		val motion = sampleMotion(activePlan, elapsedMs)
		val finished = elapsedMs >= activePlan.durationMs
		val clickableMode = activePlan.mode != AimMode.PRACTICE &&
			activePlan.mode != AimMode.PRACTICE_RETURN &&
			activePlan.mode != AimMode.PRE_AIM
		val readyToClick = clickableMode &&
			elapsedMs >= activePlan.clickReadyAtMs &&
			angularError(motion.rotation, activePlan.final) <= clickReadyTolerance(activePlan)
		return AimUpdateResult(motion.rotation, readyToClick, finished)
	}

	fun clear() {
		plan = null
	}

	private fun sampleMotion(plan: AimPlan, elapsedMs: Long): MotionSample {
		val progress = (elapsedMs.toDouble() / max(1L, plan.durationMs)).coerceIn(0.0, 1.0)
		val durationSeconds = max(0.001, plan.durationMs / 1000.0)
		val yaw = sampleAxis(plan.yawMotion, progress, durationSeconds)
		val pitch = sampleAxis(plan.pitchMotion, progress, durationSeconds)
		val curve = curveSample(progress, durationSeconds)
		val curveYaw = plan.curveDirection.yaw * plan.curveAmount
		val curvePitch = plan.curveDirection.pitch * plan.curveAmount
		return MotionSample(
			rotation = Rotation(
				(yaw.position + curveYaw * curve.position).toFloat(),
				(pitch.position + curvePitch * curve.position).toFloat().coerceIn(MIN_PITCH, MAX_PITCH)
			),
			velocity = Rotation(
				(yaw.velocity + curveYaw * curve.velocity).toFloat(),
				(pitch.velocity + curvePitch * curve.velocity).toFloat()
			),
			acceleration = Rotation(
				(yaw.acceleration + curveYaw * curve.acceleration).toFloat(),
				(pitch.acceleration + curvePitch * curve.acceleration).toFloat()
			)
		)
	}

	private fun sampleAxis(axis: MotionAxis, t: Double, durationSeconds: Double): AxisSample {
		val t2 = t * t
		val t3 = t2 * t
		val t4 = t3 * t
		val t5 = t4 * t
		val position = axis.c0 + axis.c1 * t + axis.c2 * t2 + axis.c3 * t3 + axis.c4 * t4 + axis.c5 * t5
		val velocityT = axis.c1 + 2.0 * axis.c2 * t + 3.0 * axis.c3 * t2 + 4.0 * axis.c4 * t3 + 5.0 * axis.c5 * t4
		val accelerationT = 2.0 * axis.c2 + 6.0 * axis.c3 * t + 12.0 * axis.c4 * t2 + 20.0 * axis.c5 * t3
		return AxisSample(
			position = position,
			velocity = velocityT / durationSeconds,
			acceleration = accelerationT / (durationSeconds * durationSeconds)
		)
	}

	private fun curveSample(t: Double, durationSeconds: Double): AxisSample {
		val t2 = t * t
		val t3 = t2 * t
		val t4 = t3 * t
		val t5 = t4 * t
		val t6 = t5 * t
		val position = 64.0 * (t3 - 3.0 * t4 + 3.0 * t5 - t6)
		val velocityT = 64.0 * (3.0 * t2 - 12.0 * t3 + 15.0 * t4 - 6.0 * t5)
		val accelerationT = 64.0 * (6.0 * t - 36.0 * t2 + 60.0 * t3 - 30.0 * t4)
		return AxisSample(
			position = position,
			velocity = velocityT / durationSeconds,
			acceleration = accelerationT / (durationSeconds * durationSeconds)
		)
	}

	private fun quinticAxis(
		start: Double,
		end: Double,
		startVelocity: Double,
		endVelocity: Double,
		startAcceleration: Double,
		endAcceleration: Double,
		durationMs: Long
	): MotionAxis {
		val durationSeconds = max(0.001, durationMs / 1000.0)
		val c0 = start
		val c1 = startVelocity * durationSeconds
		val c2 = 0.5 * startAcceleration * durationSeconds * durationSeconds
		val remainingPosition = end - (c0 + c1 + c2)
		val remainingVelocity = endVelocity * durationSeconds - (c1 + 2.0 * c2)
		val remainingAcceleration = endAcceleration * durationSeconds * durationSeconds - 2.0 * c2
		return MotionAxis(
			c0 = c0,
			c1 = c1,
			c2 = c2,
			c3 = 10.0 * remainingPosition - 4.0 * remainingVelocity + 0.5 * remainingAcceleration,
			c4 = -15.0 * remainingPosition + 7.0 * remainingVelocity - remainingAcceleration,
			c5 = 6.0 * remainingPosition - 3.0 * remainingVelocity + 0.5 * remainingAcceleration
		)
	}

	private fun planDurationMs(
		distance: Double,
		mode: AimMode,
		settings: AimSettings,
		moveContext: AimMoveContext?,
		random: Random,
		timing: AimTimingProfile
	): Long {
		val baseMs = BASE_DURATION_MS + distance * MS_PER_DEGREE
		val modeScale = when (mode) {
			AimMode.START_BUTTON -> 0.94
			AimMode.NORMAL_BUTTON -> 1.0
			AimMode.CHAINED_RETARGET -> 0.86
			AimMode.PRE_AIM -> 1.06
			AimMode.WAIT_CORRECTION -> 0.80
			AimMode.PRACTICE -> 0.92
			AimMode.PRACTICE_RETURN -> 0.98
		}
		val contextScale = when {
			moveContext == null -> 1.0
			moveContext.chebyshevDistance <= 1 && moveContext.reversingDirection -> 1.08
			moveContext.chebyshevDistance <= 1 && moveContext.passesThroughTarget -> 0.82
			moveContext.chebyshevDistance <= 1 && moveContext.continuingDirection -> 0.86
			moveContext.chebyshevDistance <= 1 -> 0.92
			moveContext.chebyshevDistance == 2 && moveContext.reversingDirection -> 1.04
			moveContext.chebyshevDistance == 2 -> 0.94
			else -> 0.96
		}
		val jitterStrength = 0.45 + settings.randomness * 0.55
		val jitter = 1.0 + random.between(-0.025, 0.03) * jitterStrength
		val requested = baseMs * modeScale * contextScale * jitter / settings.speed
		val velocityFloor = distance * QUINTIC_PEAK_VELOCITY_FACTOR / MAX_ANGULAR_SPEED * 1000.0
		val accelerationFloor = sqrt(distance * QUINTIC_PEAK_ACCELERATION_FACTOR / MAX_ANGULAR_ACCELERATION) * 1000.0
		val minimum = max(modeMinimumMs(mode), max(velocityFloor, accelerationFloor))
		val maximum = timing.maximumDurationMs ?: modeMaximumMs(mode)
		return max(requested, minimum)
			.toLong()
			.coerceIn(modeMinimumMs(mode).toLong(), maximum.coerceAtLeast(modeMinimumMs(mode).toLong()))
	}

	private fun modeMinimumMs(mode: AimMode): Double =
		when (mode) {
			AimMode.START_BUTTON -> 80.0
			AimMode.NORMAL_BUTTON -> 86.0
			AimMode.CHAINED_RETARGET -> 76.0
			AimMode.PRE_AIM -> 100.0
			AimMode.WAIT_CORRECTION -> 65.0
			AimMode.PRACTICE -> 82.0
			AimMode.PRACTICE_RETURN -> 90.0
		}

	private fun modeMaximumMs(mode: AimMode): Long =
		when (mode) {
			AimMode.START_BUTTON -> 220L
			AimMode.NORMAL_BUTTON,
			AimMode.CHAINED_RETARGET -> 260L
			AimMode.PRE_AIM -> 280L
			AimMode.WAIT_CORRECTION -> 220L
			AimMode.PRACTICE,
			AimMode.PRACTICE_RETURN -> 260L
		}

	private fun curveAmount(distance: Double, mode: AimMode, settings: AimSettings, random: Random): Double {
		if (distance < MIN_CURVE_DISTANCE || mode == AimMode.WAIT_CORRECTION) {
			return 0.0
		}
		val modeScale = when (mode) {
			AimMode.PRE_AIM -> 0.45
			AimMode.PRACTICE -> 1.08
			AimMode.PRACTICE_RETURN -> 0.62
			else -> 1.0
		}
		val randomnessScale = 0.55 + settings.randomness * 0.45
		val overshootScale = 0.82 + settings.overshootStrength * 0.18
		return ((MIN_CURVE_AMOUNT + distance * CURVE_PER_DEGREE) *
			modeScale * randomnessScale * overshootScale * random.between(0.82, 1.12))
			.coerceAtMost(MAX_CURVE_AMOUNT)
	}

	private fun perpendicularDirection(yaw: Double, pitch: Double, distance: Double, random: Random): Rotation {
		if (distance <= 0.0001) {
			return ZERO_ROTATION
		}
		val sign = if (random.nextBoolean()) 1.0 else -1.0
		return Rotation(
			(-pitch / distance * sign).toFloat(),
			(yaw / distance * sign).toFloat()
		)
	}

	private fun carriesMotion(previous: AimPlan, nextMode: AimMode, nowMs: Long): Boolean {
		if (nextMode == AimMode.START_BUTTON) {
			return false
		}
		val idleMs = nowMs - previous.startedAtMs - previous.durationMs
		return idleMs <= MAX_MOTION_CARRY_IDLE_MS
	}

	private fun clickReadyTolerance(plan: AimPlan): Double =
		(0.18 + plan.angularDistance * 0.007).coerceIn(0.18, 0.48)

	private fun angularError(rotation: Rotation, target: Rotation): Double {
		val yaw = Mth.wrapDegrees(rotation.yaw - target.yaw).toDouble()
		val pitch = (rotation.pitch - target.pitch).toDouble()
		return sqrt(yaw * yaw + pitch * pitch)
	}

	private fun rotationTo(player: LocalPlayer, target: Vec3): Rotation {
		val eye = player.eyePosition
		val dx = target.x - eye.x
		val dy = target.y - eye.y
		val dz = target.z - eye.z
		val horizontal = sqrt(dx * dx + dz * dz)
		return Rotation(
			Math.toDegrees(kotlin.math.atan2(dz, dx)).toFloat() - 90.0f,
			-Math.toDegrees(kotlin.math.atan2(dy, horizontal)).toFloat().coerceIn(MIN_PITCH, MAX_PITCH)
		)
	}

	private fun AimSettings.coerced(timing: AimTimingProfile): AimSettings =
		copy(
			speed = speed.coerceIn(timing.minimumSpeed, timing.maximumSpeed),
			randomness = randomness.coerceIn(0.0, 1.0),
			overshootStrength = overshootStrength.coerceIn(0.0, 1.3),
			microCorrection = microCorrection.coerceIn(0.0, 1.0)
		)

	private fun clampMagnitude(rotation: Rotation, maximum: Double): Rotation {
		val length = sqrt(rotation.yaw * rotation.yaw + rotation.pitch * rotation.pitch.toDouble())
		if (length <= maximum || length <= 0.0001) {
			return rotation
		}
		val scale = maximum / length
		return Rotation((rotation.yaw * scale).toFloat(), (rotation.pitch * scale).toFloat())
	}

	private fun Random.between(minimum: Double, maximum: Double): Double =
		minimum + (maximum - minimum) * nextDouble()

	private data class AxisSample(
		val position: Double,
		val velocity: Double,
		val acceleration: Double
	)

	private data class MotionSample(
		val rotation: Rotation,
		val velocity: Rotation,
		val acceleration: Rotation
	)

	private companion object {
		private const val MIN_PITCH = -90.0f
		private const val MAX_PITCH = 90.0f
		private const val MIN_AIM_SPEED = 0.35
		private const val MAX_AIM_SPEED = 2.25
		private const val BASE_DURATION_MS = 74.0
		private const val MS_PER_DEGREE = 3.15
		private const val MAX_ANGULAR_SPEED = 540.0
		private const val MAX_ANGULAR_ACCELERATION = 14_000.0
		private const val QUINTIC_PEAK_VELOCITY_FACTOR = 1.875
		private const val QUINTIC_PEAK_ACCELERATION_FACTOR = 5.8
		private const val CLICK_READY_PROGRESS = 0.72
		private const val MAX_MOTION_CARRY_IDLE_MS = 90L
		private const val MIN_CURVE_DISTANCE = 1.5
		private const val MIN_CURVE_AMOUNT = 0.012
		private const val CURVE_PER_DEGREE = 0.0036
		private const val MAX_CURVE_AMOUNT = 0.18
		private val ZERO_ROTATION = Rotation(0.0f, 0.0f)

		private fun monotonicNowMs(): Long =
			System.nanoTime() / 1_000_000L
	}
}
