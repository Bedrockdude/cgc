package cgc.cgc.dungeon.room

import cgc.cgc.data.Pos
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.location.Floor
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkAccess
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.roundToInt

object DungeonRoomScanner {
	private val gson = Gson()
	private val roomsByCore by lazy { loadRooms().flatMap { room -> room.cores.orEmpty().map { it to room } }.toMap() }
	private val roomsByCenter = hashMapOf<Pair<Int, Int>, ScannedDungeonRoom>()
	private val doorsByCenter = hashMapOf<Pair<Int, Int>, RawDoor>()
	private var lastScanAt = 0L
	private var currentRoom: ScannedDungeonRoom? = null
	private var layoutRevision = 0L
	private var currentLayout = ScannedDungeonLayout.EMPTY

	fun reset() {
		roomsByCenter.clear()
		doorsByCenter.clear()
		lastScanAt = 0L
		currentRoom = null
		layoutRevision = 0L
		currentLayout = ScannedDungeonLayout.EMPTY
	}

	fun tick(client: Minecraft) {
		if (!Location.area.isArea(Island.DUNGEON) || Location.floor == Floor.NONE || DungeonState.inBoss) {
			currentRoom = null
			return
		}

		if (System.currentTimeMillis() - lastScanAt >= SCAN_INTERVAL_MS) {
			scanLoadedRooms(client)
		}
		currentRoom = scanCurrentRoom(client) ?: currentRoomForPlayer(client)
	}

	fun currentRoom(client: Minecraft = Minecraft.getInstance(), forceScan: Boolean = false): ScannedDungeonRoom? {
		if (!Location.area.isArea(Island.DUNGEON) || DungeonState.inBoss) {
			return null
		}
		if (forceScan) {
			scanLoadedRooms(client)
			currentRoom = currentRoomForPlayer(client) ?: scanCurrentRoom(client)
		}
		return currentRoom ?: currentRoomForPlayer(client)
	}

	fun layout(): ScannedDungeonLayout = currentLayout

	fun allRooms(): List<ScannedDungeonRoom> = currentLayout.rooms

	fun roomAt(x: Int, z: Int): ScannedDungeonRoom? = roomCenter(x, z)?.let(roomsByCenter::get)

	fun adjacentRooms(room: ScannedDungeonRoom): List<ScannedDungeonRoom> = currentLayout.adjacentRooms(room)

	fun doorsFor(room: ScannedDungeonRoom): List<ScannedDungeonDoor> = currentLayout.doorsFor(room)

	fun bloodRoute(): List<ScannedDungeonRoom> = currentLayout.bloodRoute

	fun bloodRouteDoors(): List<ScannedDungeonDoor> = currentLayout.bloodRouteDoors

	private fun scanLoadedRooms(client: Minecraft) {
		val level = client.level ?: return
		val tiles = arrayListOf<RoomTile>()

		for (arrayX in 0..10 step 2) {
			for (arrayZ in 0..10 step 2) {
				val x = START_X + arrayX * HALF_ROOM_SIZE
				val z = START_Z + arrayZ * HALF_ROOM_SIZE
				val loadedPos = BlockPos(x, SCAN_Y, z)
				if (!level.isLoaded(loadedPos)) {
					continue
				}

				scanRoomTile(client, x, z, arrayX, arrayZ)?.let { tiles.add(it) }
			}
		}

		val previousRooms = roomsByCenter.toMap()
		val scannedRooms = buildScannedRooms(client, tiles)
		val scannedByCenter = hashMapOf<Pair<Int, Int>, ScannedDungeonRoom>()
		for (room in scannedRooms) {
			val publicRoom = room.toPublicRoom()
			val resolvedRoom = room.tiles
				.asSequence()
				.mapNotNull { previousRooms[it.centerX to it.centerZ] }
				.fold(publicRoom) { resolved, previous -> chooseBetterRoom(resolved, previous) }
			for (tile in room.tiles) {
				val center = tile.centerX to tile.centerZ
				scannedByCenter[center] = resolvedRoom
			}
		}
		roomsByCenter.clear()
		roomsByCenter.putAll(mergeRetainedRooms(previousRooms, scannedByCenter))
		scanLoadedDoors(client)
		rebuildLayout()
		lastScanAt = System.currentTimeMillis()
	}

