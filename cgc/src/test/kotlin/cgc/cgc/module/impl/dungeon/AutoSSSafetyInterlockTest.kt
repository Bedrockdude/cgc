package cgc.cgc.module.impl.dungeon

import cgc.cgc.runtime.PhysicalInputTracker
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoSSSafetyInterlockTest {
	private val start = Vec3(107.0, 120.0, 94.0)
	private val initialRotation = Rotation(10.0f, 20.0f)

	@Test
	fun `any real player displacement trips the movement interlock`() {
		val interlock = armedInterlock()

		assertEquals(
			AutoSSSafetyViolation.PLAYER_MOVED,
			interlock.check(start.add(0.001, 0.0, 0.0), initialRotation, input(), 1_000L)
		)
	}

	@Test
	fun `camera movement without physical mouse input is rejected`() {
		val interlock = armedInterlock()

		assertEquals(
			AutoSSSafetyViolation.UNAUTHORIZED_CAMERA_MOVEMENT,
			interlock.check(start, Rotation(11.0f, 20.0f), input(), 1_000L)
		)
	}

	@Test
	fun `recent physical mouse movement authorizes the player camera change`() {
		val interlock = armedInterlock()
		val physicalInput = input(mouseSequence = 2L, lastMouseMoveAtMs = 975L)

		assertNull(interlock.check(start, Rotation(11.0f, 20.0f), physicalInput, 1_000L))
		assertEquals(Rotation(11.0f, 20.0f), interlock.expectedRotation)
	}

	@Test
	fun `stale physical mouse movement does not authorize a later camera change`() {
		val interlock = armedInterlock()
		val staleInput = input(mouseSequence = 2L, lastMouseMoveAtMs = 899L)

		assertEquals(
			AutoSSSafetyViolation.UNAUTHORIZED_CAMERA_MOVEMENT,
			interlock.check(start, Rotation(11.0f, 20.0f), staleInput, 1_000L)
		)
	}

	@Test
	fun `solver-recorded camera movement is accepted without physical input`() {
		val interlock = armedInterlock()
		val solverRotation = Rotation(-35.0f, 8.0f)

		interlock.recordSolverRotation(solverRotation)

		assertNull(interlock.check(start, solverRotation, input(), 1_000L))
	}

	private fun armedInterlock(): AutoSSSafetyInterlock =
		AutoSSSafetyInterlock().also { it.arm(start, initialRotation, input()) }

	private fun input(
		mouseSequence: Long = 1L,
		lastMouseMoveAtMs: Long = Long.MIN_VALUE
	): PhysicalInputTracker.Snapshot =
		PhysicalInputTracker.Snapshot(
			keyboardPressSequence = 0L,
			mouseButtonPressSequence = 0L,
			mouseMoveSequence = mouseSequence,
			scrollSequence = 0L,
			lastKeyboardPressAtMs = Long.MIN_VALUE,
			lastMouseButtonPressAtMs = Long.MIN_VALUE,
			lastMouseMoveAtMs = lastMouseMoveAtMs,
			lastScrollAtMs = Long.MIN_VALUE
		)
}
