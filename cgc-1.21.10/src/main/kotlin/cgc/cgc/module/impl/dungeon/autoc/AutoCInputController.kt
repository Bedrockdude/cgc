package cgc.cgc.module.impl.dungeon.autoc

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

class AutoCInputController {
	private val heldKeys = linkedSetOf<KeyMapping>()

	fun press(vararg keys: KeyMapping) {
		for (key in keys) {
			key.isDown = true
			heldKeys.add(key)
		}
	}

	fun release(key: KeyMapping) {
		if (heldKeys.remove(key)) {
			key.isDown = false
		}
	}

	fun releaseAll() {
		for (key in heldKeys) {
			key.isDown = false
		}
		heldKeys.clear()
	}

	fun releaseMovement(client: Minecraft) {
		val options = client.options
		release(options.keyUp)
		release(options.keyDown)
		release(options.keyLeft)
		release(options.keyRight)
		release(options.keySprint)
	}

	fun movementInputBaseline(client: Minecraft): MovementInputBaseline =
		MovementInputBaseline(physicalMovementKeys(client).toMutableSet())

	fun movementInputDown(client: Minecraft, baseline: MovementInputBaseline): Boolean {
		val current = physicalMovementKeys(client)
		baseline.ignoredHeld.retainAll(current)
		return current.any { it !in baseline.ignoredHeld }
	}

	fun anyMovementInputDown(client: Minecraft): Boolean =
		physicalMovementKeys(client).isNotEmpty()

	private fun physicalMovementKeys(client: Minecraft): Set<String> =
		movementKeys(client)
			.asSequence()
			.filter { physicalDown(client, it) }
			.map { it.saveString() }
			.toSet()

	fun physicalDown(client: Minecraft, key: KeyMapping): Boolean {
		val input = runCatching { InputConstants.getKey(key.saveString()) }.getOrNull() ?: return false
		if (input == InputConstants.UNKNOWN) {
			return false
		}
		return InputConstants.isKeyDown(client.window, input.value)
	}

	private fun movementKeys(client: Minecraft): List<KeyMapping> {
		val options = client.options
		return listOf(
			options.keyUp,
			options.keyDown,
			options.keyLeft,
			options.keyRight,
			options.keyJump,
			options.keyShift,
			options.keySprint
		)
	}

	data class MovementInputBaseline(
		internal val ignoredHeld: MutableSet<String>
	)
}
