package cgc.cgc.client

import cgc.cgc.config.CgcSettings
import cgc.cgc.client.gui.AutoCEditScreen
import cgc.cgc.client.gui.CgcConfigScreen
import cgc.cgc.client.gui.CgcUiScreen
import cgc.cgc.module.CgcModules
import cgc.cgc.module.impl.dungeon.AutoC
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeType
import cgc.cgc.module.impl.dungeon.LeapCounter
import cgc.cgc.utils.ChatUtils
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ComponentUtils
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CompletableFuture

object CgcCommandRegistry {
	private val AC_COMMON_ARG_SUGGESTIONS = listOf(
		"AS",
		"nr",
		"h1",
		"h2",
		"h3",
		"h4",
		"R(0.5)",
		"R(1)",
		"R(2)",
		"wait(0.5)",
		"wait(1)",
		"maxA(1)",
		"maxA(3)",
		"phaseStart(p1)",
		"phaseStart(p2)",
		"phaseStart(s1)",
		"phaseStart(s2)",
		"phaseStart(s3)",
		"phaseStart(s4)",
		"phaseStart(p4)",
		"phaseStart(p5)"
	)
	private val dispatcher = CommandDispatcher<ClientSuggestionProvider>()
	private val phaseSuggestions = suggestions("p1", "p2", "p3", "p4", "5p")
	private val classSuggestions = suggestions("A", "M", "B", "T", "H", "A M B T H")
	private val autoCNodeSuggestions = suggestions(*AutoCNodeType.commandNames())
	private val fabricAutoCNodeSuggestions = fabricSuggestions(*AutoCNodeType.commandNames())
	private val autoCArgSuggestions = SuggestionProvider<ClientSuggestionProvider> { ctx, builder ->
		suggestAcArgs(StringArgumentType.getString(ctx, "node"), builder)
	}
	private val fabricAutoCArgSuggestions = SuggestionProvider<FabricClientCommandSource> { ctx, builder ->
		suggestAcArgs(StringArgumentType.getString(ctx, "node"), builder)
	}

	init {
		rebuild()
	}

	@JvmStatic
	fun getDispatcher(): CommandDispatcher<ClientSuggestionProvider> =
		dispatcher

	fun registerFabricCommands(
		dispatcher: CommandDispatcher<FabricClientCommandSource>,
		openConfig: () -> Unit,
		openUi: () -> Unit
	) {
			dispatcher.register(
			fabricLiteral("cgc")
				.then(fabricAutoCCommand("ac"))
				.then(fabricLiteral("ui").executes {
					openUi()
					1
				})
				.executes {
					openConfig()
					1
				}
		)
		dispatcher.register(fabricAutoCCommand("ac"))
		registerFabricLeapCounter(dispatcher, "lc")
		registerFabricLeapCounter(dispatcher, "leapcounter")
	}

	fun handlePrefixedChat(message: String): Boolean =
		!tryExecutePrefixed(message)

	@JvmStatic
	fun tryExecutePrefixed(message: String): Boolean {
		val prefix = CgcSettings.commandPrefix.value
		if (prefix.isBlank() || !message.startsWith(prefix)) {
			return false
		}

		val commandLine = normalize(message.substring(prefix.length))
		if (commandLine.isBlank()) {
			return false
		}

		executeLocal(commandLine)
		return true
	}

	@JvmStatic
	fun executeLocal(commandLine: String) {
		val source = Minecraft.getInstance().connection?.suggestionsProvider
		if (source == null) {
			error("You need to be in-world to run CGC commands.")
			return
		}

		val normalized = normalize(commandLine)
		if (normalized.isBlank()) {
			return
		}

		try {
			dispatcher.execute(normalized, source)
		} catch (e: CommandSyntaxException) {
			ChatUtils.chat(ComponentUtils.fromMessage(e.rawMessage))
		} catch (e: Exception) {
			error("Something went wrong running that command.")
		}
	}

