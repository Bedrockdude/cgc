package cgc.cgc.module.impl.dungeon.autopuzzles

import net.minecraft.world.phys.Vec3
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminatorFanGeometryTest {
	@Test
	fun `fan angle is hardcoded to the confirmed five degrees`() {
		assertEquals(5.0, TerminatorFanGeometry.FAN_ANGLE_DEGREES)
	}

	@Test
	fun `center direction is unchanged and both side offsets are five degrees`() {
		val center = Vec3.directionFromRotation(-22.0f, 37.5f).normalize()
		val fan = TerminatorFanGeometry.directions(center).associateBy { it.lane }

		assertVectorEquals(center, fan.getValue(TerminatorFanLane.CENTER).direction)
		assertEquals(5.0, horizontalAngle(center, fan.getValue(TerminatorFanLane.LEFT).direction), 1.0E-9)
		assertEquals(5.0, horizontalAngle(center, fan.getValue(TerminatorFanLane.RIGHT).direction), 1.0E-9)
	}

	@Test
	fun `side arrows retain the center pitch`() {
		val center = Vec3.directionFromRotation(31.0f, -63.0f).normalize()
		val fan = TerminatorFanGeometry.directions(center)

		assertTrue(fan.all { kotlin.math.abs(it.direction.y - center.y) < 1.0E-12 })
	}

	@Test
	fun `fan lane order prefers the normal center arrow before side compensation`() {
		assertEquals(
			listOf(TerminatorFanLane.CENTER, TerminatorFanLane.LEFT, TerminatorFanLane.RIGHT),
			TerminatorFanGeometry.directions(Vec3(0.0, 0.0, 1.0)).map { it.lane }
		)
	}

	@Test
	fun `camera compensation makes each selected lane hit the requested direction`() {
		val targetDirection = Vec3(-0.31, 0.22, 0.91).normalize()
		for (lane in TerminatorFanLane.entries) {
			val cameraDirection = TerminatorFanGeometry.cameraDirectionForLaneHit(targetDirection, lane)
			val resultingLane = TerminatorFanGeometry.directions(cameraDirection).single { it.lane == lane }
			assertVectorEquals(targetDirection, resultingLane.direction, tolerance = 1.0E-12)
		}
	}

	@Test
	fun `opposite fan rays are symmetric around an arbitrary center heading`() {
		val center = Vec3(0.41, -0.18, 0.89).normalize()
		val fan = TerminatorFanGeometry.directions(center).associateBy { it.lane }
		val left = fan.getValue(TerminatorFanLane.LEFT).direction
		val right = fan.getValue(TerminatorFanLane.RIGHT).direction

		assertEquals(center.dot(left), center.dot(right), 1.0E-12)
		assertEquals(left.y, right.y, 1.0E-12)
	}

	private fun horizontalAngle(first: Vec3, second: Vec3): Double {
		val a = Vec3(first.x, 0.0, first.z).normalize()
		val b = Vec3(second.x, 0.0, second.z).normalize()
		return Math.toDegrees(acos(a.dot(b).coerceIn(-1.0, 1.0)))
	}

	private fun assertVectorEquals(expected: Vec3, actual: Vec3, tolerance: Double = 1.0E-9) {
		assertEquals(expected.x, actual.x, tolerance)
		assertEquals(expected.y, actual.y, tolerance)
		assertEquals(expected.z, actual.z, tolerance)
	}
}
