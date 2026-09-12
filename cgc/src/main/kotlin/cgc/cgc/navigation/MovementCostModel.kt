package cgc.cgc.navigation

import net.minecraft.util.Mth
import kotlin.math.abs

data class MovementCostModel(
	val walkBlockMs: Double = 145.0,
	val jumpMs: Double = 235.0,
	val etherwarpUseMs: Double = 285.0,
	val degreesPerSecond: Double = 420.0,
	val actionSwitchMs: Double = 45.0
) {
	fun rotationMs(fromYaw: Float, toYaw: Float): Double =
		abs(Mth.wrapDegrees(toYaw - fromYaw)) / degreesPerSecond * 1000.0

	fun rotationMs(fromYaw: Float, fromPitch: Float, toYaw: Float, toPitch: Float): Double {
		val yaw = Mth.wrapDegrees(toYaw - fromYaw).toDouble()
		val pitch = (toPitch - fromPitch).toDouble()
		return kotlin.math.sqrt(yaw * yaw + pitch * pitch) / degreesPerSecond * 1000.0
	}

	fun switchMs(previous: NavigationActionType?, next: NavigationActionType): Double =
		if (previous == null || previous == next || previous.isWalk && next.isWalk) 0.0 else actionSwitchMs

	private val NavigationActionType.isWalk: Boolean
		get() = this == NavigationActionType.WALK || this == NavigationActionType.ASCEND || this == NavigationActionType.DESCEND
}
