package cgc.cgc.data

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.Window

class Keybind(
	var keyName: String = "key.keyboard.unknown",
	private var action: (() -> Unit)? = null
) {
	private var wasPressed = false

	fun setRunnable(action: (() -> Unit)?) {
		this.action = action
	}

	fun run() {
		action?.invoke()
	}

	fun tick(window: Window) {
		val key = runCatching { InputConstants.getKey(keyName) }.getOrNull() ?: return
		if (key == InputConstants.UNKNOWN) {
			wasPressed = false
			return
		}

		val pressed = InputConstants.isKeyDown(window, key.value)
		if (pressed && !wasPressed) {
			run()
		}
		wasPressed = pressed
	}
}
