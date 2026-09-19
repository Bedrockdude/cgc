package cgc.cgc.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.abs

internal enum class DungeonKeyKind {
	WITHER,
	BLOOD
}

internal object AutoLeapSignals {
	private val controlCode = Regex("§.")
	private val deviceCompletion = Regex("^(.+?) completed a device! \\(\\d+/\\d+\\)$")
	private val keyPickupPattern = Regex(
		"^RIGHT CLICK on (.+?) to open it\\. This key can only be used to open \\d+ doors?!$",
		RegexOption.IGNORE_CASE
	)

	private val crushMessages = setOf(
		"[BOSS] Storm: Oof",
		"[BOSS] Storm: Ouch, that hurt!"
	)

	fun normalize(message: String): String =
		message.replace(controlCode, "").trim()

	fun isStormCrush(message: String): Boolean =
		normalize(message) in crushMessages

	fun lightningDurationTicks(title: String): Long? {
		val countdown = normalize(title).toIntOrNull() ?: return null
		if (countdown != 4 && countdown != 6) {
			return null
		}
		return countdown * LIGHTNING_COUNTDOWN_TICKS_PER_NUMBER
	}

	fun isOwnDeviceCompletion(message: String, playerName: String): Boolean {
		val actor = deviceCompletion.matchEntire(normalize(message))?.groupValues?.get(1) ?: return false
		return actor.equals(playerName, ignoreCase = true)
			|| actor.lowercase(Locale.ROOT).endsWith(" ${playerName.lowercase(Locale.ROOT)}")
	}

	fun isOnI4(position: Vec3): Boolean =
		abs(position.y - I4_Y) < I4_Y_TOLERANCE
			&& position.x in I4_MIN_X..I4_MAX_X
			&& position.z in I4_MIN_Z..I4_MAX_Z

	fun isI4DeviceBlock(position: BlockPos): Boolean =
		position in i4DeviceBlocks

	fun keyPickup(message: String): DungeonKeyKind? {
		val target = keyPickupPattern.matchEntire(normalize(message))?.groupValues?.get(1) ?: return null
		return when {
			target.contains("BLOOD", ignoreCase = true) -> DungeonKeyKind.BLOOD
			target.contains("WITHER", ignoreCase = true) -> DungeonKeyKind.WITHER
			else -> null
		}
	}

	private const val LIGHTNING_COUNTDOWN_TICKS_PER_NUMBER = 27L
	private const val I4_Y = 127.0
	private const val I4_Y_TOLERANCE = 0.5
	private const val I4_MIN_X = 62.0
	private const val I4_MAX_X = 65.0
	private const val I4_MIN_Z = 34.0
	private const val I4_MAX_Z = 37.0
	private val i4DeviceBlocks = buildSet {
		for (x in 64..68 step 2) {
			for (y in 126..130 step 2) {
				add(BlockPos(x, y, 50))
			}
		}
	}
}

internal class I4BlockCompletionTracker {
	private val completedBlocks = mutableSetOf<BlockPos>()

	fun observeTransition(position: BlockPos, wasEmerald: Boolean, isBlueTerracotta: Boolean): Boolean {
		if (!wasEmerald || !isBlueTerracotta || !AutoLeapSignals.isI4DeviceBlock(position)) {
			return false
		}

		return completedBlocks.add(position) && completedBlocks.size == I4_DEVICE_BLOCK_COUNT
	}

	fun reset() {
		completedBlocks.clear()
	}

	private companion object {
		private const val I4_DEVICE_BLOCK_COUNT = 9
	}
}
