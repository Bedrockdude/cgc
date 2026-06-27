package cgc.cgc.module.impl.render

import cgc.cgc.data.Colour
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.BooleanSetting
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class EnderPearlTrajectory : CgcModule(
	id = "EnderPearlTrajectory",
	displayName = "Ender Pearl Trajectory",
	category = ModuleCategory.RENDER,
	description = "Renders the predicted ender pearl path.",
	defaultEnabled = false
), WorldRenderExtractModule {
	private val trajectory = BooleanSetting("Trajectory", true)

	init {
		registerProperty(trajectory)
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val level = client.level ?: return
		if (!trajectory.value || !isHoldingEnderPearl(player)) {
			return
		}

		val tickDelta = client.deltaTracker.getGameTimeDeltaPartialTick(false)
		val points = calculatePearlTrajectory(level, player, tickDelta)
		if (points.size > 1) {
			renderLineStrip(context, points, TRAJECTORY_COLOR)
		}
	}

	private fun isHoldingEnderPearl(player: LocalPlayer): Boolean =
		player.mainHandItem.`is`(Items.ENDER_PEARL) || player.offhandItem.`is`(Items.ENDER_PEARL)

	private fun calculatePearlTrajectory(level: ClientLevel, player: LocalPlayer, tickDelta: Float): List<Vec3> {
		val points = arrayListOf<Vec3>()
		val look = player.getViewVector(tickDelta).normalize()
		val right = sideOffset(look)
		var pos = player.getEyePosition(tickDelta)
			.add(0.0, -0.10000000149011612, 0.0)
			.add(right)
		var velocity = look.scale(PEARL_POWER)
		points.add(pos)

		repeat(TRAJECTORY_STEPS) {
			val next = pos.add(velocity)
			val hit = level.clip(
				ClipContext(
					pos,
					next,
					ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE,
					player
				)
			)

			if (hit.type != HitResult.Type.MISS) {
				points.add(hit.location)
				return points
			}

			points.add(next)
			pos = next
			velocity = velocity.scale(PEARL_DRAG).add(0.0, -PEARL_GRAVITY, 0.0)
		}

		return points
	}

	private fun sideOffset(look: Vec3): Vec3 {
		val crossed = look.cross(UP)
		if (crossed.lengthSqr() < 1.0E-6) {
			return Vec3(RIGHT_SIDE_OFFSET, 0.0, 0.0)
		}
		return crossed.normalize().scale(RIGHT_SIDE_OFFSET)
	}

	private fun renderLineStrip(context: LevelRenderContext, points: List<Vec3>, color: Colour) {
		val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
		val matrices = context.poseStack()
		val buffer = context.bufferSource().getBuffer(RenderTypes.lines())
		matrices.pushPose()
		matrices.translate(-camera.x, -camera.y, -camera.z)

		for (i in 0 until points.lastIndex) {
			val a = points[i]
			val b = points[i + 1]
			val normal = b.subtract(a).normalForLine()
			buffer.addVertex(matrices.last(), a.x.toFloat(), a.y.toFloat(), a.z.toFloat())
				.setColor(color.red, color.green, color.blue, color.alpha)
				.setNormal(matrices.last(), normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
			buffer.addVertex(matrices.last(), b.x.toFloat(), b.y.toFloat(), b.z.toFloat())
				.setColor(color.red, color.green, color.blue, color.alpha)
				.setNormal(matrices.last(), normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
		}

		matrices.popPose()
	}

	private fun Vec3.normalForLine(): Vec3 {
		val length = sqrt(lengthSqr())
		if (length < 1.0E-6) {
			return UP
		}
		return Vec3(x / length, y / length, z / length)
	}

	private companion object {
		private const val TRAJECTORY_STEPS = 120
		private const val PEARL_POWER = 1.5
		private const val PEARL_GRAVITY = 0.03
		private const val PEARL_DRAG = 0.99
		private const val RIGHT_SIDE_OFFSET = 0.28
		private val TRAJECTORY_COLOR = Colour(170, 70, 255)
		private val UP = Vec3(0.0, 1.0, 0.0)
	}
}