	private fun scanLoadedDoors(client: Minecraft) {
		val level = client.level ?: return
		for (arrayX in 0..10) for (arrayZ in 0..10) {
			if ((arrayX and 1) == (arrayZ and 1)) continue
			val x = START_X + arrayX * HALF_ROOM_SIZE
			val z = START_Z + arrayZ * HALF_ROOM_SIZE
			val loadedPos = BlockPos(x, SCAN_Y, z)
			if (!level.isLoaded(loadedPos)) continue
			val roof = roofHeight(x, z, level.getChunk(loadedPos))
			val marker = level.getBlockState(BlockPos(x, DOOR_MARKER_Y, z)).block
			val type = when (marker) {
				Blocks.RED_TERRACOTTA -> DungeonDoorType.BLOOD
				Blocks.INFESTED_CHISELED_STONE_BRICKS -> DungeonDoorType.ENTRANCE
				Blocks.COAL_BLOCK -> DungeonDoorType.WITHER
				else -> DungeonDoorType.NORMAL
			}
			val physicalDoor = roof in DOOR_ROOF_HEIGHTS || type != DungeonDoorType.NORMAL
			if (physicalDoor) doorsByCenter[x to z] = RawDoor(x, z, type)
			else doorsByCenter.remove(x to z)
		}
	}

	private fun rebuildLayout() {
		val rooms = roomsByCenter.values.distinctBy { it.signature }
		val doors = doorsByCenter.values.mapNotNull { raw ->
			val horizontal = ((raw.x - START_X) / HALF_ROOM_SIZE) and 1 == 1
			val first = if (horizontal) roomsByCenter[(raw.x - HALF_ROOM_SIZE) to raw.z]
			else roomsByCenter[raw.x to (raw.z - HALF_ROOM_SIZE)]
			val second = if (horizontal) roomsByCenter[(raw.x + HALF_ROOM_SIZE) to raw.z]
			else roomsByCenter[raw.x to (raw.z + HALF_ROOM_SIZE)]
			if (first == null || second == null || first.signature == second.signature) null
			else ScannedDungeonDoor(BlockPos(raw.x, DOOR_MARKER_Y, raw.z), raw.type, first, second)
		}.distinctBy { it.position }
		val route = findBloodRoute(rooms, doors)
		val routeDoors = route.zipWithNext().mapNotNull { (a, b) ->
			doors.firstOrNull {
				(it.firstRoom.signature == a.signature && it.secondRoom.signature == b.signature) ||
					(it.firstRoom.signature == b.signature && it.secondRoom.signature == a.signature)
			}
		}
		layoutRevision++
		currentLayout = ScannedDungeonLayout(layoutRevision, rooms, doors, route, routeDoors)
	}

	private fun findBloodRoute(
		rooms: List<ScannedDungeonRoom>,
		doors: List<ScannedDungeonDoor>
	): List<ScannedDungeonRoom> {
		val start = rooms.firstOrNull { it.type.equals("ENTRANCE", true) } ?: return emptyList()
		val target = rooms.firstOrNull { it.type.equals("BLOOD", true) } ?: return emptyList()
		val previous = hashMapOf<String, ScannedDungeonRoom?>()
		val queue = ArrayDeque<ScannedDungeonRoom>()
		previous[start.signature] = null
		queue.add(start)
		while (queue.isNotEmpty()) {
			val room = queue.removeFirst()
			if (room.signature == target.signature) break
			for (next in doors.mapNotNull { it.other(room) }) {
				if (next.signature !in previous) {
					previous[next.signature] = room
					queue.add(next)
				}
			}
		}
		if (target.signature !in previous) return emptyList()
		val result = arrayListOf<ScannedDungeonRoom>()
		var cursor: ScannedDungeonRoom? = target
		while (cursor != null) {
			result.add(cursor)
			cursor = previous[cursor.signature]
		}
		return result.asReversed()
	}

