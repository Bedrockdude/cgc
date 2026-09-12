package cgc.cgc.module.impl.dungeon.autopuzzles.blaze

import cgc.cgc.data.Colour
import cgc.cgc.module.SubModule
import cgc.cgc.module.impl.dungeon.AutoPuzzles
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.NumberSetting

class BlazeSubModule(module: AutoPuzzles) : SubModule<AutoPuzzles>(module, "Blaze", defaultEnabled = false) {
	val aimSpeed = NumberSetting("Aim Speed", 0.5, 2.0, 1.0, 0.05)
	val blazeRemovalWait = NumberSetting("Blaze Removal Wait", 100.0, 2_000.0, 500.0, 50.0, unit = "ms")
	val firstBlazeColor = ColourSetting("First Blaze Color", Colour(85, 255, 85))
	val secondBlazeColor = ColourSetting("Second Blaze Color", Colour(255, 213, 79))
	val otherBlazeColor = ColourSetting("Other Blaze Color", Colour(255, 85, 85))
	val firstLineColor = ColourSetting("First Line Color", Colour(85, 255, 85))
	val secondLineColor = ColourSetting("Second Line Color", Colour(255, 170, 0))
	val lineThickness = NumberSetting("Line Thickness", 0.5, 5.0, 2.0, 0.5)
	val startWaypointColor = ColourSetting("Start Waypoint Color", Colour(0, 255, 255))
	val controller = BlazeController(this)

	init {
		registerProperty(
			aimSpeed,
			blazeRemovalWait,
			firstBlazeColor,
			secondBlazeColor,
			otherBlazeColor,
			firstLineColor,
			secondLineColor,
			lineThickness,
			startWaypointColor
		)
	}

	override fun reset() {
		controller.stop("Blaze was disabled", terminal = true)
	}
}
