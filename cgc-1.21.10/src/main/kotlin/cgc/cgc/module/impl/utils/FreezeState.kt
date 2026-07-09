package cgc.cgc.module.impl.utils

import cgc.cgc.data.Keybind
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ClientTickModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.WorldLoadModule
import cgc.cgc.module.setting.KeybindSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.utils.ChatUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth

class FreezeState : CgcModule(
	id = "FreezeState",
	displayName = "Freeze State",
	category = ModuleCategory.UTILS,
	description = "Records player states and replays them tick by tick for testing.",
	defaultEnabled = false
), ClientTickModule, WorldLoadModule {
	private val freezeKey = KeybindSetting("Freeze Key", Keybind(action = this::toggle), persistent = true)
	private val advanceTickKey = KeybindSetting("Advance Tick", Keybind())
	private val rewindTickKey = KeybindSetting("Rewind Tick", Keybind())
	private val amountOfTicks = NumberSetting("Amount of Ticks", 1.0, 5000.0, 200.0, 1.0, " ticks", onEdit = this::trimHistory)

	private val history = arrayListOf<PlayerSnapshot>()
	private var cursor: Int? = null
	private var applyingSnapshot = false
	private var advanceWasDown = false
	private var rewindWasDown = false

	init {
		registerProperty(freezeKey, advanceTickKey, rewindTickKey, amountOfTicks)
		freezeKey.register()
	}

	override fun onEnable() {
		advanceTickKey.register()
		rewindTickKey.register()
		history.clear()
		cursor = null
		advanceWasDown = false
		rewindWasDown = false
		Minecraft.getInstance().player?.let(::recordSnapshot)
		ChatUtils.actionBar("Freeze State enabled")
	}

	override fun onDisable() {
		advanceTickKey.unregister()
		rewindTickKey.unregister()
		cursor = null
		applyingSnapshot = false
		advanceWasDown = false
		rewindWasDown = false
		ChatUtils.actionBar("Freeze State disabled")
	}

	override fun onWorldLoad() {
		history.clear()
		cursor = null
		applyingSnapshot = false
		advanceWasDown = false
		rewindWasDown = false
	}

	override fun onClientTick(client: Minecraft) {
		val player = client.player ?: return
		if (client.level == null || applyingSnapshot) {
			return
		}

		if (!updatePlayback(client)) {
			recordSnapshot(player)
		}
	}

	override fun reset() {
		history.clear()
		cursor = null
		applyingSnapshot = false
		advanceWasDown = false
		rewindWasDown = false
	}

	private fun updatePlayback(client: Minecraft): Boolean {
		val advanceDown = advanceTickKey.value.isDown(client.window)
		val rewindDown = rewindTickKey.value.isDown(client.window)
		val direction = when {
			advanceDown && !rewindDown -> 1
			rewindDown && !advanceDown -> -1
			else -> 0
		}
		val pressed = when (direction) {
			1 -> !advanceWasDown
			-1 -> !rewindWasDown
			else -> false
		}
		var playbackActive = cursor != null

		when {
			direction != 0 && (pressed || playbackActive) -> playbackActive = step(direction)
			playbackActive -> holdSnapshot()
		}

		advanceWasDown = advanceDown
		rewindWasDown = rewindDown
		return playbackActive
	}

	private fun step(delta: Int): Boolean {
		if (!enabled || history.isEmpty()) {
			return false
		}

		val current = cursor ?: history.lastIndex
		val next = (current + delta).coerceIn(0, history.lastIndex)
		cursor = next
		applySnapshot(history[next])
		ChatUtils.actionBar("Freeze State ${next + 1}/${history.size}")
		return true
	}

	private fun holdSnapshot() {
		val index = cursor ?: return
		val snapshot = history.getOrNull(index) ?: return
		applySnapshot(snapshot)
	}

	private fun recordSnapshot(player: LocalPlayer) {
		val snapshot = PlayerSnapshot.from(player)
		val last = history.lastOrNull()
		if (last != null && snapshot.samePositionAs(last)) {
			return
		}

		if (last == null) {
			history.add(snapshot)
		} else {
			for (step in 1..SAMPLES_PER_TICK) {
				val progress = step.toDouble() / SAMPLES_PER_TICK
				val interpolated = last.interpolate(snapshot, progress)
				if (!interpolated.samePositionAs(history.last())) {
					history.add(interpolated)
				}
			}
		}
		trimHistory()
	}

	private fun applySnapshot(snapshot: PlayerSnapshot) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		applyingSnapshot = true
		try {
			val yaw = player.yRot
			val pitch = player.xRot
			player.absSnapTo(snapshot.x, snapshot.y, snapshot.z, yaw, pitch)
			player.yHeadRot = yaw
			player.setDeltaMovement(0.0, 0.0, 0.0)
			player.fallDistance = 0.0
			client.connection?.send(
				ServerboundMovePlayerPacket.PosRot(
					snapshot.x,
					snapshot.y,
					snapshot.z,
					yaw,
					pitch,
					snapshot.onGround,
					snapshot.horizontalCollision
				)
			)
		} finally {
			applyingSnapshot = false
		}
	}

	private fun trimHistory() {
		val maxSize = historyCapacity()
		while (history.size > maxSize) {
			history.removeAt(0)
			cursor = cursor?.let { (it - 1).coerceAtLeast(0) }
		}
		cursor = cursor?.coerceAtMost(history.lastIndex)
		if (history.isEmpty()) {
			cursor = null
		}
	}

	private fun historyCapacity(): Int =
		amountOfTicks.value.toInt().coerceAtLeast(1) * SAMPLES_PER_TICK

	private data class PlayerSnapshot(
		val x: Double,
		val y: Double,
		val z: Double,
		val yaw: Float,
		val pitch: Float,
		val onGround: Boolean,
		val horizontalCollision: Boolean
	) {
		fun samePositionAs(other: PlayerSnapshot): Boolean =
			x == other.x && y == other.y && z == other.z

		fun interpolate(other: PlayerSnapshot, progress: Double): PlayerSnapshot {
			val t = progress.coerceIn(0.0, 1.0)
			return PlayerSnapshot(
				x = Mth.lerp(t, x, other.x),
				y = Mth.lerp(t, y, other.y),
				z = Mth.lerp(t, z, other.z),
				yaw = (yaw + Mth.wrapDegrees(other.yaw - yaw) * t).toFloat(),
				pitch = Mth.lerp(t.toFloat(), pitch, other.pitch),
				onGround = if (t >= 0.5) other.onGround else onGround,
				horizontalCollision = if (t >= 0.5) other.horizontalCollision else horizontalCollision
			)
		}

		companion object {
			fun from(player: LocalPlayer): PlayerSnapshot {
				return PlayerSnapshot(
					x = player.x,
					y = player.y,
					z = player.z,
					yaw = player.yRot,
					pitch = player.xRot,
					onGround = player.onGround(),
					horizontalCollision = player.horizontalCollision
				)
			}
		}
	}

	private companion object {
		private const val SAMPLES_PER_TICK = 4
	}
}
