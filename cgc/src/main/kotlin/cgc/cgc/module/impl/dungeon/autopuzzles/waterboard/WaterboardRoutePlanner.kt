package cgc.cgc.module.impl.dungeon.autopuzzles.waterboard

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

object WaterboardRoutePlanner {
	data class Walk(val target: Vec3)

	fun safeStraightWalk(level: ClientLevel, player: LocalPlayer, target: Vec3): Walk? {
		val start = player.position()
		val dx = target.x - start.x
		val dz = target.z - start.z
		val horizontal = kotlin.math.sqrt(dx * dx + dz * dz)
		if (horizontal <= ARRIVAL_DISTANCE || horizontal > MAX_WALK_DISTANCE || kotlin.math.abs(target.y - start.y) > MAX_Y_DELTA) {
			return null
		}
		val samples = ceil(horizontal / SAMPLE_STEP).toInt().coerceAtLeast(1)
		for (index in 1..samples) {
			val amount = index.toDouble() / samples
			val sample = Vec3(start.x + dx * amount, start.y + (target.y - start.y) * amount, start.z + dz * amount)
			val movedBox = player.boundingBox.move(sample.x - start.x, sample.y - start.y, sample.z - start.z)
			if (!level.noCollision(player, movedBox)) return null
			for ((ox, oz) in FOOTPRINT_OFFSETS) {
				val support = BlockPos.containing(sample.x + ox, sample.y - SUPPORT_PROBE, sample.z + oz)
				if (level.getBlockState(support).getCollisionShape(level, support).isEmpty) return null
			}
		}
		return Walk(target)
	}

	private val FOOTPRINT_OFFSETS = listOf(-0.22 to -0.22, -0.22 to 0.22, 0.22 to -0.22, 0.22 to 0.22)
	private const val MAX_WALK_DISTANCE = 3.0
	private const val MAX_Y_DELTA = 0.45
	private const val SAMPLE_STEP = 0.20
	private const val SUPPORT_PROBE = 0.08
	private const val ARRIVAL_DISTANCE = 0.18
}
