package cgc.cgc.navigation

import cgc.cgc.runtime.InputCommand
import cgc.cgc.runtime.InputScheduler
import cgc.cgc.runtime.ItemInteractionUtils
import cgc.cgc.runtime.PhysicalInputTracker
import cgc.cgc.utils.ItemUtils
import cgc.cgc.module.impl.dungeon.AimMode
import cgc.cgc.module.impl.dungeon.AimSettings
import cgc.cgc.module.impl.dungeon.AimTimingProfile
import cgc.cgc.module.impl.dungeon.SimonSaysAimController
import cgc.cgc.module.impl.dungeon.VanillaMouseMotion
import cgc.cgc.module.impl.dungeon.autoc.AutoCLookController
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.level.ClipContext
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.HitResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import net.minecraft.world.phys.Vec3

/** Reusable coordinate-navigation facade. Callers never depend on planner internals. */
object NavigationService {
	private val ids = AtomicLong()
	private val planner = Executors.newSingleThreadExecutor { task -> Thread(task, "CGC Etherwarp Pathfinder").apply { isDaemon = true } }
	private var snapshot = NavigationSnapshot(null, NavigationStatus.IDLE, null, null, 0)
	private var pending: CompletableFuture<NavigationRoute>? = null
	private var lookaheadPending: CompletableFuture<NavigationRoute>? = null
	private var lookaheadRoute: NavigationRoute? = null
	private var requestGeneration = 0L
	private var options = NavigationOptions()
	private var phase = Phase.NONE
	private var phaseTicks = 0
	private var actionStartedAt = 0L
	private var originalSlot = -1
	private var abilitySlot = -1
	private var inputBaseline: PhysicalInputTracker.Snapshot? = null
	private val aimController = SimonSaysAimController()
	private val mouseMotion = VanillaMouseMotion()
	private val etherwarpLookController = AutoCLookController()
	private var aimActionIndex = -1
	private var abilitySucceeded = false
	private var etherwarpClicked = false
	private var teleportOrigin: Vec3? = null
	private val failedEdges = linkedSetOf<NavigationEdgeKey>()
	private var bestActionDistance = Double.POSITIVE_INFINITY
	private var lastProgressAtMs = 0L
	private var localGoal: BlockPos? = null
	private var currentAimTarget: Vec3? = null
	private var etherwarpAimBlock: BlockPos? = null

	@JvmStatic
	fun navigateTo(goal: BlockPos, options: NavigationOptions = NavigationOptions()): NavigationHandle {
		val client = Minecraft.getInstance()
		val player = client.player ?: return NavigationHandle(-1)
		val level = client.level ?: return NavigationHandle(-1)
		cancelInternal(NavigationStatus.CANCELLED, "replaced")
		val handle = NavigationHandle(ids.incrementAndGet())
		val etherwarpStack = (0..8).asSequence().map(player.inventory::getItem).firstOrNull(ItemUtils::isEtherwarp)
		val actualRange = (BASE_ETHERWARP_RANGE + (etherwarpStack?.let(ItemUtils::tunerDistance) ?: 0)).coerceAtMost(MAX_ETHERWARP_RANGE)
		val effectiveOptions = options.copy(etherwarpRange = actualRange.toDouble())
		this.options = effectiveOptions
		snapshot = NavigationSnapshot(handle, NavigationStatus.PLANNING, goal, null, 0)
		inputBaseline = PhysicalInputTracker.snapshot()
		val generation = ++requestGeneration
		val start = player.blockPosition()
		val yaw = player.yRot
		val pitch = player.xRot
		val eye = predictedCrouchedEye(player)
		val excluded = failedEdges.toSet()
		pending = CompletableFuture.supplyAsync({
			HybridPathfinder(MinecraftNavigationWorld(level)).find(start, goal, yaw, effectiveOptions, excluded, pitch, eye)
		}, planner)
			.whenComplete { route, error ->
				client.execute {
					if (generation != requestGeneration || snapshot.handle != handle) return@execute
					pending = null
					if (error != null || route == null || route.actions.isEmpty()) {
						val detail = error?.message ?: route?.diagnostics?.let {
							"no usable route (${it.etherwarpCandidates} Etherwarp, ${it.walkingCandidates} setup candidates)"
						} ?: "no usable route"
						snapshot = snapshot.copy(status = NavigationStatus.FAILED, route = route, detail = detail)
					} else {
						snapshot = snapshot.copy(status = NavigationStatus.MOVING, route = route, actionIndex = 0,
							detail = if (route.reachedGoal) "route ready" else "executing partial route")
						startRollingLookahead(route, generation)
					}
				}
			}
		return handle
	}

