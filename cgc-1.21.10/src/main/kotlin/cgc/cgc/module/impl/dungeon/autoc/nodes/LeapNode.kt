package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.DungeonClass
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.LeapCounter
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer

class LeapNode(
	pos: Pos = Pos(),
	private val clazz: DungeonClass = DungeonClass.NONE,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.leap(clazz)
		return true
	}

	override fun name(): String =
		"leap"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		30

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("class", clazz.displayName)
		return json
	}

	fun className(): String =
		clazz.displayName

	companion object {
		val COLOUR = Colour(70, 255, 210)

		fun supply(player: LocalPlayer, args: String): LeapNode? {
			val clazz = LeapCounter.parseClass(args.trim())
			if (clazz == DungeonClass.NONE) {
				return null
			}
			return LeapNode(Pos(player.position()), clazz)
		}
	}
}
