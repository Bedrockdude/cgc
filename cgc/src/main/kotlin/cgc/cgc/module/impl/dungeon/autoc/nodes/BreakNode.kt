package cgc.cgc.module.impl.dungeon.autoc.nodes

import cgc.cgc.data.Colour
import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.AutoCNode
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeContext
import cgc.cgc.module.impl.dungeon.autoc.AutoCNodeUtils
import cgc.cgc.runtime.CgcRenderer3D
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

class BreakNode(
	pos: Pos = Pos(),
	val zeroTick: Boolean = true,
	val recordSeconds: Double = DEFAULT_RECORD_SECONDS,
	private val blocks: MutableList<Pos> = mutableListOf(),
	val notMoving: Boolean = false,
	radius: Float = AutoCNode.DEFAULT_RADIUS
) : AutoCNode(pos, radius) {
	override fun run(player: LocalPlayer, playerPos: Pos, context: AutoCNodeContext): Boolean =
		context.breakBlocks(blocks, zeroTick, notMoving)

	override fun render(depth: Boolean) {
		super.render(depth)
		val level = Minecraft.getInstance().level ?: return
		for (block in blocks) {
			val bp = block.asBlockPos()
			val state = level.getBlockState(bp)
			val shape = state.getShape(level, bp)
			if (!shape.isEmpty) {
				CgcRenderer3D.outlineBox(shape.bounds().move(bp), COLOUR, depth)
			} else {
				CgcRenderer3D.outlineBox(AABB(bp), COLOUR, depth)
			}
		}
	}

	override fun name(): String =
		"break"

	override fun colour(): Colour =
		COLOUR

	override fun priority(): Int =
		32

	override fun serialize(): JsonObject {
		val json = super.serialize()
		json.addProperty("zeroTick", zeroTick)
		json.addProperty("recordSeconds", recordSeconds)
		json.addProperty("notMoving", notMoving)
		val array = JsonArray()
		blocks.forEach { array.add(AutoCNodeUtils.writePos(it)) }
		json.add("blocks", array)
		return json
	}

	fun addBlock(pos: BlockPos): Boolean {
		if (blocks.any { it.asBlockPos() == pos }) {
			return false
		}
		blocks.add(Pos(pos))
		return true
	}

	fun blockCount(): Int =
		blocks.size

	fun blockList(): List<Pos> =
		blocks.toList()

	companion object {
		val COLOUR = Colour(255, 255, 80)
		private const val DEFAULT_RECORD_SECONDS = 3.0

		fun supply(player: LocalPlayer, args: String): BreakNode? {
			val split = args.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
			if (split.size !in 2..3) {
				return null
			}
			val zeroTick = when (split[0].lowercase()) {
				"true" -> true
				"false" -> false
				else -> return null
			}
			val seconds = split[1].toDoubleOrNull() ?: return null
			if (seconds <= 0.0) {
				return null
			}
			val notMoving = split.getOrNull(2)?.equals("notMoving", ignoreCase = true) ?: false
			if (split.size == 3 && !notMoving) {
				return null
			}
			return BreakNode(Pos(player.position()), zeroTick, seconds, notMoving = notMoving)
		}
	}
}
