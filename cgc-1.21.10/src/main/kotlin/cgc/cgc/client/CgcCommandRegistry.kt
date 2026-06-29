package cgc.cgc.client

import cgc.cgc.config.CgcSettings
import cgc.cgc.client.gui.CgcConfigScreen
import cgc.cgc.client.gui.CgcUiScreen
import cgc.cgc.module.CgcModules
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
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ComponentUtils
import java.text.Normalizer
import java.util.Locale

object CgcCommandRegistry {
	private val dispatcher = CommandDispatcher<ClientSuggestionProvider>()
	private val phaseSuggestions = suggestions("p1", "p2", "p3", "p4", "5p")
	private val classSuggestions = suggestions("A", "M", "B", "T", "H", "A M B T H")

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
				.then(fabricLiteral("ui").executes {
					openUi()
					1
				})
				.executes {
					openConfig()
					1
				}
		)
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
				.then(literal("ui").executes {
					Minecraft.getInstance().setScreen(CgcUiScreen())
					1
				})
				.executes {
					Minecraft.getInstance().setScreen(CgcConfigScreen())
					1
				}
		)
		registerLeapCounter("lc")
		registerLeapCounter("leapcounter")
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
		info("Commands: ${CgcSettings.commandPrefix.value}lc.")
	}

	private fun lcUsage() {
		info("LC: use ${CgcSettings.commandPrefix.value}lc add p3 3 A M B T H or ${CgcSettings.commandPrefix.value}lc remove.")
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
