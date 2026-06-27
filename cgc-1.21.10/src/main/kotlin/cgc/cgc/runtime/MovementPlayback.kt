package cgc.cgc.runtime

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

object MovementPlayback {
	private val gson = GsonBuilder().create()
	private val inputType = object : TypeToken<List<RecordedInput>>() {}.type

	private var routeName = ""
	private var inputs: List<RecordedInput> = emptyList()
	private var index = 0
	private var state = State.IDLE

	fun play(route: String?): Boolean {
		val name = route?.removeSuffix(".json")?.takeIf { it.isNotBlank() } ?: "inputs"
		if (state == State.PLAYING) {
			return false
		}

		val loaded = loadRoute(name)
		if (loaded.isEmpty()) {
			return false
		}

		routeName = name
		inputs = loaded
		index = 0
		state = State.PLAYING
		return true
	}

	fun tick(client: Minecraft) {
		if (state != State.PLAYING) {
			return
		}

		val player = client.player
		if (player == null || index >= inputs.size) {
			stop()
			return
		}

		val next = inputs[index++]
		InputScheduler.schedule(
			InputCommand(
				ticks = 1,
				yaw = next.yaw,
				pitch = next.pitch,
				forward = next.forward,
				back = next.back,
				left = next.left,
				right = next.right,
				jump = next.jump,
				sneak = next.sneak,
				sprint = next.sprint
			)
		)

		val use = next.useItem
		if (next.using && use != null && use.item.isNotBlank()) {
			PacketOrderManager.register(PacketOrderManager.State.ITEM_USE) {
				ItemInteractionUtils.useItemBySkyBlockId(use.item, use.yaw, use.pitch)
			}
		}
	}

	fun stop() {
		routeName = ""
		inputs = emptyList()
		index = 0
		state = State.IDLE
	}

	fun clear() {
		stop()
	}

	fun isPlaying(): Boolean =
		state == State.PLAYING

	private fun loadRoute(route: String): List<RecordedInput> {
		for (file in candidateFiles(route)) {
			if (!file.exists()) continue
			return runCatching {
				Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
					gson.fromJson<List<RecordedInput>>(reader, inputType) ?: emptyList()
				}
			}.getOrDefault(emptyList())
		}
		return emptyList()
	}

	private fun candidateFiles(route: String): List<Path> {
		val config = FabricLoader.getInstance().configDir
		return listOf(
			config.resolve("cgc").resolve("dungeon/recorder").resolve("$route.json"),
			config.resolve("rsm").resolve("dungeon/recorder").resolve("$route.json")
		)
	}

	private enum class State {
		PLAYING,
		IDLE
	}

	data class RecordedInput(
		val yaw: Float = 0.0f,
		val pitch: Float = 0.0f,
		val using: Boolean = false,
		val useItem: UseItem? = null,
		val forward: Boolean = false,
		val back: Boolean = false,
		val left: Boolean = false,
		val right: Boolean = false,
		val jump: Boolean = false,
		val sneak: Boolean = false,
		val sprint: Boolean = false
	)

	data class UseItem(
		val item: String = "",
		val yaw: Float = 0.0f,
		val pitch: Float = 0.0f
	)
}
