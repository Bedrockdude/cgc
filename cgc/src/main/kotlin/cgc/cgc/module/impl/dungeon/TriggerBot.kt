package cgc.cgc.module.impl.dungeon

import cgc.cgc.data.Phase7
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.SubModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.group.GroupSetting
import cgc.cgc.utils.DungeonUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class TriggerBot : CgcModule(
	id = "TriggerBot",
	displayName = "TriggerBot",
	category = ModuleCategory.DUNGEONS,
	description = "Clicks configured dungeon targets when you look at them.",
	defaultEnabled = false
), ClientTickModule, WorldLoadModule {
	private val lever = LeverTrigger(this)
	private val align = AlignTrigger(this)

	init {
		registerProperty(
			GroupSetting("Lever", lever),
			GroupSetting("Align", align)
		)
	}

	override fun onClientTick(client: Minecraft) {
		if (lever.enabled) {
			lever.tick(client)
		} else {
			lever.resetTriggerState()
		}
		if (align.enabled) {
			align.tick(client)
		} else {
			align.resetTriggerState()
		}
	}

	override fun onWorldLoad() {
		reset()
	}

	override fun reset() {
		lever.resetTriggerState()
		align.resetTriggerState()
	}

	private fun leverAreaCheck(forceSkyblock: Boolean): Boolean =
		(forceSkyblock || Location.area.isArea(Island.DUNGEON)) &&
			DungeonState.inBoss &&
			(Location.floor == Floor.F7 || Location.floor == Floor.M7) &&
			DungeonUtils.isPhase(Phase7.P3)

	private fun alignAreaCheck(): Boolean =
		Location.area.isArea(Island.DUNGEON) &&
			DungeonState.inBoss &&
			(Location.floor == Floor.F7 || Location.floor == Floor.M7) &&
			(DungeonUtils.isPhase(Phase7.P1) || DungeonUtils.isPhase(Phase7.P2))

	private class LeverTrigger(module: TriggerBot) : SubModule<TriggerBot>(
		module = module,
		name = "Lever",
		defaultEnabled = true
	) {
		private val forceSkyblock = BooleanSetting("Force Skyblock", false)
		private val clickDelay = NumberSetting("Click Delay", 50.0, 500.0, 150.0, 10.0, " ms")

		private var nextClickAt = 0L
		private var lastLookedLever: BlockPos? = null
		private var lastClickedLever: BlockPos? = null

		init {
			registerProperty(forceSkyblock, clickDelay)
		}

		fun tick(client: Minecraft) {
			val player = client.player ?: return
			val level = client.level ?: return
			val gameMode = client.gameMode ?: return

			if (!module.leverAreaCheck(forceSkyblock.value)) {
				resetTriggerState()
				return
			}
			if (player.position().distanceToSqr(LEVER_EXCEPTION_CENTER) <= LEVER_EXCEPTION_RADIUS_SQ) {
				resetLookState()
				return
			}

			val hit = player.pick(LEVER_RAYCAST_DISTANCE, 0.0f, false) as? BlockHitResult ?: run {
				resetLookState()
				return
			}
			if (hit.type != HitResult.Type.BLOCK) {
				resetLookState()
				return
			}

			val pos = hit.blockPos
			val state = level.getBlockState(pos)
			if (!state.`is`(Blocks.LEVER)) {
				resetLookState()
				return
			}
			if (lastLookedLever != pos) {
				lastLookedLever = pos
				lastClickedLever = null
			}
			if (state.getValue(LeverBlock.POWERED) || lastClickedLever == pos) {
				return
			}

			val now = System.currentTimeMillis()
			if (now < nextClickAt) {
				return
			}

			gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
			player.swing(InteractionHand.MAIN_HAND)
			lastClickedLever = pos
			nextClickAt = now + clickDelay.value.toLong()
		}

		fun resetTriggerState() {
			nextClickAt = 0L
			resetLookState()
		}

		private fun resetLookState() {
			lastLookedLever = null
			lastClickedLever = null
		}

		private companion object {
			private val LEVER_EXCEPTION_CENTER = Vec3(60.5, 132.0, 140.5)
			private const val LEVER_EXCEPTION_RADIUS_SQ = 100.0
			private const val LEVER_RAYCAST_DISTANCE = 5.0
		}
	}

	private class AlignTrigger(module: TriggerBot) : SubModule<TriggerBot>(
		module = module,
		name = "Align",
		defaultEnabled = true
	) {
		private val forceSkyblock = BooleanSetting("Force Skyblock", false)
		private val cps = NumberSetting("CPS", 1.0, 20.0, 10.0, 1.0)

		private val recentRotations = hashMapOf<Int, TimedRotation>()
		private var queuedFrameId = -1
		private var queuedFrameIndex = -1
		private var queuedClicks = 0
		private var nextClickAt = 0L

		init {
			registerProperty(forceSkyblock, cps)
		}

		fun tick(client: Minecraft) {
			val player = client.player ?: return
			val level = client.level ?: return
			val gameMode = client.gameMode ?: return
			if (!forceSkyblock.value && !module.alignAreaCheck()) {
				resetTriggerState()
				return
			}

			val frames = getFrames(level)
			if (frames.isEmpty()) {
				resetTriggerState()
				return
			}

			val rotations = currentRotations(frames)
			val solution = findSolution(rotations) ?: run {
				clearQueue()
				return
			}
			val holdoutIndex = bottomLeftActiveIndex(solution, rotations)
			val lookedFrame = lookedAtArrowFrame(client, frames.values) ?: run {
				clearQueue()
				return
			}
			val frameIndex = frameIndex(lookedFrame.blockPosition())
			if (frameIndex !in 0..24 || solution[frameIndex] == EMPTY_ROTATION || rotations[frameIndex] == EMPTY_ROTATION) {
				clearQueue()
				return
			}

			if (queuedFrameId != lookedFrame.getId()) {
				queuedFrameId = lookedFrame.getId()
				queuedFrameIndex = frameIndex
				queuedClicks = clicksNeededForFrame(rotations[frameIndex], solution[frameIndex], frameIndex == holdoutIndex)
				nextClickAt = 0L
			}
			if (queuedFrameIndex != frameIndex || queuedClicks <= 0) {
				return
			}

			val now = System.currentTimeMillis()
			if (now < nextClickAt) {
				return
			}

			gameMode.interact(player, lookedFrame, EntityHitResult(lookedFrame, lookedFrame.boundingBox.center), InteractionHand.MAIN_HAND)
			player.swing(InteractionHand.MAIN_HAND)
			val updatedRotation = (rotations[frameIndex] + 1) % FRAME_ROTATIONS
			recentRotations[frameIndex] = TimedRotation(updatedRotation, now)
			queuedClicks--
			nextClickAt = now + clickDelayMs()
		}

		fun resetTriggerState() {
			recentRotations.clear()
			clearQueue()
		}

		private fun clearQueue() {
			queuedFrameId = -1
			queuedFrameIndex = -1
			queuedClicks = 0
			nextClickAt = 0L
		}

		private fun getFrames(level: ClientLevel): Map<Int, ItemFrame> =
			level.entitiesForRendering()
				.asSequence()
				.filterIsInstance<ItemFrame>()
				.filter { it.item.`is`(Items.ARROW) }
				.mapNotNull { frame ->
					val index = frameIndex(frame.blockPosition())
					if (index in 0..24) index to frame else null
				}
				.toMap()

		private fun currentRotations(frames: Map<Int, ItemFrame>): List<Int> {
			val now = System.currentTimeMillis()
			return (0..24).map { index ->
				val recent = recentRotations[index]
				if (recent != null && now - recent.updatedAtMs < RECENT_ROTATION_KEEP_MS) {
					recent.rotation
				} else {
					frames[index]?.rotation ?: EMPTY_ROTATION
				}
			}
		}

		private fun findSolution(rotations: List<Int>): List<Int>? =
			POSSIBLE_SOLUTIONS.firstOrNull { solution ->
				solution.indices.all { index ->
					!((solution[index] == EMPTY_ROTATION || rotations[index] == EMPTY_ROTATION) && solution[index] != rotations[index])
				}
			}

		private fun bottomLeftActiveIndex(solution: List<Int>, rotations: List<Int>): Int =
			HOLDOUT_PRIORITY.firstOrNull { index ->
				solution[index] != EMPTY_ROTATION && rotations[index] != EMPTY_ROTATION
			} ?: -1

		private fun clicksNeededForFrame(currentRotation: Int, solvedRotation: Int, isHoldout: Boolean): Int {
			if (!isHoldout) {
				return clicksNeeded(currentRotation, solvedRotation)
			}

			val oneClickFromSolved = (solvedRotation + FRAME_ROTATIONS - 1) % FRAME_ROTATIONS
			return clicksNeeded(currentRotation, oneClickFromSolved)
		}

		private fun clicksNeeded(currentRotation: Int, targetRotation: Int): Int =
			(FRAME_ROTATIONS - currentRotation + targetRotation) % FRAME_ROTATIONS

		private fun lookedAtArrowFrame(client: Minecraft, frames: Collection<ItemFrame>): ItemFrame? {
			val hitFrame = (client.hitResult as? EntityHitResult)?.entity as? ItemFrame
			if (hitFrame != null && hitFrame.item.`is`(Items.ARROW)) {
				return hitFrame
			}

			val player = client.player ?: return null
			val eye = player.eyePosition
			val end = eye.add(player.lookAngle.scale(ALIGN_RAYCAST_DISTANCE))
			return frames
				.mapNotNull { frame ->
					val hit = frame.boundingBox.inflate(ALIGN_FRAME_PICK_INFLATE).clip(eye, end).orElse(null) ?: return@mapNotNull null
					frame to eye.distanceToSqr(hit)
				}
				.minByOrNull { it.second }
				?.first
		}

		private fun frameIndex(pos: BlockPos): Int {
			if (pos.x != FRAME_GRID_CORNER.x) {
				return -1
			}
			val yOffset = pos.y - FRAME_GRID_CORNER.y
			val zOffset = pos.z - FRAME_GRID_CORNER.z
			if (yOffset !in 0 until GRID_SIZE || zOffset !in 0 until GRID_SIZE) {
				return -1
			}
			return yOffset + zOffset * GRID_SIZE
		}

		private fun clickDelayMs(): Long =
			(1000.0 / cps.value.toDouble().coerceAtLeast(1.0)).toLong().coerceAtLeast(1L)

		private data class TimedRotation(val rotation: Int, val updatedAtMs: Long)

		private companion object {
			private val FRAME_GRID_CORNER = BlockPos(-2, 120, 75)
			private const val GRID_SIZE = 5
			private const val FRAME_ROTATIONS = 8
			private const val EMPTY_ROTATION = -1
			private const val RECENT_ROTATION_KEEP_MS = 1000L
			private const val ALIGN_RAYCAST_DISTANCE = 5.0
			private const val ALIGN_FRAME_PICK_INFLATE = 0.12
			private val HOLDOUT_PRIORITY = buildHoldoutPriority()
			private val POSSIBLE_SOLUTIONS = listOf(
				listOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
				listOf(-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1),
				listOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3),
				listOf(5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1),
				listOf(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
				listOf(7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
				listOf(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
				listOf(-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1),
				listOf(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1)
			)

			private fun buildHoldoutPriority(): List<Int> {
				val priority = arrayListOf<Int>()
				priority.add(frameIndexFromVisual(0, 0))
				priority.add(frameIndexFromVisual(0, 1))
				for (column in 1 until GRID_SIZE) {
					priority.add(frameIndexFromVisual(column, 0))
				}
				for (row in 1 until GRID_SIZE) {
					val startColumn = if (row == 1) 1 else 0
					for (column in startColumn until GRID_SIZE) {
						priority.add(frameIndexFromVisual(column, row))
					}
				}
				return priority
			}

			private fun frameIndexFromVisual(columnFromLeft: Int, rowFromBottom: Int): Int {
				val zOffset = GRID_SIZE - 1 - columnFromLeft
				return rowFromBottom + zOffset * GRID_SIZE
			}
		}
	}
}
