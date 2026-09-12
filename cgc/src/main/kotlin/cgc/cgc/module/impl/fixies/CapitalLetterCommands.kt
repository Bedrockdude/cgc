package cgc.cgc.module.impl.fixies

import cgc.cgc.client.CgcCommandRegistry
import cgc.cgc.config.CgcSettings
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import java.util.Locale

class CapitalLetterCommands : CgcModule(
	id = "CapitalLetterCommands",
	displayName = "Capital Letter Commands",
	category = ModuleCategory.FIXIES,
	description = "Lowercases command words while preserving free-text command arguments.",
	defaultEnabled = false
) {
	init {
		instance = this
	}

	private fun normalize(message: String): String {
		val prefix = commandPrefix(message) ?: return message
		val tokens = commandTokens(message, prefix.length)
		if (tokens.isEmpty()) {
			return message
		}

		val literalIndexes = linkedSetOf(0)
		var textStart = knownTextStart(tokens)
		dispatcher(prefix.custom)?.let { dispatcher ->
			dispatcherTextStart(tokens, dispatcher, literalIndexes)?.let { textStart = minOf(textStart, it) }
		}
		markKnownLiteralTokens(tokens, literalIndexes, textStart)

		val builder = StringBuilder(message)
		var offset = 0
		for ((index, token) in tokens.withIndex()) {
			if (index >= textStart || index !in literalIndexes) {
				continue
			}

			val lower = token.value.lowercase(Locale.ROOT)
			if (lower == token.value) {
				continue
			}

			builder.replace(token.start + offset, token.end + offset, lower)
			offset += lower.length - token.value.length
		}
		return builder.toString()
	}

	private fun dispatcher(custom: Boolean): CommandDispatcher<ClientSuggestionProvider>? =
		if (custom) {
			CgcCommandRegistry.getDispatcher()
		} else {
			Minecraft.getInstance().player?.connection?.commands
		}

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

			val argumentChildren = children.filterIsInstance<ArgumentCommandNode<ClientSuggestionProvider, *>>()
			if (argumentChildren.any(::isTextArgument)) {
				return index
			}
			if (argumentChildren.isEmpty()) {
				return null
			}

			children = argumentChildren.flatMap { it.children }
		}
		return null
	}

	private fun findLiteral(children: Collection<CommandNode<ClientSuggestionProvider>>, value: String): CommandNode<ClientSuggestionProvider>? =
		children
			.filterIsInstance<LiteralCommandNode<ClientSuggestionProvider>>()
			.firstOrNull { it.literal.equals(value, ignoreCase = true) }

	private fun isTextArgument(node: ArgumentCommandNode<ClientSuggestionProvider, *>): Boolean {
		val type = node.type
		return type is StringArgumentType && type.type != StringArgumentType.StringType.SINGLE_WORD
	}

	private fun knownTextStart(tokens: List<Token>): Int {
		val root = tokens[0].lower
		if (root in DIRECT_TEXT_COMMANDS) {
			return 1
		}
		if (root in PLAYER_TEXT_COMMANDS) {
			return 2
		}
		if (tokens.size >= 2 && root in GROUP_COMMANDS && tokens[1].lower in GROUP_TEXT_SUBCOMMANDS) {
			return 2
		}
		return Int.MAX_VALUE
	}

	private fun markKnownLiteralTokens(tokens: List<Token>, literalIndexes: MutableSet<Int>, textStart: Int) {
		if (tokens.size < 2 || textStart <= 1) {
			return
		}

		val root = tokens[0].lower
		val second = tokens[1].lower
		if (root == "chat" || root in GROUP_COMMANDS || (root in SUBCOMMAND_ROOTS && second in KNOWN_SUBCOMMANDS)) {
			literalIndexes.add(1)
		}
	}

	private fun commandPrefix(message: String): Prefix? {
		if (message.startsWith("/")) {
			return Prefix(1, custom = false)
		}

		val customPrefix = CgcSettings.commandPrefix.value
		return if (customPrefix.isNotBlank() && message.startsWith(customPrefix)) {
			Prefix(customPrefix.length, custom = true)
		} else {
			null
		}
	}

	private fun commandTokens(message: String, startIndex: Int): List<Token> {
		val tokens = arrayListOf<Token>()
		var index = startIndex
		while (index < message.length) {
			while (index < message.length && message[index].isWhitespace()) {
				index++
			}
			if (index >= message.length) {
				break
			}

			val start = index
			while (index < message.length && !message[index].isWhitespace()) {
				index++
			}
			tokens.add(Token(start, index, message.substring(start, index)))
		}
		return tokens
	}

	private data class Prefix(val length: Int, val custom: Boolean)
	private data class Token(val start: Int, val end: Int, val value: String) {
		val lower: String = value.lowercase(Locale.ROOT)
	}

	companion object {
		private var instance: CapitalLetterCommands? = null

		private val DIRECT_TEXT_COMMANDS = setOf(
			"achat",
			"allchat",
			"gc",
			"gchat",
			"guildchat",
			"oc",
			"officerchat",
			"pc",
			"pchat",
			"partychat",
			"r",
			"reply",
			"shout"
		)
		private val PLAYER_TEXT_COMMANDS = setOf("msg", "m", "tell", "t", "w", "whisper", "pm", "message")
		private val GROUP_COMMANDS = setOf("p", "party", "g", "guild")
		private val GROUP_TEXT_SUBCOMMANDS = setOf("c", "chat")
		private val SUBCOMMAND_ROOTS = setOf("ac", "cgc", "coop", "is", "island", "lc", "leapcounter", "sb", "skyblock")
		private val KNOWN_SUBCOMMANDS = setOf(
			"accept",
			"add",
			"allinvite",
			"chat",
			"clear",
			"create",
			"delete",
			"demote",
			"deny",
			"disband",
			"help",
			"invite",
			"join",
			"kick",
			"leave",
			"list",
			"mute",
			"private",
			"promote",
			"public",
			"remove",
			"settings",
			"toggle",
			"transfer",
			"undo",
			"unmute",
			"warp"
		)

		@JvmStatic
		fun normalizeInput(message: String): String =
			instance?.takeIf { it.enabled }?.normalize(message) ?: message
	}
}
