package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import cgc.cgc.runtime.ItemInteractionUtils
import cgc.cgc.utils.ItemUtils
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class UseNode(
	pos: Pos = Pos(),
	private val yaw: Float = 0.0f,
	private val pitch: Float = 0.0f,
	private val skyBlockId: String = "",
	private val itemId: String = "",
	private val displayName: String = "",
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.useItem(yaw, pitch, skyBlockId, itemId, displayName)
		return true
	}

	override fun name(): String =
		"use"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		24

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("yaw", yaw)
		json.addProperty("pitch", pitch)
		json.addProperty("skyBlockId", skyBlockId)
		json.addProperty("itemId", itemId)
		json.addProperty("displayName", displayName)
		return json
	}

	companion object {
		val COLOUR = Colour(180, 180, 180)

		fun supply(player: LocalPlayer, args: String): UseNode? {
			val stack = player.inventory.selectedItem
			if (stack.isEmpty) {
				return null
			}
			val skyBlockId = ItemUtils.skyBlockId(stack)
			val itemId = ItemInteractionUtils.itemId(stack)
			if (skyBlockId.isBlank() && itemId.isBlank()) {
				return null
			}
			return UseNode(
				pos = Pos(player.position()),
				yaw = player.yRot,
				pitch = player.xRot,
				skyBlockId = skyBlockId,
				itemId = itemId,
				displayName = stack.hoverName.string
			)
		}
	}
}
