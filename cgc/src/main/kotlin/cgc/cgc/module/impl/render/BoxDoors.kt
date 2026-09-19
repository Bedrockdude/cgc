package cgc.cgc.module.impl.render

import cgc.cgc.data.Colour
import cgc.cgc.dungeon.DungeonState
import cgc.cgc.dungeon.room.DungeonDoorType
import cgc.cgc.dungeon.room.DungeonRoomScanner
import cgc.cgc.dungeon.room.ScannedDungeonDoor
import cgc.cgc.dungeon.room.ScannedDungeonLayout
import cgc.cgc.dungeon.room.ScannedDungeonRoom
import cgc.cgc.location.Island
import cgc.cgc.location.Location
import cgc.cgc.module.CgcModule
import cgc.cgc.module.ModuleCategory
import cgc.cgc.module.SubModule
import cgc.cgc.module.WorldRenderExtractModule
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.NumberSetting
import cgc.cgc.module.setting.group.GroupSetting
import cgc.cgc.runtime.CgcRenderer3D
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class BoxDoors : CgcModule(
	id = "BoxDoors",
	displayName = "Box Doors",
	category = ModuleCategory.RENDER,
	description = "Draws configurable boxes around dungeon doors.",
	defaultEnabled = false
), WorldRenderExtractModule {
	private val doorScope = ModeSetting("Door Scope", CURRENT_ROOM, listOf(CURRENT_ROOM, ALL_DOORS))

	private val bloodRush = DoorStyleSettings(
		groupName = "Blood Rush Doors",
		outlineDefault = Colour(255, 85, 85),
		fillDefault = Colour(255, 85, 85, 64)
	)
	private val nextBloodRush = DoorStyleSettings(
		groupName = "Next Blood Rush Door",
		outlineDefault = Colour(85, 255, 85),
		fillDefault = Colour(85, 255, 85, 80)
	)
	private val normal = DoorStyleSettings(
		groupName = "Normal Doors",
		outlineDefault = Colour(85, 255, 255),
		fillDefault = Colour(85, 255, 255, 48)
	)

	init {
		registerProperty(doorScope, bloodRush.group, nextBloodRush.group, normal.group)
	}

	override fun onWorldRenderExtract(context: LevelRenderContext) {
		if (!Location.area.isArea(Island.DUNGEON) || DungeonState.inBoss) {
			return
		}

		val layout = DungeonRoomScanner.layout()
		val currentRoom = effectiveCurrentRoom(layout, DungeonRoomScanner.currentRoom())
		val visibleDoors = selectDoors(layout, currentRoom, doorScope.isMode(ALL_DOORS))
		if (visibleDoors.isEmpty()) {
			return
		}

		for (door in visibleDoors) {
			val style = when (classifyDoor(layout, currentRoom, door)) {
				DoorStyleKind.NEXT_BLOOD_RUSH -> nextBloodRush
				DoorStyleKind.BLOOD_RUSH -> bloodRush
				DoorStyleKind.NORMAL -> normal
			}
			style.render(doorBox(door))
		}
	}

	private inner class DoorStyleSettings(
		groupName: String,
		outlineDefault: Colour,
		fillDefault: Colour
	) {
		val group = GroupSetting(groupName, SubModule(this@BoxDoors, groupName, true))
		private val renderMode = ModeSetting("Render Mode", FILLED_OUTLINE, listOf(OUTLINE, FILLED_OUTLINE))
		private val outlineColour = ColourSetting("Outline Colour", outlineDefault)
		private val fillColour = ColourSetting(
			"Fill Colour (Alpha = Opacity)",
			fillDefault,
			supplier = { renderMode.isMode(FILLED_OUTLINE) }
		)
		private val lineThickness = NumberSetting("Line Thickness", 1.0, 10.0, 3.0, 0.5)

		init {
			group.add(renderMode, outlineColour, fillColour, lineThickness)
		}

		fun render(box: AABB) {
			if (!group.value.enabled) {
				return
			}
			val width = lineThickness.value.toFloat()
			if (renderMode.isMode(FILLED_OUTLINE)) {
				CgcRenderer3D.filledOutlineBox(box, fillColour.value, outlineColour.value, depth = false, outlineWidth = width)
			} else {
				CgcRenderer3D.outlineBox(box, outlineColour.value, depth = false, width = width)
			}
		}
	}

	internal enum class DoorStyleKind {
		NORMAL,
		BLOOD_RUSH,
		NEXT_BLOOD_RUSH
	}

	internal companion object {
		const val CURRENT_ROOM = "Current Room"
		const val ALL_DOORS = "All Doors"
		private const val OUTLINE = "Outline"
		private const val FILLED_OUTLINE = "Filled Outline"
		private const val DOOR_Y = 69.0
		private const val DOOR_WIDTH = 3.0
		private const val DOOR_HEIGHT = 4.0

		fun selectDoors(
			layout: ScannedDungeonLayout,
			currentRoom: ScannedDungeonRoom?,
			allDoors: Boolean
		): List<ScannedDungeonDoor> {
			if (allDoors) {
				return layout.doors
			}
			if (currentRoom == null) {
				return emptyList()
			}
			val visible = layout.doorsFor(currentRoom).toMutableList()
			nextRoomPreviewDoor(layout, currentRoom)?.let { preview ->
				if (visible.none { it.position == preview.position }) {
					visible.add(preview)
				}
			}
			return visible
		}

		fun effectiveCurrentRoom(
			layout: ScannedDungeonLayout,
			detectedRoom: ScannedDungeonRoom?
		): ScannedDungeonRoom? {
			if (detectedRoom == null || !detectedRoom.type.equals("ENTRANCE", ignoreCase = true)) {
				return detectedRoom
			}
			val routeIndex = layout.bloodRoute.indexOfFirst { it.signature == detectedRoom.signature }
			if (routeIndex in 0 until layout.bloodRoute.lastIndex) {
				return layout.bloodRoute[routeIndex + 1]
			}
			return layout.adjacentRooms(detectedRoom)
				.firstOrNull { !it.type.equals("ENTRANCE", ignoreCase = true) }
				?: detectedRoom
		}

		fun classifyDoor(
			layout: ScannedDungeonLayout,
			currentRoom: ScannedDungeonRoom?,
			door: ScannedDungeonDoor
		): DoorStyleKind {
			val route = layout.bloodRoute
			if (currentRoom != null) {
				val preview = nextRoomPreviewDoor(layout, currentRoom)
				if (preview?.position == door.position) {
					return DoorStyleKind.NEXT_BLOOD_RUSH
				}
			}
			if (route.zipWithNext().any { (first, second) -> door.connects(first, second) }) {
				return DoorStyleKind.BLOOD_RUSH
			}
			if (route.isEmpty() && (door.type == DungeonDoorType.WITHER || door.type == DungeonDoorType.BLOOD)) {
				return DoorStyleKind.BLOOD_RUSH
			}
			return DoorStyleKind.NORMAL
		}

		private fun nextRoomPreviewDoor(
			layout: ScannedDungeonLayout,
			currentRoom: ScannedDungeonRoom
		): ScannedDungeonDoor? {
			val route = layout.bloodRoute
			val currentIndex = route.indexOfFirst { it.signature == currentRoom.signature }
			if (currentIndex < 0 || currentIndex + 2 > route.lastIndex) {
				return null
			}
			val nextRoom = route[currentIndex + 1]
			val roomAfterNext = route[currentIndex + 2]
			return layout.doors.firstOrNull { it.connects(nextRoom, roomAfterNext) }
		}

		private fun ScannedDungeonDoor.connects(first: ScannedDungeonRoom, second: ScannedDungeonRoom): Boolean =
			(firstRoom.signature == first.signature && secondRoom.signature == second.signature) ||
				(firstRoom.signature == second.signature && secondRoom.signature == first.signature)

		fun doorBox(door: ScannedDungeonDoor): AABB =
			AABB.ofSize(
				Vec3(door.position.x + 0.5, DOOR_Y + DOOR_HEIGHT / 2.0, door.position.z + 0.5),
				DOOR_WIDTH,
				DOOR_HEIGHT,
				DOOR_WIDTH
			)
	}
}
