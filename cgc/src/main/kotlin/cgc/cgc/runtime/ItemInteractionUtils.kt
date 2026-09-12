package cgc.cgc.runtime

import cgc.cgc.utils.ItemUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object ItemInteractionUtils {
	fun selectHotbarItem(vararg skyBlockIds: String): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		if (skyBlockIds.any { ItemUtils.skyBlockId(player.inventory.selectedItem).equals(it, ignoreCase = true) }) {
			return true
		}

		val slot = (0..8).firstOrNull { slot ->
			val stack = player.inventory.getItem(slot)
			skyBlockIds.any { ItemUtils.skyBlockId(stack).equals(it, ignoreCase = true) }
		} ?: return false

		selectSlot(slot)
		return true
	}

	fun selectHotbarItem(skyBlockId: String, itemId: String): Boolean {
		return selectHotbarItem(skyBlockId, itemId, "")
	}

	fun selectHotbarItem(skyBlockId: String, itemId: String, displayName: String): Boolean {
		if (skyBlockId.isBlank() && itemId.isBlank()) {
			return false
		}

		val player = Minecraft.getInstance().player ?: return false
		if (matches(player.inventory.selectedItem, skyBlockId, itemId, displayName)) {
			return true
		}

		val slot = (0..8).firstOrNull { slot ->
			matches(player.inventory.getItem(slot), skyBlockId, itemId, displayName)
		} ?: return false

		selectSlot(slot)
		return true
	}

	fun itemId(stack: ItemStack): String =
		BuiltInRegistries.ITEM.getKey(stack.item).toString()

	fun useHeldAir(): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		val gameMode = client.gameMode ?: return false
		gameMode.useItem(player, InteractionHand.MAIN_HAND)
		player.swing(InteractionHand.MAIN_HAND)
		return true
	}

	fun useItemBySkyBlockId(itemId: String, yaw: Float? = null, pitch: Float? = null): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		if (!selectHotbarItem(itemId)) {
			return false
		}
		yaw?.let { player.yRot = it }
		pitch?.let { player.xRot = it.coerceIn(-90.0f, 90.0f) }
		return useHeldAir()
	}

	fun useBlockTargetWithItem(target: Vec3, vararg skyBlockIds: String): Boolean {
		val client = Minecraft.getInstance()
		val player = client.player ?: return false
		val gameMode = client.gameMode ?: return false
		if (!selectHotbarItem(*skyBlockIds)) {
			return false
		}

		RotationUtils.lookAt(target)
		val hit = player.pick(5.0, 0.0f, false)
		return if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK && hit.blockPos == BlockPos.containing(target)) {
			gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
			player.swing(InteractionHand.MAIN_HAND)
			true
		} else {
			useHeldAir()
		}
	}

	private fun selectSlot(slot: Int) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		if (player.inventory.selectedSlot == slot) {
			return
		}
		player.inventory.selectedSlot = slot
		client.connection?.connection?.send(ServerboundSetCarriedItemPacket(slot))
	}

	private fun matches(stack: ItemStack, skyBlockId: String, itemId: String, displayName: String = ""): Boolean {
		if (stack.isEmpty) {
			return false
		}
		if (skyBlockId.isNotBlank()) {
			return ItemUtils.skyBlockId(stack).equals(skyBlockId, ignoreCase = true)
		}
		if (displayName.isNotBlank() && itemId.isNotBlank()) {
			return itemId(stack).equals(itemId, ignoreCase = true) && stack.hoverName.string.equals(displayName, ignoreCase = true)
		}
		return itemId.isNotBlank() && itemId(stack).equals(itemId, ignoreCase = true)
	}
}
