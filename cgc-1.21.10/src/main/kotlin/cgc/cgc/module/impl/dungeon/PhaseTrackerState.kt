package cgc.cgc.module.impl.dungeon

internal enum class TrackedBossPhase {
	NONE,
	P1,
	P2,
	P3,
	P4,
	P5
}

internal enum class P3ProgressState {
	ACTIVE,
	WAITING_FOR_GATE,
	DONE
}

internal data class PhaseTrackerSnapshot(
	val phase: TrackedBossPhase,
	val section: Int,
	val completed: Int,
	val total: Int?,
	val p3State: P3ProgressState,
	val necronDead: Boolean
)

/**
 * Keeps the F7/M7 phase progress independent from rendering and Minecraft state.
 *
 * P3 completion deliberately requires both the section counter and the gate signal.
 * Hypixel sends a separate subtitle when a gate is destroyed, or a five-second warning
 * when it is going to open automatically.
 */
internal class PhaseTrackerState(
	private val automaticGateDelayMs: Long = AUTOMATIC_GATE_DELAY_MS
) {
	private var phase = TrackedBossPhase.NONE
	private var section = 0
	private var completed = 0
	private var total: Int? = null
	private var gateOpened = false
	private var automaticGateAt: Long? = null
	private var sectionDoneAt: Long? = null
	private var necronDead = false

	fun snapshot(): PhaseTrackerSnapshot =
		PhaseTrackerSnapshot(
			phase = phase,
			section = section,
			completed = completed,
			total = total,
			p3State = when {
				sectionDoneAt != null -> P3ProgressState.DONE
				isCounterComplete() -> P3ProgressState.WAITING_FOR_GATE
				else -> P3ProgressState.ACTIVE
			},
			necronDead = necronDead
		)

	fun reset() {
		phase = TrackedBossPhase.NONE
		section = 0
		completed = 0
		total = null
		gateOpened = false
		automaticGateAt = null
		sectionDoneAt = null
		necronDead = false
	}

	fun synchronize(targetPhase: TrackedBossPhase, p3Section: Int = 1) {
		if (targetPhase == TrackedBossPhase.NONE) {
			return
		}

		if (phase == TrackedBossPhase.NONE || targetPhase.ordinal > phase.ordinal) {
			if (targetPhase == TrackedBossPhase.P3) {
				startP3(p3Section)
			} else {
				startPhase(targetPhase)
			}
		}
	}

	fun handleMessage(message: String, nowMs: Long): Boolean {
		val text = stripFormatting(message)
		when (text) {
			P1_START -> {
				startPhase(TrackedBossPhase.P1)
				return true
			}
			P2_START -> {
				startPhase(TrackedBossPhase.P2)
				return true
			}
			P3_START -> {
				startP3(1)
				return true
			}
			GATE_DESTROYED, CORE_OPENING -> {
				markGateOpened(nowMs)
				return true
			}
			GATE_OPENING -> {
				scheduleAutomaticGate(nowMs)
				return true
			}
			P4_START -> {
				startPhase(TrackedBossPhase.P4)
				return true
			}
			P5_START -> {
				startPhase(TrackedBossPhase.P5)
				return true
			}
		}

		val match = TERMINAL_PROGRESS.find(text) ?: return false
		if (phase != TrackedBossPhase.P3) {
			return false
		}

		val current = match.groupValues[1].toIntOrNull() ?: return false
		val maximum = match.groupValues[2].toIntOrNull() ?: return false
		if (maximum <= 0) {
			return false
		}

		updateP3Progress(current.coerceIn(0, maximum), maximum, nowMs)
		return true
	}

	fun tick(nowMs: Long, doneDisplayMs: Long) {
		val gateAt = automaticGateAt
		if (phase == TrackedBossPhase.P3 && gateAt != null && nowMs >= gateAt) {
			automaticGateAt = null
			markGateOpened(nowMs)
		}

		val doneAt = sectionDoneAt ?: return
		if (phase == TrackedBossPhase.P3 && section < LAST_P3_SECTION && nowMs - doneAt >= doneDisplayMs.coerceAtLeast(0L)) {
			advanceSection()
		}
	}

	fun handleNecronBossBar(name: String?, progress: Float): Boolean {
		if (phase != TrackedBossPhase.P4 || progress > 0.0f) {
			return false
		}
		if (name == null || !name.contains(NECRON, ignoreCase = true)) {
			return false
		}

		necronDead = true
		return true
	}

	private fun updateP3Progress(current: Int, maximum: Int, nowMs: Long) {
		if (sectionDoneAt != null) {
			if (section < LAST_P3_SECTION && (current < completed || total != maximum)) {
				advanceSection()
			} else {
				return
			}
		} else if (gateOpened && section < LAST_P3_SECTION && current < completed) {
			advanceSection()
		}

		completed = current
		total = maximum
		tryCompleteSection(nowMs)
	}

	private fun markGateOpened(nowMs: Long) {
		if (phase != TrackedBossPhase.P3 || sectionDoneAt != null) {
			return
		}

		gateOpened = true
		automaticGateAt = null
		tryCompleteSection(nowMs)
	}

	private fun scheduleAutomaticGate(nowMs: Long) {
		if (phase != TrackedBossPhase.P3 || sectionDoneAt != null || gateOpened) {
			return
		}

		val opensAt = nowMs + automaticGateDelayMs
		automaticGateAt = automaticGateAt?.coerceAtMost(opensAt) ?: opensAt
	}

	private fun tryCompleteSection(nowMs: Long) {
		if (gateOpened && isCounterComplete() && sectionDoneAt == null) {
			sectionDoneAt = nowMs
		}
	}

	private fun isCounterComplete(): Boolean {
		val maximum = total ?: return false
		return completed >= maximum
	}

	private fun startP3(startSection: Int) {
		startPhase(TrackedBossPhase.P3)
		section = startSection.coerceIn(1, LAST_P3_SECTION)
	}

	private fun startPhase(newPhase: TrackedBossPhase) {
		phase = newPhase
		section = 0
		completed = 0
		total = null
		gateOpened = false
		automaticGateAt = null
		sectionDoneAt = null
		necronDead = false
	}

	private fun advanceSection() {
		if (section >= LAST_P3_SECTION) {
			return
		}

		section++
		completed = 0
		total = null
		gateOpened = false
		automaticGateAt = null
		sectionDoneAt = null
	}

	private fun stripFormatting(message: String): String =
		message.replace(CONTROL_CODE, "").trim()

	private companion object {
		private const val AUTOMATIC_GATE_DELAY_MS = 5_000L
		private const val LAST_P3_SECTION = 4
		private const val NECRON = "Necron"

		private const val P1_START = "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"
		private const val P2_START = "[BOSS] Maxor: I'M TOO YOUNG TO DIE AGAIN!"
		private const val P3_START = "[BOSS] Goldor: Who dares trespass into my domain?"
		private const val P4_START = "[BOSS] Necron: I'm afraid, your journey ends now."
		private const val P5_START = "[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you."
		private const val GATE_DESTROYED = "The gate has been destroyed!"
		private const val GATE_OPENING = "The gate will open in 5 seconds!"
		private const val CORE_OPENING = "The Core entrance is opening!"

		private val CONTROL_CODE = Regex("§.")
		private val TERMINAL_PROGRESS = Regex(
			"^.*? (?:activated|completed) a (?:terminal|device|lever)! \\((\\d+)/(\\d+)\\)"
		)
	}
}
