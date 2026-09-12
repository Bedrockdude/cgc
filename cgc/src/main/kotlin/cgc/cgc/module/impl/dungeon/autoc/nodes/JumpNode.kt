package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import net.minecraft.client.player.LocalPlayer

class JumpNode(
	pos: Pos = Pos(),
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.jump()
		return true
	}

	override fun name(): String =
		"jump"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		34

	companion object {
		val COLOUR = Colour(255, 125, 60)

		fun supply(player: LocalPlayer, args: String): JumpNode =
			JumpNode(Pos(player.position()))
	}
}
