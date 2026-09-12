package cgc.cgc.module.impl.dungeon.autopuzzles.waterboard

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

enum class WaterLever(
	val key: String,
	val blockX: Int,
	val blockY: Int,
	val blockZ: Int,
	val approachX: Double,
	val approachY: Double,
	val approachZ: Double,
	val legacyOrder: Int
) {
	QUARTZ("quartz_block", 20, 61, 20, 17.55, 60.0, 19.65, 0),
	GOLD("gold_block", 20, 61, 15, 17.55, 60.0, 16.35, 1),
	COAL("coal_block", 20, 61, 10, 17.55, 60.0, 11.35, 2),
	DIAMOND("diamond_block", 10, 61, 20, 12.45, 60.0, 19.65, 3),
	EMERALD("emerald_block", 10, 61, 15, 12.45, 60.0, 16.35, 4),
	TERRACOTTA("hardened_clay", 10, 61, 10, 12.45, 60.0, 11.35, 5),
	WATER("water", 15, 60, 5, 15.5, 59.0, 7.35, 6);

	companion object {
		fun fromKey(key: String): WaterLever? = entries.firstOrNull { it.key == key }
	}
}

object WaterboardData {
	data class Point(val x: Double, val y: Double, val z: Double)
	data class Geometry(val etherwarpSupport: Point)

	val solutions: Map<Int, Map<String, Map<WaterLever, List<Double>>>> by lazy { loadSolutions() }
	val geometry: Map<WaterLever, Geometry> by lazy { loadGeometry() }

	private fun loadSolutions(): Map<Int, Map<String, Map<WaterLever, List<Double>>>> {
		val stream = javaClass.classLoader.getResourceAsStream(SOLUTIONS_RESOURCE)
			?: error("Missing $SOLUTIONS_RESOURCE")
		val type = object : TypeToken<Map<String, Map<String, Map<String, List<Double>>>>>() {}.type
		val raw: Map<String, Map<String, Map<String, List<Double>>>> = InputStreamReader(stream, StandardCharsets.UTF_8).use {
			Gson().fromJson(it, type)
		}
		require(raw.keys == EXPECTED_PATTERNS) { "Waterboard patterns must be 0..3" }
		return raw.mapKeys { (pattern, _) -> pattern.toInt() }.mapValues { (_, gates) ->
			require(gates.keys == EXPECTED_GATE_KEYS) { "Waterboard gate table is incomplete" }
			gates.mapValues { (_, schedule) ->
				schedule.mapKeys { (key, _) ->
					WaterLever.fromKey(key) ?: error("Unknown Waterboard lever $key")
				}.onEach { (_, times) ->
					require(times.isNotEmpty() && times.all { it.isFinite() && it >= 0.0 }) { "Invalid Waterboard time" }
				}
			}
		}
	}

	private fun loadGeometry(): Map<WaterLever, Geometry> {
		val stream = javaClass.classLoader.getResourceAsStream(POSITIONS_RESOURCE)
			?: error("Missing $POSITIONS_RESOURCE")
		val root = InputStreamReader(stream, StandardCharsets.UTF_8).use { Gson().fromJson(it, JsonObject::class.java) }
		require(root.keySet() == WaterLever.entries.map { it.key }.toSet()) { "Waterboard positions are incomplete" }
		return WaterLever.entries.associateWith { lever ->
			val point = root.getAsJsonObject(lever.key).getAsJsonObject("etherwarp")
			val result = Point(point["x"].asDouble, point["y"].asDouble, point["z"].asDouble)
			require(result.x.isFinite() && result.y.isFinite() && result.z.isFinite())
			Geometry(result)
		}
	}

	private val EXPECTED_PATTERNS = setOf("0", "1", "2", "3")
	private val EXPECTED_GATE_KEYS = setOf("012", "013", "014", "023", "024", "034", "123", "124", "134", "234")
	private const val SOLUTIONS_RESOURCE = "assets/cgc/autopuzzles/waterSolutions.json"
	private const val POSITIONS_RESOURCE = "assets/cgc/autopuzzles/waterboardPositions.json"
}
