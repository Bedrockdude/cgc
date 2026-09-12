package cgc.cgc.module.impl.dungeon

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoSSDebugRecorderTest {
	@TempDir
	lateinit var reportDirectory: Path

	@Test
	fun `failure report contains timeline lag statistics and final state`() {
		val recorder = AutoSSDebugRecorder(
			reportDirectory = { reportDirectory },
			wallClock = { Instant.parse("2026-08-20T12:00:00Z") }
		)
		recorder.begin(
			trigger = "test",
			metadata = linkedMapOf("minecraft_version" to "test-version"),
			phase = "waiting_start_click",
			initialSnapshot = linkedMapOf("state" to "starting"),
			nowMs = 1_000L
		)
		recorder.event("click_preflight_verified", "button=(110,121,92)", 1_050L, progress = true)
		recorder.observeTick(
			nowMs = 1_300L,
			phase = "waiting_click_ack",
			snapshot = linkedMapOf("pending_click" to "sequence=42")
		)

		val result = assertNotNull(
			recorder.fail(
				reason = "test acknowledgement timeout",
				finalSnapshot = linkedMapOf("pending_click" to "sequence=42 age_ms=6000"),
				nowMs = 7_050L
			)
		)
		val report = assertNotNull(result.path)
		assertNull(result.error)
		val contents = Files.readString(report)

		assertTrue(contents.contains("failure_reason: test acknowledgement timeout"))
		assertTrue(contents.contains("maximum_client_tick_gap_ms: 300"))
		assertTrue(contents.contains("client_lag_spikes: 1"))
		assertTrue(contents.contains("minecraft_version: test-version"))
		assertTrue(contents.contains("pending_click: sequence=42 age_ms=6000"))
		assertTrue(contents.contains("[click_preflight_verified] button=(110,121,92)"))
		assertTrue(contents.contains("[phase] waiting_click_ack"))
	}

	@Test
	fun `successful session does not leave a report file`() {
		val recorder = AutoSSDebugRecorder(
			reportDirectory = { reportDirectory },
			wallClock = { Instant.parse("2026-08-20T12:00:00Z") }
		)
		recorder.begin("test", emptyMap(), "starting", emptyMap(), 100L)
		recorder.complete(mapOf("state" to "done"), 500L)

		assertEquals(0L, Files.list(reportDirectory).use { it.count() })
		assertTrue(!recorder.isActive)
	}

	@Test
	fun `phase changes reset the no-progress timer`() {
		val recorder = AutoSSDebugRecorder(
			reportDirectory = { reportDirectory },
			wallClock = { Instant.parse("2026-08-20T12:00:00Z") }
		)
		recorder.begin("test", emptyMap(), "starting", emptyMap(), 1_000L)

		recorder.observeTick(4_000L, "starting", emptyMap())
		assertEquals(3_000L, recorder.stalledForMs(4_000L))

		recorder.observeTick(4_100L, "capturing_pattern", emptyMap())
		assertEquals(0L, recorder.stalledForMs(4_100L))
	}
}
