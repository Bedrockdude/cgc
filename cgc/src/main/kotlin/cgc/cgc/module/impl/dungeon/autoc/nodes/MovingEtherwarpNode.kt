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

/** Etherwarps without taking ownership of an active walk action. */
class MovingEtherwarpNode(
	pos: Pos = Pos(),
	private val yaw: Float = 0.0f,
	private val pitch: Float = 0.0f,
	private val block: Pos = Pos(),
	private val target: Pos = Pos(),
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean {
		context.movingEtherwarp(yaw, pitch, block.asBlockPos(), target.asVec3())
		return true
	}

	override fun prepareAwaitSecret(context: AutoCNodeContext) {
		context.smoothLookAtBlock(yaw, pitch, block.asBlockPos())
	}

	override fun render(depth: Boolean) {
		super.render(depth)
		val level = Minecraft.getInstance().level ?: return
		val bp = block.asBlockPos()
		val shape = level.getBlockState(bp).getShape(level, bp)
		if (!shape.isEmpty) {
			CgcRenderer3D.outlineBox(shape.bounds().move(bp), COLOUR, depth)
		} else {
			CgcRenderer3D.outlineBox(AABB(bp), COLOUR, depth)
		}
	}

	override fun name(): String = "movingetherwarp"

	override fun colour(): Colour = COLOUR

	override fun priority(): Int = 28

	override fun routeDelayTicks(): Int = 1

	override fun serialize(): JsonObject = super.serialize().also { json ->
		json.addProperty("yaw", yaw)
		json.addProperty("pitch", pitch)
		json.add("block", AutoCNodeUtils.writePos(block))
		json.add("target", AutoCNodeUtils.writePos(target))
	}

	companion object {
		val COLOUR = Colour(40, 190, 255)

		fun supply(player: LocalPlayer, args: String): MovingEtherwarpNode? {
			val looked = AutoCNodeUtils.lookedBlock(player) ?: return null
			return MovingEtherwarpNode(
				pos = Pos(player.position()),
				yaw = looked.yaw,
				pitch = looked.pitch,
				block = Pos(looked.blockPos),
				target = Pos(looked.hitPos)
			)
		}
	}
}
