package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.data.Pos
import cgc.cgc.dungeon.room.ScannedDungeonRoom
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

object AutoPuzzleRoomCoordinates {
	fun worldBlock(room: ScannedDungeonRoom, x: Int, y: Int, z: Int): BlockPos =
		room.toWorldBlock(Pos(x.toDouble(), y.toDouble(), z.toDouble())).asBlockPos()

	fun worldPoint(room: ScannedDungeonRoom, x: Double, y: Double, z: Double): Vec3 =
		room.toWorldBlock(Pos(x, y, z)).asVec3()

	/** Position transform used by legacy room-space player/entity coordinates. */
	fun worldPosition(room: ScannedDungeonRoom, x: Double, y: Double, z: Double): Vec3 =
		room.toWorld(Pos(x, y, z)).asVec3()

	fun relativeBlock(room: ScannedDungeonRoom, pos: BlockPos): BlockPos =
		room.toRelativeBlock(Pos(pos)).asBlockPos()

	fun relativePoint(room: ScannedDungeonRoom, pos: Vec3): Vec3 =
		room.toRelativeBlock(Pos(pos)).asVec3()

	fun relativePosition(room: ScannedDungeonRoom, pos: Vec3): Vec3 =
		room.toRelative(Pos(pos)).asVec3()

	fun relativeDirection(room: ScannedDungeonRoom, direction: Direction): Direction =
		horizontalDirection(room.toRelativeYaw(directionYaw(direction)))

	fun worldDirection(room: ScannedDungeonRoom, direction: Direction): Direction =
		horizontalDirection(room.toWorldYaw(directionYaw(direction)))

	fun legacyWaterPoint(room: ScannedDungeonRoom, x: Double, y: Double, z: Double): Vec3 =
		worldPoint(room, x - LEGACY_WATER_ORIGIN, y, z - LEGACY_WATER_ORIGIN)

	fun legacyWaterBlock(room: ScannedDungeonRoom, x: Int, y: Int, z: Int): BlockPos =
		worldBlock(room, x - LEGACY_WATER_ORIGIN.toInt(), y, z - LEGACY_WATER_ORIGIN.toInt())

	/** The Noamm beam table is authored with a 180-degree baseline relative to room-space coordinates. */
	fun legacyCreeperBeamBlock(room: ScannedDungeonRoom, x: Int, y: Int, z: Int): BlockPos =
		worldBlock(room, -x, y, -z)

	fun horizontalChunksLoaded(level: ClientLevel, minX: Int, maxX: Int, minZ: Int, maxZ: Int): Boolean {
		for (chunkX in Math.floorDiv(minX, 16)..Math.floorDiv(maxX, 16)) {
			for (chunkZ in Math.floorDiv(minZ, 16)..Math.floorDiv(maxZ, 16)) {
				if (!level.hasChunk(chunkX, chunkZ)) return false
			}
		}
		return true
	}

	private fun directionYaw(direction: Direction): Float = when (direction) {
		Direction.SOUTH -> 0.0f
		Direction.WEST -> 90.0f
		Direction.NORTH -> 180.0f
		Direction.EAST -> -90.0f
		else -> error("Expected a horizontal direction, got $direction")
	}

	private fun horizontalDirection(yaw: Float): Direction = when (Math.floorMod(kotlin.math.round(yaw / 90.0f).toInt(), 4)) {
		0 -> Direction.SOUTH
		1 -> Direction.WEST
		2 -> Direction.NORTH
		else -> Direction.EAST
	}

	private const val LEGACY_WATER_ORIGIN = 15.0
}
