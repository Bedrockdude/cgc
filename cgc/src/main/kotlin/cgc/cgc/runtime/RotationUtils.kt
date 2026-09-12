package cgc.cgc.runtime

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

object RotationUtils {
	fun lookAt(target: Vec3) {
		val player = Minecraft.getInstance().player ?: return
		val eye = player.eyePosition
		val dx = target.x - eye.x
		val dy = target.y - eye.y
		val dz = target.z - eye.z
		val horizontal = sqrt(dx * dx + dz * dz)
		player.yRot = (-Math.toDegrees(atan2(dx, dz))).toFloat()
		player.xRot = (-Math.toDegrees(atan2(dy, horizontal))).toFloat().coerceIn(-90.0f, 90.0f)
	}
}
