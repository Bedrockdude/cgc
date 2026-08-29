package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.data.Pos
import cgc.cgc.module.impl.dungeon.autoc.nodes.MovingEtherwarpNode
import com.google.gson.GsonBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MovingEtherwarpNodeTest {
	private val gson = GsonBuilder()
		.registerTypeHierarchyAdapter(AutoCNode::class.java, AutoCNodeAdapter())
		.create()

	@Test
	fun `moving etherwarp survives route serialization`() {
		val source = MovingEtherwarpNode(
			pos = Pos(1.5, 70.0, 2.5),
			yaw = 32.0f,
			pitch = -18.0f,
			block = Pos(5.0, 72.0, 8.0),
			target = Pos(5.5, 72.4, 8.5)
		)

		val json = gson.toJson(source, AutoCNode::class.java)
		val restored = gson.fromJson(json, AutoCNode::class.java)

		assertIs<MovingEtherwarpNode>(restored)
		assertEquals("movingetherwarp", restored.name())
		assertEquals("movingetherwarp", restored.serialize().get("type").asString)
		assertEquals(5.0, restored.serialize().getAsJsonObject("block").get("x").asDouble)
	}

	@Test
	fun `moving etherwarp is exposed as an etherwarp command type`() {
		val type = AutoCNodeType.byName("movingetherwarp")

		assertEquals(AutoCNodeType.MOVING_ETHERWARP, type)
		assertEquals(true, type?.isEtherwarp())
	}
}
