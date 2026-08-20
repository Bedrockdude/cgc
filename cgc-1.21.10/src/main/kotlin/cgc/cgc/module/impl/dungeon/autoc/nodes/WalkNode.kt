package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class WalkNode(
	pos: Pos = Pos(),
	private val yaw: Float = 0.0f,
	private val pitch: Float = 0.0f,
	var activeForSeconds: Double = 0.0,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.startWalk(yaw, pitch, activeForSeconds)
		return true
	}

	override fun name(): String =
		"walk"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		6

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("yaw", yaw)
		json.addProperty("pitch", pitch)
		json.addProperty("activeFor", activeForSeconds)
		return json
	}

	companion object {
		val COLOUR = Colour(80, 230, 120)

		fun supply(player: LocalPlayer, args: String): WalkNode =
			WalkNode(Pos(player.position()), player.yRot, player.xRot)
	}
}
