package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.utils.ItemUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object AutoPuzzleItems {
	data class Shortbow(val slot: Int, val terminator: Boolean)
	private val shortbowBySkyBlockId = hashMapOf<String, Boolean>()

	fun firstShortbow(context: AutoPuzzleContext): Shortbow? =
		(0..8).firstNotNullOfOrNull { slot ->
			val stack = context.player.inventory.getItem(slot)
			if (isShortbow(stack)) Shortbow(slot, ItemUtils.skyBlockId(stack).equals("TERMINATOR", true)) else null
		}

	fun firstEtherwarp(context: AutoPuzzleContext): Int? =
		(0..8).firstOrNull { ItemUtils.isEtherwarp(context.player.inventory.getItem(it)) }

	fun firstWaterboardClickItem(context: AutoPuzzleContext): Int? =
		firstShortbow(context)?.slot ?: (0..8).firstOrNull { isDungeonBreaker(context.player.inventory.getItem(it)) }

	fun select(context: AutoPuzzleContext, slot: Int): Boolean {
		if (slot !in 0..8 || context.player.inventory.getItem(slot).isEmpty) return false
		if (context.player.inventory.selectedSlot == slot) return true
		val connection = context.client.connection?.connection ?: return false
		context.player.inventory.selectedSlot = slot
		connection.send(ServerboundSetCarriedItemPacket(slot))
		return true
	}

	fun isShortbow(stack: ItemStack): Boolean {
		if (stack.isEmpty || !stack.`is`(Items.BOW)) return false
		val id = ItemUtils.skyBlockId(stack).uppercase(java.util.Locale.ROOT)
		if (id.isNotBlank()) return shortbowBySkyBlockId.getOrPut(id) { hasShortbowLore(stack) }
		return hasShortbowLore(stack)
	}

	private fun hasShortbowLore(stack: ItemStack): Boolean =
		stack.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY)
			.lines()
			.any { it.string.contains(SHORTBOW_LORE, ignoreCase = true) }

	fun isDungeonBreaker(stack: ItemStack): Boolean {
		if (stack.isEmpty) return false
		if (ItemUtils.skyBlockId(stack).equals("DUNGEONBREAKER", ignoreCase = true)) return true
		return stack.hoverName.string.trim().equals("Dungeon Breaker", ignoreCase = true)
	}

	private const val SHORTBOW_LORE = "Shortbow: Instantly shoots!"
}
