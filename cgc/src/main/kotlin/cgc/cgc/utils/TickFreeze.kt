package cgc.cgc.utils

object TickFreeze {
	private var frozenUntil = 0L
	private var frozen = false

	@JvmStatic
	var partialTick: Float = 0.0f
		private set

	@JvmStatic
	fun freeze(durationMs: Long) {
		if (durationMs <= 0L) {
			return
		}

		frozen = true
		frozenUntil = System.currentTimeMillis() + durationMs
	}

	@JvmStatic
	fun freeze() {
		frozen = true
		frozenUntil = Long.MAX_VALUE
	}

	@JvmStatic
	fun unfreeze() {
		frozen = false
		frozenUntil = 0L
	}

	@JvmStatic
	fun isFrozen(): Boolean {
		if (!frozen) {
			return false
		}

		if (System.currentTimeMillis() >= frozenUntil) {
			unfreeze()
			return false
		}

		return true
	}

	@JvmStatic
	fun setPartialTick(value: Float) {
		partialTick = value
	}
}
