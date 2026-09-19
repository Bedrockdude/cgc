package cgc.cgc.module.impl.dungeon.autopuzzles.icefill

import cgc.cgc.data.Colour
import cgc.cgc.data.Keybind
import cgc.cgc.module.SubModule
import cgc.cgc.module.impl.dungeon.AutoPuzzles
import cgc.cgc.module.setting.ButtonSetting
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting

class IceFillSubModule(module: AutoPuzzles) : SubModule<AutoPuzzles>(module, "Ice Fill", defaultEnabled = false) {
	val turnSpeed = NumberSetting("Turn Speed", 0.10, 0.80, 0.40, 0.01, displayAsPercent = true)
	val pathColor = ColourSetting("Path Color", Colour(0, 255, 0))
	val lineThickness = NumberSetting("Line Thickness", 0.5, 5.0, 5.0, 0.5)
	val controller = IceFillController(this)
	val recordFloorRoute = KeybindSetting("Record Floor Route", Keybind(action = { controller.toggleRouteRecording() }))
	val setFallY = ButtonSetting("Set Fall Y", "Set", action = { controller.captureFallY() })
	val setFloor1Start = ButtonSetting("Set Floor 1 Start", "Set", action = { controller.captureFloorStart(0) })
	val setFloor2Start = ButtonSetting("Set Floor 2 Start", "Set", action = { controller.captureFloorStart(1) })
	val setFloor3Start = ButtonSetting("Set Floor 3 Start", "Set", action = { controller.captureFloorStart(2) })

	init {
		registerProperty(turnSpeed, pathColor, lineThickness, recordFloorRoute, setFallY, setFloor1Start, setFloor2Start, setFloor3Start)
	}

	override fun reset() {
		controller.stop("Ice Fill was disabled", terminal = true)
	}
}
