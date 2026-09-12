package cgc.cgc.shitterlist

import java.util.UUID
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShitterListRelayCodecTest {
	@Test
	fun `encrypted relay update round trips`() {
		val update = ShitterListUpdate(
			UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
			listed = true,
			name = "Notch"
		)
		val topic = ShitterListRelayCodec.topic(update)

		assertEquals(update, ShitterListRelayCodec.decode(topic, ShitterListRelayCodec.encode(update)))
	}

	@Test
	fun `tampered payload is rejected`() {
		val update = ShitterListUpdate(
			UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"),
			listed = false,
			name = "jeb_"
		)
		val decoded = Base64.getUrlDecoder().decode(ShitterListRelayCodec.encode(update))
		decoded[decoded.lastIndex] = (decoded.last().toInt() xor 1).toByte()
		val payload = Base64.getUrlEncoder().withoutPadding().encode(decoded)

		assertNull(ShitterListRelayCodec.decode(ShitterListRelayCodec.topic(update), payload))
	}
}
