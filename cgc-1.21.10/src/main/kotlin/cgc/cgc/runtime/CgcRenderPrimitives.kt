package cgc.cgc.runtime

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.world.phys.AABB

object CgcRenderPrimitives {
	fun lineBox(poseStack: PoseStack, buffer: VertexConsumer, box: AABB, red: Float, green: Float, blue: Float, alpha: Float) {
		val x1 = box.minX.toFloat()
		val y1 = box.minY.toFloat()
		val z1 = box.minZ.toFloat()
		val x2 = box.maxX.toFloat()
		val y2 = box.maxY.toFloat()
		val z2 = box.maxZ.toFloat()

		line(poseStack, buffer, x1, y1, z1, x2, y1, z1, red, green, blue, alpha)
		line(poseStack, buffer, x2, y1, z1, x2, y1, z2, red, green, blue, alpha)
		line(poseStack, buffer, x2, y1, z2, x1, y1, z2, red, green, blue, alpha)
		line(poseStack, buffer, x1, y1, z2, x1, y1, z1, red, green, blue, alpha)
		line(poseStack, buffer, x1, y2, z1, x2, y2, z1, red, green, blue, alpha)
		line(poseStack, buffer, x2, y2, z1, x2, y2, z2, red, green, blue, alpha)
		line(poseStack, buffer, x2, y2, z2, x1, y2, z2, red, green, blue, alpha)
		line(poseStack, buffer, x1, y2, z2, x1, y2, z1, red, green, blue, alpha)
		line(poseStack, buffer, x1, y1, z1, x1, y2, z1, red, green, blue, alpha)
		line(poseStack, buffer, x2, y1, z1, x2, y2, z1, red, green, blue, alpha)
		line(poseStack, buffer, x2, y1, z2, x2, y2, z2, red, green, blue, alpha)
		line(poseStack, buffer, x1, y1, z2, x1, y2, z2, red, green, blue, alpha)
	}

	fun filledBox(poseStack: PoseStack, buffer: VertexConsumer, box: AABB, red: Float, green: Float, blue: Float, alpha: Float) {
		val x1 = box.minX.toFloat()
		val y1 = box.minY.toFloat()
		val z1 = box.minZ.toFloat()
		val x2 = box.maxX.toFloat()
		val y2 = box.maxY.toFloat()
		val z2 = box.maxZ.toFloat()

		quad(poseStack, buffer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, red, green, blue, alpha)
		quad(poseStack, buffer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, red, green, blue, alpha)
		quad(poseStack, buffer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, red, green, blue, alpha)
		quad(poseStack, buffer, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, red, green, blue, alpha)
		quad(poseStack, buffer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, red, green, blue, alpha)
		quad(poseStack, buffer, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, red, green, blue, alpha)
	}

	private fun line(
		poseStack: PoseStack,
		buffer: VertexConsumer,
		x1: Float,
		y1: Float,
		z1: Float,
		x2: Float,
		y2: Float,
		z2: Float,
		red: Float,
		green: Float,
		blue: Float,
		alpha: Float
	) {
		val dx = x2 - x1
		val dy = y2 - y1
		val dz = z2 - z1
		buffer.addVertex(poseStack.last(), x1, y1, z1)
			.setColor(red, green, blue, alpha)
			.setNormal(poseStack.last(), dx, dy, dz)
			.setLineWidth(1.0f)
		buffer.addVertex(poseStack.last(), x2, y2, z2)
			.setColor(red, green, blue, alpha)
			.setNormal(poseStack.last(), dx, dy, dz)
			.setLineWidth(1.0f)
	}

	private fun quad(
		poseStack: PoseStack,
		buffer: VertexConsumer,
		x1: Float,
		y1: Float,
		z1: Float,
		x2: Float,
		y2: Float,
		z2: Float,
		x3: Float,
		y3: Float,
		z3: Float,
		x4: Float,
		y4: Float,
		z4: Float,
		red: Float,
		green: Float,
		blue: Float,
		alpha: Float
	) {
		buffer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha)
		buffer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha)
		buffer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha)
		buffer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha)
	}
}