	private fun rebuild() {
		dispatcher.register(literal("help").executes {
			usage()
			1
		})
		dispatcher.register(
			literal("cgc")
				.then(autoCCommand("ac"))
				.then(literal("ui").executes {
					Minecraft.getInstance().setScreen(CgcUiScreen())
					1
				})
				.executes {
					Minecraft.getInstance().setScreen(CgcConfigScreen())
					1
				}
		)
		dispatcher.register(autoCCommand("ac"))
		registerLeapCounter("lc")
		registerLeapCounter("leapcounter")
	}

	private fun autoCCommand(name: String): LiteralArgumentBuilder<ClientSuggestionProvider> =
		literal(name)
			.then(
				literal("add")
					.then(
						argument("node", StringArgumentType.word())
							.suggests(autoCNodeSuggestions)
							.then(
								argument("args", StringArgumentType.greedyString())
									.suggests(autoCArgSuggestions)
									.executes { ctx ->
										addAcNode(
											StringArgumentType.getString(ctx, "node"),
											StringArgumentType.getString(ctx, "args")
										)
									}
							)
							.executes { ctx -> addAcNode(StringArgumentType.getString(ctx, "node"), "") }
					)
					.executes {
						acUsage()
						1
					}
			)
			.then(literal("remove").executes { removeAcNode() })
			.then(literal("undo").executes { undoAcNode() })
			.then(literal("edit").executes { editAcNodes() })
			.then(literal("help").executes {
				acUsage()
				1
			})
			.executes {
				acUsage()
				1
			}

	private fun fabricAutoCCommand(name: String): LiteralArgumentBuilder<FabricClientCommandSource> =
		fabricLiteral(name)
			.then(
				fabricLiteral("add")
					.then(
						fabricArgument("node", StringArgumentType.word())
							.suggests(fabricAutoCNodeSuggestions)
							.then(
								fabricArgument("args", StringArgumentType.greedyString())
									.suggests(fabricAutoCArgSuggestions)
									.executes { ctx ->
										addAcNode(
											StringArgumentType.getString(ctx, "node"),
											StringArgumentType.getString(ctx, "args")
										)
									}
							)
							.executes { ctx -> addAcNode(StringArgumentType.getString(ctx, "node"), "") }
					)
					.executes {
						acUsage()
						1
					}
			)
			.then(fabricLiteral("remove").executes { removeAcNode() })
			.then(fabricLiteral("undo").executes { undoAcNode() })
			.then(fabricLiteral("edit").executes { editAcNodes() })
			.then(fabricLiteral("help").executes {
				acUsage()
				1
			})
			.executes {
				acUsage()
				1
			}

	private fun registerLeapCounter(name: String) {
		dispatcher.register(
			literal(name)
				.then(
					literal("add")
						.then(
							argument("phase", StringArgumentType.word())
								.suggests(phaseSuggestions)
								.then(
									argument("radius", DoubleArgumentType.doubleArg(0.1))
										.then(
											argument("classes", StringArgumentType.greedyString())
												.suggests(classSuggestions)
												.executes { ctx ->
													addLcNode(
														StringArgumentType.getString(ctx, "phase"),
														DoubleArgumentType.getDouble(ctx, "radius"),
														StringArgumentType.getString(ctx, "classes")
													)
												}
										)
								)
						)
				)
				.then(
					literal("remove")
						.then(
							argument("index", IntegerArgumentType.integer(0))
								.executes { ctx -> removeLcNode(IntegerArgumentType.getInteger(ctx, "index")) }
						)
						.executes { removeNearestLcNode() }
				)
				.then(literal("rm").executes { removeNearestLcNode() })
				.then(literal("delete").executes { removeNearestLcNode() })
				.then(literal("list").executes { listLcNodes() })
				.executes {
					lcUsage()
					1
				}
		)
	}

