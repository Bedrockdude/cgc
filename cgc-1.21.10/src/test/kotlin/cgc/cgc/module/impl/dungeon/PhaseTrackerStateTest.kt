package cgc.cgc.module.impl.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhaseTrackerStateTest {
	@Test
	fun `p3 section waits for both terminal progress and destroyed gate`() {
		val state = PhaseTrackerState()
		state.handleMessage(P3_START, 0L)
		state.handleMessage("Player activated a terminal! (7/7)", 100L)

		var snapshot = state.snapshot()
		assertEquals(1, snapshot.section)
		assertEquals(7, snapshot.completed)
		assertEquals(7, snapshot.total)
		assertEquals(P3ProgressState.WAITING_FOR_GATE, snapshot.p3State)

		state.handleMessage("The gate has been destroyed!", 200L)
		snapshot = state.snapshot()
		assertEquals(P3ProgressState.DONE, snapshot.p3State)

		state.tick(1_699L, 1_500L)
		assertEquals(1, state.snapshot().section)
		state.tick(1_700L, 1_500L)
		snapshot = state.snapshot()
		assertEquals(2, snapshot.section)
		assertEquals(0, snapshot.completed)
		assertNull(snapshot.total)
		assertEquals(P3ProgressState.ACTIVE, snapshot.p3State)
	}

	@Test
	fun `automatic gate changes to done only after five seconds`() {
		val state = PhaseTrackerState()
		state.handleMessage(P3_START, 0L)
		state.handleMessage("Player completed a device! (7/7)", 50L)
		state.handleMessage("The gate will open in 5 seconds!", 100L)

		state.tick(5_099L, 1_500L)
		assertEquals(P3ProgressState.WAITING_FOR_GATE, state.snapshot().p3State)

		state.tick(5_100L, 1_500L)
		assertEquals(P3ProgressState.DONE, state.snapshot().p3State)
	}

	@Test
	fun `gate and counter can arrive in either order`() {
		val state = PhaseTrackerState()
		state.handleMessage(P3_START, 0L)
		state.handleMessage("The gate has been destroyed!", 10L)
		assertEquals(P3ProgressState.ACTIVE, state.snapshot().p3State)

		state.handleMessage("Player activated a lever! (7/7)", 20L)
		assertEquals(P3ProgressState.DONE, state.snapshot().p3State)
	}

	@Test
	fun `all four p3 sections advance without advancing past section four`() {
		val state = PhaseTrackerState()
		state.handleMessage(P3_START, 0L)

		for (section in 1..4) {
			val now = section * 1_000L
			state.handleMessage("Player activated a terminal! (8/8)", now)
			state.handleMessage(
				if (section == 4) "The Core entrance is opening!" else "The gate has been destroyed!",
				now + 1L
			)
			assertEquals(section, state.snapshot().section)
			assertEquals(P3ProgressState.DONE, state.snapshot().p3State)

			state.tick(now + 2L, 0L)
			assertEquals(if (section == 4) 4 else section + 1, state.snapshot().section)
		}

		assertEquals(P3ProgressState.DONE, state.snapshot().p3State)
	}

	@Test
	fun `p4 marks Necron dead from a zero health boss bar`() {
		val state = PhaseTrackerState()
		state.handleMessage(P4_START, 0L)

		assertFalse(state.handleNecronBossBar(null, 0.0f))
		assertFalse(state.handleNecronBossBar("Goldor", 0.0f))
		assertFalse(state.snapshot().necronDead)
		assertFalse(state.handleNecronBossBar("Necron", 0.25f))
		assertTrue(state.handleNecronBossBar("Necron", 0.0f))
		assertTrue(state.snapshot().necronDead)
	}

	@Test
	fun `formatted terminal messages are accepted`() {
		val state = PhaseTrackerState()
		state.handleMessage(P3_START, 0L)

		assertTrue(state.handleMessage("§6Player §aactivated a terminal! (§c3§a/§c7§a)", 10L))
		assertEquals(3, state.snapshot().completed)
		assertEquals(7, state.snapshot().total)
	}

	private companion object {
		private const val P3_START = "[BOSS] Goldor: Who dares trespass into my domain?"
		private const val P4_START = "[BOSS] Necron: I'm afraid, your journey ends now."
	}
}