	private fun scanCurrentRoom(client: Minecraft): ScannedDungeonRoom? {
		val player = client.player ?: return null
		val center = roomCenter(player.blockX, player.blockZ) ?: return null
		val cached = roomsByCenter[center]
		if (cached != null && cached.isKnownCoreRoom() && cached.canTransform) {
			return cached
		}

		val tile = scanRoomTile(client, center.first, center.second, 0, 0) ?: return null
		val room = buildScannedRooms(client, listOf(tile)).firstOrNull()?.toPublicRoom() ?: return null
		val resolved = chooseBetterRoom(room, cached)
		roomsByCenter[center] = resolved
		return resolved
	}

	private fun currentRoomForPlayer(client: Minecraft): ScannedDungeonRoom? {
		val player = client.player ?: return null
		val center = roomCenter(player.blockX, player.blockZ) ?: return null
		return roomsByCenter[center]
	}

	private fun scanRoomTile(client: Minecraft, x: Int, z: Int, arrayX: Int, arrayZ: Int): RoomTile? {
		val level = client.level ?: return null
		val loadedPos = BlockPos(x, SCAN_Y, z)
		if (!level.isLoaded(loadedPos)) {
			return null
		}

		val chunk = level.getChunk(loadedPos)
		val roofHeight = roofHeight(x, z, chunk)
		if (roofHeight <= 0) {
			return null
		}

		val core = roomCore(x, z, roofHeight, chunk)
		val data = roomsByCore[core]
		return RoomTile(x, z, arrayX, arrayZ, roofHeight, core, data)
	}

	private fun buildScannedRooms(client: Minecraft, tiles: List<RoomTile>): List<InternalScannedRoom> {
		val groups = tiles.groupBy { it.routeGroupKey }
		return groups.map { (_, groupTiles) ->
			val first = groupTiles.first()
			val rotationResult = findMainAndRotation(client, groupTiles)
			InternalScannedRoom(
				key = routeKey(first.data, first.core),
				displayName = first.data?.displayName ?: "Unknown ${first.core}",
				type = first.data?.roomType ?: "UNKNOWN",
				shape = first.data?.roomShape ?: "Unknown",
				core = first.core,
				main = rotationResult.main ?: first,
				rotation = rotationResult.rotation,
				tiles = groupTiles
			)
		}
	}

	private fun findMainAndRotation(client: Minecraft, tiles: List<RoomTile>): RotationResult {
		val first = tiles.firstOrNull() ?: return RotationResult(null, DungeonRoomRotation.UNKNOWN)
		if (first.data?.roomType == "FAIRY") {
			return RotationResult(first, DungeonRoomRotation.TOPLEFT)
		}

		if (first.data?.roomType == "ENTRANCE" && first.data.displayName != "Entrance 2") {
			findEntranceRotation(client, tiles)?.let { return it }
		}

		val realTiles = tiles.filter { !it.separator }
		val expectedTiles = expectedTileCount(first.data?.roomShape)
		if (expectedTiles > 0 && realTiles.size < expectedTiles) {
			return RotationResult(null, DungeonRoomRotation.UNKNOWN)
		}

		for (tile in realTiles) {
			for (index in CORNER_OFFSETS.indices) {
				val offset = CORNER_OFFSETS[index]
				val pos = BlockPos(tile.centerX + offset.first, tile.roofHeight, tile.centerZ + offset.second)
				if (!isLoaded(client, pos)) {
					return RotationResult(null, DungeonRoomRotation.UNKNOWN)
				}
				val level = client.level ?: return RotationResult(null, DungeonRoomRotation.UNKNOWN)
				if (level.getBlockState(pos).block == Blocks.BLUE_TERRACOTTA && isCorner(client, pos)) {
					return RotationResult(tile, DungeonRoomRotation.byCornerIndex(index))
				}
			}
		}

		if (first.data?.roomShape != "L") {
			findBoundingCornerRotation(client, realTiles)?.let { return it }
		}

		return if (first.data?.roomType == "ENTRANCE") {
			findEntranceRotation(client, tiles) ?: RotationResult(null, DungeonRoomRotation.UNKNOWN)
		} else {
			RotationResult(null, DungeonRoomRotation.UNKNOWN)
		}
	}