	private fun registerFabricLeapCounter(dispatcher: CommandDispatcher<FabricClientCommandSource>, name: String) {
		dispatcher.register(
			fabricLiteral(name)
				.then(
					fabricLiteral("add")
						.then(
							fabricArgument("phase", StringArgumentType.word())
								.suggests(fabricSuggestions("p1", "p2", "p3", "p4", "5p"))
								.then(
									fabricArgument("radius", DoubleArgumentType.doubleArg(0.1))
										.then(
											fabricArgument("classes", StringArgumentType.greedyString())
												.suggests(fabricSuggestions("A", "M", "B", "T", "H", "A M B T H"))
												.executes { ctx ->
													addLcNode(
														StringArgumentType.getString(ctx, "phase"),
														DoubleArgumentType.getDouble(ctx, "radius"),
														StringArgumentType.getString(ctx, "classes")
													)
												}
										)
								)
						)
				)
				.then(
					fabricLiteral("remove")
						.then(
							fabricArgument("index", IntegerArgumentType.integer(0))
								.executes { ctx -> removeLcNode(IntegerArgumentType.getInteger(ctx, "index")) }
						)
						.executes { removeNearestLcNode() }
				)
				.then(fabricLiteral("rm").executes { removeNearestLcNode() })
				.then(fabricLiteral("delete").executes { removeNearestLcNode() })
				.then(fabricLiteral("list").executes { listLcNodes() })
				.executes {
					lcUsage()
					1
				}
		)
	}

	private fun addLcNode(phaseRaw: String, radius: Double, classRaw: String): Int {
		val module = leapCounter() ?: return 0
		val phase = LeapCounter.parsePhase(phaseRaw)
		if (phase.name == "UNKNOWN") {
			error("Invalid LC phase. Use p1, p2, p3, p4, or 5p.")
			return 0
		}

		val classes = LeapCounter.parseClasses(classRaw)
		if (classes.isEmpty()) {
			error("Add at least one class: A M B T H.")
			return 0
		}

		module.addNode(phase, radius, classes)
		return 1
	}

	private fun removeLcNode(index: Int): Int {
		val module = leapCounter() ?: return 0
		if (module.removeIndexed(index)) {
			info("LC: removed node $index.")
			return 1
		}

		error("No LC node exists at index $index.")
		return 0
	}

	private fun removeNearestLcNode(): Int {
		val module = leapCounter() ?: return 0
		val player = Minecraft.getInstance().player ?: return 0
		if (module.removeNearest(player.position())) {
			info("LC: removed nearest node.")
			return 1
		}

		error("No LC nodes to remove.")
		return 0
	}

	private fun listLcNodes(): Int {
		val module = leapCounter() ?: return 0
		val nodes = module.getNodes()
		if (nodes.isEmpty()) {
			info("LC: no nodes.")
			return 1
		}

		info("LC: ${nodes.size} node(s).")
		nodes.take(8).forEachIndexed { index, node ->
			info("$index: ${LeapCounter.phaseName(node.phase)} r=${"%.1f".format(node.radius)} ${node.classList()}")
		}
		return 1
	}

	private fun leapCounter(): LeapCounter? {
		val module = CgcModules.manager.get("LeapCounter") as? LeapCounter
		if (module == null) {
			error("Leap Counter is not registered.")
		}
		return module
	}

	private fun usage() {
		info("Commands: ${CgcSettings.commandPrefix.value}lc, ${CgcSettings.commandPrefix.value}ac.")
	}

	private fun lcUsage() {
		info("LC: use ${CgcSettings.commandPrefix.value}lc add p3 3 A M B T H or ${CgcSettings.commandPrefix.value}lc remove.")
	}

	private fun acUsage() {
		info("AC: use /ac add <${AutoCNodeType.commandNames().joinToString("|")}>, /ac remove, /ac undo, or /ac edit.")
		info("AC args: etherwarp [exactlyPos], strafe <W|A|S|D>, interact <true|false>, crouch <seconds>, wait <seconds>, track <seconds> <x> <y> <z>, command <command>, leap <class>, record <seconds>, break <true|false> <seconds> [notMoving].")
		info("AC modifiers: AS, nr, h<number>, R(number), wait(number), maxA(number), phaseStart(p1|p2|s1|s2|s3|s4|p4|p5). Walk/strafe can use activeFor(number).")
		info("AC stop nodes can use n<node>, for example ncrouch.")
	}

