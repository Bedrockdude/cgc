package cgc.cgc.shitterlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShitterListModelsTest {
	@Test
	fun `minecraft UUID accepts dashed and compact forms`() {
		val dashed = parseMinecraftUuid("5fe3bc5b-48cd-468a-8fb8-a652a3c9387e")
		val compact = parseMinecraftUuid("5fe3bc5b48cd468a8fb8a652a3c9387e")

		assertEquals(dashed, compact)
		assertEquals("5fe3bc5b48cd468a8fb8a652a3c9387e", compact?.compactString())
	}

	@Test
	fun `invalid UUID is rejected`() {
		assertNull(parseMinecraftUuid("not-a-uuid"))
	}

	@Test
	fun `protected UUID cannot be added`() {
		assertEquals(
			ShitterListChangeResult.Protected,
			ShitterListService.add("5fe3bc5b48cd468a8fb8a652a3c9387e").join()
		)
		assertEquals(
			"Error You are not good enough to use this command",
			ShitterListService.PROTECTED_ERROR_MESSAGE
		)
	}
}