	private fun findBoundingCornerRotation(client: Minecraft, tiles: List<RoomTile>): RotationResult? {
		if (tiles.isEmpty()) {
			return null
		}

		val level = client.level ?: return null
		val minX = tiles.minOf { it.centerX }
		val maxX = tiles.maxOf { it.centerX }
		val minZ = tiles.minOf { it.centerZ }
		val maxZ = tiles.maxOf { it.centerZ }
		val heights = tiles.map { it.roofHeight }.distinct()
		val corners = listOf(
			BlockPos(minX - ROOM_CORNER_OFFSET, 0, minZ - ROOM_CORNER_OFFSET),
			BlockPos(maxX + ROOM_CORNER_OFFSET, 0, minZ - ROOM_CORNER_OFFSET),
			BlockPos(maxX + ROOM_CORNER_OFFSET, 0, maxZ + ROOM_CORNER_OFFSET),
			BlockPos(minX - ROOM_CORNER_OFFSET, 0, maxZ + ROOM_CORNER_OFFSET)
		)

		for (height in heights) {
			val positions = corners.map { corner -> BlockPos(corner.x, height, corner.z) }
			if (positions.any { !isLoaded(client, it) }) {
				return RotationResult(null, DungeonRoomRotation.UNKNOWN)
			}
			for (index in corners.indices) {
				val pos = positions[index]
				if (level.getBlockState(pos).block == Blocks.BLUE_TERRACOTTA) {
					val main = tiles.minByOrNull { tile -> cornerDistance(index, tile, minX, maxX, minZ, maxZ) }
						?: tiles.first()
					return RotationResult(main, DungeonRoomRotation.byCornerIndex(index))
				}
			}
		}
		return null
	}

	private fun cornerDistance(index: Int, tile: RoomTile, minX: Int, maxX: Int, minZ: Int, maxZ: Int): Int =
		when (index) {
			0 -> (tile.centerX - minX) * (tile.centerX - minX) + (tile.centerZ - minZ) * (tile.centerZ - minZ)
			1 -> (tile.centerX - maxX) * (tile.centerX - maxX) + (tile.centerZ - minZ) * (tile.centerZ - minZ)
			2 -> (tile.centerX - maxX) * (tile.centerX - maxX) + (tile.centerZ - maxZ) * (tile.centerZ - maxZ)
			3 -> (tile.centerX - minX) * (tile.centerX - minX) + (tile.centerZ - maxZ) * (tile.centerZ - maxZ)
			else -> 0
		}

	private fun findEntranceRotation(client: Minecraft, tiles: List<RoomTile>): RotationResult? {
		val level = client.level ?: return null
		for (tile in tiles) {
			for (index in ENTRANCE_OFFSETS.indices) {
				val offset = ENTRANCE_OFFSETS[index]
				val pos = BlockPos(tile.centerX + offset.first, tile.roofHeight, tile.centerZ + offset.second)
				if (!isLoaded(client, pos)) {
					return RotationResult(null, DungeonRoomRotation.UNKNOWN)
				}
				if (level.getBlockState(pos).block == Blocks.BLUE_TERRACOTTA) {
					return RotationResult(tile, DungeonRoomRotation.byCornerIndex(index % 4))
				}
			}
		}
		return null
	}