	@JvmStatic fun status(): NavigationSnapshot = snapshot

	@JvmStatic fun cancel(handle: NavigationHandle? = snapshot.handle, reason: String = "cancelled") {
		if (handle == null || handle == snapshot.handle) cancelInternal(NavigationStatus.CANCELLED, reason)
	}

	fun tick(client: Minecraft) {
		if (snapshot.status != NavigationStatus.MOVING) return
		val player = client.player ?: return cancelInternal(NavigationStatus.CANCELLED, "player unavailable")
		if (client.level == null || client.screen != null) return cancelInternal(NavigationStatus.CANCELLED, "world or screen changed")
		if (manualInputChanged()) return cancelInternal(NavigationStatus.CANCELLED, "manual input")
		val route = snapshot.route ?: return
		skipPassedWalkingWaypoints(player, route)
		val action = route.actions.getOrNull(snapshot.actionIndex)
		if (action == null) {
			if (adoptRollingLookahead()) return
			if (lookaheadPending != null) {
				snapshot = snapshot.copy(detail = "waiting for rolling route extension")
				return
			}
			if (route.reachedGoal || distanceSq(player.blockPosition(), snapshot.goal ?: player.blockPosition()) <= options.arrivalRadius * options.arrivalRadius) {
				return cancelInternal(NavigationStatus.ARRIVED, "arrived")
			}
			val goal = snapshot.goal ?: return cancelInternal(NavigationStatus.FAILED, "goal lost")
			navigateTo(goal, options)
			return
		}
		when (action.type) {
			NavigationActionType.WALK, NavigationActionType.ASCEND, NavigationActionType.DESCEND -> tickWalk(client, action)
			NavigationActionType.ETHERWARP -> tickAbility(client, action)
		}
	}

	fun reset() = cancelInternal(NavigationStatus.IDLE, "world changed")

	private fun tickWalk(client: Minecraft, action: NavigationAction) {
		val player = client.player ?: return
		if (distanceSq(player.blockPosition(), action.end) <= 1.5) return advance()
		if (actionStartedAt == 0L) {
			if (distanceSq(player.blockPosition(), action.start) > WALK_START_TOLERANCE_SQ) return replan("walking action start no longer matches player")
			actionStartedAt = System.currentTimeMillis()
			bestActionDistance = player.position().distanceTo(Vec3.atCenterOf(action.end))
			lastProgressAtMs = actionStartedAt
		}
		val now = System.currentTimeMillis()
		val distance = player.position().distanceTo(Vec3.atCenterOf(action.end))
		if (distance < bestActionDistance - PROGRESS_EPSILON) {
			bestActionDistance = distance
			lastProgressAtMs = now
		}
		if (now - lastProgressAtMs >= STUCK_WINDOW_MS) {
			failedEdges += NavigationEdgeKey(action.type, action.start, action.end)
			InputScheduler.clear()
			return replan("walking transition made no progress")
		}
		if (now - actionStartedAt > WALK_TIMEOUT_MS) return replan("movement blocked")
		val target = selectWalkTarget(client, action)
		ensureAim(player, action, target)
		val desired = aimController.update(System.nanoTime() / 1_000_000L)
		mouseMotion.apply(client, player, desired.rotation)
		val movement = movementKeys(player, target)
		InputScheduler.schedule(InputCommand(ticks = 1, forward = movement.forward, left = movement.left, right = movement.right,
			sprint = movement.forward, jump = action.type == NavigationActionType.ASCEND))
	}

