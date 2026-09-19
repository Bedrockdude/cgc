package cgc.cgc.module.impl.dungeon.autopuzzles.creeperbeams

import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleRoomCoordinates
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

object CreeperBeamSolver {
	enum class EndpointState { SEA_LANTERN, PRISMARINE, OTHER }

	data class WorldPair(
		val sourceIndex: Int,
		val first: BlockPos,
		val second: BlockPos,
		val firstState: EndpointState,
		val secondState: EndpointState,
		val selectionScore: Double = 0.0
	) {
		val active: Boolean get() = firstState == EndpointState.SEA_LANTERN || secondState == EndpointState.SEA_LANTERN
		val untouched: Boolean get() = firstState == EndpointState.SEA_LANTERN && secondState == EndpointState.SEA_LANTERN
		val mixed: Boolean get() = active && !untouched && firstState != EndpointState.OTHER && secondState != EndpointState.OTHER
	}

	sealed interface AutomationResult {
		data class Ready(val pairs: List<WorldPair>) : AutomationResult
		data class Ineligible(val reason: String) : AutomationResult
	}

	fun observe(level: ClientLevel, context: cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext): List<WorldPair> =
		CreeperBeamData.normalizedPairs.mapIndexed { index, pair ->
			val first = AutoPuzzleRoomCoordinates.legacyCreeperBeamBlock(context.room, pair.first.x, pair.first.y, pair.first.z)
			val second = AutoPuzzleRoomCoordinates.legacyCreeperBeamBlock(context.room, pair.second.x, pair.second.y, pair.second.z)
			WorldPair(index, first, second, state(level, first), state(level, second), selectionScore(pair))
		}.filter { it.firstState != EndpointState.OTHER && it.secondState != EndpointState.OTHER }

	fun endpointsLoaded(context: cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext): Boolean =
		CreeperBeamData.normalizedPairs.all { pair ->
			val first = AutoPuzzleRoomCoordinates.legacyCreeperBeamBlock(context.room, pair.first.x, pair.first.y, pair.first.z)
			val second = AutoPuzzleRoomCoordinates.legacyCreeperBeamBlock(context.room, pair.second.x, pair.second.y, pair.second.z)
			context.level.isLoaded(first) && context.level.isLoaded(second)
		}

	fun selectAutomationPairs(candidates: List<WorldPair>): AutomationResult {
		if (candidates.any { it.firstState == EndpointState.PRISMARINE || it.secondState == EndpointState.PRISMARINE }) {
			return AutomationResult.Ineligible("the board was already partially changed")
		}
		val untouched = candidates.filter { it.untouched }
		if (untouched.size < REQUIRED_PAIR_COUNT) {
			return AutomationResult.Ineligible("expected at least 4 untouched beam pairs, found ${untouched.size}")
		}
		val selected = firstDisjointSelection(
			untouched.sortedWith(compareBy<WorldPair> { it.selectionScore }.thenBy { it.sourceIndex }),
			REQUIRED_PAIR_COUNT
		)
			?: return AutomationResult.Ineligible("no set of 4 disjoint untouched beam pairs was found")
		return AutomationResult.Ready(selected)
	}

	internal fun firstDisjointSelection(candidates: List<WorldPair>, count: Int): List<WorldPair>? {
		fun search(index: Int, selected: MutableList<WorldPair>, endpoints: MutableSet<BlockPos>): List<WorldPair>? {
			if (selected.size == count) return selected.toList()
			if (candidates.size - index < count - selected.size) return null
			for (candidateIndex in index until candidates.size) {
				val candidate = candidates[candidateIndex]
				if (candidate.first in endpoints || candidate.second in endpoints || candidate.first == candidate.second) continue
				selected += candidate
				endpoints += candidate.first
				endpoints += candidate.second
				search(candidateIndex + 1, selected, endpoints)?.let { return it }
				endpoints -= candidate.first
				endpoints -= candidate.second
				selected.removeAt(selected.lastIndex)
			}
			return null
		}
		return search(0, arrayListOf(), hashSetOf())
	}

	fun state(level: ClientLevel, pos: BlockPos): EndpointState =
		when {
			level.getBlockState(pos).`is`(Blocks.SEA_LANTERN) -> EndpointState.SEA_LANTERN
			level.getBlockState(pos).`is`(Blocks.PRISMARINE) -> EndpointState.PRISMARINE
			else -> EndpointState.OTHER
		}

	private fun selectionScore(pair: CreeperBeamData.BeamPair): Double =
		endpointDistance(pair.first) + endpointDistance(pair.second)

	private fun endpointDistance(pos: BlockPos): Double =
		kotlin.math.sqrt((pos.x * pos.x + (pos.y - 74) * (pos.y - 74) + pos.z * pos.z).toDouble())

	private const val REQUIRED_PAIR_COUNT = 4
}
