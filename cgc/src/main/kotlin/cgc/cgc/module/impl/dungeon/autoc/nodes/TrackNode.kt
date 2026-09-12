package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeUtils
import cgc.cgc.runtime.CgcRenderer3D
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.AABB

class TrackNode(
	pos: Pos = Pos(),
	private val seconds: Double = 0.0,
	private val block: Pos = Pos(),
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.startTrack(seconds, block.asBlockPos())
		return true
	}

	override fun render(depth: Boolean) {
		super.render(depth)
		val bp = block.asBlockPos()
		val level = Minecraft.getInstance().level ?: return
		val shape = level.getBlockState(bp).getShape(level, bp)
		CgcRenderer3D.outlineBox(if (shape.isEmpty) AABB(bp) else shape.bounds().move(bp), COLOUR, depth)
	}

	override fun name(): String = "track"

	override fun colour(): Colour = COLOUR

	override fun priority(): Int = 25

	override fun serialize(): JsonObject = super.serialize().also {
		it.addProperty("seconds", seconds)
		it.add("block", AutoCNodeUtils.writePos(block))
	}

	companion object {
		val COLOUR = Colour(230, 120, 210)

		fun supply(player: LocalPlayer, args: String): TrackNode? {
			val values = args.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
			if (values.size != 4) return null
			val seconds = values[0].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
			val x = values[1].toIntOrNull() ?: return null
			val y = values[2].toIntOrNull() ?: return null
			val z = values[3].toIntOrNull() ?: return null
			return TrackNode(Pos(player.position()), seconds, Pos(x.toDouble(), y.toDouble(), z.toDouble()))
		}
	}
}