	private fun tickAbility(client: Minecraft, action: NavigationAction) {
		val player = client.player ?: return
		when (phase) {
			Phase.NONE -> {
				InputScheduler.clear()
				if (originalSlot < 0) originalSlot = player.inventory.selectedSlot
				abilitySlot = findEtherwarpSlot()
				if (abilitySlot < 0) return replan("Etherwarp unavailable")
				if (player.inventory.selectedSlot != abilitySlot) {
					selectSlot(abilitySlot)
					phase = Phase.SWAP_IN
					phaseTicks = SWAP_SETTLE_TICKS
				} else {
					phase = Phase.AIM
				}
			}
			Phase.SWAP_IN -> if (--phaseTicks <= 0) phase = Phase.AIM
			Phase.AIM -> {
				val crouchedEye = predictedCrouchedEye(player)
				if (!ensureEtherwarpAim(player, action, crouchedEye)) return
				etherwarpLookController.update(player)
				InputScheduler.schedule(InputCommand(ticks = 1, sneak = true))
				if (!etherwarpLookController.hasPlan() && isLookingAtEtherwarpTarget(player, action.targetBlock, crouchedEye)) {
					phase = Phase.CROUCH_WINDOW
					phaseTicks = ETHERWARP_CROUCH_WINDOW_TICKS
					etherwarpClicked = false
				}
			}
			Phase.CROUCH_WINDOW -> {
				InputScheduler.schedule(InputCommand(ticks = 1, sneak = true))
				if (!etherwarpClicked && phaseTicks == ETHERWARP_CLICK_REMAINING_TICKS) {
					val crouched = player.isShiftKeyDown && player.pose == Pose.CROUCHING
					if (!crouched) {
						etherwarpLookController.clear()
						etherwarpAimBlock = null
						phase = Phase.AIM
						return
					}
					if (!liveEtherwarpMatches(client, player, action)) {
						failedEdges += NavigationEdgeKey(action.type, action.start, action.end)
						return replan("live Etherwarp prediction did not match planned landing")
					}
					teleportOrigin = player.position()
					if (!ItemInteractionUtils.useHeldAir()) return replan("could not send Etherwarp use")
					etherwarpClicked = true
				}
				if (--phaseTicks <= 0) {
					if (!etherwarpClicked) phase = Phase.AIM
					else { phase = Phase.VERIFY; phaseTicks = TELEPORT_VERIFY_TICKS }
				}
			}
			Phase.VERIFY -> {
				InputScheduler.schedule(InputCommand(ticks = 1, sneak = true))
				val nearGroundedEnd = distanceSq(player.blockPosition(), action.end) <= TELEPORT_TOLERANCE_SQ
				val moved = teleportOrigin?.distanceToSqr(player.position()) ?: 0.0
				if (nearGroundedEnd && moved >= MIN_ETHERWARP_DISPLACEMENT_SQ) return finishAbility(true)
				if (--phaseTicks <= 0) beginRestore(false)
			}
			Phase.SWAP_OUT -> if (--phaseTicks <= 0) {
				phase = Phase.NONE
				if (abilitySucceeded) advance() else replan("teleport did not reach expected landing")
			}
		}
	}

	private fun finishAbility(success: Boolean) {
		if (!success) return beginRestore(false)
		val routeBeforeExtension = snapshot.route
		if (snapshot.actionIndex == routeBeforeExtension?.actions?.lastIndex) adoptRollingLookahead()
		val route = snapshot.route
		val next = route?.actions?.getOrNull(snapshot.actionIndex + 1)
		val current = route?.actions?.getOrNull(snapshot.actionIndex)
		val chainEtherwarp = current?.type == NavigationActionType.ETHERWARP && next?.type == NavigationActionType.ETHERWARP
		if (chainEtherwarp) {
			advance()
			// Keep the Etherwarp selected and crouched while immediately aiming the next warp.
			phase = Phase.AIM
			return
		}
		beginRestore(true)
	}

