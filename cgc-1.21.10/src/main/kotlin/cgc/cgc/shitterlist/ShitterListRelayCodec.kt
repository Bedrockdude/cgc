package cgc.cgc.shitterlist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ShitterListRelayCodec {
	private const val VERSION = 1
	private const val TOPIC_ROOT = "cgc/7aba865cd9c5ab67903cb150e360f40e/shitter-list/v1"
	private const val KEY_BASE64 = "369UzeWoMOPXakUsqc86cq3+pXkNIIfpp8tkKEy6hHI="
	private const val NONCE_SIZE = 12
	private val key = Base64.getDecoder().decode(KEY_BASE64)
	private val random = SecureRandom()

	val topicFilter: String = "$TOPIC_ROOT/+"

	fun topic(update: ShitterListUpdate): String =
		"$TOPIC_ROOT/${topicSuffix(update)}"

	fun encode(update: ShitterListUpdate): ByteArray {
		val suffix = topicSuffix(update)
		val json = JsonObject().apply {
			addProperty("version", VERSION)
			addProperty("uuid", update.uuid.compactString())
			addProperty("listed", update.listed)
			update.name?.let { addProperty("name", it) }
		}
		val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
		cipher.updateAAD(suffix.toByteArray(StandardCharsets.US_ASCII))
		val encrypted = cipher.doFinal(json.toString().toByteArray(StandardCharsets.UTF_8))
		return Base64.getUrlEncoder().withoutPadding().encode(nonce + encrypted)
	}

	fun decode(topic: String, payload: ByteArray): ShitterListUpdate? = runCatching {
		if (!topic.startsWith("$TOPIC_ROOT/")) return null
		val suffix = topic.substringAfterLast('/')
		val packed = Base64.getUrlDecoder().decode(payload)
		if (packed.size <= NONCE_SIZE) return null
		val nonce = packed.copyOfRange(0, NONCE_SIZE)
		val encrypted = packed.copyOfRange(NONCE_SIZE, packed.size)
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
		cipher.updateAAD(suffix.toByteArray(StandardCharsets.US_ASCII))
		val json = String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
		val obj = JsonParser.parseString(json).asJsonObject
		if (obj.get("version")?.asInt != VERSION) return null
		val uuid = parseMinecraftUuid(obj.get("uuid")?.asString.orEmpty()) ?: return null
		val update = ShitterListUpdate(
			uuid = uuid,
			listed = obj.get("listed")?.asBoolean ?: return null,
			name = obj.get("name")?.asString?.takeIf { it.matches(Regex("^[A-Za-z0-9_]{1,16}$")) }
		)
		if (!MessageDigest.isEqual(suffix.toByteArray(), topicSuffix(update).toByteArray())) return null
		update
	}.getOrNull()

	private fun topicSuffix(update: ShitterListUpdate): String {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(key, "HmacSHA256"))
		val digest = mac.doFinal(update.uuid.compactString().toByteArray(StandardCharsets.US_ASCII))
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
	}
}
