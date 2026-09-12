package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class CrouchNode(
	pos: Pos = Pos(),
	private val seconds: Double = 0.0,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.crouch(seconds)
		return true
	}

	override fun name(): String =
		"crouch"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		22

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("seconds", seconds)
		return json
	}

	companion object {
		val COLOUR = Colour(85, 210, 170)

		fun supply(player: LocalPlayer, args: String): CrouchNode? {
			val seconds = args.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: return null
			return CrouchNode(Pos(player.position()), seconds)
		}
	}
}