	private fun isCorner(client: Minecraft, pos: BlockPos): Boolean {
		val level = client.level ?: return false
		var counter = 0
		for (offset in ADJACENT_OFFSETS) {
			if (level.getBlockState(BlockPos(pos.x + offset.first, pos.y, pos.z + offset.second)).block != Blocks.AIR) {
				counter++
			}
		}
		return counter <= 2
	}

	private fun isLoaded(client: Minecraft, pos: BlockPos): Boolean =
		client.level?.isLoaded(pos) == true

	private fun roomCenter(x: Int, z: Int): Pair<Int, Int>? {
		if (x !in DUNGEON_MIN_COORD..DUNGEON_MAX_COORD || z !in DUNGEON_MIN_COORD..DUNGEON_MAX_COORD) {
			return null
		}

		val centerX = ((x - START_X) / ROOM_SIZE.toFloat()).roundToInt() * ROOM_SIZE + START_X
		val centerZ = ((z - START_Z) / ROOM_SIZE.toFloat()).roundToInt() * ROOM_SIZE + START_Z
		if (centerX !in START_X..LAST_ROOM_CENTER || centerZ !in START_Z..LAST_ROOM_CENTER) {
			return null
		}
		return centerX to centerZ
	}

	private fun roofHeight(x: Int, z: Int, chunk: ChunkAccess): Int {
		val mutable = BlockPos.MutableBlockPos(x, SCAN_Y, z)
		for (y in MAX_SCAN_Y downTo MIN_SCAN_Y) {
			mutable.set(x, y, z)
			val block = chunk.getBlockState(mutable).block
			if (block != Blocks.AIR) {
				return if (block == Blocks.GOLD_BLOCK) y - 1 else y
			}
		}
		return -1
	}

	private fun roomCore(x: Int, z: Int, roomHeight: Int, chunk: ChunkAccess): Int {
		val mutable = BlockPos.MutableBlockPos()
		val clampedHeight = roomHeight.coerceIn(MIN_CORE_SCAN_Y - 1, MAX_CORE_SCAN_Y)
		val builder = StringBuilder(150)
		builder.append("0".repeat(MAX_CORE_SCAN_Y - clampedHeight))

		var bedrock = 0
		for (y in clampedHeight downTo MIN_CORE_SCAN_Y) {
			mutable.set(x, y, z)
			val block = chunk.getBlockState(mutable).block
			if (block == Blocks.AIR && bedrock >= 2 && y < AIR_BREAK_Y) {
				builder.append("0".repeat(y - MIN_CORE_SCAN_Y + 1))
				break
			}

			if (block == Blocks.BEDROCK) {
				bedrock++
			} else {
				bedrock = 0
				if (IGNORED_CORE_BLOCKS.contains(block)) {
					continue
				}
			}
			builder.append(block)
		}
		return builder.toString().hashCode()
	}

