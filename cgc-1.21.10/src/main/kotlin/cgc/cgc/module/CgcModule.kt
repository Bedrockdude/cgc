package cgc.cgc.module

import cgc.cgc.module.setting.Setting
import cgc.cgc.module.setting.DragSetting
import cgc.cgc.module.setting.group.DefaultGroupSetting
import cgc.cgc.module.setting.group.GroupSetting

abstract class CgcModule(
	val id: String,
	val displayName: String,
	val category: ModuleCategory,
	val description: String,
	defaultEnabled: Boolean = false,
	val toggleable: Boolean = true
) : ModuleBase(defaultEnabled) {
	private val group = DefaultGroupSetting("General", this)
	private val registeredGroups = arrayListOf<GroupSetting<out SubModule<*>>>()

	val settings: List<GroupSetting<out SubModule<*>>>
		get() = registeredGroups

	val aliases: List<String>
		get() = listOf(displayName, id)

	override fun setEnabled(enabled: Boolean) {
		if (!toggleable || this.enabled == enabled) return

		this.enabledState = enabled
		if (this.enabled) {
			onEnable()
			registeredGroups.forEach { it.value.onModuleToggled(true) }
		} else {
			onDisable()
			reset()
			registeredGroups.forEach { it.value.onModuleToggled(false) }
		}
	}

	override fun toggle() {
		setEnabled(!enabled)
	}

	override fun onKeyToggle() {
		toggle()
	}

	fun registerProperty(vararg settings: Setting<*>) {
		for (setting in settings) {
			if (setting is GroupSetting<*>) {
				val dupe = registeredGroups.firstOrNull { it.name.equals(setting.name, ignoreCase = true) }
				if (dupe != null) {
					registeredGroups.remove(dupe)
					dupe.value.onModuleToggled(false)
				}
				registeredGroups.add(setting)
			} else {
				group.add(setting)
			}
		}

		if (group.value.settings.isEmpty()) {
			registeredGroups.remove(group)
		} else if (!registeredGroups.contains(group)) {
			registeredGroups.add(0, group)
		}
	}

	fun getSettingFromName(name: String): Setting<*>? =
		registeredGroups.firstOrNull { it.name.equals(name, ignoreCase = true) }

	fun getShownSettings(): List<GroupSetting<out SubModule<*>>> =
		registeredGroups.filter { it.isVisible() }

	@Suppress("UNCHECKED_CAST")
	fun <T : SubModule<*>> getSubModule(subModule: Class<T>): T? =
		registeredGroups.firstOrNull { subModule.isAssignableFrom(it.value::class.java) }?.value as? T

	fun flatSettings(): List<Setting<*>> =
		registeredGroups.flatMap { it.value.settings }

	fun getDragSettings(): List<DragSetting> =
		flatSettings().filterIsInstance<DragSetting>()

	protected fun registerSettings(vararg settings: Setting<*>) {
		registerProperty(*settings)
	}

	open fun onEnable() {
	}

	open fun onDisable() {
	}

	protected open fun reset() {
	}

	open fun onGuiClosed() {
	}

	open fun onLoaded() {
	}
}
