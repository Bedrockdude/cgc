package cgc.cgc.shitterlist

import net.fabricmc.loader.api.FabricLoader
import java.util.UUID
import java.util.concurrent.CompletableFuture

object ShitterListService {
	const val PROTECTED_ERROR_MESSAGE = "Error You are not good enough to use this command"
	val protectedUuid: UUID = requireNotNull(parseMinecraftUuid("5fe3bc5b48cd468a8fb8a652a3c9387e"))
	private val repository by lazy {
		ShitterListRepository(FabricLoader.getInstance().configDir.resolve("cgc").resolve("shitter-list.json"))
	}
	private val relay by lazy {
		ShitterListRelay(repository) { update -> update.uuid != protectedUuid }
	}

	fun start() {
		repository.load()
		val protectedEntry = repository.remove(protectedUuid)
		relay.start()
		if (protectedEntry is ShitterListChangeResult.Removed) {
			relay.publish(ShitterListUpdate(protectedUuid, listed = false, name = protectedEntry.entry.name))
		}
	}

	fun shutdown() {
		if (runCatching { relay }.isSuccess) relay.shutdown()
		MinecraftProfileLookup.shutdown()
	}

	fun entries(): List<ShitterListEntry> =
		repository.entries().filterNot { it.uuid == protectedUuid }

	fun isListed(uuid: UUID): Boolean =
		uuid != protectedUuid && repository.contains(uuid)

	fun findByName(name: String): ShitterListEntry? =
		repository.findByName(name)?.takeUnless { it.uuid == protectedUuid }

	fun syncConnected(): Boolean =
		relay.connected

	fun add(target: String): CompletableFuture<ShitterListChangeResult> {
		val raw = target.trim()
		parseMinecraftUuid(raw)?.let { uuid ->
			if (uuid == protectedUuid) return CompletableFuture.completedFuture(ShitterListChangeResult.Protected)
			val result = addResolved(uuid, null)
			if (result is ShitterListChangeResult.Added) {
				MinecraftProfileLookup.byUuid(uuid).thenAccept { profile ->
					if (profile != null && repository.contains(uuid)) {
						publishNameRefresh(profile)
					}
				}
			}
			return CompletableFuture.completedFuture(result)
		}

		if (!isMinecraftName(raw)) return CompletableFuture.completedFuture(ShitterListChangeResult.InvalidTarget)
		return MinecraftProfileLookup.byName(raw)
			.thenApply { profile ->
				when {
					profile == null -> ShitterListChangeResult.ProfileNotFound
					profile.uuid == protectedUuid -> ShitterListChangeResult.Protected
					else -> addResolved(profile.uuid, profile.name)
				}
			}
			.exceptionally { ShitterListChangeResult.LookupFailed }
	}

	fun remove(target: String): CompletableFuture<ShitterListChangeResult> {
		val raw = target.trim()
		parseMinecraftUuid(raw)?.let { uuid ->
			if (uuid == protectedUuid) return CompletableFuture.completedFuture(ShitterListChangeResult.NotListed)
			return CompletableFuture.completedFuture(removeResolved(uuid))
		}

		if (!isMinecraftName(raw)) return CompletableFuture.completedFuture(ShitterListChangeResult.InvalidTarget)
		findByName(raw)?.let { return CompletableFuture.completedFuture(removeResolved(it.uuid)) }
		return MinecraftProfileLookup.byName(raw)
			.thenApply { profile ->
				if (profile == null || profile.uuid == protectedUuid) {
					ShitterListChangeResult.NotListed
				} else {
					removeResolved(profile.uuid)
				}
			}
			.exceptionally { ShitterListChangeResult.LookupFailed }
	}

	fun resolveName(name: String): CompletableFuture<MinecraftProfile?> =
		MinecraftProfileLookup.byName(name)

	private fun addResolved(uuid: UUID, name: String?): ShitterListChangeResult {
		val result = repository.add(uuid, name)
		if (result is ShitterListChangeResult.Added) {
			relay.publish(ShitterListUpdate(uuid, listed = true, name = name))
		}
		return result
	}

	private fun removeResolved(uuid: UUID): ShitterListChangeResult {
		val result = repository.remove(uuid)
		if (result is ShitterListChangeResult.Removed) {
			relay.publish(ShitterListUpdate(uuid, listed = false, name = result.entry.name))
		}
		return result
	}

	private fun publishNameRefresh(profile: MinecraftProfile) {
		repository.refreshName(profile.uuid, profile.name)?.let(relay::publish)
	}

	private fun isMinecraftName(value: String): Boolean =
		value.matches(Regex("^[A-Za-z0-9_]{1,16}$"))
}
