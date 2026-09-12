package cgc.cgc.runtime

import cgc.cgc.navigation.NavigationService
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet

object CgcRuntime {
	fun clientTickStart(client: Minecraft) {
		PacketOrderManager.onTickStart(client)
		MovementPlayback.tick(client)
		NavigationService.tick(client)
		InputScheduler.tick(client)
	}

	fun worldLoad() {
		MovementPlayback.clear()
		NavigationService.reset()
		InputScheduler.clear()
		PacketOrderManager.clear()
	}

	fun packetReceive(packet: Packet<*>) {
		PacketOrderManager.onPacketReceive(packet)
	}

	fun packetSend(packet: Packet<*>): Boolean =
		PacketOrderManager.onPacketSend(packet)
}
