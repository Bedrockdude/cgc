package cgc.cgc.module

import cgc.cgc.module.impl.dungeon.AutoSSSpecsafe
import cgc.cgc.module.impl.dungeon.SSTriggerBot
import cgc.cgc.module.impl.render.CgcClickGuiModule
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.location.Location
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

object CgcModules {
	val manager = ModuleManager()

	private var bootstrapped = false

	fun bootstrap() {
		if (bootstrapped) return
		bootstrapped = true

		manager.register(
			CgcClickGuiModule(),
			AutoSSSpecsafe(),
			SSTriggerBot()
		)
	}

	fun clientTick(client: Minecraft) {
		pollKeybinds(client)

		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<ClientTickModule>()
			.forEach { it.onClientTick(client) }
	}

	@JvmStatic
	fun worldLoad() {
		Location.reset()
		manager.all()
			.asSequence()
			.filterIsInstance<WorldLoadModule>()
			.forEach { it.onWorldLoad() }
	}

	@JvmStatic
	fun chatMessage(message: String) {
		Location.noteDungeonBossChat(message)
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<ChatMessageModule>()
			.forEach { it.onChatMessage(message) }
	}

	@JvmStatic
	fun blockChange(pos: BlockPos, newState: BlockState) {
		val oldState = Minecraft.getInstance().level?.getBlockState(pos)
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<BlockChangeModule>()
			.forEach { it.onBlockChange(pos, oldState, newState) }
	}

	fun worldRenderStart() {
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<WorldRenderStartModule>()
			.forEach { it.onWorldRenderStart() }
	}

	fun worldRenderExtract(context: WorldRenderContext) {
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<WorldRenderExtractModule>()
			.forEach { it.onWorldRenderExtract(context) }
	}

	fun hudRender(gfx: GuiGraphics) {
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<HudRenderModule>()
			.forEach { it.onHudRender(gfx) }
	}

	private fun pollKeybinds(client: Minecraft) {
		val window = client.window
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.flatMap { it.flatSettings().asSequence() }
			.filterIsInstance<KeybindSetting>()
			.filter { it.persistent || it.isRegistered() }
			.forEach { it.value.tick(window) }
	}
}
