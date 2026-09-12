package cgc.cgc.module.impl.dungeon.autopuzzles

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

interface AutoPuzzleController {
	val roomNames: Set<String>

	fun tickStart(client: Minecraft) {}

	fun tick(context: AutoPuzzleContext)

	fun blockChanged(pos: BlockPos, oldState: BlockState?, newState: BlockState) {}

	fun render(context: LevelRenderContext) {}

	fun stop(reason: String, terminal: Boolean = true) {}

	fun leaveRoom() {}

	fun runChanged(runSequence: Long) {}

	fun worldReset() {}
}