	private fun suggestAcArgs(nodeRaw: String, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
		val remaining = builder.remaining
		val tokenStart = remaining.indexAfterLastWhitespace()
		val previousTokens = remaining
			.substring(0, tokenStart)
			.trim()
			.split(Regex("\\s+"))
			.filter { it.isNotBlank() }
		val tokenBuilder = builder.createOffset(builder.start + tokenStart)
		return SharedSuggestionProvider.suggest(acArgSuggestionsFor(nodeRaw, previousTokens), tokenBuilder)
	}

	private fun acArgSuggestionsFor(nodeRaw: String, previousTokens: List<String>): List<String> {
		val type = AutoCNodeType.byName(nodeRaw)
		val base = when (type) {
			AutoCNodeType.ETHERWARP -> if (previousTokens.any { it.equals("exactlyPos", ignoreCase = true) }) emptyList() else listOf("exactlyPos")
			AutoCNodeType.WALK -> listOf("activeFor(0.5)", "activeFor(1)", "activeFor(2)")
			AutoCNodeType.STRAFE -> {
				val direction = if (previousTokens.any { it.equalsAny("W", "A", "S", "D") }) emptyList() else listOf("W", "A", "S", "D")
				direction + listOf("activeFor(0.5)", "activeFor(1)", "activeFor(2)")
			}
			AutoCNodeType.INTERACT -> if (previousTokens.any { it.equalsAny("true", "false") }) emptyList() else listOf("true", "false")
			AutoCNodeType.LEAP -> if (previousTokens.any { it.isClassArg() }) emptyList() else listOf("A", "Archer", "M", "Mage", "B", "Berserk", "T", "Tank", "H", "Healer")
			AutoCNodeType.BREAK -> when {
				previousTokens.isEmpty() -> listOf("true", "false")
				previousTokens.first().equalsAny("true", "false") && previousTokens.size == 1 -> listOf("1", "2", "3", "5")
				previousTokens.size == 2 -> listOf("notMoving")
				else -> emptyList()
			}
			AutoCNodeType.RECORD -> if (previousTokens.any { it.toDoubleOrNull() != null }) emptyList() else listOf("1", "2", "3", "5")
			AutoCNodeType.CROUCH -> if (previousTokens.any { it.toDoubleOrNull() != null }) emptyList() else listOf("0", "0.25", "0.5", "1")
			AutoCNodeType.WAIT -> if (previousTokens.any { it.toDoubleOrNull() != null }) emptyList() else listOf("0.25", "0.5", "1", "2")
			AutoCNodeType.TRACK -> when (previousTokens.size) {
				0 -> listOf("0.5", "1", "2", "3")
				else -> emptyList()
			}
			AutoCNodeType.STOP -> AutoCNodeType.commandNames()
				.asSequence()
				.filter { !it.equals("stop", ignoreCase = true) }
				.map { "n$it" }
				.filter { suggestion -> previousTokens.none { it.equals(suggestion, ignoreCase = true) } }
				.toList()
			AutoCNodeType.COMMAND -> listOf("pc", "warp")
			else -> emptyList()
		}
		return base + AC_COMMON_ARG_SUGGESTIONS
	}

	private fun String.equalsAny(vararg values: String): Boolean =
		values.any { equals(it, ignoreCase = true) }

	private fun String.isClassArg(): Boolean =
		equalsAny("A", "Archer", "M", "Mage", "B", "Berserk", "T", "Tank", "H", "Healer")

	private fun String.indexAfterLastWhitespace(): Int {
		val index = indexOfLast { it.isWhitespace() }
		return if (index == -1) 0 else index + 1
	}

