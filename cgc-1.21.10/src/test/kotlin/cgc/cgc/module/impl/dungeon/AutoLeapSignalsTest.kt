package cgc.cgc.module.impl.dungeon

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoLeapSignalsTest {
	@Test
	fun `i4 requires the exact device area`() {
		assertTrue(AutoLeapSignals.isOnI4(Vec3(63.5, 127.0, 35.5)))
		assertFalse(AutoLeapSignals.isOnI4(Vec3(66.0, 127.0, 35.5)))
		assertFalse(AutoLeapSignals.isOnI4(Vec3(63.5, 128.0, 35.5)))
	}

	@Test
	fun `device completion belongs to the local player`() {
		assertTrue(AutoLeapSignals.isOwnDeviceCompletion("Player completed a device! (1/7)", "Player"))
		assertTrue(AutoLeapSignals.isOwnDeviceCompletion("[MVP+] Player completed a device! (4/7)", "Player"))
		assertFalse(AutoLeapSignals.isOwnDeviceCompletion("Teammate completed a device! (1/7)", "Player"))
	}

	@Test
	fun `lightning duration follows the first countdown title`() {
		assertEquals(108L, AutoLeapSignals.lightningDurationTicks("4"))
		assertEquals(162L, AutoLeapSignals.lightningDurationTicks("§c6"))
		assertNull(AutoLeapSignals.lightningDurationTicks("3"))
	}

	@Test
	fun `storm crush accepts both server variants`() {
		assertTrue(AutoLeapSignals.isStormCrush("[BOSS] Storm: Oof"))
		assertTrue(AutoLeapSignals.isStormCrush("[BOSS] Storm: Ouch, that hurt!"))
	}

	@Test
	fun `key pickup distinguishes wither and blood doors`() {
		assertEquals(
			DungeonKeyKind.WITHER,
			AutoLeapSignals.keyPickup("RIGHT CLICK on a WITHER door to open it. This key can only be used to open 1 door!")
		)
		assertEquals(
			DungeonKeyKind.BLOOD,
			AutoLeapSignals.keyPickup("RIGHT CLICK on the BLOOD DOOR to open it. This key can only be used to open 1 door!")
		)
		assertNull(AutoLeapSignals.keyPickup("unrelated action bar"))
	}
}
