package cgc.cgc.config

import cgc.cgc.module.setting.BooleanSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.Setting

object CgcSettings {
	val openAnimation = BooleanSetting("Open Animation", true)
	val moduleToggleClick = ModeSetting("Module Toggle Click", "Right", listOf("Left", "Right"))
	val guiTransparency = NumberSetting("GUI Transparency", 0.0, 0.95, 0.10, 0.05, displayAsPercent = true)

	val settings: List<Setting<*>> = listOf(
		openAnimation,
		moduleToggleClick,
		guiTransparency
	)

	val toggleMouseButton: Int
		get() = if (moduleToggleClick.value == "Left") 0 else 1
}
