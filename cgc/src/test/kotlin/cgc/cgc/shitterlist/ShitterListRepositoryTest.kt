package cgc.cgc.shitterlist

import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShitterListRepositoryTest {
	@Test
	fun `local entries and pending changes survive restart`() {
		val file = createTempDirectory("cgc-shitter-list").resolve("shitter-list.json")
		val uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
		val first = ShitterListRepository(file)

		assertIs<ShitterListChangeResult.Added>(first.add(uuid, "Notch"))

		val reloaded = ShitterListRepository(file)
		assertEquals(listOf(ShitterListEntry(uuid, "Notch")), reloaded.entries())
		assertEquals(listOf(ShitterListUpdate(uuid, true, "Notch")), reloaded.pendingUpdates())
	}

	@Test
	fun `pending local intent wins over an older retained update`() {
		val file = createTempDirectory("cgc-shitter-list").resolve("shitter-list.json")
		val uuid = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")
		val repository = ShitterListRepository(file)
		val local = ShitterListUpdate(uuid, true, "jeb_")

		repository.add(uuid, "jeb_")
		repository.applyRemote(ShitterListUpdate(uuid, false, "jeb_"))
		assertTrue(repository.contains(uuid))

		repository.acknowledge(local)
		repository.applyRemote(ShitterListUpdate(uuid, false, "jeb_"))
		assertFalse(repository.contains(uuid))
	}

	@Test
	fun `name refresh keeps the player listed and updates pending state`() {
		val file = createTempDirectory("cgc-shitter-list").resolve("shitter-list.json")
		val uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
		val repository = ShitterListRepository(file)
		repository.add(uuid, null)

		val update = repository.refreshName(uuid, "Notch")

		assertEquals(ShitterListUpdate(uuid, true, "Notch"), update)
		assertEquals(listOf(ShitterListEntry(uuid, "Notch")), repository.entries())
		assertEquals(listOf(ShitterListUpdate(uuid, true, "Notch")), repository.pendingUpdates())
	}
}
