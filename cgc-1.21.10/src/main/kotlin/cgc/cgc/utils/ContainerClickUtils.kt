package cgc.cgc.utils

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.network.HashedStack
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack

object ContainerClickUtils {
	fun sendWindowClick(slotNumber: Int, button: Int, containerInput: ContainerInput, player: Player, menu: AbstractContainerMenu) {
		val connection = Minecraft.getInstance().connection ?: return
		val slots = menu.slots
		val previousStacks = slots.map { it.item.copy() }

		menu.clicked(slotNumber, button, containerInput, player)
		val changedSlots = Int2ObjectOpenHashMap<HashedStack>()
		for (index in slots.indices) {
			val before = previousStacks[index]
			val after = slots[index].item
			if (!ItemStack.matches(before, after)) {
				changedSlots.put(index, HashedStack.create(after, connection.decoratedHashOpsGenenerator()))
			}
		}

		val carried = HashedStack.create(menu.carried, connection.decoratedHashOpsGenenerator())
		connection.connection.send(
			ServerboundContainerClickPacket(
				menu.containerId,
				menu.stateId,
				slotNumber.toShort(),
				button.toByte(),
				containerInput,
				changedSlots,
				carried
			)
		)
	}

	fun close(menu: AbstractContainerMenu?) {
		val connection = Minecraft.getInstance().connection ?: return
		if (menu != null) {
			connection.connection.send(ServerboundContainerClosePacket(menu.containerId))
		}
	}
}
