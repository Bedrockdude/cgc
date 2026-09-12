package cgc.cgc.navigation

import net.minecraft.core.BlockPos

enum class NavigationActionType { WALK, ASCEND, DESCEND, ETHERWARP }

data class NavigationAction(
	val type: NavigationActionType,
	val start: BlockPos,
	val end: BlockPos,
	val estimatedTimeMs: Double,
	val riskPenaltyMs: Double = 0.0,
	val rotationPenaltyMs: Double = 0.0,
	val switchPenaltyMs: Double = 0.0,
	val yaw: Float? = null,
	val pitch: Float? = null,
	val targetBlock: BlockPos? = null,
	val targetPoint: net.minecraft.world.phys.Vec3? = null
) {
	val totalCostMs: Double get() = estimatedTimeMs + riskPenaltyMs + rotationPenaltyMs + switchPenaltyMs
}

data class NavigationRoute(
	val actions: List<NavigationAction>,
	val reachedGoal: Boolean,
	val diagnostics: NavigationDiagnostics
) {
	val estimatedTimeMs: Double = actions.sumOf { it.totalCostMs }
}

data class NavigationDiagnostics(
	val planningTimeMs: Long,
	val statesExpanded: Int,
	val walkingCandidates: Int,
	val etherwarpCandidates: Int,
	val collisionChecks: Int,
	val raycasts: Int
)

data class NavigationOptions(
	val allowEtherwarp: Boolean = true,
	val etherwarpRange: Double = 57.0,
	val maxConsecutiveSetupMoves: Int = 3,
	val planningBudgetMs: Long = 400L,
	val maxExpansions: Int = 20_000,
	val heuristicWeight: Double = 1.35,
	val arrivalRadius: Double = 1.5
)

enum class NavigationStatus { IDLE, PLANNING, MOVING, ARRIVED, BLOCKED, FAILED, CANCELLED }

@JvmInline value class NavigationHandle(val id: Long)

data class NavigationEdgeKey(val type: NavigationActionType, val start: BlockPos, val end: BlockPos)

data class NavigationSnapshot(
	val handle: NavigationHandle?,
	val status: NavigationStatus,
	val goal: BlockPos?,
	val route: NavigationRoute?,
	val actionIndex: Int,
	val detail: String = ""
)
