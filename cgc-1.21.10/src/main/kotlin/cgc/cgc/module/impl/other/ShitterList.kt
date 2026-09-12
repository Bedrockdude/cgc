package cgc.cgc.module.impl.other

import cgc.cgc.module.CgcModule
import cgc.cgc.module.ChatMessageModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.shitterlist.MinecraftProfile
import cgc.cgc.shitterlist.ShitterListService
import cgc.cgc.utils.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ShitterList : CgcModule(
	id = "ShitterList",
	displayName = "Shitter List",
	category = ModuleCategory.OTHER,
	description = "Automatically kicks listed players who join through Party Finder.",
	defaultEnabled = false
), ChatMessageModule, WorldLoadModule {
	private val kickAttempts = ConcurrentHashMap<String, Long>()

	override fun onChatMessage(message: String) {
		val name = partyFinderJoinName(message) ?: return
		val key = name.lowercase(Locale.ROOT)
		val now = System.currentTimeMillis()
		val previousAttempt = kickAttempts.put(key, now)
		if (previousAttempt != null && now - previousAttempt < KICK_DEDUPLICATION_MS) return

		ShitterListService.findByName(name)?.let { entry ->
			kickIfStillListed(name, entry.uuid)
			return
		}

		ShitterListService.resolveName(name).thenAccept { profile ->
			if (profile != null && ShitterListService.isListed(profile.uuid)) {
				kickIfStillListed(name, profile)
			}
		}
	}

	override fun onDisable() {
		kickAttempts.clear()
	}

	override fun onWorldLoad() {
		kickAttempts.clear()
	}

	private fun kickIfStillListed(name: String, profile: MinecraftProfile) {
		kickIfStillListed(name, profile.uuid)
	}

	private fun kickIfStillListed(name: String, uuid: java.util.UUID) {
		Minecraft.getInstance().execute {
			if (!enabled || !ShitterListService.isListed(uuid)) return@execute
			val connection = Minecraft.getInstance().connection ?: return@execute
			connection.sendCommand("party kick $name")
			ChatUtils.chat(
				"${ChatFormatting.RED}Shitter List » ${ChatFormatting.RESET}Auto-kicked $name from Party Finder."
			)
		}
	}

	companion object {
		private const val KICK_DEDUPLICATION_MS = 10_000L
		private val DUNGEON_JOIN = Regex(
			"^Party Finder > ([A-Za-z0-9_]{1,16}) joined the dungeon group! \\([A-Za-z]+ Level \\d+\\)$"
		)
		private val GROUP_JOIN = Regex(
			"^Party Finder > (?:(?:\\[[^]]+]) )?([A-Za-z0-9_]{1,16}) joined the group! \\(Combat Level \\d+\\)$"
		)

		internal fun partyFinderJoinName(message: String): String? =
			DUNGEON_JOIN.matchEntire(message)?.groupValues?.get(1)
				?: GROUP_JOIN.matchEntire(message)?.groupValues?.get(1)
	}
}
