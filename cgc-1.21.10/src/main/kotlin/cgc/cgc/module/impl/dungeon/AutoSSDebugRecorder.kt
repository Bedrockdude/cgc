package cgc.cgc.module.impl.dungeon

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Temporary, self-contained flight recorder for Auto SS.
 *
 * A session stays in memory while the solver is healthy. Only abnormal exits and
 * watchdog stalls create a file, so successful dungeon runs do not accumulate
 * reports. Removing this class and the small set of calls in [AutoSSSpecsafe]
 * removes the diagnostic tool without changing the solver itself.
 */
internal class AutoSSDebugRecorder(
	private val reportDirectory: () -> Path = {
		FabricLoader.getInstance().configDir.resolve("cgc").resolve("reports")
	},
	private val wallClock: () -> Instant = Instant::now
) {
	private var session: Session? = null

	val isActive: Boolean
		@Synchronized get() = session != null

	@Synchronized
	fun begin(
		trigger: String,
		metadata: Map<String, String>,
		phase: String,
		initialSnapshot: Map<String, String>,
		nowMs: Long
	) {
		val startedAt = wallClock()
		val newSession = Session(
			id = UUID.randomUUID().toString().substring(0, 8),
			startedAt = startedAt,
			startedAtMs = nowMs,
			lastProgressAtMs = nowMs,
			lastTickAtMs = nowMs,
			lastPhase = phase,
			metadata = LinkedHashMap(metadata),
			lastSnapshot = LinkedHashMap(initialSnapshot)
		)
		session = newSession
		append(newSession, nowMs, "session_started", "trigger=$trigger", progress = true)
		append(newSession, nowMs, "phase", phase, progress = true)
	}

	@Synchronized
	fun event(category: String, details: String, nowMs: Long, progress: Boolean = false) {
		val active = session ?: return
		append(active, nowMs, category, details, progress)
	}

	@Synchronized
	fun observeTick(
		nowMs: Long,
		phase: String,
		snapshot: Map<String, String>
	) {
		val active = session ?: return
		active.tickCount++
		val tickGapMs = (nowMs - active.lastTickAtMs).coerceAtLeast(0L)
		active.lastTickAtMs = nowMs
		active.maximumTickGapMs = maxOf(active.maximumTickGapMs, tickGapMs)
		if (tickGapMs >= CLIENT_LAG_SPIKE_MS) {
			active.clientLagSpikeCount++
			append(
				active,
				nowMs,
				"client_lag_spike",
				"client tick gap=${tickGapMs}ms threshold=${CLIENT_LAG_SPIKE_MS}ms",
				progress = false
			)
		}

		active.lastSnapshot = LinkedHashMap(snapshot)
		if (phase != active.lastPhase) {
			active.lastPhase = phase
			active.phaseTransitionCount++
			append(active, nowMs, "phase", phase, progress = true)
		}
	}

	@Synchronized
	fun stalledForMs(nowMs: Long): Long? {
		val active = session ?: return null
		return (nowMs - active.lastProgressAtMs).coerceAtLeast(0L)
	}

	@Synchronized
	fun fail(reason: String, finalSnapshot: Map<String, String>, nowMs: Long): AutoSSDebugSaveResult? {
		val active = session ?: return null
		active.lastSnapshot = LinkedHashMap(finalSnapshot)
		append(active, nowMs, "failure", reason, progress = false)
		val failedAt = wallClock()
		val contents = renderReport(active, reason, failedAt, nowMs)
		val result = writeReport(active, contents)
		session = null
		return result
	}

	@Synchronized
	fun complete(finalSnapshot: Map<String, String>, nowMs: Long) {
		val active = session ?: return
		active.lastSnapshot = LinkedHashMap(finalSnapshot)
		append(active, nowMs, "completed", "Simon Says completed successfully; report discarded", progress = true)
		session = null
	}

	private fun append(session: Session, nowMs: Long, category: String, details: String, progress: Boolean) {
		if (progress) {
			session.lastProgressAtMs = nowMs
		}
		val elapsedMs = (nowMs - session.startedAtMs).coerceAtLeast(0L)
		val safeCategory = singleLine(category)
		val safeDetails = singleLine(details)
		val previous = session.timeline.lastOrNull()
		if (previous != null &&
			previous.category == safeCategory &&
			previous.details == safeDetails &&
			elapsedMs - previous.lastElapsedMs <= EVENT_COALESCE_WINDOW_MS
		) {
			previous.repetitions++
			previous.lastElapsedMs = elapsedMs
			return
		}

		if (session.timeline.size >= MAX_TIMELINE_ENTRIES) {
			session.droppedTimelineEntries++
			return
		}
		session.timeline += TimelineEntry(elapsedMs, elapsedMs, safeCategory, safeDetails)
	}

	private fun renderReport(session: Session, reason: String, failedAt: Instant, failedAtMs: Long): String =
		buildString {
			appendLine("CGC Auto SS diagnostic report")
			appendLine("format_version: 1")
			appendLine("session_id: ${session.id}")
			appendLine("started_at_utc: ${session.startedAt}")
			appendLine("failed_at_utc: $failedAt")
			appendLine("duration_ms: ${(failedAtMs - session.startedAtMs).coerceAtLeast(0L)}")
			appendLine("failure_reason: ${singleLine(reason)}")
			appendLine()
			appendLine("statistics:")
			appendLine("  client_ticks_observed: ${session.tickCount}")
			appendLine("  phase_transitions: ${session.phaseTransitionCount}")
			appendLine("  client_lag_spikes: ${session.clientLagSpikeCount}")
			appendLine("  maximum_client_tick_gap_ms: ${session.maximumTickGapMs}")
			appendLine("  no_progress_for_ms: ${(failedAtMs - session.lastProgressAtMs).coerceAtLeast(0L)}")
			appendLine("  dropped_timeline_entries: ${session.droppedTimelineEntries}")
			appendLine()
			appendLine("session_metadata:")
			appendKeyValues(session.metadata)
			appendLine()
			appendLine("final_state:")
			appendKeyValues(session.lastSnapshot)
			appendLine()
			appendLine("timeline:")
			for (entry in session.timeline) {
				val range = if (entry.repetitions <= 1) {
					"+${entry.firstElapsedMs}ms"
				} else {
					"+${entry.firstElapsedMs}ms..+${entry.lastElapsedMs}ms x${entry.repetitions}"
				}
				appendLine("  $range [${entry.category}] ${entry.details}")
			}
		}

	private fun StringBuilder.appendKeyValues(values: Map<String, String>) {
		if (values.isEmpty()) {
			appendLine("  (none)")
			return
		}
		for ((key, value) in values) {
			appendLine("  ${singleLine(key)}: ${singleLine(value)}")
		}
	}

	private fun writeReport(session: Session, contents: String): AutoSSDebugSaveResult {
		return runCatching {
			val directory = reportDirectory()
			Files.createDirectories(directory)
			val timestamp = FILE_TIMESTAMP_FORMAT.format(session.startedAt)
			val path = directory.resolve("autosss-failure-$timestamp-${session.id}.txt")
			Files.writeString(
				path,
				contents,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE
			)
			LOGGER.warn("Auto SS diagnostic report written to {}", path)
			AutoSSDebugSaveResult(path, null)
		}.getOrElse { error ->
			LOGGER.error("Could not write Auto SS diagnostic report.\n{}", contents, error)
			AutoSSDebugSaveResult(null, "${error::class.simpleName}: ${error.message ?: "unknown error"}")
		}
	}

	private fun singleLine(value: String): String =
		value
			.replace('\r', ' ')
			.replace('\n', ' ')
			.replace('\t', ' ')
			.take(MAX_VALUE_LENGTH)

	private data class Session(
		val id: String,
		val startedAt: Instant,
		val startedAtMs: Long,
		var lastProgressAtMs: Long,
		var lastTickAtMs: Long,
		var lastPhase: String,
		val metadata: LinkedHashMap<String, String>,
		var lastSnapshot: LinkedHashMap<String, String>,
		val timeline: MutableList<TimelineEntry> = arrayListOf(),
		var tickCount: Long = 0L,
		var phaseTransitionCount: Int = 0,
		var clientLagSpikeCount: Int = 0,
		var maximumTickGapMs: Long = 0L,
		var droppedTimelineEntries: Int = 0
	)

	private data class TimelineEntry(
		val firstElapsedMs: Long,
		var lastElapsedMs: Long,
		val category: String,
		val details: String,
		var repetitions: Int = 1
	)

	private companion object {
		private val LOGGER = LoggerFactory.getLogger(AutoSSDebugRecorder::class.java)
		private val FILE_TIMESTAMP_FORMAT = DateTimeFormatter
			.ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'")
			.withZone(ZoneOffset.UTC)
		private const val CLIENT_LAG_SPIKE_MS = 125L
		private const val EVENT_COALESCE_WINDOW_MS = 1_000L
		private const val MAX_TIMELINE_ENTRIES = 10_000
		private const val MAX_VALUE_LENGTH = 4_000
	}
}

internal data class AutoSSDebugSaveResult(
	val path: Path?,
	val error: String?
)
