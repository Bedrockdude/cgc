package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.dungeon.DungeonPuzzleState
import cgc.cgc.dungeon.DungeonPuzzleStateTracker
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.dungeon.room.DungeonRoomScanner
import cgc.cgc.dungeon.room.ScannedDungeonRoom
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer

data class AutoPuzzleContext(
	val nowMs: Long,
	val runSequence: Long,
	val dungeonStarted: Boolean,
	val client: Minecraft,
	val player: LocalPlayer,
	val level: ClientLevel,
	val room: ScannedDungeonRoom,
	val roomSignature: String
) {
	fun puzzleState(puzzle: cgc.cgc.dungeon.DungeonPuzzle): DungeonPuzzleState =
		DungeonPuzzleStateTracker.state(puzzle)

	companion object {
		fun create(client: Minecraft, nowMs: Long = monotonicNowMs()): AutoPuzzleContext? {
			val player = client.player ?: return null
			val level = client.level ?: return null
			val room = DungeonRoomScanner.currentRoom(client) ?: return null
			if (!room.canTransform) return null
			return AutoPuzzleContext(
				nowMs = nowMs,
				runSequence = DungeonState.runSequence,
				dungeonStarted = DungeonState.started,
				client = client,
				player = player,
				level = level,
				room = room,
				roomSignature = room.signature
			)
		}

		fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
	}
}
