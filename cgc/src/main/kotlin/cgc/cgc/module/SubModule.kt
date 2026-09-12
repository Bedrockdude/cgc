package cgc.cgc.module

import cgc.cgc.module.setting.Setting

open class SubModule<T : CgcModule>(
	val module: T,
	val name: String,
	defaultEnabled: Boolean = false
) : ModuleBase(defaultEnabled) {
	private val registeredSettings = arrayListOf<Setting<*>>()

	val settings: List<Setting<*>>
		get() = registeredSettings

	fun registerProperty(vararg settings: Setting<*>) {
		registeredSettings.addAll(settings)
	}

	fun getSettingFromName(name: String): Setting<*>? =
		registeredSettings.firstOrNull { it.name.equals(name, ignoreCase = true) }

	fun getShownSettings(): List<Setting<*>> =
		registeredSettings.filter { it.isVisible() }

	override fun setEnabled(enabled: Boolean) {
		if (this.enabled == enabled) return
		this.enabledState = enabled

		if (enabled) {
			onEnable()
		} else {
			onDisable()
			reset()
		}
	}

	override fun toggle() {
		setEnabled(!enabled)
	}

	override fun onKeyToggle() {
		toggle()
	}

	open fun onModuleToggled(state: Boolean) {
		if (state && enabled) {
			onEnable()
		} else {
			reset()
			onDisable()
		}
	}

	protected open fun onEnable() {
	}

	protected open fun onDisable() {
	}

	protected open fun reset() {
	}
}
