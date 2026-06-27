package cgc.cgc.utils

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items

class SpiritLeapMenu(
	private val modulePrefix: String,
	private val onSuccess: (String) -> Unit = {}
) {
	var target: String? = null
		private set
	private var openingGui = false
	private var windowOpen = false
	private var openingGuiSince = 0L
	private var container: AbstractContainerMenu? = null

	val isActive: Boolean
		get() = openingGui || windowOpen || container != null

	fun start(targetName: String): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		val gameMode = client.gameMode ?: return false
		target = targetName
		openingGui = true
		windowOpen = false
		openingGuiSince = System.currentTimeMillis()
		gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND)
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
		return true
	}

	fun handleOpenScreen(packet: ClientboundOpenScreenPacket): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		if (packet.containerId !in 1..100) {
			return false
		}

		if (openingGui && packet.title.string == SPIRIT_LEAP_TITLE) {
			openingGui = false
			windowOpen = true
			container = packet.type.create(packet.containerId, player.inventory)
			return true
		}

		if (openingGui) {
			clear()
		}
		return false
	}

	fun handleSetSlot(packet: ClientboundContainerSetSlotPacket): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		val menu = container ?: return false
		val targetName = target ?: return false
		if (packet.containerId !in 1..100
			|| !windowOpen
			|| openingGui
			|| menu.containerId != packet.containerId
			|| packet.slot < 11
		) {
			return false
		}

		menu.setItem(packet.slot, packet.stateId, packet.item)
		if (packet.slot > 16) {
			ChatUtils.chat("${ChatFormatting.RED}$modulePrefix failed to find player.")
			close()
			return true
		}

		val stack = packet.item
		if (!stack.`is`(Items.PLAYER_HEAD)) {
			return true
		}

		val name = ChatFormatting.stripFormatting(stack.hoverName.string) ?: return true
		if (!name.equals(targetName, ignoreCase = true)) {
			return true
		}

		ContainerClickUtils.sendWindowClick(packet.slot, 0, ContainerInput.CLONE, player, menu)
		onSuccess(targetName)
		clear()
		return true
	}

	fun tickTimeout(timeoutMs: Long) {
		if ((!openingGui && !windowOpen) || openingGuiSince == 0L) {
			return
		}

		if (System.currentTimeMillis() - openingGuiSince > timeoutMs) {
			ChatUtils.chat("${ChatFormatting.RED}$modulePrefix leap menu did not open.")
			close()
		}
	}

	fun close() {
		ContainerClickUtils.close(container)
		clear()
	}

	fun clear() {
		target = null
		openingGui = false
		windowOpen = false
		openingGuiSince = 0L
		container = null
	}

	private companion object {
		private const val SPIRIT_LEAP_TITLE = "Spirit Leap"
	}
}
