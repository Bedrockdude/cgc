package cgc.cgc.location

import net.minecraft.ChatFormatting
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import java.util.regex.Pattern

object Location {
	private val teamPattern = Pattern.compile("^team_(\\d+)$")

	@JvmStatic
	var inSkyblock: Boolean = false
		private set

	@JvmStatic
	var floor: Floor = Floor.NONE
		private set

	@JvmStatic
	var area: Island = Island.UNKNOWN
		private set

	@JvmStatic
	fun reset() {
		inSkyblock = false
		floor = Floor.NONE
		area = Island.UNKNOWN
	}

	@JvmStatic
	fun handleObjective(packet: ClientboundSetObjectivePacket) {
		val text = strip(packet.displayName.string)
		if (text.contains("SKYBLOCK", ignoreCase = true)) {
			inSkyblock = true
		}
	}

	@JvmStatic
	fun handleSetScore(packet: ClientboundSetScorePacket) {
		handleScoreboardLine(strip(packet.owner()))
	}

	@JvmStatic
	fun handleSetPlayerTeam(packet: ClientboundSetPlayerTeamPacket) {
		val params = packet.parameters.orElse(null) ?: return
		if (!teamPattern.matcher(packet.name).find()) return

		val formatted = params.playerPrefix.string + params.playerSuffix.string
		handleScoreboardLine(strip(formatted))
	}

	@JvmStatic
	fun handlePlayerInfo(packet: ClientboundPlayerInfoUpdatePacket) {
		if (!inSkyblock) return

		for (entry in packet.entries()) {
			val display = entry.displayName() ?: continue
			val text = strip(display.string.trim())
			if (text.startsWith("Area: ") || text.startsWith("Dungeon: ")) {
				setArea(Island.findByName(text))
			}
		}
	}

	@JvmStatic
	fun noteDungeonBossChat(message: String) {
		if (message.startsWith("[BOSS] Maxor:")
			|| message.startsWith("[BOSS] Storm:")
			|| message.startsWith("[BOSS] Goldor:")
			|| message.startsWith("[BOSS] Necron:")
			|| message.startsWith("[BOSS] Wither King:")
		) {
			inSkyblock = true
			setArea(Island.DUNGEON)
			if (floor == Floor.NONE) {
				floor = Floor.F7
			}
		}
	}

	private fun handleScoreboardLine(text: String) {
		if (text.contains("The Catacombs")) {
			inSkyblock = true
			setArea(Island.DUNGEON)
			extractFloor(text)?.let { floor = it }
		} else if (text.contains("Time Elapsed: ") && area == Island.DUNGEON) {
			inSkyblock = true
		}
	}

	private fun setArea(newArea: Island) {
		if (newArea != Island.UNKNOWN) {
			area = newArea
		}
	}

	private fun extractFloor(text: String): Floor? {
		val start = text.indexOf('(')
		val end = text.indexOf(')', start + 1)
		if (start < 0 || end <= start) return null
		return Floor.findByName(text.substring(start + 1, end)).takeUnless { it == Floor.NONE }
	}

	private fun strip(text: String): String =
		ChatFormatting.stripFormatting(text) ?: text
}
