package cgc.cgc.module.impl.dungeon.autopuzzles

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object AutoPuzzleInputOwner {
	private var active: Lease? = null

	@Synchronized
	fun acquire(owner: String, client: Minecraft): Lease? {
		if (active != null) return null
		return Lease(owner, client).also { active = it }
	}

	@Synchronized
	fun currentOwner(): String? = active?.owner

	@Synchronized
	fun releaseAll() {
		active?.close()
	}

	class Lease internal constructor(
		val owner: String,
		private val client: Minecraft
	) : AutoCloseable {
		private val ownedKeys = linkedSetOf<KeyMapping>()
		private var closed = false

		fun press(key: KeyMapping) {
			if (closed) return
			key.isDown = true
			ownedKeys.add(key)
		}

		fun release(key: KeyMapping) {
			if (closed || !ownedKeys.remove(key)) return
			key.isDown = physicalDown(key)
		}

		fun releaseMovement() {
			val options = client.options
			release(options.keyUp)
			release(options.keyDown)
			release(options.keyLeft)
			release(options.keyRight)
			release(options.keyJump)
			release(options.keySprint)
		}

		@Synchronized
		override fun close() {
			if (closed) return
			closed = true
			for (key in ownedKeys) {
				key.isDown = physicalDown(key)
			}
			ownedKeys.clear()
			synchronized(AutoPuzzleInputOwner) {
				if (active === this) active = null
			}
		}

		private fun physicalDown(key: KeyMapping): Boolean {
			val input = runCatching { InputConstants.getKey(key.saveString()) }.getOrNull() ?: return false
			if (input == InputConstants.UNKNOWN) return false
			return InputConstants.isKeyDown(client.window, input.value)
		}
	}
}
