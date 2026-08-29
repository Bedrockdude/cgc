package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.nodes.EtherwarpNode
import com.google.gson.GsonBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EtherwarpNodeTest {
	private val gson = GsonBuilder()
		.registerTypeHierarchyAdapter(AutoCNode::class.java, AutoCNodeAdapter())
		.create()

	@Test
	fun `exact position survives route serialization`() {
		val source = EtherwarpNode(target = Pos(1.25, 2.5, 3.75), exactlyPos = true)

		val restored = gson.fromJson(gson.toJson(source, AutoCNode::class.java), AutoCNode::class.java)

		assertIs<EtherwarpNode>(restored)
		assertTrue(restored.exactlyPos)
	}

	@Test
	fun `old etherwarp routes default exact position to false`() {
		val restored = gson.fromJson(
			"""{"type":"etherwarp","pos":{"x":0,"y":0,"z":0},"yaw":0,"pitch":0,"block":{"x":1,"y":2,"z":3},"target":{"x":1.5,"y":2.5,"z":3.5}}""",
			AutoCNode::class.java
		)

		assertIs<EtherwarpNode>(restored)
		assertFalse(restored.exactlyPos)
	}
}
