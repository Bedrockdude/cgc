package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class BonzoNode(
	pos: Pos = Pos(),
	private val yaw: Float = 0.0f,
	private val pitch: Float = 0.0f,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.bonzo(yaw, pitch)
		return true
	}

	override fun name(): String =
		"bonzo"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		24

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("yaw", yaw)
		json.addProperty("pitch", pitch)
		return json
	}

	companion object {
		val COLOUR = Colour(255, 80, 150)

		fun supply(player: LocalPlayer, args: String): BonzoNode =
			BonzoNode(Pos(player.position()), player.yRot, player.xRot)
	}
}
