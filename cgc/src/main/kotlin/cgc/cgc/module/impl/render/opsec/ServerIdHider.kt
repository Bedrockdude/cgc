package cgc.cgc.module.impl.render.opsec

import cgc.cgc.module.SubModule
import cgc.cgc.module.setting.StringSetting
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import java.util.regex.Pattern

class ServerIdHider(module: OpSec) : SubModule<OpSec>(module, "Server ID Hider", true) {
	private val replacement = StringSetting("Replacement", "", maxLength = 64)

	init {
		registerProperty(replacement)
	}

	fun onPostHandleSetPlayerTeam(packet: ClientboundSetPlayerTeamPacket) {
		if (!module.enabled || !enabled) {
			return
		}

		val params = packet.parameters.orElse(null) ?: return
		if (!TEAM_PATTERN.matcher(packet.name).find()) {
			return
		}

		val unformatted = ChatFormatting.stripFormatting(params.playerPrefix.string + params.playerSuffix.string) ?: return
		val matcher = SERVER_ID.matcher(unformatted)
		if (!matcher.find()) {
			return
		}

		val team = Minecraft.getInstance().connection?.scoreboard()?.getPlayerTeam(packet.name) ?: return
		team.setPlayerPrefix(Component.literal(matcher.group("date")).withStyle(ChatFormatting.GRAY))
		team.setPlayerSuffix(Component.literal(" ${replacement.value}"))
	}

	private companion object {
		private val TEAM_PATTERN = Pattern.compile("^team_(\\d+)$")
		private val SERVER_ID = Pattern.compile("(?<date>\\d{2}/\\d{2}/\\d{2}) (?<server>[Mm]\\d{1,4}[A-Z]{1,4})")
	}
}
