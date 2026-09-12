package cgc.cgc.module.impl.general

import cgc.cgc.client.CgcCommandRegistry
import cgc.cgc.config.CgcSettings
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.setting.ButtonSetting
import cgc.cgc.module.setting.PlayerNameAliasListSetting
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.Locale

class PartyNamesTweaks : CgcModule(
	id = "party-names-tweaks",
	displayName = "Party Names Tweaks",
	category = ModuleCategory.GENERAL,
	description = "Lets configured short names stand in for player usernames in commands.",
	defaultEnabled = false
) {
	private val names = PlayerNameAliasListSetting("Names")
	private val addName = ButtonSetting("Add Name", "Add Name", { names.addAlias() })

	init {
		instance = this
		registerProperty(addName, names)
	}

	private fun resolve(message: String): String {
		val prefix = commandPrefix(message) ?: return message
		val aliases = names.configuredAliases().associateBy { it.alias.lowercase(Locale.ROOT) }
		if (aliases.isEmpty()) return message

		val tokens = commandTokens(message, prefix.length)
		if (tokens.size < 2) return message

		val literalIndexes = linkedSetOf(0)
		var textStart = knownTextStart(tokens)
		dispatcher(prefix.custom)?.let { dispatcher ->
			dispatcherTextStart(tokens, dispatcher, literalIndexes)?.let { textStart = minOf(textStart, it) }
		}
		markKnownLiteralTokens(tokens, literalIndexes, textStart)

		return replaceAliasTokens(
			message,
			tokens,
			aliases.mapValues { it.value.username },
			literalIndexes,
			textStart
		)
	}

	private fun dispatcher(custom: Boolean): CommandDispatcher<ClientSuggestionProvider>? =
		if (custom) CgcCommandRegistry.getDispatcher() else Minecraft.getInstance().player?.connection?.commands

	private fun dispatcherTextStart(
		tokens: List<Token>,
		dispatcher: CommandDispatcher<ClientSuggestionProvider>,
		literalIndexes: MutableSet<Int>
	): Int? {
		var children = dispatcher.root.children
		for ((index, token) in tokens.withIndex()) {
			val literal = findLiteral(children, token.value)
			if (literal != null) {
				literalIndexes.add(index)
				children = literal.children
				continue
			}

			val arguments = children.filterIsInstance<ArgumentCommandNode<ClientSuggestionProvider, *>>()
			// Many proxy/plugin commands expose their entire argument tail as one greedy string.
			// At the first argument that does not tell us whether it is chat text or a player slot,
			// so the explicit chat-command rules below remain authoritative there.
			if (index > 1 && arguments.any(::isFreeTextArgument)) return index
			if (arguments.isEmpty()) return null
			children = arguments.flatMap { it.children }
		}
		return null
	}

	private fun findLiteral(
		children: Collection<CommandNode<ClientSuggestionProvider>>,
		value: String
	): CommandNode<ClientSuggestionProvider>? =
		children.filterIsInstance<LiteralCommandNode<ClientSuggestionProvider>>()
			.firstOrNull { it.literal.equals(value, ignoreCase = true) }

	private fun isFreeTextArgument(node: ArgumentCommandNode<ClientSuggestionProvider, *>): Boolean {
		val type = node.type
		return type is StringArgumentType && type.type != StringArgumentType.StringType.SINGLE_WORD
	}

	private fun commandPrefix(message: String): Prefix? {
		if (message.startsWith('/')) return Prefix(1, custom = false)
		val customPrefix = CgcSettings.commandPrefix.value
		return if (customPrefix.isNotBlank() && message.startsWith(customPrefix)) {
			Prefix(customPrefix.length, custom = true)
		} else null
	}

	private fun knownTextStart(tokens: List<Token>): Int {
		val root = tokens[0].lower
		if (root in DIRECT_TEXT_COMMANDS) return 1
		if (root in PLAYER_TEXT_COMMANDS) return 2
		if (tokens.size >= 2 && root in GROUP_COMMANDS && tokens[1].lower in GROUP_TEXT_SUBCOMMANDS) return 2
		return Int.MAX_VALUE
	}

	private fun markKnownLiteralTokens(tokens: List<Token>, literalIndexes: MutableSet<Int>, textStart: Int) {
		if (tokens.size < 2 || textStart <= 1) return
		val root = tokens[0].lower
		if ((root in GROUP_COMMANDS || root in SUBCOMMAND_ROOTS) && tokens[1].lower in KNOWN_SUBCOMMANDS) {
			literalIndexes.add(1)
		}
	}

	private fun addSuggestions(input: String, suggestions: Suggestions): Suggestions {
		if (suggestions.list.isEmpty()) return suggestions
		val canonicalSuggestions = suggestions.list.mapTo(hashSetOf()) { it.text.lowercase(Locale.ROOT) }
		val range = suggestions.range
		val typed = input.substring(range.start.coerceAtMost(input.length), range.end.coerceAtMost(input.length))
		val extras = names.configuredAliases()
			.asSequence()
			.filter { it.username.lowercase(Locale.ROOT) in canonicalSuggestions }
			.filter { it.alias.startsWith(typed, ignoreCase = true) }
			.filterNot { alias -> suggestions.list.any { it.text.equals(alias.alias, ignoreCase = true) } }
			.map { Suggestion(range, it.alias, Component.literal(it.username)) }
			.toList()
		if (extras.isEmpty()) return suggestions
		return Suggestions(range, (suggestions.list + extras).sortedBy { it.text.lowercase(Locale.ROOT) })
	}

	private data class Prefix(val length: Int, val custom: Boolean)
	private data class Token(val start: Int, val end: Int, val value: String) {
		val lower: String = value.lowercase(Locale.ROOT)
	}

	companion object {
		private var instance: PartyNamesTweaks? = null

		private val DIRECT_TEXT_COMMANDS = setOf(
			"ac", "achat", "allchat", "cc", "coopchat", "gc", "gchat", "guildchat", "me",
			"oc", "officerchat", "pc", "pchat", "partychat", "r", "reply", "say", "shout"
		)
		private val PLAYER_TEXT_COMMANDS = setOf("msg", "m", "tell", "t", "w", "whisper", "pm", "message")
		private val GROUP_COMMANDS = setOf("p", "party", "g", "guild")
		private val GROUP_TEXT_SUBCOMMANDS = setOf("c", "chat")
		private val SUBCOMMAND_ROOTS = setOf("ac", "cgc", "coop", "is", "island", "lc", "leapcounter", "sb", "skyblock")
		private val KNOWN_SUBCOMMANDS = setOf(
			"accept", "add", "allinvite", "chat", "clear", "create", "delete", "demote", "deny",
			"disband", "help", "invite", "join", "kick", "leave", "list", "mute", "private",
			"promote", "public", "remove", "settings", "toggle", "transfer", "undo", "unmute", "warp"
		)

		private fun commandTokens(message: String, startIndex: Int): List<Token> {
			val tokens = arrayListOf<Token>()
			var index = startIndex
			while (index < message.length) {
				while (index < message.length && message[index].isWhitespace()) index++
				if (index >= message.length) break
				val start = index
				while (index < message.length && !message[index].isWhitespace()) index++
				tokens.add(Token(start, index, message.substring(start, index)))
			}
			return tokens
		}

		private fun replaceAliasTokens(
			message: String,
			tokens: List<Token>,
			aliases: Map<String, String>,
			literalIndexes: Set<Int>,
			textStart: Int
		): String {
			val builder = StringBuilder(message)
			var offset = 0
			for ((index, token) in tokens.withIndex()) {
				if (index == 0 || index >= textStart || index in literalIndexes) continue
				val username = aliases[token.lower] ?: continue
				builder.replace(token.start + offset, token.end + offset, username)
				offset += username.length - token.value.length
			}
			return builder.toString()
		}

		internal fun resolveTokenAliasesForTest(
			message: String,
			prefixLength: Int,
			aliases: Map<String, String>,
			literalIndexes: Set<Int> = setOf(0),
			textStart: Int = Int.MAX_VALUE
		): String = replaceAliasTokens(
			message,
			commandTokens(message, prefixLength),
			aliases.mapKeys { it.key.lowercase(Locale.ROOT) },
			literalIndexes,
			textStart
		)

		@JvmStatic
		fun resolveInput(message: String): String =
			instance?.takeIf { it.enabled }?.resolve(message) ?: message

		@JvmStatic
		fun augmentSuggestions(input: String, suggestions: Suggestions): Suggestions =
			instance?.takeIf { it.enabled }?.addSuggestions(input, suggestions) ?: suggestions
	}
}
