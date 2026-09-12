package cgc.cgc.module.impl.dungeon

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Follows an absolute rotation target using whole raw-mouse counts. Passing the
 * counts through [LocalPlayer.turn] reproduces vanilla's sensitivity transform
 * and updates the previous rotations used by render interpolation.
 */
internal class VanillaMouseMotion {
	private var lastAppliedAtNs = 0L

	fun apply(
		client: Minecraft,
		player: LocalPlayer,
		desired: Rotation,
		nowNs: Long = System.nanoTime()
	): Rotation {
		if (client.screen != null || !client.mouseHandler.isMouseGrabbed) {
			lastAppliedAtNs = 0L
			return currentRotation(player)
		}

		val frameSeconds = if (lastAppliedAtNs == 0L) {
			DEFAULT_FRAME_SECONDS
		} else {
			((nowNs - lastAppliedAtNs) / NANOS_PER_SECOND).coerceIn(MIN_FRAME_SECONDS, MAX_FRAME_SECONDS)
		}
		lastAppliedAtNs = nowNs

		val sensitivity = client.options.sensitivity().get()
		val mouseScale = mouseScale(sensitivity)
		val degreesPerCount = mouseScale * TURN_DEGREES_PER_UNIT
		val yawError = Mth.wrapDegrees(desired.yaw - player.yRot).toDouble()
		val pitchError = (desired.pitch.coerceIn(MIN_PITCH, MAX_PITCH) - player.xRot).toDouble()
		val limited = clampMagnitude(yawError, pitchError, MAX_ANGULAR_SPEED * frameSeconds)
		val yawCounts = rawCounts(limited.first, degreesPerCount)
		val pitchCounts = rawCounts(limited.second, degreesPerCount)

		if (yawCounts != 0 || pitchCounts != 0) {
			player.turn(yawCounts * mouseScale, pitchCounts * mouseScale)
		}
		return currentRotation(player)
	}

	fun clear() {
		lastAppliedAtNs = 0L
	}

	private fun currentRotation(player: LocalPlayer): Rotation =
		Rotation(player.yRot, player.xRot.coerceIn(MIN_PITCH, MAX_PITCH))

	private fun clampMagnitude(yaw: Double, pitch: Double, maximum: Double): Pair<Double, Double> {
		val magnitude = sqrt(yaw * yaw + pitch * pitch)
		if (magnitude <= maximum || magnitude <= 0.000_001) {
			return yaw to pitch
		}
		val scale = maximum / magnitude
		return yaw * scale to pitch * scale
	}

	internal companion object {
		private const val MIN_PITCH = -90.0f
		private const val MAX_PITCH = 90.0f
		private const val TURN_DEGREES_PER_UNIT = 0.15
		private const val MAX_ANGULAR_SPEED = 540.0
		private const val DEFAULT_FRAME_SECONDS = 1.0 / 60.0
		private const val MIN_FRAME_SECONDS = 1.0 / 1000.0
		private const val MAX_FRAME_SECONDS = 1.0 / 20.0
		private const val NANOS_PER_SECOND = 1_000_000_000.0

		fun mouseScale(sensitivity: Double): Double {
			val base = sensitivity * 0.6000000238418579 + 0.20000000298023224
			return base * base * base * 8.0
		}

		fun degreesPerCount(sensitivity: Double): Double =
			mouseScale(sensitivity) * TURN_DEGREES_PER_UNIT

		fun rawCounts(deltaDegrees: Double, degreesPerCount: Double): Int {
			if (!deltaDegrees.isFinite() || !degreesPerCount.isFinite() || degreesPerCount <= 0.0) {
				return 0
			}
			return (deltaDegrees / degreesPerCount).roundToInt()
		}
	}
}