	private fun beginRestore(success: Boolean) {
		abilitySucceeded = success
		if (!success) snapshot.route?.actions?.getOrNull(snapshot.actionIndex)?.let { action ->
			failedEdges += NavigationEdgeKey(action.type, action.start, action.end)
		}
		if (originalSlot >= 0 && originalSlot != abilitySlot) selectSlot(originalSlot)
		phase = Phase.SWAP_OUT
		phaseTicks = SWAP_SETTLE_TICKS
		if (!success) snapshot = snapshot.copy(detail = "teleport failed; restoring slot")
	}

	private fun advance() {
		phase = Phase.NONE; phaseTicks = 0; actionStartedAt = 0L; abilitySucceeded = false; etherwarpClicked = false; teleportOrigin = null
		clearAim()
		snapshot = snapshot.copy(actionIndex = snapshot.actionIndex + 1)
	}

	private fun replan(reason: String) {
		val goal = snapshot.goal ?: return cancelInternal(NavigationStatus.FAILED, reason)
		val currentOptions = options
		cancelInternal(NavigationStatus.CANCELLED, reason)
		navigateTo(goal, currentOptions)
	}

	private fun cancelInternal(status: NavigationStatus, detail: String) {
		requestGeneration++
		pending?.cancel(true); pending = null
		lookaheadPending?.cancel(true); lookaheadPending = null; lookaheadRoute = null
		InputScheduler.clear()
		val player = Minecraft.getInstance().player
		if (player != null && originalSlot >= 0 && player.inventory.selectedSlot != originalSlot) selectSlot(originalSlot)
		phase = Phase.NONE; originalSlot = -1; abilitySlot = -1; actionStartedAt = 0L; inputBaseline = null; abilitySucceeded = false; etherwarpClicked = false; teleportOrigin = null
		clearAim()
		snapshot = if (status == NavigationStatus.IDLE) NavigationSnapshot(null, status, null, null, 0, detail) else snapshot.copy(status = status, detail = detail)
	}

	private fun startRollingLookahead(route: NavigationRoute, generation: Long = requestGeneration) {
		if (route.reachedGoal || route.actions.isEmpty() || lookaheadPending != null || lookaheadRoute != null) return
		val client = Minecraft.getInstance()
		val level = client.level ?: return
		val goal = snapshot.goal ?: return
		val last = route.actions.last()
		val start = last.end
		val yaw = last.yaw ?: 0f
		val pitch = last.pitch ?: 0f
		val eye = Vec3(start.x + .5, start.y + ROLLING_CROUCH_EYE_HEIGHT, start.z + .5)
		val rollingOptions = options.copy(planningBudgetMs = ROLLING_LOOKAHEAD_BUDGET_MS)
		val excluded = failedEdges.toSet()
		val future = CompletableFuture.supplyAsync({
			HybridPathfinder(MinecraftNavigationWorld(level)).find(start, goal, yaw, rollingOptions, excluded, pitch, eye)
		}, planner)
		lookaheadPending = future
		future.whenComplete { extension, error ->
			client.execute {
				if (generation != requestGeneration) return@execute
				lookaheadPending = null
				if (error == null && extension != null && extension.actions.isNotEmpty() && extension.actions.first().start == start) {
					lookaheadRoute = extension
					snapshot = snapshot.copy(detail = "rolling route extension ready")
				}
			}
		}
	}

	private fun adoptRollingLookahead(): Boolean {
		val current = snapshot.route ?: return false
		val extension = lookaheadRoute ?: return false
		if (current.actions.lastOrNull()?.end != extension.actions.firstOrNull()?.start) {
			lookaheadRoute = null
			return false
		}
		val combined = NavigationRoute(
			actions = current.actions + extension.actions,
			reachedGoal = extension.reachedGoal,
			diagnostics = extension.diagnostics
		)
		lookaheadRoute = null
		snapshot = snapshot.copy(route = combined, detail = "rolling route extended")
		startRollingLookahead(combined)
		return true
	}

