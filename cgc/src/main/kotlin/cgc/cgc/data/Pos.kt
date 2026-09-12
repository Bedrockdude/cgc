package cgc.cgc.data

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

data class Pos(
	var x: Double = 0.0,
	var y: Double = 0.0,
	var z: Double = 0.0
) {
	constructor(pos: BlockPos) : this(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
	constructor(vec: Vec3) : this(vec.x, vec.y, vec.z)

	fun asBlockPos(): BlockPos =
		BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))

	fun asVec3(): Vec3 =
		Vec3(x, y, z)

	fun squaredDistanceTo(pos: Vec3): Double {
		val dx = pos.x - x
		val dy = pos.y - y
		val dz = pos.z - z
		return dx * dx + dy * dy + dz * dz
	}

	fun toChatString(): String =
		"$x,$y,$z"
}
