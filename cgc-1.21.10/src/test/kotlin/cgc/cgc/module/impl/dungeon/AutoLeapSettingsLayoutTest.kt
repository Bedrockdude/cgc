package cgc.cgc.module.impl.dungeon

import cgc.cgc.module.setting.ModeSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoLeapSettingsLayoutTest {
	@Test
	fun `settings are organized by encounter stage`() {
		val module = AutoLeap()

		assertEquals(listOf("Overview", "Clear", "Storm", "Goldor"), module.settings.map { it.name })
		assertTrue(module.settings.all { !it.toggleable })
		assertTrue(module.settings.all { it.description.isNotBlank() })
		assertEquals(listOf("Class"), module.settings[0].value.settings.map { it.name })
		assertEquals(
			listOf("Blood Rush Enabled", "Blood Rush Target", "Include Blood Key"),
			module.settings[1].value.settings.map { it.name }
		)
		assertEquals(
			listOf("Tank CP Enabled", "Tank CP Target", "Heal CP Enabled", "Heal CP Target", "Mage CP Enabled", "Mage CP Target"),
			module.settings[2].value.settings.map { it.name }
		)
		assertEquals(
			listOf("I4 Leap Enabled", "I4 Leap Target", "S1 Leap Enabled", "S1 Leap Target", "S2 Leap Enabled", "S2 Leap Target", "S3 Leap Enabled", "S3 Leap Target", "S4 Leap Enabled", "S4 Leap Target"),
			module.settings[3].value.settings.map { it.name }
		)
	}

	@Test
	fun `new leap destinations keep their requested defaults`() {
		val settings = AutoLeap().flatSettings().associateBy { it.name }

		assertEquals("Tank", (settings.getValue("I4 Leap Target") as ModeSetting).value)
		assertEquals("Tank", (settings.getValue("Tank CP Target") as ModeSetting).value)
		assertEquals("Healer", (settings.getValue("Heal CP Target") as ModeSetting).value)
		assertEquals("Healer", (settings.getValue("Blood Rush Target") as ModeSetting).value)
		assertEquals("Archer", (settings.getValue("S1 Leap Target") as ModeSetting).value)
		assertFalse((settings.getValue("Include Blood Key") as cgc.cgc.module.setting.BooleanSetting).value)
	}
}
