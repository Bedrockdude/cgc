package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class CommandNode(
	pos: Pos = Pos(),
	private val command: String = "",
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.runCommand(command)
		return true
	}

	override fun name(): String =
		"command"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		22

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("command", command)
		return json
	}

	companion object {
		val COLOUR = Colour(255, 205, 70)

		fun supply(player: LocalPlayer, args: String): CommandNode? {
			val command = args.trim()
			if (command.isBlank()) {
				return null
			}
			return CommandNode(Pos(player.position()), command)
		}
	}
}
