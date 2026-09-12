package cgc.cgc.terminal

object TerminalContext {
	@JvmStatic
	var inTerminal: Boolean = false
		private set

	private val closeListeners = arrayListOf<() -> Unit>()

	@JvmStatic
	fun open() {
		inTerminal = true
	}

	@JvmStatic
	fun close() {
		val wasInTerminal = inTerminal
		inTerminal = false
		if (wasInTerminal) {
			val listeners = closeListeners.toList()
			closeListeners.clear()
			listeners.forEach { it.invoke() }
		}
	}

	fun runOnClose(action: () -> Unit) {
		closeListeners.add(action)
	}

	@JvmStatic
	fun reset() {
		inTerminal = false
		closeListeners.clear()
	}
}
