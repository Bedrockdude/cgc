package cgc.cgc.runtime

import cgc.cgc.data.Colour
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB

object CgcRenderer3D {
	private val lineTasks = arrayListOf<BoxTask>()
	private val filledTasks = arrayListOf<BoxTask>()

	fun outlineBox(aabb: AABB, colour: Colour, depth: Boolean) {
		lineTasks.add(BoxTask(aabb, colour, depth))
	}

	fun filledBox(aabb: AABB, colour: Colour, depth: Boolean) {
		filledTasks.add(BoxTask(aabb, colour, depth))
	}

	fun filledOutlineBox(aabb: AABB, fill: Colour, outline: Colour, depth: Boolean) {
		filledBox(aabb, fill, depth)
		outlineBox(aabb, outline, depth)
	}

	fun render(context: LevelRenderContext) {
		if (lineTasks.isEmpty() && filledTasks.isEmpty()) {
			return
		}

		val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
		val stack = context.poseStack()
		val source = context.bufferSource()

		stack.pushPose()
		stack.translate(-camera.x, -camera.y, -camera.z)

		renderLines(source, stack)
		renderFilled(source, stack)

		stack.popPose()
		clear()
	}

	fun clear() {
		lineTasks.clear()
		filledTasks.clear()
	}

	private fun renderLines(source: MultiBufferSource.BufferSource, stack: PoseStack) {
		renderLineBatch(source, stack, RenderTypes.secondaryBlockOutline(), depth = true)
		renderLineBatch(source, stack, RenderTypes.linesTranslucent(), depth = false)
	}

	private fun renderLineBatch(source: MultiBufferSource.BufferSource, stack: PoseStack, type: RenderType, depth: Boolean) {
		val tasks = lineTasks.filter { it.depth == depth }
		if (tasks.isEmpty()) {
			return
		}

		val buffer = source.getBuffer(type)
		for (task in tasks) {
			renderOutlineBox(stack.last(), buffer, task.aabb, task.colour)
		}
		source.endBatch(type)
	}

	private fun renderFilled(source: MultiBufferSource.BufferSource, stack: PoseStack) {
		val tasks = filledTasks
		if (tasks.isEmpty()) {
			return
		}

		val type = RenderTypes.debugFilledBox()
		val buffer = source.getBuffer(type)
		for (task in tasks) {
			addFilledBoxVertices(stack.last(), buffer, task.aabb, task.colour)
		}
		source.endBatch(type)
	}

	private fun renderOutlineBox(pose: PoseStack.Pose, buffer: VertexConsumer, aabb: AABB, colour: Colour) {
		val corners = corners(aabb)
		for (i in EDGE_PAIRS.indices step 2) {
			val first = EDGE_PAIRS[i] * 3
			val second = EDGE_PAIRS[i + 1] * 3
			val x0 = corners[first]
			val y0 = corners[first + 1]
			val z0 = corners[first + 2]
			val x1 = corners[second]
			val y1 = corners[second + 1]
			val z1 = corners[second + 2]
			val dx = x1 - x0
			val dy = y1 - y0
			val dz = z1 - z0

			buffer.addVertex(pose, x0, y0, z0)
				.setColor(colour.argb())
				.setNormal(pose, dx, dy, dz)
				.setLineWidth(LINE_WIDTH)
			buffer.addVertex(pose, x1, y1, z1)
				.setColor(colour.argb())
				.setNormal(pose, dx, dy, dz)
				.setLineWidth(LINE_WIDTH)
		}
	}

	private fun corners(aabb: AABB): FloatArray =
		floatArrayOf(
			aabb.minX.toFloat(), aabb.minY.toFloat(), aabb.minZ.toFloat(),
			aabb.maxX.toFloat(), aabb.minY.toFloat(), aabb.minZ.toFloat(),
			aabb.maxX.toFloat(), aabb.maxY.toFloat(), aabb.minZ.toFloat(),
			aabb.minX.toFloat(), aabb.maxY.toFloat(), aabb.minZ.toFloat(),
			aabb.minX.toFloat(), aabb.minY.toFloat(), aabb.maxZ.toFloat(),
			aabb.maxX.toFloat(), aabb.minY.toFloat(), aabb.maxZ.toFloat(),
			aabb.maxX.toFloat(), aabb.maxY.toFloat(), aabb.maxZ.toFloat(),
			aabb.minX.toFloat(), aabb.maxY.toFloat(), aabb.maxZ.toFloat()
		)

	private fun addFilledBoxVertices(pose: PoseStack.Pose, buffer: VertexConsumer, aabb: AABB, colour: Colour) {
		val matrix = pose.pose()
		val color = colour.argb()
		val minX = aabb.minX.toFloat()
		val minY = aabb.minY.toFloat()
		val minZ = aabb.minZ.toFloat()
		val maxX = aabb.maxX.toFloat()
		val maxY = aabb.maxY.toFloat()
		val maxZ = aabb.maxZ.toFloat()

		buffer.addVertex(matrix, minX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, minY, maxZ).setColor(color)
		buffer.addVertex(matrix, minX, maxY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, maxY, maxZ).setColor(color)
		buffer.addVertex(matrix, minX, maxY, maxZ).setColor(color)
		buffer.addVertex(matrix, minX, minY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, minY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, minY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, minZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, minZ).setColor(color)
		buffer.addVertex(matrix, maxX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, maxY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, maxX, minY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, minY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, minY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, minY, maxZ).setColor(color)
		buffer.addVertex(matrix, minX, maxY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, maxY, minZ).setColor(color)
		buffer.addVertex(matrix, minX, maxY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, minZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(color)
		buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(color)
	}

	private data class BoxTask(val aabb: AABB, val colour: Colour, val depth: Boolean)

	private val EDGE_PAIRS = intArrayOf(
		0, 1,
		1, 5,
		5, 4,
		4, 0,
		3, 2,
		2, 6,
		6, 7,
		7, 3,
		0, 3,
		1, 2,
		5, 6,
		4, 7
	)

	private const val LINE_WIDTH = 3.0f
}
