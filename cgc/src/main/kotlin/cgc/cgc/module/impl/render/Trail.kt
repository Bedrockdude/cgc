package cgc.cgc.module.impl.render

import cgc.cgc.data.Colour
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.PacketSendModule
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.runtime.CgcRenderer3D
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class Trail : CgcModule(
	id = "Trail",
	displayName = "Trail",
	category = ModuleCategory.RENDER,
	description = "Renders the path from outgoing movement packets.",
	defaultEnabled = false
), PacketSendModule, WorldRenderExtractModule, WorldLoadModule {
	private val mode = ModeSetting("Trail Type", "Line", listOf("Tick", "Line"))
	private val colour = ColourSetting("Start Colour", Colour(0, 0, 255), supplier = { mode.isMode("Line") })
	private val endColour = ColourSetting("End Colour", Colour(0, 0, 255), supplier = { mode.isMode("Line") })
	private val airColour = ColourSetting("Air Colour", Colour(0, 255, 255), supplier = { mode.isMode("Tick") })
	private val groundColour = ColourSetting("Ground Colour", Colour(255, 0, 0), supplier = { mode.isMode("Tick") })
	private val trailLength = NumberSetting("Trail Length", 5.0, 400.0, 40.0, 1.0)
	private val trailWidth = NumberSetting("Trail Width", 0.01, 0.2, 0.05, 0.01)
	private val depth = BooleanSetting("Depth", false)

	private val packets = arrayListOf<C04>()
	private var delayedC04: C04? = null

	init {
		registerProperty(
			trailLength,
			trailWidth,
			mode,
			colour,
			endColour,
			airColour,
			groundColour,
			depth
		)
	}

	override fun onPacketSend(packet: Packet<*>): Boolean {
		if (packet !is ServerboundMovePlayerPacket) {
			return false
		}

		delayedC04?.let {
			packets.add(it)
			while (packets.size > trailLength.value.toInt()) {
				packets.removeAt(0)
			}
			delayedC04 = null
		}

		if (!packet.hasPosition()) {
			return false
		}

		val pos = Vec3(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0))
		if (packets.lastOrNull()?.pos == pos) {
			return false
		}

		delayedC04 = C04(pos, packet.isOnGround)
		return false
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		when {
			mode.isMode("Tick") -> drawTicks()
			mode.isMode("Line") -> drawLine()
		}
	}

	override fun onWorldLoad() {
		packets.clear()
		delayedC04 = null
	}

	private fun drawTicks() {
		val boxSize = trailWidth.value.toDouble() * 0.5
		for (packet in packets) {
			val pos = packet.pos
			val aabb = AABB(
				pos.x - boxSize,
				pos.y,
				pos.z - boxSize,
				pos.x + boxSize,
				pos.y + boxSize * 2.0,
				pos.z + boxSize
			)
			CgcRenderer3D.outlineBox(aabb, if (packet.onGround) groundColour.value else airColour.value, depth.value)
		}
	}

	private fun drawLine() {
		CgcRenderer3D.lineList(packets.map { it.pos }, colour.value, endColour.value, depth.value)
	}

	private data class C04(val pos: Vec3, val onGround: Boolean)
}
