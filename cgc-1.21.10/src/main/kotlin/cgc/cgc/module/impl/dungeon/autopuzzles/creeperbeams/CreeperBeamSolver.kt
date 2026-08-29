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
		val secondState: EndpointState
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
			val first = AutoPuzzleRoomCoordinates.worldBlock(context.room, pair.first.x, pair.first.y, pair.first.z)
			val second = AutoPuzzleRoomCoordinates.worldBlock(context.room, pair.second.x, pair.second.y, pair.second.z)
			WorldPair(index, first, second, state(level, first), state(level, second))
		}.filter { it.firstState != EndpointState.OTHER && it.secondState != EndpointState.OTHER }

	fun endpointsLoaded(context: cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext): Boolean =
		CreeperBeamData.normalizedPairs.all { pair ->
			val first = AutoPuzzleRoomCoordinates.worldBlock(context.room, pair.first.x, pair.first.y, pair.first.z)
			val second = AutoPuzzleRoomCoordinates.worldBlock(context.room, pair.second.x, pair.second.y, pair.second.z)
			context.level.isLoaded(first) && context.level.isLoaded(second)
		}

	fun selectAutomationPairs(candidates: List<WorldPair>): AutomationResult {
		if (candidates.any { it.firstState == EndpointState.PRISMARINE || it.secondState == EndpointState.PRISMARINE }) {
			return AutomationResult.Ineligible("the board was already partially changed")
		}
		val untouched = candidates.filter { it.untouched }
		if (untouched.size != 4) {
			return AutomationResult.Ineligible("expected 4 untouched beam pairs, found ${untouched.size}")
		}
		val endpoints = untouched.flatMap { listOf(it.first, it.second) }
		if (endpoints.distinct().size != endpoints.size) {
			return AutomationResult.Ineligible("beam endpoints are ambiguous")
		}
		return AutomationResult.Ready(untouched.sortedBy { it.sourceIndex })
	}

	fun state(level: ClientLevel, pos: BlockPos): EndpointState =
		when {
			level.getBlockState(pos).`is`(Blocks.SEA_LANTERN) -> EndpointState.SEA_LANTERN
			level.getBlockState(pos).`is`(Blocks.PRISMARINE) -> EndpointState.PRISMARINE
			else -> EndpointState.OTHER
		}
}