	private fun findEtherwarpSlot(): Int {
		val player = Minecraft.getInstance().player ?: return -1
		return (0..8).firstOrNull { slot ->
			val stack = player.inventory.getItem(slot)
			ItemUtils.isEtherwarp(stack)
		} ?: -1
	}

	private fun selectSlot(slot: Int) {
		val client = Minecraft.getInstance(); val player = client.player ?: return
		player.inventory.selectedSlot = slot
		client.connection?.connection?.send(ServerboundSetCarriedItemPacket(slot))
	}

	private fun ensureAim(player: net.minecraft.client.player.LocalPlayer, action: NavigationAction, overrideTarget: Vec3? = null) {
		val point = overrideTarget ?: action.targetBlock?.let(Vec3::atCenterOf)
			?: Vec3(action.end.x + 0.5, action.end.y + player.eyeHeight.toDouble(), action.end.z + 0.5)
		if (aimActionIndex == snapshot.actionIndex && aimController.hasTarget() && currentAimTarget?.distanceToSqr(point) ?: Double.POSITIVE_INFINITY < 0.04) return
		aimController.start(
			player = player,
			target = point,
			settings = AimSettings(speed = 1.65, randomness = 0.08, overshootStrength = 0.7),
			mode = AimMode.CHAINED_RETARGET,
			nowMs = System.nanoTime() / 1_000_000L,
			timing = AimTimingProfile(minimumSpeed = 0.18, maximumSpeed = 2.25, maximumDurationMs = 800L)
		)
		aimActionIndex = snapshot.actionIndex
		currentAimTarget = point
	}

	private fun clearAim() {
		aimActionIndex = -1
		currentAimTarget = null
		aimController.clear()
		mouseMotion.clear()
		etherwarpLookController.clear()
		etherwarpAimBlock = null
	}

	private fun ensureEtherwarpAim(player: net.minecraft.client.player.LocalPlayer, action: NavigationAction, eye: Vec3): Boolean {
		val block = action.targetBlock ?: return false
		if (etherwarpAimBlock == block && etherwarpLookController.hasPlan()) return true
		if (etherwarpAimBlock == block && isLookingAtEtherwarpTarget(player, block, eye)) return true
		val point = visibleEtherwarpAimPoint(player, block, action.targetPoint, eye) ?: run {
			failedEdges += NavigationEdgeKey(action.type, action.start, action.end)
			replan("Etherwarp target is no longer visible from actual position")
			return false
		}
		val rotation = AutoCNodeUtils.rotationTo(eye, point, player.yRot)
		etherwarpLookController.startEtherwarp(player, rotation.yaw, rotation.pitch, point.distanceTo(eye))
		etherwarpAimBlock = block
		return true
	}

	private fun visibleEtherwarpAimPoint(player: net.minecraft.client.player.LocalPlayer, block: BlockPos, preferred: Vec3?, eye: Vec3): Vec3? {
		val level = Minecraft.getInstance().level ?: return null
		if (preferred != null && canRaycastEtherwarpPoint(player, block, preferred, eye)) return preferred
		val state = level.getBlockState(block)
		val shape = state.getShape(level, block)
		return collisionPartAimCandidates(shape, block, ETHERWARP_AIM_INSET).asSequence()
			.filter { eye.distanceToSqr(it) <= options.etherwarpRange * options.etherwarpRange }
			.filter { point -> canRaycastEtherwarpPoint(player, block, point, eye) }
			.minByOrNull { point ->
				val rotation = AutoCNodeUtils.rotationTo(eye, point, player.yRot)
				kotlin.math.abs(net.minecraft.util.Mth.wrapDegrees(rotation.yaw - player.yRot)) +
					kotlin.math.abs(rotation.pitch - player.xRot) + eye.distanceToSqr(point) * .002
			}
	}

