package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import cgc.cgc.module.impl.dungeon.autoc.AutoCStrafeDirection
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class StrafeNode(
	pos: Pos = Pos(),
	private val direction: AutoCStrafeDirection = AutoCStrafeDirection.W,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.startStrafe(direction)
		return true
	}

	override fun name(): String =
		"strafe"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		6

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("direction", direction.commandName)
		return json
	}

	companion object {
		val COLOUR = Colour(80, 180, 255)

		fun supply(player: LocalPlayer, args: String): StrafeNode? {
			val direction = AutoCStrafeDirection.parse(args.trim()) ?: return null
			return StrafeNode(Pos(player.position()), direction)
		}
	}
}
