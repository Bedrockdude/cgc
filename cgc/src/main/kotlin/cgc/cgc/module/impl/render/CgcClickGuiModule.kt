package cgc.cgc.module.impl.render

import cgc.cgc.config.CgcSettings
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory

class CgcClickGuiModule : CgcModule(
	id = "ClickGUI",
	displayName = "Click GUI",
	category = ModuleCategory.RENDER,
	description = "Configures the CGC click GUI.",
	defaultEnabled = true,
	toggleable = false
) {
	init {
		registerProperty(*CgcSettings.settings.toTypedArray())
	}
}
