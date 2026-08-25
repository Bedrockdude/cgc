package cgc.cgc.module.impl.render.opsec

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NickHiderTest {
	@BeforeTest
	fun bootstrapMinecraft() {
		SharedConstants.tryDetectVersion()
		Bootstrap.bootStrap()
	}

	@Test
	fun `custom name accepts formatted component json`() {
		val component = NickHider.parseCustomName(CUSTOM_NAME_JSON)

		assertEquals("ＭｉｋｕＩＲＬ", component.string)
		assertEquals(7, component.siblings.size)

		val cyan = component.siblings.first().style
		assertEquals(0x00E0FF, cyan.color?.value)
		assertTrue(cyan.isBold)
		assertNotNull(cyan.shadowColor)

		val blue = component.siblings[4].style
		assertEquals(0x3701D4, blue.color?.value)
		assertTrue(blue.isBold)
		assertNotNull(blue.shadowColor)
	}

	@Test
	fun `plain and invalid json names remain literal text`() {
		assertEquals("MikuIRL", NickHider.parseCustomName("MikuIRL").string)
		assertEquals("{not json", NickHider.parseCustomName("{not json").string)
	}

	private companion object {
		const val CUSTOM_NAME_JSON = """{"text":"","extra":[{"text":"Ｍ","color":"#00E0FF","bold":true,"shadow_color":[0,0,0,1]},{"text":"ｉ","color":"#00E0FF","bold":true,"shadow_color":[0,0,0,1]},{"text":"ｋ","color":"#00E0FF","bold":true,"shadow_color":[0,0,0,1]},{"text":"ｕ","color":"#00E0FF","bold":true,"shadow_color":[0,0,0,1]},{"text":"Ｉ","color":"#3701D4","bold":true,"shadow_color":[0,0,0,1]},{"text":"Ｒ","color":"#3701D4","bold":true,"shadow_color":[0,0,0,1]},{"text":"Ｌ","color":"#3701D4","bold":true,"shadow_color":[0,0,0,1]}]}"""
	}
}
