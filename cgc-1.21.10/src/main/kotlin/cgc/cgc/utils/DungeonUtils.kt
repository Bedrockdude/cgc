package cgc.cgc.utils

import cgc.cgc.data.Phase7
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object DungeonUtils {
	private val s1Box = AABB(89.0, 0.0, 30.0, 113.0, 255.0, 122.0)
	private val s2Box = AABB(19.0, 0.0, 121.0, 111.0, 255.0, 145.0)
	private val s3Box = AABB(-6.0, 0.0, 50.0, 19.0, 255.0, 143.0)
	private val s4Box = AABB(-2.0, 0.0, 27.0, 90.0, 255.0, 51.0)
	private val bossBox = AABB(134.0, 0.0, 147.0, -8.0, 254.0, -8.0)

	fun getF7Phase(): Phase7 {
		val player = Minecraft.getInstance().player ?: return Phase7.UNKNOWN
		val y = player.position().y
		return when {
			y > 210.0 -> Phase7.P1
			y > 155.0 -> Phase7.P2
			y > 100.0 -> Phase7.P3
			y > 45.0 -> Phase7.P4
			else -> Phase7.P5
		}
	}

	fun isPositionInF7Boss(pos: Vec3): Boolean =
		bossBox.contains(pos)

	fun getP3Section(pos: Vec3? = Minecraft.getInstance().player?.position()): Phase7 =
		when {
			pos == null -> Phase7.UNKNOWN
			s1Box.contains(pos) -> Phase7.S1
			s2Box.contains(pos) -> Phase7.S2
			s3Box.contains(pos) -> Phase7.S3
			s4Box.contains(pos) -> Phase7.S4
			else -> Phase7.UNKNOWN
		}

	fun isPhase(phase: Phase7): Boolean =
		getF7Phase() == phase
}
