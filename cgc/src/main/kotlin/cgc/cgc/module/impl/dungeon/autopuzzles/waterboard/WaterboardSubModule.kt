package cgc.cgc.module.impl.dungeon.autopuzzles.waterboard

import cgc.cgc.data.Colour
import cgc.cgc.module.SubModule
import cgc.cgc.module.impl.dungeon.AutoPuzzles
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.NumberSetting

class WaterboardSubModule(module: AutoPuzzles) : SubModule<AutoPuzzles>(module, "Waterboard", defaultEnabled = false) {
	val aimSpeed = NumberSetting("Aim Speed", 0.5, 2.0, 1.0, 0.05)
	val actionDelay = NumberSetting("Action Delay", 0.0, 1_000.0, 120.0, 10.0, unit = "ms")
	val countdownColor = ColourSetting("Countdown Color", Colour(85, 255, 85))
	val controller = WaterboardController(this)

	init {
		registerProperty(aimSpeed, actionDelay, countdownColor)
	}

	override fun reset() {
		controller.stop("Waterboard was disabled", terminal = true)
	}
}
