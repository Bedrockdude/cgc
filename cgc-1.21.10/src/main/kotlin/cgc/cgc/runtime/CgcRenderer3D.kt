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
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object CgcRenderer3D {
	private val lineTasks = arrayListOf<BoxTask>()
	private val circleTasks = arrayListOf<CircleTask>()
	private val filledTasks = arrayListOf<BoxTask>()
	private val circleCache = hashMapOf<Int, CircleData>()

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

	fun circle(pos: Vec3, depth: Boolean, radius: Float, colour: Colour, slices: Int) {
		if (radius <= 0.0f || slices < 3) {
			return
		}
		circleTasks.add(CircleTask(pos, depth, radius, colour, slices))
	}

	fun render(context: LevelRenderContext) {
		if (lineTasks.isEmpty() && circleTasks.isEmpty() && filledTasks.isEmpty()) {
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
		circleTasks.clear()
		filledTasks.clear()
	}

	private fun renderLines(source: MultiBufferSource.BufferSource, stack: PoseStack) {
		renderLineBatch(source, stack, RenderTypes.secondaryBlockOutline(), depth = true)
		renderLineBatch(source, stack, RenderTypes.linesTranslucent(), depth = false)
	}

	private fun renderLineBatch(source: MultiBufferSource.BufferSource, stack: PoseStack, type: RenderType, depth: Boolean) {
		val boxes = lineTasks.filter { it.depth == depth }
		val circles = circleTasks.filter { it.depth == depth }
		if (boxes.isEmpty() && circles.isEmpty()) {
			return
		}

		val buffer = source.getBuffer(type)
		for (task in boxes) {
			renderOutlineBox(stack.last(), buffer, task.aabb, task.colour)
		}
		for (task in circles) {
			renderCircle(stack.last(), buffer, task)
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

	private fun renderCircle(pose: PoseStack.Pose, buffer: VertexConsumer, task: CircleTask) {
		val data = circleCache.getOrPut(task.slices) { CircleData(task.slices) }
		val matrix = pose.pose()
		val alpha = task.colour.alpha / 255.0f
		val red = task.colour.red / 255.0f
		val green = task.colour.green / 255.0f
		val blue = task.colour.blue / 255.0f
		val y = task.pos.y.toFloat()

		for (i in 0 until task.slices) {
			val next = (i + 1) % task.slices
			val x1 = task.pos.x.toFloat() + data.x[i] * task.radius
			val z1 = task.pos.z.toFloat() + data.z[i] * task.radius
			val x2 = task.pos.x.toFloat() + data.x[next] * task.radius
			val z2 = task.pos.z.toFloat() + data.z[next] * task.radius
			val nx = data.nx[i]
			val nz = data.nz[i]

			buffer.addVertex(matrix, x1, y, z1)
				.setColor(red, green, blue, alpha)
				.setNormal(nx, 0.0f, nz)
				.setLineWidth(LINE_WIDTH)
			buffer.addVertex(matrix, x2, y, z2)
				.setColor(red, green, blue, alpha)
				.setNormal(nx, 0.0f, nz)
				.setLineWidth(LINE_WIDTH)
		}
	}

	private data class BoxTask(val aabb: AABB, val colour: Colour, val depth: Boolean)
	private data class CircleTask(val pos: Vec3, val depth: Boolean, val radius: Float, val colour: Colour, val slices: Int)

	private class CircleData(slices: Int) {
		val x = FloatArray(slices)
		val z = FloatArray(slices)
		val nx = FloatArray(slices)
		val nz = FloatArray(slices)

		init {
			val step = (Math.PI * 2.0 / slices).toFloat()
			for (i in 0 until slices) {
				val angle = i * step
				x[i] = cos(angle)
				z[i] = sin(angle)
			}

			for (i in 0 until slices) {
				val next = (i + 1) % slices
				nx[i] = x[next] - x[i]
				nz[i] = z[next] - z[i]
			}
		}
	}

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
