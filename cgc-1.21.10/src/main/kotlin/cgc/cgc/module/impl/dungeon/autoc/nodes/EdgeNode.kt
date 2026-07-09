package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import net.minecraft.client.player.LocalPlayer

class EdgeNode(
	pos: Pos = Pos(),
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.edge()
		return true
	}

	override fun name(): String =
		"edge"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		36

	companion object {
		val COLOUR = Colour(255, 95, 135)

		fun supply(player: LocalPlayer, args: String): EdgeNode =
			EdgeNode(Pos(player.position()))
	}
}
