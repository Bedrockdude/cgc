package cgc.cgc.module.setting

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerNameAliasListSettingTest {
	@Test
	fun `aliases round trip through config`() {
		val setting = PlayerNameAliasListSetting("Names")
		val entry = setting.addAlias()
		setting.setField(entry, PlayerNameAliasListSetting.Field.ALIAS, "Bers")
		setting.setField(entry, PlayerNameAliasListSetting.Field.USERNAME, "BersPrio")

		val json = JsonObject()
		setting.saveToJson(json)
		val loaded = PlayerNameAliasListSetting("Names")
		loaded.loadFromJson(json)

		assertEquals(listOf("Bers" to "BersPrio"), loaded.configuredAliases().map { it.alias to it.username })
	}

	@Test
	fun `invalid username characters are removed and duplicate aliases are ignored`() {
		val setting = PlayerNameAliasListSetting("Names")
		val first = setting.addAlias()
		setting.setField(first, PlayerNameAliasListSetting.Field.ALIAS, "Be-rs!")
		setting.setField(first, PlayerNameAliasListSetting.Field.USERNAME, "Bers_Pr!o")
		val duplicate = setting.addAlias()
		setting.setField(duplicate, PlayerNameAliasListSetting.Field.ALIAS, "bers")
		setting.setField(duplicate, PlayerNameAliasListSetting.Field.USERNAME, "SomeoneElse")

		assertEquals(listOf("Bers" to "Bers_Pro"), setting.configuredAliases().map { it.alias to it.username })
	}
}
