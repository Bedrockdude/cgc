package cgc.cgc.module

abstract class ModuleBase(
	protected var initialEnabled: Boolean = false
) {
	protected var enabledState: Boolean = initialEnabled

	val enabled: Boolean
		get() = enabledState

	abstract fun setEnabled(enabled: Boolean)

	abstract fun toggle()

	abstract fun onKeyToggle()
}
