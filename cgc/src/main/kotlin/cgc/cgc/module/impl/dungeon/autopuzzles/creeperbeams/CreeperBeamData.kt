package cgc.cgc.module.impl.dungeon.autopuzzles.creeperbeams

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object CreeperBeamData {
	data class BeamPair(val first: BlockPos, val second: BlockPos)

	val sourcePairs: List<BeamPair> by lazy { loadAndValidate() }
	val normalizedPairs: List<BeamPair> by lazy { sourcePairs.distinct() }

	private fun loadAndValidate(): List<BeamPair> {
		val stream = javaClass.classLoader.getResourceAsStream(RESOURCE)
			?: error("Missing $RESOURCE")
		val type = object : TypeToken<List<List<List<Int>>>>() {}.type
		val raw: List<List<List<Int>>> = InputStreamReader(stream, StandardCharsets.UTF_8).use {
			Gson().fromJson(it, type)
		}
		require(raw.size == 14) { "Expected 14 Creeper Beam source pairs, got ${raw.size}" }
		val pairs = raw.mapIndexed { index, pair ->
			require(pair.size == 2 && pair.all { it.size == 3 }) { "Invalid Creeper Beam pair $index" }
			BeamPair(
				BlockPos(pair[0][0], pair[0][1], pair[0][2]),
				BlockPos(pair[1][0], pair[1][1], pair[1][2])
			)
		}
		require(pairs.size - pairs.distinct().size == 1) { "Expected exactly one duplicate Creeper Beam pair" }
		return pairs
	}

	private const val RESOURCE = "assets/cgc/autopuzzles/creeperBeamSolutions.json"
}
