package cgc.cgc.module

import cgc.cgc.dungeon.DungeonState
import cgc.cgc.dungeon.room.DungeonRoomScanner
import cgc.cgc.terminal.TerminalContext
import cgc.cgc.module.impl.dungeon.AutoC
import cgc.cgc.module.impl.dungeon.AutoSSSpecsafe
import cgc.cgc.module.impl.dungeon.AutoLeap
import cgc.cgc.module.impl.dungeon.AutoTerms
import cgc.cgc.module.impl.dungeon.BreakerAura
import cgc.cgc.module.impl.dungeon.DungeonBreaker
import cgc.cgc.module.impl.dungeon.FastLeap
import cgc.cgc.module.impl.dungeon.LeapCounter
import cgc.cgc.module.impl.dungeon.PhaseTracker
import cgc.cgc.module.impl.dungeon.Relics
import cgc.cgc.module.impl.dungeon.SSTriggerBot
import cgc.cgc.module.impl.dungeon.TerminalSolver
import cgc.cgc.module.impl.dungeon.TriggerBot
import cgc.cgc.module.impl.fixies.CapitalLetterCommands
import cgc.cgc.module.impl.general.InventoryButtons
import cgc.cgc.module.impl.movement.Ether
import cgc.cgc.module.impl.movement.VelocityBuffer
import cgc.cgc.module.impl.other.DNYapper
import cgc.cgc.module.impl.player.BonzoHelper
import cgc.cgc.module.impl.player.HotbarSwitcher
import cgc.cgc.module.impl.player.MaskHelper
import cgc.cgc.module.impl.render.CgcClickGuiModule
import cgc.cgc.module.impl.render.EnderPearlTrajectory
import cgc.cgc.module.impl.render.Trail
import cgc.cgc.module.impl.render.opsec.OpSec
import cgc.cgc.module.impl.utils.FreezeState
import cgc.cgc.module.impl.utils.TerminalTimes
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.runtime.CgcRuntime
import cgc.cgc.location.Location
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.world.level.block.state.BlockState

object CgcModules {
	val manager = ModuleManager()

	private var bootstrapped = false

	fun bootstrap() {
		if (bootstrapped) return
		bootstrapped = true

		manager.register(
			CgcClickGuiModule(),
			Ether(),
			VelocityBuffer(),
			FreezeState(),
			TerminalTimes(),
			DungeonBreaker(),
			BreakerAura(),
			LeapCounter(),
			PhaseTracker(),
			FastLeap(),
			AutoC(),
			AutoLeap(),
			Relics(),
			AutoTerms(),
			TerminalSolver(),
			AutoSSSpecsafe(),
			SSTriggerBot(),
			TriggerBot(),
			EnderPearlTrajectory(),
			Trail(),
			OpSec(),
			CapitalLetterCommands(),
			InventoryButtons(),
			BonzoHelper(),
			HotbarSwitcher(),
			MaskHelper(),
			DNYapper()
		)
	}

	fun clientTickStart(client: Minecraft) {
		CgcRuntime.clientTickStart(client)
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<ClientTickStartModule>()
			.forEach { it.onClientTickStart(client) }
	}

	fun clientTick(client: Minecraft) {
		DungeonState.tick(client)
		DungeonRoomScanner.tick(client)
		pollKeybinds(client)

		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<ClientTickModule>()
			.forEach { it.onClientTick(client) }
	}

	@JvmStatic
	fun worldLoad() {
		CgcRuntime.worldLoad()
		Location.reset()
		DungeonState.reset()
		DungeonRoomScanner.reset()
		TerminalContext.reset()
		manager.all()
			.asSequence()
			.filterIsInstance<WorldLoadModule>()
			.forEach { it.onWorldLoad() }
	}

	@JvmStatic
	fun chatMessage(message: String) {
		Location.noteDungeonBossChat(message)
		DungeonState.handleChat(message)
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<ChatMessageModule>()
			.forEach { it.onChatMessage(message) }
	}

	@JvmStatic
	fun actionBarMessage(message: String) {
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<ActionBarMessageModule>()
			.forEach { it.onActionBarMessage(message) }
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

	fun worldRenderExtract(context: LevelRenderContext) {
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<WorldRenderExtractModule>()
			.forEach { it.onWorldRenderExtract(context) }
	}

	fun hudRender(gfx: GuiGraphicsExtractor) {
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<HudRenderModule>()
			.forEach { it.onHudRender(gfx) }
	}

	@JvmStatic
	fun packetReceive(packet: Packet<*>): Boolean =
		run {
			CgcRuntime.packetReceive(packet)
			manager.all()
				.asSequence()
				.filter { it.enabled }
				.filterIsInstance<PacketReceiveModule>()
				.any { it.onPacketReceive(packet) }
		}

	@JvmStatic
	fun packetPostReceive(packet: Packet<*>) {
		manager.all()
			.asSequence()
			.filter { it.enabled }
			.filterIsInstance<PacketPostReceiveModule>()
			.forEach { it.onPacketPostReceive(packet) }
	}

	@JvmStatic
	fun packetSend(packet: Packet<*>): Boolean =
		run {
			if (CgcRuntime.packetSend(packet)) {
				return@run true
			}
			manager.all()
				.asSequence()
				.filter { it.enabled }
				.filterIsInstance<PacketSendModule>()
				.any { it.onPacketSend(packet) }
		}

	private fun pollKeybinds(client: Minecraft) {
		val window = client.window
		manager.all()
			.asSequence()
			.flatMap { module ->
				module.flatSettings()
					.asSequence()
					.filterIsInstance<KeybindSetting>()
					.filter { it.persistent || (module.enabled && it.isRegistered()) }
			}
			.forEach { it.value.tick(window) }
	}
}
