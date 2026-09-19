package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.module.impl.dungeon.AutoPuzzles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class AutoPuzzlesDefaultsTest {
	@Test
	fun `module and all four connected puzzle groups are opt in`() {
		val module = AutoPuzzles()
		assertEquals("AutoPuzzles", module.id)
		assertEquals("Auto Puzzles", module.displayName)
		assertFalse(module.enabled)
		assertEquals(listOf("Blaze", "Creeper Beams", "Ice Fill", "Waterboard"), module.settings.map { it.name })
		assertFalse(module.blaze.enabled)
		assertFalse(module.creeperBeams.enabled)
		assertFalse(module.iceFill.enabled)
		assertFalse(module.waterboard.enabled)
		assertEquals(emptyList(), module.settings.filter { it.name == "General" })
	}

	@Test
	fun `frozen setting names and defaults remain stable`() {
		val module = AutoPuzzles()
		assertEquals(1.0, module.blaze.aimSpeed.value.toDouble())
		assertEquals(500L, module.blaze.blazeRemovalWait.value.toLong())
		assertEquals(2.0, module.creeperBeams.lineThickness.value.toDouble())
		assertEquals(0.40, module.iceFill.turnSpeed.value.toDouble())
		assertEquals(5.0, module.iceFill.lineThickness.value.toDouble())
		assertEquals(120L, module.waterboard.actionDelay.value.toLong())
		assertEquals(
			listOf("Turn Speed", "Path Color", "Line Thickness", "Record Floor Route", "Set Fall Y", "Set Floor 1 Start", "Set Floor 2 Start", "Set Floor 3 Start"),
			module.iceFill.settings.map { it.name }
		)
	}

	@Test
	fun `room dispatch reads live child enabled state`() {
		val module = AutoPuzzles()
		assertNull(module.controllerForRoom("Higher Blaze"))

		module.blaze.setEnabled(true)
		assertSame(module.blaze.controller, module.controllerForRoom("Higher Blaze"))

		module.blaze.setEnabled(false)
		assertNull(module.controllerForRoom("Higher Blaze"))
	}
}