	private fun canRaycastEtherwarpPoint(player: net.minecraft.client.player.LocalPlayer, block: BlockPos, point: Vec3, eye: Vec3): Boolean {
		val level = Minecraft.getInstance().level ?: return false
		val towardCenter = Vec3.atCenterOf(block).subtract(point)
		val end = if (towardCenter.lengthSqr() > 1.0E-6) point.add(towardCenter.normalize().scale(ETHERWARP_AIM_OVERSHOOT)) else point
		val conservative = conservativeFirstBlock(eye, end) { pos ->
			!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
		}
		if (conservative?.block != block) return false
		val hit = level.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
		return hit.type == HitResult.Type.BLOCK && hit.blockPos == block
	}

	private fun isLookingAtEtherwarpTarget(player: net.minecraft.client.player.LocalPlayer, block: BlockPos?, eye: Vec3): Boolean {
		block ?: return false
		val level = Minecraft.getInstance().level ?: return false
		val end = eye.add(player.lookAngle.scale(options.etherwarpRange))
		val conservative = conservativeFirstBlock(eye, end) { pos ->
			!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
		}
		if (conservative?.block != block) return false
		val hit = level.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
		return hit.type == HitResult.Type.BLOCK && hit.blockPos == block
	}

	private fun liveEtherwarpMatches(
		client: Minecraft,
		player: net.minecraft.client.player.LocalPlayer,
		action: NavigationAction
	): Boolean {
		val level = client.level ?: return false
		val prediction = MinecraftNavigationWorld(level).predictEtherwarp(
			player.eyePosition,
			player.yRot,
			player.xRot,
			options.etherwarpRange
		) ?: return false
		return prediction.targetBlock == action.targetBlock && prediction.landingFeet == action.end
	}

	private fun predictedCrouchedEye(player: net.minecraft.client.player.LocalPlayer): Vec3 =
		player.position().add(0.0, player.getEyeHeight(Pose.CROUCHING).toDouble(), 0.0)

	private fun selectWalkTarget(client: Minecraft, action: NavigationAction): Vec3 {
		val player = client.player ?: return Vec3.atCenterOf(action.end)
		val world = client.level?.let(::MinecraftNavigationWorld) ?: return Vec3.atCenterOf(action.end)
		val current = player.position()
		localGoal?.let { goal ->
			if (current.distanceToSqr(Vec3.atCenterOf(goal)) <= 1.4 || !world.standable(goal)) localGoal = null
			else return Vec3.atCenterOf(goal)
		}
		val direct = furthestVisibleWalkTarget(world, current, action)
		if (world.movementCorridorClear(current, direct)) return direct
		val candidate = localAvoidanceCandidates(player.blockPosition())
			.asSequence()
			.filter(world::standable)
			.filter { world.movementCorridorClear(current, Vec3(it.x + .5, it.y.toDouble(), it.z + .5), 0.10) }
			.minByOrNull { candidate ->
				val onward = Vec3.atCenterOf(candidate).distanceTo(Vec3.atCenterOf(action.end))
				onward + world.clearancePenalty(candidate) * 1.8
			}
		localGoal = candidate
		return candidate?.let(Vec3::atCenterOf) ?: direct
	}

	private fun furthestVisibleWalkTarget(world: MinecraftNavigationWorld, current: Vec3, fallback: NavigationAction): Vec3 {
		val actions = snapshot.route?.actions ?: return Vec3.atCenterOf(fallback.end)
		var target = Vec3(fallback.end.x + .5, fallback.end.y.toDouble(), fallback.end.z + .5)
		val limit = minOf(actions.lastIndex, snapshot.actionIndex + WALK_LOOKAHEAD_ACTIONS)
		for (index in snapshot.actionIndex + 1..limit) {
			val candidate = actions[index]
			if (candidate.type !in WALK_TYPES) break
			val point = Vec3(candidate.end.x + .5, candidate.end.y.toDouble(), candidate.end.z + .5)
			if (!world.movementCorridorClear(current, point, 0.08)) break
			target = point
		}
		return target
	}

