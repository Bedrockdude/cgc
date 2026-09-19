package cgc.cgc.module.setting

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WardrobeLoadoutListSettingTest {
	@Test
	fun `round trips configured loadouts`() {
		val setting = WardrobeLoadoutListSetting("Loadouts")
		val loadout = setting.addLoadout()
		setting.setName(loadout, "Archer Loadout")
		loadout.autoClose = true
		loadout.keybind.keyName = "key.keyboard.g"

		val json = JsonObject()
		setting.saveToJson(json)
		val loaded = WardrobeLoadoutListSetting("Loadouts")
		loaded.loadFromJson(json)

		assertEquals(1, loaded.value.size)
		assertEquals("Archer Loadout", loaded.value.single().name)
		assertTrue(loaded.value.single().autoClose)
		assertEquals("key.keyboard.g", loaded.value.single().keybind.keyName)
	}

	@Test
	fun `filters blank and control-only names`() {
		val setting = WardrobeLoadoutListSetting("Loadouts")
		val blank = setting.addLoadout()
		setting.setName(blank, "\n\t")
		val configured = setting.addLoadout()
		setting.setName(configured, "Mage")

		assertEquals(listOf("Mage"), setting.configuredLoadouts().map { it.name })
		assertFalse(blank.name.contains('\n'))
	}
}
