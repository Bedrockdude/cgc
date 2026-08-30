package cgc.cgc.module.impl.dungeon

import cgc.cgc.runtime.PhysicalInputTracker
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

internal enum class AutoSSSafetyViolation {
	PLAYER_MOVED,
	UNAUTHORIZED_CAMERA_MOVEMENT
}

/**
 * Latches the position and camera state owned by an Auto SS run. Camera changes
 * are accepted only when accompanied by a recent raw mouse callback or when the
 * solver explicitly records the rotation it applied itself.
 */
internal class AutoSSSafetyInterlock {
	var startingPosition: Vec3? = null
		private set
	var expectedRotation: Rotation? = null
		private set
	private var acceptedMouseMoveSequence = 0L

	fun arm(position: Vec3, rotation: Rotation, input: PhysicalInputTracker.Snapshot) {
		startingPosition = position
		expectedRotation = rotation
		acceptedMouseMoveSequence = input.mouseMoveSequence
	}

	fun check(
		position: Vec3,
		rotation: Rotation,
		input: PhysicalInputTracker.Snapshot,
		nowMs: Long
	): AutoSSSafetyViolation? {
		val start = startingPosition ?: return null
		if (position.distanceToSqr(start) > POSITION_TOLERANCE_SQ) {
			return AutoSSSafetyViolation.PLAYER_MOVED
		}

		val expected = expectedRotation ?: return null
		if (rotationErrorDegrees(rotation, expected) <= ROTATION_TOLERANCE_DEGREES) {
			return null
		}

		val recentPhysicalMouseMovement = input.mouseMoveSequence != acceptedMouseMoveSequence &&
			input.lastMouseMoveAtMs in (nowMs - PHYSICAL_MOUSE_AUTHORIZATION_MS)..nowMs
		if (!recentPhysicalMouseMovement) {
			return AutoSSSafetyViolation.UNAUTHORIZED_CAMERA_MOVEMENT
		}

		expectedRotation = rotation
		acceptedMouseMoveSequence = input.mouseMoveSequence
		return null
	}

	fun recordSolverRotation(rotation: Rotation) {
		if (startingPosition != null) {
			expectedRotation = rotation
		}
	}

	fun clear() {
		startingPosition = null
		expectedRotation = null
		acceptedMouseMoveSequence = 0L
	}

	private fun rotationErrorDegrees(actual: Rotation, expected: Rotation): Double {
		val yaw = Mth.wrapDegrees(actual.yaw - expected.yaw).toDouble()
		val pitch = (actual.pitch - expected.pitch).toDouble()
		return sqrt(yaw * yaw + pitch * pitch)
	}

	private companion object {
		// Ignore only sub-micrometre floating-point noise; any real displacement aborts.
		const val POSITION_TOLERANCE_SQ = 1.0E-12
		const val ROTATION_TOLERANCE_DEGREES = 1.0E-4
		const val PHYSICAL_MOUSE_AUTHORIZATION_MS = 100L
	}
}
