package cgc.cgc.runtime

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object InputScheduler {
	private val activeInputs = arrayListOf<InputCommand>()
	private val autoPressedKeys = hashSetOf<KeyMapping>()

	fun schedule(input: InputCommand) {
		if (input.ticks > 0) {
			activeInputs.add(input)
		}
	}

	fun tick(client: Minecraft) {
		if (activeInputs.isEmpty()) {
			releaseAutoKeys()
			return
		}

		releaseAutoKeys()
		val iterator = activeInputs.iterator()
		while (iterator.hasNext()) {
			val input = iterator.next()
			input.apply(client)
			input.ticks--
			if (input.ticks <= 0) {
				iterator.remove()
			}
		}
	}

	fun clear() {
		activeInputs.clear()
		releaseAutoKeys()
	}

	private fun InputCommand.apply(client: Minecraft) {
		val player = client.player ?: return
		yaw?.let { player.yRot = it }
		pitch?.let { player.xRot = it.coerceIn(-90.0f, 90.0f) }
		setKey(client.options.keyUp, forward)
		setKey(client.options.keyDown, back)
		setKey(client.options.keyLeft, left)
		setKey(client.options.keyRight, right)
		setKey(client.options.keyJump, jump)
		setKey(client.options.keyShift, sneak)
		setKey(client.options.keySprint, sprint)
	}

	private fun setKey(key: KeyMapping, down: Boolean) {
		if (!down) return
		key.isDown = true
		autoPressedKeys.add(key)
	}

	private fun releaseAutoKeys() {
		for (key in autoPressedKeys) {
			key.isDown = false
		}
		autoPressedKeys.clear()
	}
}
