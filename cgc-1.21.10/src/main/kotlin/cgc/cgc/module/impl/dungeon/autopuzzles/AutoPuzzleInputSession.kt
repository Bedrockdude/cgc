package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.runtime.PhysicalInputTracker

class AutoPuzzleInputSession private constructor(
	private var baseline: PhysicalInputTracker.Snapshot,
	private val acquiredAtMs: Long
) {
	fun cancellationReason(nowMs: Long): String? {
		val current = PhysicalInputTracker.snapshot()
		if (nowMs < acquiredAtMs + INPUT_GRACE_MS) {
			baseline = current
			return null
		}
		if (current.keyboardPressSequence != baseline.keyboardPressSequence) {
			if (current.lastKeyboardPressAtMs >= acquiredAtMs + INPUT_GRACE_MS) return "keyboard input"
			baseline = baseline.copy(
				keyboardPressSequence = current.keyboardPressSequence,
				lastKeyboardPressAtMs = current.lastKeyboardPressAtMs
			)
		}
		if (current.mouseButtonPressSequence != baseline.mouseButtonPressSequence) {
			if (current.lastMouseButtonPressAtMs >= acquiredAtMs + INPUT_GRACE_MS) return "mouse button input"
			baseline = baseline.copy(
				mouseButtonPressSequence = current.mouseButtonPressSequence,
				lastMouseButtonPressAtMs = current.lastMouseButtonPressAtMs
			)
		}
		if (current.scrollSequence != baseline.scrollSequence) {
			if (current.lastScrollAtMs >= acquiredAtMs + INPUT_GRACE_MS) return "mouse wheel input"
			baseline = baseline.copy(
				scrollSequence = current.scrollSequence,
				lastScrollAtMs = current.lastScrollAtMs
			)
		}
		if (current.mouseMoveSequence != baseline.mouseMoveSequence) {
			if (current.lastMouseMoveAtMs < acquiredAtMs + INPUT_GRACE_MS) {
				baseline = baseline.copy(
					mouseMoveSequence = current.mouseMoveSequence,
					lastMouseMoveAtMs = current.lastMouseMoveAtMs
				)
			} else {
				return "mouse movement"
			}
		}
		return null
	}

	companion object {
		const val INPUT_GRACE_MS = 1_000L
		const val MOUSE_MOVE_GRACE_MS = INPUT_GRACE_MS

		fun acquire(nowMs: Long, requireReleased: Boolean = true): AutoPuzzleInputSession? {
			if (requireReleased && PhysicalInputTracker.hasHeldKeyOrButton()) return null
			return AutoPuzzleInputSession(PhysicalInputTracker.snapshot(), nowMs)
		}
	}
}
