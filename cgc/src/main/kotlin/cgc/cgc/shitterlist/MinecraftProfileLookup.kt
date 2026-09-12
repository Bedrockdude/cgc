package cgc.cgc.shitterlist

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal object MinecraftProfileLookup {
	private const val PROFILE_API = "https://api.minecraftservices.com/minecraft/profile/lookup"
	private val executor = Executors.newFixedThreadPool(2) { task ->
		Thread(task, "CGC Minecraft Profile Lookup").apply { isDaemon = true }
	}
	private val client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.executor(executor)
		.build()
	private val cache = ConcurrentHashMap<String, MinecraftProfile>()
	private val inFlight = ConcurrentHashMap<String, CompletableFuture<MinecraftProfile?>>()

	fun byName(name: String): CompletableFuture<MinecraftProfile?> {
		if (!name.matches(Regex("^[A-Za-z0-9_]{1,16}$"))) {
			return CompletableFuture.completedFuture(null)
		}
		val key = name.lowercase(Locale.ROOT)
		cache[key]?.let { return CompletableFuture.completedFuture(it) }
		return inFlight.computeIfAbsent("name:$key") {
			request("$PROFILE_API/name/$name").whenComplete { _, _ -> inFlight.remove("name:$key") }
		}
	}

	fun byUuid(uuid: java.util.UUID): CompletableFuture<MinecraftProfile?> {
		cache.values.firstOrNull { it.uuid == uuid }?.let { return CompletableFuture.completedFuture(it) }
		val key = uuid.compactString()
		return inFlight.computeIfAbsent("uuid:$key") {
			request("$PROFILE_API/$key").whenComplete { _, _ -> inFlight.remove("uuid:$key") }
		}
	}

	fun shutdown() {
		executor.shutdownNow()
	}

	private fun request(url: String): CompletableFuture<MinecraftProfile?> {
		val request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(10))
			.header("Accept", "application/json")
			.GET()
			.build()
		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply { response ->
				when (response.statusCode()) {
					200 -> parse(response.body())
					404 -> null
					else -> throw IllegalStateException("Minecraft profile lookup returned HTTP ${response.statusCode()}")
				}
			}
	}

	private fun parse(json: String): MinecraftProfile? = runCatching {
		val obj = JsonParser.parseString(json).asJsonObject
		val uuid = parseMinecraftUuid(obj.get("id")?.asString.orEmpty()) ?: return null
		val name = obj.get("name")?.asString?.takeIf { it.matches(Regex("^[A-Za-z0-9_]{1,16}$")) } ?: return null
		MinecraftProfile(uuid, name).also { profile ->
			cache[profile.name.lowercase(Locale.ROOT)] = profile
		}
	}.getOrNull()
}
