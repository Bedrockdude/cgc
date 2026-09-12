package cgc.cgc.shitterlist

import java.util.UUID

data class ShitterListEntry(
	val uuid: UUID,
	val name: String?
)

data class ShitterListUpdate(
	val uuid: UUID,
	val listed: Boolean,
	val name: String?
)

data class MinecraftProfile(
	val uuid: UUID,
	val name: String
)

sealed interface ShitterListChangeResult {
	data class Added(val entry: ShitterListEntry) : ShitterListChangeResult
	data class Removed(val entry: ShitterListEntry) : ShitterListChangeResult
	data class AlreadyListed(val entry: ShitterListEntry) : ShitterListChangeResult
	data object NotListed : ShitterListChangeResult
	data object Protected : ShitterListChangeResult
	data object InvalidTarget : ShitterListChangeResult
	data object ProfileNotFound : ShitterListChangeResult
	data object LookupFailed : ShitterListChangeResult
}

internal fun UUID.compactString(): String =
	toString().replace("-", "")

internal fun parseMinecraftUuid(raw: String): UUID? {
	val compact = raw.trim().replace("-", "")
	if (!compact.matches(Regex("^[0-9a-fA-F]{32}$"))) return null
	return runCatching {
		UUID.fromString(
			"${compact.substring(0, 8)}-${compact.substring(8, 12)}-${compact.substring(12, 16)}-" +
				"${compact.substring(16, 20)}-${compact.substring(20)}"
		)
	}.getOrNull()
}
