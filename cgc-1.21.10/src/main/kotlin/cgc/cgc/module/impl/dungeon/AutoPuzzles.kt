package cgc.cgc.module.impl.dungeon

import cgc.cgc.dungeon.DungeonState
import cgc.cgc.module.BlockChangeModule
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ClientTickStartModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleContext
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleController
import cgc.cgc.module.impl.dungeon.autopuzzles.AutoPuzzleInputOwner
import cgc.cgc.module.impl.dungeon.autopuzzles.blaze.BlazeSubModule
import cgc.cgc.module.impl.dungeon.autopuzzles.creeperbeams.CreeperBeamsSubModule
import cgc.cgc.module.impl.dungeon.autopuzzles.icefill.IceFillSubModule
import cgc.cgc.module.impl.dungeon.autopuzzles.waterboard.WaterboardSubModule
import cgc.cgc.module.setting.group.GroupSetting
import cgc.cgc.runtime.PhysicalInputTracker
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class AutoPuzzles : CgcModule(
	id = "AutoPuzzles",
	displayName = "Auto Puzzles",
	category = ModuleCategory.DUNGEONS,
	description = "Automatically solves selected Dungeon puzzles with human-like movement, aiming, interactions, and configurable guidance.",
	defaultEnabled = false
), ClientTickStartModule, ClientTickModule, BlockChangeModule, WorldRenderExtractModule, WorldLoadModule {
	val blaze = BlazeSubModule(this)
	val creeperBeams = CreeperBeamsSubModule(this)
	val iceFill = IceFillSubModule(this)
	val waterboard = WaterboardSubModule(this)

	private val entries: List<Entry>
		get() = listOf(
			Entry({ blaze.enabled }, blaze.controller),
			Entry({ creeperBeams.enabled }, creeperBeams.controller),
			Entry({ iceFill.enabled }, iceFill.controller),
			Entry({ waterboard.enabled }, waterboard.controller)
		)
	private var activeController: AutoPuzzleController? = null
	private var lastRunSequence = DungeonState.runSequence

	init {
		registerProperty(
			GroupSetting("Blaze", blaze),
			GroupSetting("Creeper Beams", creeperBeams),
			GroupSetting("Ice Fill", iceFill),
			GroupSetting("Waterboard", waterboard)
		)
	}

	override fun onClientTickStart(client: Minecraft) {
		val controller = activeController ?: return
		runCatching { controller.tickStart(client) }.onFailure { error ->
			controller.stop("an internal error occurred (${error::class.simpleName})", terminal = true)
		}
	}

	override fun onClientTick(client: Minecraft) {
		if (DungeonState.runSequence != lastRunSequence) {
			lastRunSequence = DungeonState.runSequence
			entries.forEach { it.controller.runChanged(lastRunSequence) }
		}
		for (entry in entries) {
			if (!entry.isEnabled() && entry.controller === activeController) {
				entry.controller.stop("the puzzle option was disabled", terminal = true)
				activeController = null
			}
		}

		val context = AutoPuzzleContext.create(client)
		val selected = context?.let { current ->
			controllerForRoom(current.room.displayName)
		}
		if (selected !== activeController) {
			activeController?.leaveRoom()
			activeController = selected
		}
		val controller = selected ?: return
		runCatching { controller.tick(context) }.onFailure { error ->
			controller.stop("an internal error occurred (${error::class.simpleName})", terminal = true)
		}
	}

	override fun onBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState) {
		val controller = activeController ?: return
		runCatching { controller.blockChanged(pos, oldState, newState) }.onFailure { error ->
			controller.stop("an internal error occurred (${error::class.simpleName})", terminal = true)
		}
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val controller = activeController ?: return
		runCatching { controller.render(context) }.onFailure { error ->
			controller.stop("an internal error occurred (${error::class.simpleName})", terminal = true)
		}
	}

	override fun onWorldLoad() {
		AutoPuzzleInputOwner.releaseAll()
		PhysicalInputTracker.reset()
		entries.forEach { it.controller.worldReset() }
		activeController = null
	}

	override fun reset() {
		activeController?.stop("Auto Puzzles was disabled", terminal = true)
		AutoPuzzleInputOwner.releaseAll()
		activeController = null
	}

	internal fun controllerForRoom(roomName: String): AutoPuzzleController? =
		entries.firstOrNull { it.isEnabled() && roomName in it.controller.roomNames }?.controller

	private data class Entry(val isEnabled: () -> Boolean, val controller: AutoPuzzleController)
}
