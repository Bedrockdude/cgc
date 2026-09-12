package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class LookNode(
	pos: Pos = Pos(),
	private val yaw: Float = 0.0f,
	private val pitch: Float = 0.0f,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.smoothLook(yaw, pitch)
		return true
	}

	override fun name(): String =
		"look"

	override fun colour(): Colour =
		COLOUR

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("yaw", yaw)
		json.addProperty("pitch", pitch)
		return json
	}

	companion object {
		val COLOUR = Colour(100, 220, 255)

		fun supply(player: LocalPlayer): LookNode =
			LookNode(Pos(player.position()), player.yRot, player.xRot)
	}
}
