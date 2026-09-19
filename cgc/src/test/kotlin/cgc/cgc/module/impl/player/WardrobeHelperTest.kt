package cgc.cgc.module.impl.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WardrobeHelperTest {
	@Test
	fun `accepts only the first loadouts page title`() {
		assertTrue(WardrobeHelper.isLoadoutsTitle("(1/3) Loadouts"))
		assertFalse(WardrobeHelper.isLoadoutsTitle("(2/3) Loadouts"))
		assertFalse(WardrobeHelper.isLoadoutsTitle("Wardrobe"))
	}

	@Test
	fun `matches names exactly except for case`() {
		assertTrue(WardrobeHelper.namesMatch("Archer Loadout", "archer loadout"))
		assertFalse(WardrobeHelper.namesMatch("Archer Loadout", "Archer"))
		assertFalse(WardrobeHelper.namesMatch("My Archer Loadout", "Archer Loadout"))
		assertFalse(WardrobeHelper.namesMatch("Archer Loadout", " Archer Loadout "))
	}
}
