package cgc.cgc.module.impl.dungeon.autopuzzles.blaze

import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleRoomCoordinates
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.phys.AABB

object BlazeSolver {
	data class Target(val entity: Blaze, val maximumHealth: Int)
	internal data class Health(val current: Int, val maximum: Int)

	fun scanAreaLoaded(context: AutoPuzzleContext): Boolean =
		AutoPuzzleRoomCoordinates.horizontalChunksLoaded(
			context.level,
			context.room.mainX - SCAN_RADIUS,
			context.room.mainX + SCAN_RADIUS,
			context.room.mainZ - SCAN_RADIUS,
			context.room.mainZ + SCAN_RADIUS
		)

	fun observe(context: AutoPuzzleContext, reversed: Boolean): List<Target> {
		val used = hashSetOf<Int>()
		val result = arrayListOf<Target>()
		val min = AutoPuzzleRoomCoordinates.worldPoint(context.room, -18.0, 20.0, -18.0)
		val max = AutoPuzzleRoomCoordinates.worldPoint(context.room, 18.0, 125.0, 18.0)
		val scanBox = AABB(min, max).inflate(2.0)
		for (stand in context.level.getEntitiesOfClass(ArmorStand::class.java, scanBox)) {
			val health = parseHealth(stand.customName?.string ?: continue) ?: continue
			if (health.current <= 0) continue
			val blaze = context.level.getEntitiesOfClass(
				Blaze::class.java,
				stand.boundingBox.expandTowards(0.0, -2.0, 0.0)
			).firstOrNull { it.isAlive && !it.isDeadOrDying && !it.isRemoved && used.add(it.id) } ?: continue
			result.add(Target(blaze, health.maximum))
		}
		return if (reversed) result.sortedByDescending { it.maximumHealth } else result.sortedBy { it.maximumHealth }
	}

	internal fun parseHealth(name: String): Health? {
		val match = HEALTH_PATTERN.find(name) ?: return null
		val current = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
		val maximum = match.groupValues[2].replace(",", "").toIntOrNull() ?: return null
		return Health(current, maximum)
	}

	internal fun parseMaximumHealth(name: String): Int? = parseHealth(name)?.maximum

	private val HEALTH_PATTERN = Regex("^\\[Lv15].*?Blaze ([\\d,]+)/([\\d,]+)❤$")
	private const val SCAN_RADIUS = 20
}
