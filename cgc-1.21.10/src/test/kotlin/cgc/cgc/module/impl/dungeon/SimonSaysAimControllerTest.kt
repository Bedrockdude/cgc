package cgc.cgc.module.impl.dungeon

import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimonSaysAimControllerTest {
	private val settings = AimSettings(
		speed = 1.2,
		randomness = 0.1,
		overshootStrength = 1.0,
		microCorrection = 0.55
	)

	@Test
	fun `plan is deterministic and finishes exactly on target`() {
		val first = controllerFor(Rotation(7.0f, -4.0f), Rotation(41.0f, 9.0f), seed = 913L)
		val second = controllerFor(Rotation(7.0f, -4.0f), Rotation(41.0f, 9.0f), seed = 913L)
		val firstPlan = first.currentPlan()!!
		val secondPlan = second.currentPlan()!!

		assertEquals(firstPlan.durationMs, secondPlan.durationMs)
		assertEquals(firstPlan.curveDirection, secondPlan.curveDirection)
		assertEquals(firstPlan.curveAmount, secondPlan.curveAmount)

		val result = first.update(firstPlan.startedAtMs + firstPlan.durationMs)
		assertTrue(result.finished)
		assertEquals(firstPlan.final.yaw, result.rotation.yaw, 1.0E-4f)
		assertEquals(firstPlan.final.pitch, result.rotation.pitch, 1.0E-4f)
	}

	@Test
	fun `speed setting shortens ordinary and contextual moves`() {
		val slow = SimonSaysAimController()
		val fast = SimonSaysAimController()
		val context = AimMoveContext(1, 1, 1, sqrt(2.0), true, true, false, true, 1)
		val target = Rotation(42.0f, 14.0f)

		slow.start(Rotation(0.0f, 0.0f), target, settings.copy(speed = 0.8), AimMode.CHAINED_RETARGET, context, 10_000L, 44L)
		fast.start(Rotation(0.0f, 0.0f), target, settings.copy(speed = 1.6), AimMode.CHAINED_RETARGET, context, 10_000L, 44L)

		assertTrue(fast.currentPlan()!!.durationMs < slow.currentPlan()!!.durationMs)
	}

	@Test
	fun `click readiness waits for the actual arrival region`() {
		val controller = controllerFor(Rotation(0.0f, 0.0f), Rotation(36.0f, -11.0f), seed = 27L)
		val plan = controller.currentPlan()!!

		val early = controller.update(plan.startedAtMs + plan.clickReadyAtMs)
		val end = controller.update(plan.startedAtMs + plan.durationMs)

		assertFalse(early.readyToClick)
		assertTrue(end.readyToClick)
	}

	@Test
	fun `retarget preserves angular velocity without a position jump`() {
		val controller = controllerFor(Rotation(0.0f, 0.0f), Rotation(34.0f, 9.0f), seed = 71L)
		val firstPlan = controller.currentPlan()!!
		val boundaryTime = firstPlan.startedAtMs + (firstPlan.durationMs * 0.88).toLong()
		val before = controller.update(boundaryTime - 1L).rotation
		val boundary = controller.update(boundaryTime).rotation
		val incomingVelocity = delta(before, boundary, 0.001)

		controller.start(boundary, Rotation(49.0f, -8.0f), settings, AimMode.CHAINED_RETARGET, nowMs = boundaryTime, seed = 72L)
		val start = controller.update(boundaryTime).rotation
		val after = controller.update(boundaryTime + 1L).rotation
		val outgoingVelocity = delta(start, after, 0.001)

		assertTrue(angularError(start, boundary) < 1.0E-4)
		assertTrue(angularError(incomingVelocity, outgoingVelocity) < 18.0)
	}

	@Test
	fun `common puzzle turns stay inside the human motion envelope`() {
		for (distance in listOf(4.0f, 12.0f, 24.0f, 42.0f, 60.0f)) {
			for (seed in 1L..12L) {
				val controller = controllerFor(Rotation(0.0f, 0.0f), Rotation(distance, -distance * 0.22f), seed)
				val plan = controller.currentPlan()!!
				var previous = controller.update(plan.startedAtMs).rotation
				var previousVelocity = Rotation(0.0f, 0.0f)
				for (elapsed in 1L..plan.durationMs) {
					val current = controller.update(plan.startedAtMs + elapsed).rotation
					val velocity = delta(previous, current, 0.001)
					val acceleration = delta(previousVelocity, velocity, 0.001)
					assertTrue(magnitude(velocity) <= 565.0, "velocity ${magnitude(velocity)} at ${distance} degrees")
					if (elapsed > 1L) {
						assertTrue(magnitude(acceleration) <= 16_500.0, "acceleration ${magnitude(acceleration)} at ${distance} degrees")
					}
					previous = current
					previousVelocity = velocity
				}
			}
		}
	}

	@Test
	fun `Ice Fill timing profile permits deliberately slow uncapped turns`() {
		val controller = SimonSaysAimController()
		controller.start(
			start = Rotation(0.0f, 0.0f),
			target = Rotation(60.0f, 0.0f),
			settings = settings.copy(speed = 0.40),
			mode = AimMode.NORMAL_BUTTON,
			nowMs = 1_000L,
			seed = 4L,
			timing = AimTimingProfile(minimumSpeed = 0.10, maximumSpeed = 0.80, maximumDurationMs = 5_000L)
		)
		assertTrue(controller.currentPlan()!!.durationMs > 260L)
	}

	private fun controllerFor(start: Rotation, target: Rotation, seed: Long): SimonSaysAimController =
		SimonSaysAimController().also {
			it.start(start, target, settings, AimMode.NORMAL_BUTTON, nowMs = 1_000L, seed = seed)
		}

	private fun delta(start: Rotation, end: Rotation, seconds: Double): Rotation =
		Rotation(
			(Mth.wrapDegrees(end.yaw - start.yaw) / seconds).toFloat(),
			((end.pitch - start.pitch) / seconds).toFloat()
		)

	private fun magnitude(rotation: Rotation): Double =
		sqrt(rotation.yaw * rotation.yaw + rotation.pitch * rotation.pitch.toDouble())

	private fun angularError(first: Rotation, second: Rotation): Double {
		val yaw = Mth.wrapDegrees(first.yaw - second.yaw).toDouble()
		val pitch = (first.pitch - second.pitch).toDouble()
		return sqrt(yaw * yaw + pitch * pitch)
	}
}
