package cgc.cgc.module.impl.dungeon.autopuzzles

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectileAimPlannerTest {
	@Test
	fun `center hit with harmless side rays is safe`() {
		assertTrue(
			ProjectileAimPlanner.isSafeFan(
				listOf(
					ProjectileAimPlanner.FirstHit.TARGET,
					ProjectileAimPlanner.FirstHit.OTHER,
					ProjectileAimPlanner.FirstHit.OTHER
				)
			)
		)
	}

	@Test
	fun `a side arrow may be the only arrow that reaches the target`() {
		assertTrue(
			ProjectileAimPlanner.isSafeFan(
				listOf(
					ProjectileAimPlanner.FirstHit.OTHER,
					ProjectileAimPlanner.FirstHit.TARGET,
					ProjectileAimPlanner.FirstHit.OTHER
				)
			)
		)
	}

	@Test
	fun `any first collision with an unintended target rejects the whole fan`() {
		assertFalse(
			ProjectileAimPlanner.isSafeFan(
				listOf(
					ProjectileAimPlanner.FirstHit.TARGET,
					ProjectileAimPlanner.FirstHit.UNINTENDED,
					ProjectileAimPlanner.FirstHit.OTHER
				)
			)
		)
	}

	@Test
	fun `a fan that misses the intended target is rejected`() {
		assertFalse(
			ProjectileAimPlanner.isSafeFan(
				listOf(
					ProjectileAimPlanner.FirstHit.OTHER,
					ProjectileAimPlanner.FirstHit.OTHER,
					ProjectileAimPlanner.FirstHit.OTHER
				)
			)
		)
	}
}
