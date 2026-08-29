package cgc.cgc.module.impl.dungeon.autopuzzles

import cgc.cgc.data.Pos
import cgc.cgc.dungeon.room.ScannedDungeonRoom
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
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

	fun legacyWaterPoint(room: ScannedDungeonRoom, x: Double, y: Double, z: Double): Vec3 =
		worldPoint(room, x - LEGACY_WATER_ORIGIN, y, z - LEGACY_WATER_ORIGIN)

	fun legacyWaterBlock(room: ScannedDungeonRoom, x: Int, y: Int, z: Int): BlockPos =
		worldBlock(room, x - LEGACY_WATER_ORIGIN.toInt(), y, z - LEGACY_WATER_ORIGIN.toInt())

	fun horizontalChunksLoaded(level: ClientLevel, minX: Int, maxX: Int, minZ: Int, maxZ: Int): Boolean {
		for (chunkX in Math.floorDiv(minX, 16)..Math.floorDiv(maxX, 16)) {
			for (chunkZ in Math.floorDiv(minZ, 16)..Math.floorDiv(maxZ, 16)) {
				if (!level.hasChunk(chunkX, chunkZ)) return false
			}
		}
		return true
	}

	private const val LEGACY_WATER_ORIGIN = 15.0
}
