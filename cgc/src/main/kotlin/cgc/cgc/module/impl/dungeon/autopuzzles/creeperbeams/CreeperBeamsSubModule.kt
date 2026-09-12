package cgc.cgc.module.impl.dungeon.autopuzzles.creeperbeams

import cgc.cgc.data.Colour
import cgc.cgc.module.SubModule
import cgc.cgc.module.impl.dungeon.AutoPuzzles
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.NumberSetting

class CreeperBeamsSubModule(module: AutoPuzzles) : SubModule<AutoPuzzles>(module, "Creeper Beams", defaultEnabled = false) {
	val aimSpeed = NumberSetting("Aim Speed", 0.5, 2.0, 1.0, 0.05)
	val pair1Color = ColourSetting("Pair 1 Color", Colour(255, 0, 0))
	val pair2Color = ColourSetting("Pair 2 Color", Colour(0, 255, 0))
	val pair3Color = ColourSetting("Pair 3 Color", Colour(0, 0, 255))
	val pair4Color = ColourSetting("Pair 4 Color", Colour(255, 255, 0))
	val lineThickness = NumberSetting("Line Thickness", 0.5, 5.0, 2.0, 0.5)
	val startWaypointColor = ColourSetting("Start Waypoint Color", Colour(0, 255, 255))
	val controller = CreeperBeamsController(this)

	init {
		registerProperty(aimSpeed, pair1Color, pair2Color, pair3Color, pair4Color, lineThickness, startWaypointColor)
	}

	override fun reset() {
		controller.stop("Creeper Beams was disabled", terminal = true)
	}
}
