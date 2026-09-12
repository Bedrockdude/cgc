package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class WaitNode(
	pos: Pos = Pos(),
	private val seconds: Double = 0.0,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.startWait(seconds)
		return true
	}

	override fun name(): String = "wait"

	override fun colour(): Colour = COLOUR

	override fun priority(): Int = 24

	override fun serialize(): JsonObject = super.serialize().also { it.addProperty("seconds", seconds) }

	companion object {
		val COLOUR = Colour(235, 190, 75)

		fun supply(player: LocalPlayer, args: String): WaitNode? {
			val seconds = args.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
			return WaitNode(Pos(player.position()), seconds)
		}
	}
}
