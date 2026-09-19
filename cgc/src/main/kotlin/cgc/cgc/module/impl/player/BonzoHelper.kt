package cgc.cgc.module.impl.player

import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketReceiveModule
import cgc.cgc.module.PacketSendModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.utils.ChatUtils
import cgc.cgc.utils.ItemUtils
import cgc.cgc.utils.TickFreeze
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult

class BonzoHelper : CgcModule(
	id = "BonzoHelper",
	displayName = "Bonzo helper",
	category = ModuleCategory.PLAYER,
	description = "Briefly freezes client ticks after using a Bonzo Staff.",
	defaultEnabled = false
), PacketSendModule, PacketReceiveModule, ClientTickModule, WorldLoadModule {
	private val awaitVelocity = BooleanSetting("Await Velocity", false)
	private val timeout = NumberSetting("Timeout", 0.0, 2000.0, 500.0, 10.0, " ms")
	private val time = NumberSetting("Time", 0.0, 500.0, 100.0, 1.0, " ms")

	private var awaitingVelocity = false
	private var sentAt = 0L
	private var timeoutAt = 0L

	init {
		registerProperty(awaitVelocity, timeout, time)
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		if (packet !is ServerboundUseItemPacket || packet.hand != InteractionHand.MAIN_HAND) {
			return false
		}

		if (ItemUtils.skyBlockId(player.mainHandItem) !in BONZO_STAFF_IDS) {
			return false
		}

		if (awaitVelocity.value && client.hitResult is BlockHitResult && player.xRot >= 70.0f) {
			awaitingVelocity = true
			sentAt = System.currentTimeMillis()
			timeoutAt = sentAt + timeout.value.toLong()
			TickFreeze.freeze(timeout.value.toLong())
		} else {
			TickFreeze.freeze(time.value.toLong())
		}

		return false
	}

	override fun onPacketReceive(packet: Packet<*>): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		if (packet is ClientboundSetEntityMotionPacket && packet.id == player.id && awaitingVelocity) {
			TickFreeze.unfreeze()
			clearRuntime()
		}

		return false
	}

	override fun onClientTick(client: Minecraft) {
		if (awaitingVelocity && timeoutAt > 0L && System.currentTimeMillis() >= timeoutAt) {
			TickFreeze.unfreeze()
			clearRuntime()
			ChatUtils.chat("${ChatFormatting.YELLOW}Bonzo Helper » ${ChatFormatting.RESET}Reached bonzo timeout!")
		}
	}

	override fun onWorldLoad() {
		TickFreeze.unfreeze()
		clearRuntime()
	}

	override fun onDisable() {
		TickFreeze.unfreeze()
		clearRuntime()
	}

	override fun reset() {
		clearRuntime()
	}

	private fun clearRuntime() {
		awaitingVelocity = false
		sentAt = 0L
		timeoutAt = 0L
	}

	private companion object {
		private val BONZO_STAFF_IDS = setOf("BONZO_STAFF", "STARRED_BONZO_STAFF")
	}
}
