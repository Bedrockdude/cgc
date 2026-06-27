package cgc.cgc.client

import cgc.cgc.config.CgcSettings
import cgc.cgc.data.Pos
import cgc.cgc.module.CgcModules
import cgc.cgc.module.impl.dungeon.AutoP3
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
import net.minecraft.world.phys.Vec3
import java.text.Normalizer
import java.util.Locale
import kotlin.math.round

object CgcCommandRegistry {
	private val dispatcher = CommandDispatcher<ClientSuggestionProvider>()
	private const val NUMBER_PATTERN = """-?(?:\d+(?:\.\d*)?|\.\d+)"""
	private val bbgArgSplitter = Regex("""[A-Za-z_]+(?:$NUMBER_PATTERN|"[^"]*")?""")
	private val bbgArgPattern = Regex("""^([A-Za-z_]+)(?:($NUMBER_PATTERN)|"([^"]*)")?$""")
	private val phaseSuggestions = suggestions("p1", "p2", "p3", "p4", "5p")
	private val classSuggestions = suggestions("A", "M", "B", "T", "H", "A M B T H")
	private val ringNames = arrayOf(
		"align",
		"fastalign",
		"stop",
		"walk",
		"jump",
		"bonzo",
		"fastbonzo",
		"edge",
		"movement",
		"look",
		"boom",
		"leap",
		"use",
		"chat",
		"command",
		"blink"
	)
	private val ringSuggestions = suggestions(*ringNames)
	private val ringArgSuggestions = suggestions(
		"r.5",
		"w.5 h1 l.5",
		"exact",
		"yaw0",
		"pitch0",
		"route\"route\"",
		"target\"0 0 0\"",
		"size17",
		"item\"BONZO_STAFF\"",
		"message\"text\"",
		"command\"pc text\""
	)
	private val configSuggestions = suggestions("rings", "nodes")
	private val centerSuggestions = suggestions("all", "yaw", "pitch")

	init {
		rebuild()
	}

	@JvmStatic
	fun getDispatcher(): CommandDispatcher<ClientSuggestionProvider> =
		dispatcher

