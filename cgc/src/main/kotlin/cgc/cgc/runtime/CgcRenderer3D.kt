package cgc.cgc.runtime

import cgc.cgc.data.Colour
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.gui.Font
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object CgcRenderer3D {
	private val lineTasks = arrayListOf<BoxTask>()
	private val circleTasks = arrayListOf<CircleTask>()
	private val ringTasks = arrayListOf<RingTask>()
	private val lineListTasks = arrayListOf<LineListTask>()
	private val filledTasks = arrayListOf<BoxTask>()
	private val worldTextTasks = arrayListOf<WorldTextTask>()
	private val circleCache = hashMapOf<Int, CircleData>()

	fun outlineBox(aabb: AABB, colour: Colour, depth: Boolean, width: Float = LINE_WIDTH) {
		if (!width.isFinite() || width <= 0.0f) {
			return
		}
		lineTasks.add(BoxTask(aabb, colour, depth, width))
	}

	fun filledBox(aabb: AABB, colour: Colour, depth: Boolean) {
		filledTasks.add(BoxTask(aabb, colour, depth))
	}

	fun filledOutlineBox(aabb: AABB, fill: Colour, outline: Colour, depth: Boolean, outlineWidth: Float = LINE_WIDTH) {
		filledBox(aabb, fill, depth)
		outlineBox(aabb, outline, depth, outlineWidth)
	}

	fun circle(pos: Vec3, depth: Boolean, radius: Float, colour: Colour, slices: Int) {
		if (radius <= 0.0f || slices < 3) {
			return
		}
		circleTasks.add(CircleTask(pos, depth, radius, colour, slices))
	}

	fun ring(pos: Vec3, depth: Boolean, radius: Float, colour: Colour, slices: Int = 64, layers: Int = 16) {
		if (radius <= 0.0f || slices < 3 || layers < 1) {
			return
		}
		ringTasks.add(RingTask(pos, depth, radius, colour, slices, layers))
	}

	fun lineList(points: List<Vec3>, start: Colour, end: Colour, depth: Boolean, width: Float = LINE_WIDTH) {
		if (points.size < 2 || !width.isFinite() || width <= 0.0f) {
			return
		}
		lineListTasks.add(LineListTask(points.toList(), start, end, depth, width))
	}

	fun worldText(text: String, pos: Vec3, colour: Colour, depth: Boolean, scale: Float = 1.0f) {
		if (text.isBlank() || !scale.isFinite() || scale <= 0.0f) return
		worldTextTasks.add(WorldTextTask(text, pos, colour, depth, scale))
	}

	fun render(context: LevelRenderContext) {
		if (lineTasks.isEmpty() && circleTasks.isEmpty() && ringTasks.isEmpty() && lineListTasks.isEmpty() && filledTasks.isEmpty() && worldTextTasks.isEmpty()) {
			return
		}

		val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
		val stack = context.poseStack()
		val source = context.bufferSource()

		stack.pushPose()
		try {
			stack.translate(-camera.x, -camera.y, -camera.z)

			renderLines(source, stack, camera)
			renderFilled(source, stack)
			renderWorldText(source, stack)
		} finally {
			stack.popPose()
			clear()
		}
	}

	fun clear() {
		lineTasks.clear()
		circleTasks.clear()
		ringTasks.clear()
		lineListTasks.clear()
		filledTasks.clear()
		worldTextTasks.clear()
	}

	private fun renderLines(source: MultiBufferSource.BufferSource, stack: PoseStack, camera: Vec3) {
		renderLineBatch(source, stack, RenderTypes.secondaryBlockOutline(), depth = true, camera = camera)
		renderLineBatch(source, stack, CgcRenderTypes.linesThroughWalls, depth = false, camera = camera)
	}

	private fun renderLineBatch(source: MultiBufferSource.BufferSource, stack: PoseStack, type: RenderType, depth: Boolean, camera: Vec3) {
		val boxes = lineTasks.filter { it.depth == depth }
		val circles = circleTasks.filter { it.depth == depth }
		val rings = ringTasks.filter { it.depth == depth }
		val lineLists = lineListTasks.filter { it.depth == depth }
		if (boxes.isEmpty() && circles.isEmpty() && rings.isEmpty() && lineLists.isEmpty()) {
			return
		}

		val buffer = source.getBuffer(type)
		for (task in boxes) {
			renderOutlineBox(stack.last(), buffer, task)
		}
		for (task in circles) {
			renderCircle(stack.last(), buffer, task)
		}
		for (task in rings) {
			renderRing(stack.last(), buffer, task, camera)
		}
		for (task in lineLists) {
			renderLineList(stack.last(), buffer, task)
		}
		source.endBatch(type)
	}

	private fun renderFilled(source: MultiBufferSource.BufferSource, stack: PoseStack) {
		val tasks = filledTasks
		if (tasks.isEmpty()) {
			return
		}

		renderFilledBatch(source, stack, RenderTypes.debugFilledBox(), tasks.filter { it.depth })
		renderFilledBatch(source, stack, CgcRenderTypes.filledBoxThroughWalls, tasks.filterNot { it.depth })
	}

	private fun renderFilledBatch(
		source: MultiBufferSource.BufferSource,
		stack: PoseStack,
		type: RenderType,
		tasks: List<BoxTask>
	) {
		if (tasks.isEmpty()) {
			return
		}
		val buffer = source.getBuffer(type)
		for (task in tasks) {
			addFilledBoxVertices(stack.last(), buffer, task.aabb, task.colour)
		}
		source.endBatch(type)
	}

	private fun renderWorldText(source: MultiBufferSource.BufferSource, stack: PoseStack) {
		if (worldTextTasks.isEmpty()) return
		val client = Minecraft.getInstance()
		val camera = client.gameRenderer.mainCamera
		for (task in worldTextTasks) {
			stack.pushPose()
			try {
				stack.translate(task.pos.x, task.pos.y, task.pos.z)
				stack.mulPose(camera.rotation())
				val scale = WORLD_TEXT_SCALE * task.scale
				stack.scale(-scale, -scale, scale)
				val x = -client.font.width(task.text) / 2.0f
				client.font.drawInBatch(
					task.text,
					x,
					0.0f,
					task.colour.argb(),
					true,
					stack.last().pose(),
					source,
					if (task.depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH,
					0,
					FULL_BRIGHT_LIGHT
				)
			} finally {
				stack.popPose()
			}
		}
		source.endBatch()
	}

	private fun renderOutlineBox(pose: PoseStack.Pose, buffer: VertexConsumer, task: BoxTask) {
		val corners = corners(task.aabb)
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
				.setColor(task.colour.argb())
				.setNormal(pose, dx, dy, dz)
				.setLineWidth(task.width)
			buffer.addVertex(pose, x1, y1, z1)
				.setColor(task.colour.argb())
				.setNormal(pose, dx, dy, dz)
				.setLineWidth(task.width)
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

	private fun renderRing(pose: PoseStack.Pose, buffer: VertexConsumer, task: RingTask, camera: Vec3) {
		val factor = ringFactor(camera.distanceToSqr(task.pos))
		if (factor == 0) {
			return
		}

		val slices = task.slices / factor
		val layers = task.layers / factor
		if (slices < 3 || layers < 1) {
			return
		}

		val data = circleCache.getOrPut(slices) { CircleData(slices) }
		val height = task.radius * 2.0f / 3.0f
		val oneOverLayers = 1.0f / layers
		val red = task.colour.red / 255.0f
		val green = task.colour.green / 255.0f
		val blue = task.colour.blue / 255.0f

		for (i in 0 until layers) {
			val yOffset = height * i / layers
			val t = 1.0f - i * oneOverLayers
			val alpha = t * t * t
			if (alpha >= 0.01f) {
				renderRingLayer(pose, buffer, task.pos, task.radius, yOffset, red, green, blue, alpha, data, slices)
			}
		}
	}

	private fun renderRingLayer(
		pose: PoseStack.Pose,
		buffer: VertexConsumer,
		pos: Vec3,
		radius: Float,
		yOffset: Float,
		red: Float,
		green: Float,
		blue: Float,
		alpha: Float,
		data: CircleData,
		slices: Int
	) {
		val matrix = pose.pose()
		val y = pos.y.toFloat() + yOffset
		for (i in 0 until slices) {
			val next = (i + 1) % slices
			val x1 = pos.x.toFloat() + data.x[i] * radius
			val z1 = pos.z.toFloat() + data.z[i] * radius
			val x2 = pos.x.toFloat() + data.x[next] * radius
			val z2 = pos.z.toFloat() + data.z[next] * radius
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

	private fun renderLineList(pose: PoseStack.Pose, buffer: VertexConsumer, task: LineListTask) {
		val size = task.points.size - 1
		val start = task.start.argb()
		val end = task.end.argb()

		for (i in 0 until size) {
			val from = task.points[i]
			val to = task.points[i + 1]
			val dx = (to.x - from.x).toFloat()
			val dy = (to.y - from.y).toFloat()
			val dz = (to.z - from.z).toFloat()
			val t0 = i.toFloat() / size
			val t1 = (i + 1).toFloat() / size

			buffer.addVertex(pose, from.x.toFloat(), from.y.toFloat(), from.z.toFloat())
				.setColor(lerpArgb(start, end, t0))
				.setNormal(pose, dx, dy, dz)
				.setLineWidth(task.width)
			buffer.addVertex(pose, to.x.toFloat(), to.y.toFloat(), to.z.toFloat())
				.setColor(lerpArgb(start, end, t1))
				.setNormal(pose, dx, dy, dz)
				.setLineWidth(task.width)
		}
	}

	private fun lerpArgb(start: Int, end: Int, t: Float): Int {
		val a0 = start ushr 24 and 0xFF
		val r0 = start ushr 16 and 0xFF
		val g0 = start ushr 8 and 0xFF
		val b0 = start and 0xFF
		val a1 = end ushr 24 and 0xFF
		val r1 = end ushr 16 and 0xFF
		val g1 = end ushr 8 and 0xFF
		val b1 = end and 0xFF
		val alpha = (a0 + (a1 - a0) * t).toInt()
		val red = (r0 + (r1 - r0) * t).toInt()
		val green = (g0 + (g1 - g0) * t).toInt()
		val blue = (b0 + (b1 - b0) * t).toInt()
		return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
	}

	private fun ringFactor(distanceSq: Double): Int =
		when {
			distanceSq > 4096.0 -> 0
			distanceSq > 2304.0 -> 8
			distanceSq > 1024.0 -> 4
			distanceSq > 256.0 -> 2
			else -> 1
		}

	private data class BoxTask(val aabb: AABB, val colour: Colour, val depth: Boolean, val width: Float = LINE_WIDTH)
	private data class CircleTask(val pos: Vec3, val depth: Boolean, val radius: Float, val colour: Colour, val slices: Int)
	private data class RingTask(val pos: Vec3, val depth: Boolean, val radius: Float, val colour: Colour, val slices: Int, val layers: Int)
	private data class LineListTask(val points: List<Vec3>, val start: Colour, val end: Colour, val depth: Boolean, val width: Float)
	private data class WorldTextTask(val text: String, val pos: Vec3, val colour: Colour, val depth: Boolean, val scale: Float)

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
	private const val WORLD_TEXT_SCALE = 0.025f
	private const val FULL_BRIGHT_LIGHT = 0x00F000F0
}
