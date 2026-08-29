package cgc.cgc.module.impl.dungeon.autopuzzles.icefill

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createDirectories

class IceFillCalibrationStore(
	private val configuredFile: java.nio.file.Path? = null
) {
	data class Calibration(val fallY: Double?, val floorStarts: List<BlockPos?>) {
		val complete: Boolean get() = fallY != null && floorStarts.size == 3 && floorStarts.all { it != null }
	}

	private val gson = GsonBuilder().setPrettyPrinting().create()
	private var loadedCalibration: Calibration? = null
	private val file: java.nio.file.Path
		get() = configuredFile ?: FabricLoader.getInstance().configDir
			.resolve("cgc")
			.resolve("autopuzzles")
			.resolve("ice_fill_calibration.json")

	fun current(): Calibration = calibration().let { it.copy(floorStarts = it.floorStarts.toList()) }

	fun setFallY(value: Double) {
		require(value.isFinite())
		val updated = calibration().copy(fallY = value)
		save(updated)
		loadedCalibration = updated
	}

	fun setFloorStart(index: Int, value: BlockPos) {
		require(index in 0..2)
		val starts = calibration().floorStarts.toMutableList()
		while (starts.size < 3) starts.add(null)
		starts[index] = value.immutable()
		val updated = calibration().copy(floorStarts = starts)
		save(updated)
		loadedCalibration = updated
	}

	private fun calibration(): Calibration = loadedCalibration ?: load().also { loadedCalibration = it }

	private fun load(): Calibration {
		if (!Files.isRegularFile(file)) return emptyCalibration()
		return runCatching {
			val root = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
			require(root["version"]?.asInt == VERSION)
			val fallY = root["fallY"]?.takeUnless { it.isJsonNull }?.asDouble?.takeIf { it.isFinite() }
			val starts = MutableList<BlockPos?>(3) { null }
			val array = root.getAsJsonArray("floorStarts")
			if (array != null) {
				for (index in 0 until minOf(3, array.size())) {
					val element = array[index]
					if (!element.isJsonObject) continue
					val obj = element.asJsonObject
					starts[index] = BlockPos(obj["x"].asInt, obj["y"].asInt, obj["z"].asInt)
				}
			}
			Calibration(fallY, starts)
		}.getOrElse { emptyCalibration() }
	}

	private fun save(calibration: Calibration) {
		val root = JsonObject()
		root.addProperty("version", VERSION)
		if (calibration.fallY == null) root.add("fallY", com.google.gson.JsonNull.INSTANCE)
		else root.addProperty("fallY", calibration.fallY)
		val starts = com.google.gson.JsonArray()
		for (pos in calibration.floorStarts) {
			if (pos == null) {
				starts.add(com.google.gson.JsonNull.INSTANCE)
			} else {
				starts.add(JsonObject().apply {
					addProperty("x", pos.x)
					addProperty("y", pos.y)
					addProperty("z", pos.z)
				})
			}
		}
		root.add("floorStarts", starts)
		file.parent?.createDirectories()
		Files.writeString(file, gson.toJson(root), StandardCharsets.UTF_8)
	}

	private fun emptyCalibration(): Calibration = Calibration(null, MutableList(3) { null })

	private companion object {
		const val VERSION = 1
	}
}