	private fun loadRooms(): List<DungeonRoomData> {
		val stream = DungeonRoomScanner::class.java.getResourceAsStream("/assets/cgc/rooms.json") ?: return emptyList()
		return InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
			gson.fromJson(reader, object : TypeToken<List<DungeonRoomData>>() {}.type) ?: emptyList()
		}
	}

	private fun routeKey(data: DungeonRoomData?, core: Int): String {
		val raw = data?.displayName ?: "core_$core"
		val normalized = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]+"), "_").trim('_')
		return normalized.ifBlank { "core_$core" }
	}

	private fun chooseBetterRoom(scanned: ScannedDungeonRoom, previous: ScannedDungeonRoom?): ScannedDungeonRoom =
		when {
			previous == null -> scanned
			scanned.isUnknownCoreRoom() && previous.isKnownCoreRoom() -> previous
			!scanned.canTransform && previous.canTransform && scanned.key == previous.key -> previous
			else -> scanned
		}

	private fun ScannedDungeonRoom.isKnownCoreRoom(): Boolean =
		type != "UNKNOWN" && key.startsWith("core_").not()

	private fun ScannedDungeonRoom.isUnknownCoreRoom(): Boolean =
		!isKnownCoreRoom()

	private fun expectedTileCount(shape: String?): Int =
		when (shape) {
			"1x1" -> 1
			"1x2" -> 2
			"1x3" -> 3
			"1x4" -> 4
			"2x2" -> 4
			"L" -> 3
			else -> 0
		}

	private data class RoomTile(
		val centerX: Int,
		val centerZ: Int,
		val arrayX: Int,
		val arrayZ: Int,
		val roofHeight: Int,
		val core: Int,
		val data: DungeonRoomData?,
		val separator: Boolean = false
	) {
		val routeGroupKey: String = data?.displayName ?: "core_$core"
	}

	private data class RawDoor(val x: Int, val z: Int, val type: DungeonDoorType)

	private data class RotationResult(
		val main: RoomTile?,
		val rotation: DungeonRoomRotation
	)

	private data class InternalScannedRoom(
		val key: String,
		val displayName: String,
		val type: String,
		val shape: String,
		val core: Int,
		val main: RoomTile,
		val rotation: DungeonRoomRotation,
		val tiles: List<RoomTile>
	) {
		fun toPublicRoom(): ScannedDungeonRoom =
			ScannedDungeonRoom(
				key = key,
				displayName = displayName,
				type = type,
				shape = shape,
				core = core,
				mainX = main.centerX,
				mainZ = main.centerZ,
				rotation = rotation
			)
	}

	private const val START_X = -185
	private const val START_Z = -185
	private const val LAST_ROOM_CENTER = -25
	private const val DUNGEON_MIN_COORD = -200
	private const val DUNGEON_MAX_COORD = -10
	private const val ROOM_SIZE = 32
	private const val HALF_ROOM_SIZE = 16
	private const val ROOM_CORNER_OFFSET = 15
	private const val SCAN_Y = 67
	private const val MIN_SCAN_Y = 12
	private const val MAX_SCAN_Y = 160
	private const val MIN_CORE_SCAN_Y = 12
	private const val MAX_CORE_SCAN_Y = 140
	private const val AIR_BREAK_Y = 69
	private const val SCAN_INTERVAL_MS = 250L
	private const val DOOR_MARKER_Y = 69
	private val DOOR_ROOF_HEIGHTS = setOf(73, 74, 81, 82)

	private val IGNORED_CORE_BLOCKS: Set<Block> = setOf(Blocks.OAK_PLANKS, Blocks.TRAPPED_CHEST, Blocks.CHEST)
	private val CORNER_OFFSETS = listOf(-15 to -15, 15 to -15, 15 to 15, -15 to 15)
	private val ADJACENT_OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
	private val ENTRANCE_OFFSETS = listOf(
		-15 to -15,
		15 to -15,
		15 to 15,
		-15 to 15,
		-16 to -15,
		16 to -15,
		16 to 15,
		-16 to 15,
		-15 to -16,
		15 to -16,
		15 to 16,
		-15 to 16,
		-27 to -15,
		27 to -15,
		27 to 15,
		-27 to 15,
		-15 to -27,
		15 to -27,
		15 to 27,
		-15 to 27
	)
}

