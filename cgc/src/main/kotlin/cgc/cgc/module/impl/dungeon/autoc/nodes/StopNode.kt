package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer
import java.util.Locale

class StopNode(
	pos: Pos = Pos(),
	private val except: Set<String> = emptySet(),
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.stopActions(except)
		return true
	}

	override fun name(): String =
		"stop"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		100

	override fun serialize(): JsonObject {
		val json = super.serialize()
		if (except.isNotEmpty()) {
			val array = JsonArray()
			except.sorted().forEach(array::add)
			json.add("except", array)
		}
		return json
	}

	companion object {
		val COLOUR = Colour(255, 50, 50)

		fun supply(player: LocalPlayer, args: String): StopNode =
			StopNode(Pos(player.position()), parseExcept(args))

		private fun parseExcept(args: String): Set<String> =
			args.trim()
				.split(Regex("\\s+"))
				.asSequence()
				.filter { it.length > 1 && it.startsWith("n", ignoreCase = true) }
				.map { it.drop(1).lowercase(Locale.ROOT) }
				.filter { it.isNotBlank() }
				.toSet()
	}
}
