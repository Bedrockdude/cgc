package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.nodes.BreakNode
import com.google.gson.GsonBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BreakNodeTest {
	private val gson = GsonBuilder()
		.registerTypeHierarchyAdapter(AutoCNode::class.java, AutoCNodeAdapter())
		.create()

	@Test
	fun `not moving survives route serialization`() {
		val source = BreakNode(
			pos = Pos(1.5, 70.0, 2.5),
			zeroTick = false,
			recordSeconds = 2.0,
			notMoving = true
		)

		val restored = gson.fromJson(gson.toJson(source, AutoCNode::class.java), AutoCNode::class.java)

		assertIs<BreakNode>(restored)
		assertTrue(restored.notMoving)
	}

	@Test
	fun `old break routes default not moving to false`() {
		val restored = gson.fromJson(
			"""{"type":"break","pos":{"x":0,"y":0,"z":0},"zeroTick":false,"recordSeconds":2,"blocks":[]}""",
			AutoCNode::class.java
		)

		assertIs<BreakNode>(restored)
		assertFalse(restored.notMoving)
	}
}