	private fun addAcNode(nodeRaw: String, args: String): Int {
		val module = autoC() ?: return 0
		val type = AutoCNodeType.byName(nodeRaw)
		if (type == null) {
			error("Invalid AC node type. Use: ${AutoCNodeType.commandNames().joinToString(", ")}.")
			return 0
		}

		return when (val result = module.addNode(type, args)) {
			is AutoC.AddNodeResult.Added -> {
				info("AC: added ${result.node.name()} node at ${result.node.pos.toChatString()}.")
				1
			}
			AutoC.AddNodeResult.PendingCrouch -> {
				info("AC: crouching before capturing the Etherwarp target…")
				1
			}
			AutoC.AddNodeResult.Failed -> {
				error("Failed to add AC ${type.commandName} node. Usage: ${type.usage}.")
				0
			}
		}
	}

	private fun removeAcNode(): Int {
		val module = autoC() ?: return 0
		val removed = module.removeNearest()
		if (removed == null) {
			error("AC: no nodes to remove.")
			return 0
		}

		info("AC: removed ${removed.name()} node.")
		return 1
	}

	private fun undoAcNode(): Int {
		val module = autoC() ?: return 0
		val removed = module.undo()
		if (removed == null) {
			error("AC: no nodes to undo.")
			return 0
		}

		info("AC: undid ${removed.name()} node.")
		return 1
	}

	private fun editAcNodes(): Int {
		val module = autoC() ?: return 0
		val session = module.createEditSession() ?: return 0
		CgcClient.openScreenLater { AutoCEditScreen(session) }
		return 1
	}

	private fun autoC(): AutoC? {
		val module = CgcModules.manager.get("AutoC") as? AutoC
		if (module == null) {
			error("Auto C is not registered.")
		}
		return module
	}

	private fun normalize(raw: String): String =
		Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()

	private fun literal(name: String): LiteralArgumentBuilder<ClientSuggestionProvider> =
		LiteralArgumentBuilder.literal(name)

	private fun argument(name: String, type: StringArgumentType) =
		RequiredArgumentBuilder.argument<ClientSuggestionProvider, String>(name, type)

	private fun argument(name: String, type: DoubleArgumentType) =
		RequiredArgumentBuilder.argument<ClientSuggestionProvider, Double>(name, type)

	private fun argument(name: String, type: IntegerArgumentType) =
		RequiredArgumentBuilder.argument<ClientSuggestionProvider, Int>(name, type)

	private fun fabricLiteral(name: String): LiteralArgumentBuilder<FabricClientCommandSource> =
		LiteralArgumentBuilder.literal(name)

	private fun fabricArgument(name: String, type: StringArgumentType) =
		RequiredArgumentBuilder.argument<FabricClientCommandSource, String>(name, type)

	private fun fabricArgument(name: String, type: DoubleArgumentType) =
		RequiredArgumentBuilder.argument<FabricClientCommandSource, Double>(name, type)

	private fun fabricArgument(name: String, type: IntegerArgumentType) =
		RequiredArgumentBuilder.argument<FabricClientCommandSource, Int>(name, type)

	private fun suggestions(vararg values: String): SuggestionProvider<ClientSuggestionProvider> =
		SuggestionProvider { _: CommandContext<ClientSuggestionProvider>, builder ->
			SharedSuggestionProvider.suggest(values.asIterable(), builder)
		}

	private fun fabricSuggestions(vararg values: String): SuggestionProvider<FabricClientCommandSource> =
		SuggestionProvider { _: CommandContext<FabricClientCommandSource>, builder ->
			SharedSuggestionProvider.suggest(values.asIterable(), builder)
		}

	private fun info(message: String) {
		ChatUtils.chat("${ChatFormatting.AQUA}CGC » ${ChatFormatting.RESET}$message")
	}

	private fun error(message: String) {
		ChatUtils.chat("${ChatFormatting.RED}CGC » ${ChatFormatting.RESET}$message")
	}
}
