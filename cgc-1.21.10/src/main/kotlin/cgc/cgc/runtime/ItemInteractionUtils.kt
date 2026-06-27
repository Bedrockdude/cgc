package cgc.cgc.runtime

import cgc.cgc.utils.ItemUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
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

		player.inventory.selectedSlot = slot
		return true
	}

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
}
