package cgc.cgc.module

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

interface WorldLoadModule {
	fun onWorldLoad()
}

interface ChatMessageModule {
	fun onChatMessage(message: String)
}

interface BlockChangeModule {
	fun onBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState)
}

interface WorldRenderStartModule {
	fun onWorldRenderStart()
}

interface WorldRenderExtractModule {
	fun onWorldRenderExtract(context: WorldRenderContext)
}

interface HudRenderModule {
	fun onHudRender(gfx: GuiGraphics)
}
