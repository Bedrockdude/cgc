package cgc.cgc.module.impl.dungeon.autopuzzles.icefill

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories

class IceFillRouteStore(private val configuredFile: Path? = null) {
	enum class MovementInput { FORWARD, BACKWARD, LEFT, RIGHT }

	data class Route(
		val cells: List<BlockPos>,
		val facings: List<Direction>,
		val roomRelativeFacings: Boolean = true,
		val movements: List<MovementInput?> = emptyList()
	) {
		init {
			require(cells.isNotEmpty() && cells.size == facings.size)
			require(movements.isEmpty() || movements.size == cells.size)
		}
	}

	private val gson = GsonBuilder().setPrettyPrinting().create()
	private var loaded: MutableMap<String, Route>? = null
	private val file: Path
		get() = configuredFile ?: FabricLoader.getInstance().configDir
			.resolve("cgc")
			.resolve("autopuzzles")
			.resolve("ice_fill_routes.json")

	fun route(signature: String): Route? = routes()[signature]

	fun save(signature: String, route: Route) {
		val updated = routes().toMutableMap()
		updated[signature] = route
		write(updated)
		loaded = updated
	}

	private fun routes(): MutableMap<String, Route> = loaded ?: load().also { loaded = it }

	private fun load(): MutableMap<String, Route> {
		if (!Files.isRegularFile(file)) return linkedMapOf()
		return runCatching {
			val root = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
			val version = root["version"]?.asInt ?: return@runCatching linkedMapOf()
			require(version in 1..VERSION)
			val result = linkedMapOf<String, Route>()
			for ((signature, element) in root.getAsJsonObject("routes")?.entrySet().orEmpty()) {
				val steps = when {
					version == 1 && element.isJsonArray -> element.asJsonArray
					version >= 2 && element.isJsonObject -> element.asJsonObject.getAsJsonArray("steps")
					else -> null
				} ?: continue
				val roomRelativeFacings = version >= 2 && element.asJsonObject["roomRelativeFacings"]?.asBoolean == true
				val cells = arrayListOf<BlockPos>()
				val facings = arrayListOf<Direction>()
				val movements = arrayListOf<MovementInput?>()
				for (step in steps) {
					if (!step.isJsonObject) continue
					val obj = step.asJsonObject
					val facing = runCatching { Direction.valueOf(obj["facing"].asString) }.getOrNull() ?: continue
					if (facing.axis == Direction.Axis.Y) continue
					cells += BlockPos(obj["x"].asInt, obj["y"].asInt, obj["z"].asInt)
					facings += facing
					movements += obj["movement"]?.takeUnless { it.isJsonNull }?.asString?.let { name ->
						runCatching { MovementInput.valueOf(name) }.getOrNull()
					}
				}
				if (cells.isNotEmpty() && cells.size == facings.size) {
					result[signature] = Route(cells, facings, roomRelativeFacings, movements)
				}
			}
			result
		}.getOrElse { linkedMapOf() }
	}

	private fun write(routes: Map<String, Route>) {
		val root = JsonObject().apply { addProperty("version", VERSION) }
		val routesJson = JsonObject()
		for ((signature, route) in routes.toSortedMap()) {
			val steps = JsonArray()
			for (index in route.cells.indices) {
				val pos = route.cells[index]
				steps.add(JsonObject().apply {
					addProperty("x", pos.x)
					addProperty("y", pos.y)
					addProperty("z", pos.z)
					addProperty("facing", route.facings[index].name)
					route.movements.getOrNull(index)?.let { addProperty("movement", it.name) }
				})
			}
			routesJson.add(signature, JsonObject().apply {
				addProperty("roomRelativeFacings", route.roomRelativeFacings)
				add("steps", steps)
			})
		}
		root.add("routes", routesJson)
		file.parent?.createDirectories()
		Files.writeString(file, gson.toJson(root), StandardCharsets.UTF_8)
	}

	companion object {
		fun layoutSignature(cells: Collection<BlockPos>): String {
			val canonical = cells.sortedWith(compareBy<BlockPos> { it.y }.thenBy { it.x }.thenBy { it.z })
				.joinToString(";") { "${it.x},${it.y},${it.z}" }
			return MessageDigest.getInstance("SHA-256")
				.digest(canonical.toByteArray(StandardCharsets.UTF_8))
				.take(12)
				.joinToString("") { "%02x".format(it) }
		}

		private const val VERSION = 3
	}
}
