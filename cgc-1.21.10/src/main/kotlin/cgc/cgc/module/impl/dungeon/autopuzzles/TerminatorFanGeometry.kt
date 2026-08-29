package cgc.cgc.module.impl.dungeon.autopuzzles

import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

internal enum class TerminatorFanLane {
	CENTER,
	LEFT,
	RIGHT
}

internal data class TerminatorFanDirection(
	val lane: TerminatorFanLane,
	val direction: Vec3
)

/**
 * The confirmed deterministic Terminator fan. The center arrow follows the
 * ordinary shortbow direction; the other arrows use equal five-degree yaw
 * offsets while retaining the center arrow's pitch.
 */
internal object TerminatorFanGeometry {
	const val FAN_ANGLE_DEGREES = 5.0

	fun directions(centerDirection: Vec3): List<TerminatorFanDirection> {
		val center = centerDirection.normalize()
		return listOf(
			TerminatorFanDirection(TerminatorFanLane.CENTER, center),
			TerminatorFanDirection(TerminatorFanLane.LEFT, rotateYaw(center, -FAN_ANGLE_DEGREES)),
			TerminatorFanDirection(TerminatorFanLane.RIGHT, rotateYaw(center, FAN_ANGLE_DEGREES))
		)
	}

	fun cameraDirectionForLaneHit(targetDirection: Vec3, lane: TerminatorFanLane): Vec3 =
		rotateYaw(targetDirection.normalize(), -offsetDegrees(lane))

	private fun offsetDegrees(lane: TerminatorFanLane): Double = when (lane) {
		TerminatorFanLane.CENTER -> 0.0
		TerminatorFanLane.LEFT -> -FAN_ANGLE_DEGREES
		TerminatorFanLane.RIGHT -> FAN_ANGLE_DEGREES
	}

	private fun rotateYaw(direction: Vec3, offsetDegrees: Double): Vec3 {
		val radians = Math.toRadians(offsetDegrees)
		val cosine = cos(radians)
		val sine = sin(radians)
		return Vec3(
			direction.x * cosine - direction.z * sine,
			direction.y,
			direction.x * sine + direction.z * cosine
		).normalize()
	}
}