data class ScannedDungeonRoom(
	val key: String,
	val displayName: String,
	val type: String,
	val shape: String,
	val core: Int,
	val mainX: Int,
	val mainZ: Int,
	val rotation: DungeonRoomRotation
) {
	val canTransform: Boolean
		get() = rotation != DungeonRoomRotation.UNKNOWN

	val signature: String
		get() = "$key:$mainX:$mainZ"

	fun toRelative(pos: Pos): Pos {
		val local = Pos(pos.x - mainX, pos.y, pos.z - mainZ)
		return rotation.rotateRelative(local) ?: local
	}

	fun toWorld(pos: Pos): Pos {
		val rotated = rotation.rotateReal(pos)
		return Pos(rotated.x + mainX, rotated.y, rotated.z + mainZ)
	}

	fun toRelativeBlock(pos: Pos): Pos {
		val local = Pos(pos.x - mainX, pos.y, pos.z - mainZ)
		return rotation.rotateRelativeFixed(local) ?: local
	}

	fun toWorldBlock(pos: Pos): Pos {
		val rotated = rotation.rotateRealFixed(pos)
		return Pos(rotated.x + mainX, rotated.y, rotated.z + mainZ)
	}

	fun toRelativeYaw(yaw: Float): Float =
		Mth.wrapDegrees(yaw - rotation.yawOffset)

	fun toWorldYaw(yaw: Float): Float =
		Mth.wrapDegrees(yaw + rotation.yawOffset)
}

enum class DungeonRoomRotation(val yawOffset: Float) {
	TOPLEFT(0.0f),
	TOPRIGHT(90.0f),
	BOTRIGHT(180.0f),
	BOTLEFT(270.0f),
	UNKNOWN(0.0f);

	fun rotateReal(pos: Pos): Pos {
		if (this == TOPLEFT || this == UNKNOWN) {
			return pos.copy()
		}

		val x = pos.x - 0.5
		val y = pos.y
		val z = pos.z - 0.5
		val rotated = when (this) {
			TOPRIGHT -> Pos(-z, y, x)
			BOTRIGHT -> Pos(-x, y, -z)
			BOTLEFT -> Pos(z, y, -x)
			TOPLEFT, UNKNOWN -> pos.copy()
		}
		return Pos(rotated.x + 0.5, rotated.y, rotated.z + 0.5)
	}

	fun rotateRelative(pos: Pos): Pos? {
		if (this == TOPLEFT) {
			return pos.copy()
		}
		if (this == UNKNOWN) {
			return null
		}

		val x = pos.x - 0.5
		val y = pos.y
		val z = pos.z - 0.5
		val rotated = when (this) {
			TOPRIGHT -> Pos(z, y, -x)
			BOTRIGHT -> Pos(-x, y, -z)
			BOTLEFT -> Pos(-z, y, x)
			TOPLEFT, UNKNOWN -> pos.copy()
		}
		return Pos(rotated.x + 0.5, rotated.y, rotated.z + 0.5)
	}

	fun rotateRealFixed(pos: Pos): Pos {
		if (this == TOPLEFT || this == UNKNOWN) {
			return pos.copy()
		}

		val x = pos.x
		val y = pos.y
		val z = pos.z
		return when (this) {
			TOPRIGHT -> Pos(-z, y, x)
			BOTRIGHT -> Pos(-x, y, -z)
			BOTLEFT -> Pos(z, y, -x)
			TOPLEFT, UNKNOWN -> pos.copy()
		}
	}

	fun rotateRelativeFixed(pos: Pos): Pos? {
		if (this == TOPLEFT) {
			return pos.copy()
		}
		if (this == UNKNOWN) {
			return null
		}

		val x = pos.x
		val y = pos.y
		val z = pos.z
		return when (this) {
			TOPRIGHT -> Pos(z, y, -x)
			BOTRIGHT -> Pos(-x, y, -z)
			BOTLEFT -> Pos(-z, y, x)
			TOPLEFT, UNKNOWN -> pos.copy()
		}
	}

	companion object {
		fun byCornerIndex(index: Int): DungeonRoomRotation =
			when (index) {
				0 -> TOPLEFT
				1 -> TOPRIGHT
				2 -> BOTRIGHT
				3 -> BOTLEFT
				else -> UNKNOWN
			}
	}
}
