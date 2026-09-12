package cgc.cgc.runtime

/**
 * Raw callback observation only. Generated KeyMapping state and LocalPlayer
 * rotation changes never pass through this tracker.
 */
object PhysicalInputTracker {
	data class Snapshot(
		val keyboardPressSequence: Long,
		val mouseButtonPressSequence: Long,
		val mouseMoveSequence: Long,
		val scrollSequence: Long,
		val lastKeyboardPressAtMs: Long,
		val lastMouseButtonPressAtMs: Long,
		val lastMouseMoveAtMs: Long,
		val lastScrollAtMs: Long
	)

	private val heldKeys = linkedSetOf<Int>()
	private val heldMouseButtons = linkedSetOf<Int>()
	private var keyboardPressSequence = 0L
	private var mouseButtonPressSequence = 0L
	private var mouseMoveSequence = 0L
	private var scrollSequence = 0L
	private var lastMouseX: Double? = null
	private var lastMouseY: Double? = null
	private var lastKeyboardPressAtMs = Long.MIN_VALUE
	private var lastMouseButtonPressAtMs = Long.MIN_VALUE
	private var lastMouseMoveAtMs = Long.MIN_VALUE
	private var lastScrollAtMs = Long.MIN_VALUE

	@JvmStatic
	fun onKey(key: Int, action: Int) {
		onKeyAt(key, action, monotonicNowMs())
	}

	@Synchronized
	internal fun onKeyAt(key: Int, action: Int, eventAtMs: Long) {
		when (action) {
			ACTION_RELEASE -> heldKeys.remove(key)
			ACTION_PRESS, ACTION_REPEAT -> {
				heldKeys.add(key)
				keyboardPressSequence++
				lastKeyboardPressAtMs = eventAtMs
			}
		}
	}

	@JvmStatic
	fun onMouseButton(button: Int, action: Int) {
		onMouseButtonAt(button, action, monotonicNowMs())
	}

	@Synchronized
	internal fun onMouseButtonAt(button: Int, action: Int, eventAtMs: Long) {
		when (action) {
			ACTION_RELEASE -> heldMouseButtons.remove(button)
			ACTION_PRESS, ACTION_REPEAT -> {
				heldMouseButtons.add(button)
				mouseButtonPressSequence++
				lastMouseButtonPressAtMs = eventAtMs
			}
		}
	}

	@JvmStatic
	fun onMouseMove(x: Double, y: Double) {
		onMouseMoveAt(x, y, System.nanoTime() / 1_000_000L)
	}

	@Synchronized
	internal fun onMouseMoveAt(x: Double, y: Double, eventAtMs: Long) {
		val changed = lastMouseX == null || lastMouseY == null || lastMouseX != x || lastMouseY != y
		lastMouseX = x
		lastMouseY = y
		if (changed) {
			mouseMoveSequence++
			lastMouseMoveAtMs = eventAtMs
		}
	}

	@JvmStatic
	fun onScroll(xOffset: Double, yOffset: Double) {
		onScrollAt(xOffset, yOffset, monotonicNowMs())
	}

	@Synchronized
	internal fun onScrollAt(xOffset: Double, yOffset: Double, eventAtMs: Long) {
		if (xOffset != 0.0 || yOffset != 0.0) {
			scrollSequence++
			lastScrollAtMs = eventAtMs
		}
	}

	@Synchronized
	fun snapshot(): Snapshot = Snapshot(
		keyboardPressSequence,
		mouseButtonPressSequence,
		mouseMoveSequence,
		scrollSequence,
		lastKeyboardPressAtMs,
		lastMouseButtonPressAtMs,
		lastMouseMoveAtMs,
		lastScrollAtMs
	)

	@Synchronized
	fun hasHeldKeyOrButton(): Boolean = heldKeys.isNotEmpty() || heldMouseButtons.isNotEmpty()

	@Synchronized
	fun reset() {
		heldKeys.clear()
		heldMouseButtons.clear()
		lastMouseX = null
		lastMouseY = null
		lastKeyboardPressAtMs = Long.MIN_VALUE
		lastMouseButtonPressAtMs = Long.MIN_VALUE
		lastMouseMoveAtMs = Long.MIN_VALUE
		lastScrollAtMs = Long.MIN_VALUE
		keyboardPressSequence++
		mouseButtonPressSequence++
		mouseMoveSequence++
		scrollSequence++
	}

	private const val ACTION_RELEASE = 0
	private const val ACTION_PRESS = 1
	private const val ACTION_REPEAT = 2

	private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
}
