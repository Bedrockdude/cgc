package cgc.cgc.module.impl.dungeon.autoc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoCInputControllerTest {
	@Test
	fun `input held when route starts is not treated as a cancellation`() {
		val baseline = AutoCInputController.MovementInputBaseline(mutableSetOf("key.keyboard.w"))

		assertFalse(baseline.hasNewInput(setOf("key.keyboard.w")))
	}

	@Test
	fun `new input after route starts still cancels`() {
		val baseline = AutoCInputController.MovementInputBaseline(mutableSetOf("key.keyboard.w"))

		assertTrue(baseline.hasNewInput(setOf("key.keyboard.w", "key.keyboard.space")))
	}

	@Test
	fun `repressing an initially held input counts as new input`() {
		val baseline = AutoCInputController.MovementInputBaseline(mutableSetOf("key.keyboard.w"))

		assertFalse(baseline.hasNewInput(emptySet()))
		assertTrue(baseline.hasNewInput(setOf("key.keyboard.w")))
	}
}
