package cgc.cgc.module.impl.dungeon

/**
 * Tracks the Simon Says display/input boundary using a grid button that is
 * absent during pattern display and restored when player input is accepted.
 */
internal class SimonSaysInputPhaseTracker {
	var inputPhaseObservedAtMs: Long = 0L
		private set
	var displayPhaseObserved: Boolean = false
		private set
	private var markerWasButton: Boolean? = null

	fun arm(markerIsButton: Boolean) {
		inputPhaseObservedAtMs = 0L
		displayPhaseObserved = !markerIsButton
		markerWasButton = markerIsButton
	}

	fun observe(markerIsButton: Boolean, nowMs: Long) {
		if (!markerIsButton) {
			displayPhaseObserved = true
			inputPhaseObservedAtMs = 0L
		} else if (displayPhaseObserved && markerWasButton != true) {
			inputPhaseObservedAtMs = nowMs
		}
		markerWasButton = markerIsButton
	}

	fun confirmsPattern(lastPatternLightAtMs: Long): Boolean =
		displayPhaseObserved &&
			inputPhaseObservedAtMs > 0L &&
			inputPhaseObservedAtMs + SAME_PACKET_REORDER_TOLERANCE_MS >= lastPatternLightAtMs

	fun clear() {
		inputPhaseObservedAtMs = 0L
		displayPhaseObserved = false
		markerWasButton = null
	}

	private companion object {
		// Section updates can expose the marker and final light in either callback order.
		const val SAME_PACKET_REORDER_TOLERANCE_MS = 50L
	}
}
