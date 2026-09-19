package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.module.impl.dungeon.AimMode
import cgc.cgc.module.impl.dungeon.AimSettings
import cgc.cgc.module.impl.dungeon.AimTimingProfile
import cgc.cgc.module.impl.dungeon.Rotation
import cgc.cgc.module.impl.dungeon.SimonSaysAimController
import cgc.cgc.module.impl.dungeon.VanillaMouseMotion
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

enum class AutoPuzzleAimProfile {
	NORMAL,
	CHAINED,
	PRE_AIM,
	ETHERWARP,
	ICE_TURN,
	BLAZE
}

class AutoPuzzleAimController {
	private val controller = SimonSaysAimController()
	private val mouse = VanillaMouseMotion()
	private var target: Vec3? = null
	private var speed = 1.0
	private var profile = AutoPuzzleAimProfile.NORMAL

	fun start(context: AutoPuzzleContext, target: Vec3, speed: Double, profile: AutoPuzzleAimProfile) {
		this.target = target
		this.speed = speed
		this.profile = profile
		controller.start(
			player = context.player,
			target = target,
			settings = aimSettings(profile, speed),
			mode = when (profile) {
				AutoPuzzleAimProfile.CHAINED -> AimMode.CHAINED_RETARGET
				AutoPuzzleAimProfile.PRE_AIM -> AimMode.PRE_AIM
				AutoPuzzleAimProfile.BLAZE -> AimMode.START_BUTTON
				else -> AimMode.NORMAL_BUTTON
			},
			nowMs = context.nowMs,
			timing = timing(profile)
		)
	}

	fun update(context: AutoPuzzleContext): Result {
		retargetForEyeMovement(context.player, context.nowMs)
		val desired = controller.update(context.nowMs)
		mouse.apply(context.client, context.player, desired.rotation, angularSpeedLimit(profile))
		val target = target
		val error = if (target == null) Double.POSITIVE_INFINITY else angularError(context, target)
		return Result(
			ready = desired.readyToClick && error <= READY_ERROR_DEGREES,
			finished = desired.finished && error <= FINISHED_ERROR_DEGREES
		)
	}

	/** Samples the same time-based path every rendered frame; actions remain tick-gated in [update]. */
	fun frameUpdate(client: net.minecraft.client.Minecraft) {
		if (!controller.hasTarget()) return
		val player = client.player ?: return
		val nowMs = AutoPuzzleContext.monotonicNowMs()
		retargetForEyeMovement(player, nowMs)
		val desired = controller.update(nowMs)
		mouse.apply(client, player, desired.rotation, angularSpeedLimit(profile))
	}

	fun clear() {
		target = null
		controller.clear()
		mouse.clear()
	}

	fun hasTarget(): Boolean = controller.hasTarget()

	private fun retargetForEyeMovement(player: net.minecraft.client.player.LocalPlayer, nowMs: Long) {
		val point = target ?: return
		val plan = controller.currentPlan() ?: return
		val wanted = rotationTo(player.eyePosition, point)
		val yawError = Mth.wrapDegrees(wanted.yaw - plan.final.yaw).toDouble()
		val pitchError = (wanted.pitch - plan.final.pitch).toDouble()
		if (sqrt(yawError * yawError + pitchError * pitchError) <= RETARGET_EYE_ERROR_DEGREES) return
		controller.start(
			player = player,
			target = point,
			settings = aimSettings(profile, speed),
			mode = if (profile == AutoPuzzleAimProfile.BLAZE) AimMode.START_BUTTON else AimMode.CHAINED_RETARGET,
			nowMs = nowMs,
			timing = timing(profile)
		)
	}

	private fun aimSettings(profile: AutoPuzzleAimProfile, speed: Double) = AimSettings(
		speed = if (profile == AutoPuzzleAimProfile.BLAZE) speed * 1.40 else speed,
		randomness = when (profile) {
			AutoPuzzleAimProfile.ICE_TURN -> 0.08
			AutoPuzzleAimProfile.BLAZE -> 0.28
			else -> 0.18
		},
		overshootStrength = if (profile == AutoPuzzleAimProfile.BLAZE) 1.05 else 0.9
	)

	private fun timing(profile: AutoPuzzleAimProfile) = when (profile) {
		AutoPuzzleAimProfile.ICE_TURN -> AimTimingProfile(minimumSpeed = 0.10, maximumSpeed = 0.80, maximumDurationMs = 5_000L)
		AutoPuzzleAimProfile.BLAZE -> AimTimingProfile(
			minimumSpeed = 0.25,
			maximumSpeed = 2.80,
			maximumDurationMs = 500L,
			maximumAngularVelocity = 760.0
		)
		else -> AimTimingProfile()
	}

	private fun angularSpeedLimit(profile: AutoPuzzleAimProfile): Double =
		if (profile == AutoPuzzleAimProfile.BLAZE) 760.0 else 540.0

	private fun rotationTo(eye: Vec3, target: Vec3): Rotation {
		val dx = target.x - eye.x
		val dy = target.y - eye.y
		val dz = target.z - eye.z
		val horizontal = sqrt(dx * dx + dz * dz)
		return Rotation(
			Math.toDegrees(kotlin.math.atan2(dz, dx)).toFloat() - 90.0f,
			-Math.toDegrees(kotlin.math.atan2(dy, horizontal)).toFloat()
		)
	}

	private fun angularError(context: AutoPuzzleContext, target: Vec3): Double {
		val eye = context.player.eyePosition
		val dx = target.x - eye.x
		val dy = target.y - eye.y
		val dz = target.z - eye.z
		val horizontal = sqrt(dx * dx + dz * dz)
		val yaw = Math.toDegrees(kotlin.math.atan2(dz, dx)).toFloat() - 90.0f
		val pitch = -Math.toDegrees(kotlin.math.atan2(dy, horizontal)).toFloat()
		val yawError = Mth.wrapDegrees(yaw - context.player.yRot).toDouble()
		val pitchError = (pitch - context.player.xRot).toDouble()
		return sqrt(yawError * yawError + pitchError * pitchError)
	}

	data class Result(val ready: Boolean, val finished: Boolean)

	private companion object {
		const val READY_ERROR_DEGREES = 0.65
		const val FINISHED_ERROR_DEGREES = 0.80
		const val RETARGET_EYE_ERROR_DEGREES = 0.12
	}
}
