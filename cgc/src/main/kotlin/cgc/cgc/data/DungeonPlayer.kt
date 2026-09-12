package cgc.cgc.data

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import java.util.UUID

class DungeonPlayer(
	var dungeonClass: DungeonClass,
	player: Player,
	var level: Int,
	var secrets: Int = 0
) {
	val name: String = player.name.string
	private val uuid: UUID = player.gameProfile.id
	var player: Player = player
		private set

	fun findPlayer(): Player? {
		val found = Minecraft.getInstance().level?.getPlayerByUUID(uuid)
		if (found != null) {
			player = found
		}
		return player
	}

	fun update(clazz: DungeonClass, level: Int) {
		if (clazz == DungeonClass.NONE || level == 0) {
			return
		}

		dungeonClass = clazz
		this.level = level
	}

	override fun equals(other: Any?): Boolean =
		other is DungeonPlayer && name.equals(other.name, ignoreCase = true)

	override fun hashCode(): Int =
		name.lowercase().hashCode()

	override fun toString(): String =
		"DungeonPlayer{dungeonClass=$dungeonClass,name=$name,level=$level}"
}
