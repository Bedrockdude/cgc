package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.data.Pos
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

object AutoCNodeUtils {
	fun lookedBlock(player: LocalPlayer, maxDistance: Double = 61.0): LookedBlock? {
		val hit = player.pick(maxDistance, 0.0f, false)
		if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) {
			return null
		}

		val rotation = rotationTo(player.eyePosition, hit.location)
		return LookedBlock(hit.blockPos, hit.location, rotation.yaw, rotation.pitch)
	}

	fun rotationTo(from: Vec3, target: Vec3): AutoCRotation {
		val dx = target.x - from.x
		val dy = target.y - from.y
		val dz = target.z - from.z
		val horizontal = sqrt(dx * dx + dz * dz)
		return AutoCRotation(
			yaw = (-Math.toDegrees(atan2(dx, dz))).toFloat(),
			pitch = (-Math.toDegrees(atan2(dy, horizontal))).toFloat().coerceIn(-90.0f, 90.0f)
		)
	}

	fun writePos(pos: Pos): com.google.gson.JsonObject {
		val obj = com.google.gson.JsonObject()
		obj.addProperty("x", pos.x)
		obj.addProperty("y", pos.y)
		obj.addProperty("z", pos.z)
		return obj
	}
}

data class AutoCRotation(val yaw: Float, val pitch: Float)

data class LookedBlock(
	val blockPos: BlockPos,
	val hitPos: Vec3,
	val yaw: Float,
	val pitch: Float
)
