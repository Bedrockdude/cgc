package cgc.cgc.module.impl.general

import kotlin.test.Test
import kotlin.test.assertEquals

class PartyNamesTweaksTest {
	private val aliases = mapOf("Bers" to "BersPrio")

	@Test
	fun `short name is replaced as a whole command argument`() {
		assertEquals(
			"/p BersPrio",
			PartyNamesTweaks.resolveTokenAliasesForTest("/p Bers", 1, aliases)
		)
	}

	@Test
	fun `replacement is case insensitive and preserves spacing`() {
		assertEquals(
			"/party  invite  BersPrio",
			PartyNamesTweaks.resolveTokenAliasesForTest("/party  invite  bErS", 1, aliases, literalIndexes = setOf(0, 1))
		)
	}

	@Test
	fun `message text after a player argument is untouched`() {
		assertEquals(
			"/msg BersPrio hello Bers",
			PartyNamesTweaks.resolveTokenAliasesForTest("/msg Bers hello Bers", 1, aliases, textStart = 2)
		)
	}

	@Test
	fun `literal command tokens are never replaced`() {
		assertEquals(
			"/party Bers BersPrio",
			PartyNamesTweaks.resolveTokenAliasesForTest("/party Bers Bers", 1, aliases, literalIndexes = setOf(0, 1))
		)
	}
}
