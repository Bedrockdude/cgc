package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.module.impl.dungeon.AimMode
import cgc.cgc.module.impl.dungeon.AimSettings
import cgc.cgc.module.impl.dungeon.AimTimingProfile
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
	ICE_TURN
}

class AutoPuzzleAimController {
	private val controller = SimonSaysAimController()
	private val mouse = VanillaMouseMotion()
	private var target: Vec3? = null

	fun start(context: AutoPuzzleContext, target: Vec3, speed: Double, profile: AutoPuzzleAimProfile) {
		this.target = target
		controller.start(
			player = context.player,
			target = target,
			settings = AimSettings(
				speed = speed,
				randomness = if (profile == AutoPuzzleAimProfile.ICE_TURN) 0.08 else 0.18,
				overshootStrength = 0.9
			),
			mode = when (profile) {
				AutoPuzzleAimProfile.CHAINED -> AimMode.CHAINED_RETARGET
				AutoPuzzleAimProfile.PRE_AIM -> AimMode.PRE_AIM
				else -> AimMode.NORMAL_BUTTON
			},
			nowMs = context.nowMs,
			timing = if (profile == AutoPuzzleAimProfile.ICE_TURN) {
				AimTimingProfile(minimumSpeed = 0.10, maximumSpeed = 0.80, maximumDurationMs = 5_000L)
			} else {
				AimTimingProfile()
			}
		)
	}

	fun update(context: AutoPuzzleContext): Result {
		val desired = controller.update(context.nowMs)
		mouse.apply(context.client, context.player, desired.rotation)
		val target = target
		val error = if (target == null) Double.POSITIVE_INFINITY else angularError(context, target)
		return Result(
			ready = desired.readyToClick && error <= READY_ERROR_DEGREES,
			finished = desired.finished && error <= FINISHED_ERROR_DEGREES
		)
	}

	fun clear() {
		target = null
		controller.clear()
		mouse.clear()
	}

	fun hasTarget(): Boolean = controller.hasTarget()

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
	}
}
