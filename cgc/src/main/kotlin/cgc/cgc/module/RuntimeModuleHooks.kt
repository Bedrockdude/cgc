package cgc.cgc.module

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.world.level.block.state.BlockState
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

interface WorldLoadModule {
	fun onWorldLoad()
}

interface ChatMessageModule {
	fun onChatMessage(message: String)
}

interface ActionBarMessageModule {
	fun onActionBarMessage(message: String)
}

interface BlockChangeModule {
	fun onBlockChange(pos: BlockPos, oldState: BlockState?, newState: BlockState)
}

interface WorldRenderStartModule {
	fun onWorldRenderStart()
}

interface WorldRenderExtractModule {
	fun onWorldRenderExtract(context: LevelRenderContext)
}

interface HudRenderModule {
	fun onHudRender(gfx: GuiGraphicsExtractor)
}

interface PacketReceiveModule {
	fun onPacketReceive(packet: Packet<*>): Boolean
}

interface PacketPostReceiveModule {
	fun onPacketPostReceive(packet: Packet<*>)
}

interface PacketSendModule {
	fun onPacketSend(packet: Packet<*>): Boolean
}
