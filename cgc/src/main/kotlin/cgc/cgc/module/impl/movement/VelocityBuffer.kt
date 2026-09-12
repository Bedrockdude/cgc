package cgc.cgc.module.impl.movement

import cgc.cgc.data.Keybind
import cgc.cgc.module.CgcModule
import cgc.cgc.module.HudRenderModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.DragSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.utils.ChatUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.sounds.SoundEvents
import org.joml.Vector2d
import java.util.concurrent.ConcurrentLinkedQueue

class VelocityBuffer : CgcModule(
	id = "VelocityBuffer",
	displayName = "Velocity buffer",
	category = ModuleCategory.MOVEMENT,
	description = "Buffers selected incoming velocity packets until the queue is popped.",
	defaultEnabled = false
), PacketReceiveModule, HudRenderModule, WorldLoadModule {
	private val toggleKey = KeybindSetting("Toggle Key", Keybind(action = this::toggle), persistent = true)
	private val popKey = KeybindSetting("Queue Pop Key", Keybind(action = this::popQueue))
	private val gui = DragSetting("Velocity Buffer Hud", Vector2d(100.0, 100.0), Vector2d(144.0, 80.0))

	private var bufferedCount = 0
	private val queue = ConcurrentLinkedQueue<Packet<*>>()

	init {
		registerProperty(toggleKey, popKey, gui)
		toggleKey.register()
	}

	override fun onEnable() {
		popKey.register()
		flush()
		ChatUtils.actionBar("Velocity Buffer enabled")
	}

	override fun onDisable() {
		popKey.unregister()
		flush()
		ChatUtils.actionBar("Velocity Buffer disabled")
	}

	override fun onWorldLoad() {
		synchronized(queue) {
			queue.clear()
			bufferedCount = 0
		}
		if (enabled) {
			setEnabled(false)
		}
	}

	override fun onHudRender(gfx: GuiGraphicsExtractor) {
		if (queue.isEmpty()) {
			return
		}

		gfx.text(
			Minecraft.getInstance().font,
			"Buffered Packets : $bufferedCount",
			gui.position.x.toInt(),
			gui.position.y.toInt(),
			0xFFFFFFFF.toInt(),
			true
		)
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		synchronized(queue) {
			val player = Minecraft.getInstance().player ?: return false
			if (packet is ClientboundPlayerPositionPacket) {
				setEnabled(false)
				return false
			}

			if (isMotionPacket(packet, player)) {
				queue.add(packet)
				bufferedCount++
				playSound(0.5f, 0.5f)
				return true
			}

			if (!PASS_THROUGH_WHILE_BUFFERING.contains(packet.javaClass)) {
				return false
			}

			if (queue.isEmpty()) {
				return false
			}

			queue.add(packet)
			return true
		}
	}

	private fun popQueue() {
		if (Minecraft.getInstance().player == null) {
			return
		}

		synchronized(queue) {
			if (queue.isEmpty()) {
				return
			}

			while (queue.isNotEmpty()) {
				val packet = queue.poll()
				receivePacket(packet)
				val player = Minecraft.getInstance().player
				if (player != null && isMotionPacket(packet, player)) {
					bufferedCount--
					if (queue.none { isMotionPacket(it, player) }) {
						flush()
						if (enabled) {
							setEnabled(false)
						}
					}
					break
				}
			}
		}

		playSound(2.0f, 2.0f)
	}

	@Suppress("UNCHECKED_CAST")
	private fun receivePacket(packet: Packet<*>?) {
		val connection = Minecraft.getInstance().connection ?: return
		(packet as? Packet<ClientGamePacketListener>)?.handle(connection)
	}

	private fun isMotionPacket(packet: Packet<*>, player: LocalPlayer): Boolean =
		packet is ClientboundSetEntityMotionPacket && packet.id == player.id

	private fun flush() {
		synchronized(queue) {
			if (queue.isNotEmpty()) {
				queue.forEach(::receivePacket)
			}
			queue.clear()
			bufferedCount = 0
		}
	}

	private fun playSound(volume: Float, pitch: Float) {
		Minecraft.getInstance().soundManager.play(
			SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), volume, pitch)
		)
	}

	private companion object {
		private val PASS_THROUGH_WHILE_BUFFERING = setOf<Class<out Packet<*>>>(
			ClientboundPingPacket::class.java,
			ClientboundBundlePacket::class.java
		)
	}
}
