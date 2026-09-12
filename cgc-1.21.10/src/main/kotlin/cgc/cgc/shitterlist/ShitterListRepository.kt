package cgc.cgc.shitterlist

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ShitterListRepository(private val file: Path) {
	private val gson = GsonBuilder().setPrettyPrinting().create()
	private val entries = linkedMapOf<UUID, ShitterListEntry>()
	private val pending = linkedMapOf<UUID, ShitterListUpdate>()
	private var loaded = false

	@Synchronized
	fun load() {
		if (loaded) return
		loaded = true
		if (!file.exists()) return

		runCatching {
			val root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).asJsonObject
			val names = root.getAsJsonObject("names") ?: JsonObject()
			for (element in root.getAsJsonArray("uuids") ?: JsonArray()) {
				val uuid = parseMinecraftUuid(element.asString) ?: continue
				val name = names.get(uuid.compactString())?.asString?.takeIf(::isMinecraftName)
				entries[uuid] = ShitterListEntry(uuid, name)
			}

			for (element in root.getAsJsonArray("pending") ?: JsonArray()) {
				val obj = element.asJsonObject
				val uuid = parseMinecraftUuid(obj.get("uuid")?.asString.orEmpty()) ?: continue
				val listed = obj.get("listed")?.asBoolean ?: continue
				val name = obj.get("name")?.asString?.takeIf(::isMinecraftName)
				pending[uuid] = ShitterListUpdate(uuid, listed, name)
			}
		}
	}

	@Synchronized
	fun entries(): List<ShitterListEntry> {
		load()
		return entries.values.sortedWith(compareBy({ it.name?.lowercase(Locale.ROOT) ?: "~" }, { it.uuid.toString() }))
	}

	@Synchronized
	fun contains(uuid: UUID): Boolean {
		load()
		return uuid in entries
	}

	@Synchronized
	fun findByName(name: String): ShitterListEntry? {
		load()
		return entries.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
	}

	@Synchronized
	fun add(uuid: UUID, name: String?): ShitterListChangeResult {
		load()
		val existing = entries[uuid]
		if (existing != null) return ShitterListChangeResult.AlreadyListed(existing)

		val entry = ShitterListEntry(uuid, name?.takeIf(::isMinecraftName))
		val update = ShitterListUpdate(uuid, listed = true, name = entry.name)
		entries[uuid] = entry
		pending[uuid] = update
		save()
		return ShitterListChangeResult.Added(entry)
	}

	@Synchronized
	fun remove(uuid: UUID): ShitterListChangeResult {
		load()
		val entry = entries.remove(uuid) ?: return ShitterListChangeResult.NotListed
		pending[uuid] = ShitterListUpdate(uuid, listed = false, name = entry.name)
		save()
		return ShitterListChangeResult.Removed(entry)
	}

	@Synchronized
	fun refreshName(uuid: UUID, name: String): ShitterListUpdate? {
		load()
		if (!isMinecraftName(name)) return null
		val existing = entries[uuid] ?: return null
		if (existing.name == name) return null

		entries[uuid] = ShitterListEntry(uuid, name)
		val update = ShitterListUpdate(uuid, listed = true, name = name)
		pending[uuid] = update
		save()
		return update
	}

	@Synchronized
	fun applyRemote(update: ShitterListUpdate) {
		load()
		if (pending.containsKey(update.uuid)) return

		val changed = if (update.listed) {
			val entry = ShitterListEntry(update.uuid, update.name?.takeIf(::isMinecraftName))
			entries.put(update.uuid, entry) != entry
		} else {
			entries.remove(update.uuid) != null
		}
		if (changed) save()
	}

	@Synchronized
	fun pendingUpdates(): List<ShitterListUpdate> {
		load()
		return pending.values.toList()
	}

	@Synchronized
	fun acknowledge(update: ShitterListUpdate) {
		load()
		if (pending[update.uuid] != update) return
		pending.remove(update.uuid)
		save()
	}

	@Synchronized
	fun saveNow() {
		load()
		save()
	}

	private fun save() {
		val root = JsonObject()
		val uuids = JsonArray()
		val names = JsonObject()
		for (entry in entries.values.sortedBy { it.uuid.toString() }) {
			val compact = entry.uuid.compactString()
			uuids.add(compact)
			entry.name?.let { names.addProperty(compact, it) }
		}
		root.add("uuids", uuids)
		root.add("names", names)

		val pendingJson = JsonArray()
		for (update in pending.values.sortedBy { it.uuid.toString() }) {
			val obj = JsonObject()
			obj.addProperty("uuid", update.uuid.compactString())
			obj.addProperty("listed", update.listed)
			update.name?.let { obj.addProperty("name", it) }
			pendingJson.add(obj)
		}
		root.add("pending", pendingJson)

		file.parent?.createDirectories()
		val temporary = file.resolveSibling("${file.fileName}.tmp")
		Files.writeString(temporary, gson.toJson(root), StandardCharsets.UTF_8)
		try {
			Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
		} catch (_: AtomicMoveNotSupportedException) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
		}
	}

	private companion object {
		fun isMinecraftName(value: String): Boolean =
			value.matches(Regex("^[A-Za-z0-9_]{1,16}$"))
	}
}