	private fun skipPassedWalkingWaypoints(player: net.minecraft.client.player.LocalPlayer, route: NavigationRoute) {
		var index = snapshot.actionIndex
		while (index < route.actions.size) {
			val action = route.actions[index]
			if (action.type !in WALK_TYPES) break
			val start = Vec3(action.start.x + .5, action.start.y.toDouble(), action.start.z + .5)
			val end = Vec3(action.end.x + .5, action.end.y.toDouble(), action.end.z + .5)
			val direction = end.subtract(start)
			val passedPlane = player.position().subtract(end).dot(direction) >= 0.0
			if (!passedPlane && player.position().distanceToSqr(end) > WAYPOINT_PASS_RADIUS_SQ) break
			index++
		}
		if (index != snapshot.actionIndex) {
			snapshot = snapshot.copy(actionIndex = index)
			actionStartedAt = 0L
			localGoal = null
			clearAim()
		}
	}

	private fun localAvoidanceCandidates(origin: BlockPos): List<BlockPos> = buildList {
		for (radius in 1..3) for (dy in -1..1) for (dx in -radius..radius) for (dz in -radius..radius) {
			if (kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dz)) == radius) add(origin.offset(dx, dy, dz))
		}
	}

	private fun movementKeys(player: net.minecraft.client.player.LocalPlayer, target: Vec3): MovementKeys {
		val delta = target.subtract(player.position())
		val targetYaw = Math.toDegrees(kotlin.math.atan2(delta.z, delta.x)).toFloat() - 90f
		val error = net.minecraft.util.Mth.wrapDegrees(targetYaw - player.yRot)
		return when {
			error > 55f -> MovementKeys(forward = false, left = false, right = true)
			error < -55f -> MovementKeys(forward = false, left = true, right = false)
			error > 14f -> MovementKeys(forward = true, left = false, right = true)
			error < -14f -> MovementKeys(forward = true, left = true, right = false)
			else -> MovementKeys(forward = true, left = false, right = false)
		}
	}

	private fun manualInputChanged(): Boolean {
		val base = inputBaseline ?: return false
		val now = PhysicalInputTracker.snapshot()
		return now.keyboardPressSequence != base.keyboardPressSequence || now.mouseButtonPressSequence != base.mouseButtonPressSequence
	}

	private fun distanceSq(a: BlockPos, b: BlockPos): Double { val x=a.x-b.x; val y=a.y-b.y; val z=a.z-b.z; return (x*x+y*y+z*z).toDouble() }

	private enum class Phase { NONE, SWAP_IN, AIM, CROUCH_WINDOW, VERIFY, SWAP_OUT }
	private data class MovementKeys(val forward: Boolean, val left: Boolean, val right: Boolean)
	private const val SWAP_SETTLE_TICKS = 3
	private const val ETHERWARP_CROUCH_WINDOW_TICKS = 15
	private const val ETHERWARP_CLICK_REMAINING_TICKS = 8
	private const val TELEPORT_VERIFY_TICKS = 12
	private const val TELEPORT_TOLERANCE_SQ = 10.0
	private const val MIN_ETHERWARP_DISPLACEMENT_SQ = 1.0
	private const val WALK_START_TOLERANCE_SQ = 3.0 * 3.0
	private const val WALK_TIMEOUT_MS = 2_500L
	private const val STUCK_WINDOW_MS = 475L
	private const val PROGRESS_EPSILON = 0.10
	private const val WALK_LOOKAHEAD_ACTIONS = 7
	private const val WAYPOINT_PASS_RADIUS_SQ = 1.35 * 1.35
	private const val ETHERWARP_AIM_INSET = 0.018
	private const val ETHERWARP_AIM_OVERSHOOT = 0.03
	private const val BASE_ETHERWARP_RANGE = 57
	private const val MAX_ETHERWARP_RANGE = 61
	private const val ROLLING_CROUCH_EYE_HEIGHT = 1.27
	private const val ROLLING_LOOKAHEAD_BUDGET_MS = 900L
	private val WALK_TYPES = setOf(NavigationActionType.WALK, NavigationActionType.ASCEND, NavigationActionType.DESCEND)
}