	fun registerFabricCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>, openConfig: () -> Unit) {
		dispatcher.register(fabricLiteral("cgc").executes {
			openConfig()
			1
		})
		registerFabricLeapCounter(dispatcher, "lc")
		registerFabricLeapCounter(dispatcher, "leapcounter")
		registerFabricAutoP3(dispatcher, "bbg")
		registerFabricAutoP3(dispatcher, "p3")
		registerFabricAutoP3(dispatcher, "ap3")
		registerFabricAutoP3(dispatcher, "autop3")
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
		dispatcher.register(literal("cgc").executes {
			usage()
			1
		})
		registerLeapCounter("lc")
		registerLeapCounter("leapcounter")
		registerAutoP3("bbg")
		registerAutoP3("p3")
		registerAutoP3("ap3")
		registerAutoP3("autop3")
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

	private fun registerAutoP3(name: String) {
		dispatcher.register(
			literal(name)
				.then(
					literal("add")
						.then(
							argument("ring", StringArgumentType.word())
								.suggests(ringSuggestions)
								.executes { ctx -> addAutoP3Ring(StringArgumentType.getString(ctx, "ring"), "") }
								.then(
									argument("args", StringArgumentType.greedyString())
										.suggests(ringArgSuggestions)
										.executes { ctx ->
											addAutoP3Ring(
												StringArgumentType.getString(ctx, "ring"),
												StringArgumentType.getString(ctx, "args")
											)
										}
								)
						)
				)
				.then(
					literal("remove")
						.then(
							argument("index", IntegerArgumentType.integer(0))
								.executes { ctx -> removeAutoP3Ring(IntegerArgumentType.getInteger(ctx, "index")) }
						)
						.executes { removeNearestAutoP3Ring() }
				)
				.then(literal("rm").executes { removeNearestAutoP3Ring() })
				.then(literal("delete").executes { removeNearestAutoP3Ring() })
				.then(literal("undo").executes { autoP3()?.undo(); 1 })
				.then(literal("redo").executes { autoP3()?.redo(); 1 })
				.then(
					literal("load")
						.then(
							argument("config", StringArgumentType.greedyString())
								.suggests(configSuggestions)
								.executes { ctx ->
									val config = StringArgumentType.getString(ctx, "config")
									autoP3()?.loadConfig(config)
									1
								}
						)
				)
				.then(literal("list").executes {
					info("Auto P3: ${autoP3()?.ringCount() ?: 0} ring(s).")
					1
				})
				.then(
					literal("center")
						.executes {
							centerAngles(null)
							1
						}
						.then(
							argument("mode", StringArgumentType.word())
								.suggests(centerSuggestions)
								.executes { ctx ->
									centerAngles(StringArgumentType.getString(ctx, "mode"))
									1
								}
						)
				)
				.then(literal("help").executes {
					bbgUsage()
					1
				})
				.executes {
					bbgUsage()
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

	private fun registerFabricAutoP3(dispatcher: CommandDispatcher<FabricClientCommandSource>, name: String) {
		dispatcher.register(
			fabricLiteral(name)
				.then(
					fabricLiteral("add")
						.then(
							fabricArgument("ring", StringArgumentType.word())
								.suggests(fabricSuggestions(*ringNames))
								.executes { ctx -> addAutoP3Ring(StringArgumentType.getString(ctx, "ring"), "") }
								.then(
									fabricArgument("args", StringArgumentType.greedyString())
										.suggests(fabricSuggestions(*ringArgValues))
										.executes { ctx ->
											addAutoP3Ring(
												StringArgumentType.getString(ctx, "ring"),
												StringArgumentType.getString(ctx, "args")
											)
										}
								)
						)
				)
				.then(
					fabricLiteral("remove")
						.then(
							fabricArgument("index", IntegerArgumentType.integer(0))
								.executes { ctx -> removeAutoP3Ring(IntegerArgumentType.getInteger(ctx, "index")) }
						)
						.executes { removeNearestAutoP3Ring() }
				)
				.then(fabricLiteral("rm").executes { removeNearestAutoP3Ring() })
				.then(fabricLiteral("delete").executes { removeNearestAutoP3Ring() })
				.then(fabricLiteral("undo").executes { autoP3()?.undo(); 1 })
				.then(fabricLiteral("redo").executes { autoP3()?.redo(); 1 })
				.then(
					fabricLiteral("load")
						.then(
							fabricArgument("config", StringArgumentType.greedyString())
								.suggests(fabricSuggestions("rings", "nodes"))
								.executes { ctx ->
									autoP3()?.loadConfig(StringArgumentType.getString(ctx, "config"))
									1
								}
						)
				)
				.then(fabricLiteral("list").executes {
					info("Auto P3: ${autoP3()?.ringCount() ?: 0} ring(s).")
					1
				})
				.then(
					fabricLiteral("center")
						.executes {
							centerAngles(null)
							1
						}
						.then(
							fabricArgument("mode", StringArgumentType.word())
								.suggests(fabricSuggestions("all", "yaw", "pitch"))
								.executes { ctx ->
									centerAngles(StringArgumentType.getString(ctx, "mode"))
									1
								}
						)
				)
				.then(fabricLiteral("help").executes {
					bbgUsage()
					1
				})
				.executes {
					bbgUsage()
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

	private fun addAutoP3Ring(typeRaw: String, rawArgs: String): Int {
		val module = autoP3() ?: return 0
		val player = Minecraft.getInstance().player
		if (player == null) {
			error("You need to be in-world to add an Auto P3 ring.")
			return 0
		}

		val type = normalizeRingType(typeRaw)
		val parsed = parseBbgArgs(rawArgs)
		val pos = if (parsed.exact) player.position() else roundedHalf(player.position())
		val min = Pos(pos.x - parsed.width, pos.y, pos.z - parsed.length)
		val max = Pos(pos.x + parsed.width, pos.y + parsed.height, pos.z + parsed.length)
		val ring = AutoP3.AutoP3Ring(
			type = type,
			min = min,
			max = max,
			yaw = parsed.yaw ?: player.yRot,
			pitch = parsed.pitch ?: player.xRot,
			route = parsed.route,
			target = parsed.target,
			item = parsed.item,
			message = parsed.message,
			command = parsed.command,
			size = parsed.size
		)

		when (type) {
			"CHAT" -> if (ring.message.isNullOrBlank()) ring.message = rawArgs.takeIf { it.isNotBlank() }
			"COMMAND" -> if (ring.command.isNullOrBlank()) ring.command = rawArgs.takeIf { it.isNotBlank() }
		}

		if (type == "CHAT" && ring.message.isNullOrBlank()) {
			error("Chat rings need message\"text\" or text after the ring type.")
			return 0
		}

		if (type == "COMMAND" && ring.command.isNullOrBlank()) {
			error("Command rings need command\"command\" or text after the ring type.")
			return 0
		}

		module.addRing(ring)
		return 1
	}

	private fun removeAutoP3Ring(index: Int): Int {
		val module = autoP3() ?: return 0
		if (module.removeIndexed(index)) {
			info("Auto P3: removed ring $index.")
			return 1
		}

		error("No Auto P3 ring exists at index $index.")
		return 0
	}

	private fun removeNearestAutoP3Ring(): Int {
		val module = autoP3() ?: return 0
		val player = Minecraft.getInstance().player ?: return 0
		module.removeNearest(player.position())
		return 1
	}

	private fun parseBbgArgs(raw: String): ParsedBbgArgs {
		val args = ParsedBbgArgs()
		for (match in bbgArgSplitter.findAll(raw)) {
			val token = match.value
			val parsed = bbgArgPattern.matchEntire(token) ?: continue
			val key = parsed.groupValues[1].lowercase(Locale.ROOT)
			val number = parsed.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
			val text = parsed.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }

			when (key) {
				"exact" -> args.exact = true
				"r", "radius" -> if (number != null) {
					args.width = number
					args.height = number
					args.length = number
				}
				"w", "width" -> if (number != null) args.width = number
				"h", "height" -> if (number != null) args.height = number
				"l", "length" -> if (number != null) args.length = number
				"y", "yaw" -> if (number != null) args.yaw = number.toFloat()
				"p", "pitch" -> if (number != null) args.pitch = number.toFloat().coerceIn(-90.0f, 90.0f)
				"route" -> args.route = text
				"item" -> args.item = text
				"m", "message" -> args.message = text
				"c", "cmd", "command" -> args.command = text
				"target" -> args.target = parseTarget(text)
				"size" -> if (number != null) args.size = number.toInt()
			}
		}
		return args
	}

	private fun parseTarget(raw: String?): Pos? {
		val parts = raw?.split(',', ' ')?.filter { it.isNotBlank() } ?: return null
		if (parts.size != 3) {
			return null
		}
		val x = parts[0].toDoubleOrNull() ?: return null
		val y = parts[1].toDoubleOrNull() ?: return null
		val z = parts[2].toDoubleOrNull() ?: return null
		return Pos(x, y, z)
	}

	private fun centerAngles(part: String?) {
		val player = Minecraft.getInstance().player ?: return
		when (part?.lowercase(Locale.ROOT)) {
			"yaw" -> player.yRot = round(player.yRot / 45.0f) * 45.0f
			"pitch" -> player.xRot = 0.0f
			else -> {
				player.yRot = round(player.yRot / 45.0f) * 45.0f
				player.xRot = 0.0f
			}
		}
	}

	private fun normalizeRingType(raw: String): String =
		when (raw.lowercase(Locale.ROOT).replace("-", "_")) {
			"align" -> "ALIGN"
			"fastalign", "fast_align" -> "FAST_ALIGN"
			"j", "jump" -> "JUMP"
			"s", "stop" -> "STOP"
			"walk" -> "WALK"
			"edge" -> "EDGE"
			"movement", "move" -> "MOVEMENT"
			"boom", "superboom" -> "BOOM"
			"blink" -> "BLINK"
			"look", "rotate" -> "LOOK"
			"use", "rightclick", "right_click" -> "USE"
			"bonzo" -> "BONZO"
			"fastbonzo", "fast_bonzo" -> "FAST_BONZO"
			"leap" -> "LEAP"
			"chat", "message" -> "CHAT"
			"cmd", "command" -> "COMMAND"
			else -> raw.uppercase(Locale.ROOT)
		}

	private fun roundedHalf(pos: Vec3): Vec3 =
		Vec3(round(pos.x * 2.0) / 2.0, round(pos.y * 2.0) / 2.0, round(pos.z * 2.0) / 2.0)

	private fun leapCounter(): LeapCounter? {
		val module = CgcModules.manager.get("LeapCounter") as? LeapCounter
		if (module == null) {
			error("Leap Counter is not registered.")
		}
		return module
	}

	private fun autoP3(): AutoP3? {
		val module = CgcModules.manager.get("AutoP3") as? AutoP3
		if (module == null) {
			error("Auto P3 is not registered.")
		}
		return module
	}

	private fun usage() {
		info("Commands: ${CgcSettings.commandPrefix.value}lc, ${CgcSettings.commandPrefix.value}bbg.")
	}

	private fun lcUsage() {
		info("LC: use ${CgcSettings.commandPrefix.value}lc add p3 3 A M B T H or ${CgcSettings.commandPrefix.value}lc remove.")
	}

	private fun bbgUsage() {
		info("Auto P3: use ${CgcSettings.commandPrefix.value}bbg add <stop|jump|look|use|leap|chat|command>, remove, undo, redo, load, list.")
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

	private val ringArgValues = arrayOf(
		"r.5",
		"r0.5",
		"w.5 h1 l.5",
		"w0.5 h1 l0.5",
		"exact",
		"yaw0",
		"pitch0",
		"route\"route\"",
		"target\"0 0 0\"",
		"size17",
		"item\"BONZO_STAFF\"",
		"message\"text\"",
		"command\"pc text\""
	)

	private fun info(message: String) {
		ChatUtils.chat("${ChatFormatting.AQUA}CGC » ${ChatFormatting.RESET}$message")
	}

	private fun error(message: String) {
		ChatUtils.chat("${ChatFormatting.RED}CGC » ${ChatFormatting.RESET}$message")
	}

	private data class ParsedBbgArgs(
		var width: Double = 0.5,
		var height: Double = 1.0,
		var length: Double = 0.5,
		var exact: Boolean = false,
		var yaw: Float? = null,
		var pitch: Float? = null,
		var route: String? = null,
		var target: Pos? = null,
		var item: String? = null,
		var message: String? = null,
		var command: String? = null,
		var size: Int? = null
	)
}
