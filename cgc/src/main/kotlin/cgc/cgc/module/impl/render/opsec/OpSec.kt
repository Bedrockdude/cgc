package cgc.cgc.module.impl.render.opsec

import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketPostReceiveModule
import cgc.cgc.module.setting.group.GroupSetting
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket

class OpSec : CgcModule(
	id = "OpSec",
	displayName = "OpSec",
	category = ModuleCategory.RENDER,
	description = "Hides selected identifying text from the local client.",
	defaultEnabled = false
), PacketPostReceiveModule {
	private val nickHider = GroupSetting("Nick Hider", NickHider(this))
	private val serverIdHider = GroupSetting("Server ID Hider", ServerIdHider(this))

	init {
		registerProperty(nickHider, serverIdHider)
	}

	override fun onPacketPostReceive(packet: Packet<*>) {
		if (packet is ClientboundSetPlayerTeamPacket) {
			serverIdHider.value.onPostHandleSetPlayerTeam(packet)
		}
	}
}
