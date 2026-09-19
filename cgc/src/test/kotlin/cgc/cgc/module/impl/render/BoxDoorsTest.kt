package cgc.cgc.module.impl.render

import cgc.cgc.dungeon.room.DungeonDoorType
import cgc.cgc.dungeon.room.DungeonRoomRotation
import cgc.cgc.dungeon.room.ScannedDungeonDoor
import cgc.cgc.dungeon.room.ScannedDungeonLayout
import cgc.cgc.dungeon.room.ScannedDungeonRoom
import cgc.cgc.module.setting.ColourSetting
import cgc.cgc.module.setting.ModeSetting
import cgc.cgc.module.setting.NumberSetting
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoxDoorsTest {
	@Test
	fun `door styles expose independent configurable settings`() {
		val module = BoxDoors()

		assertEquals(listOf("General", "Blood Rush Doors", "Next Blood Rush Door", "Normal Doors"), module.settings.map { it.name })
		assertEquals(listOf("Door Scope"), module.settings[0].value.settings.map { it.name })
		assertEquals(
			listOf("Render Mode", "Outline Colour", "Fill Colour (Alpha = Opacity)", "Line Thickness"),
			module.settings[1].value.settings.map { it.name }
		)
		assertEquals(module.settings[1].value.settings.map { it.name }, module.settings[2].value.settings.map { it.name })
		assertEquals(module.settings[1].value.settings.map { it.name }, module.settings[3].value.settings.map { it.name })
		assertTrue(module.settings[1].value.enabled)
		assertTrue(module.settings[2].value.enabled)
		assertTrue(module.settings[3].value.enabled)

		val settings = module.flatSettings()
		assertEquals(BoxDoors.CURRENT_ROOM, (settings.first { it.name == "Door Scope" } as ModeSetting).value)
		assertEquals(64, (settings.first { it.name == "Fill Colour (Alpha = Opacity)" } as ColourSetting).value.alpha)
		assertEquals(3.0, (settings.first { it.name == "Line Thickness" } as NumberSetting).value.toDouble())
	}

	@Test
	fun `route and next route doors receive distinct styles`() {
		val entrance = room("Entrance", -185, -185, "ENTRANCE")
		val first = room("First", -153, -185)
		val second = room("Second", -121, -185)
		val blood = room("Blood", -89, -185)
		val firstDoor = door(-169, entrance, first)
		val currentRoomDoor = door(-137, first, second)
		val nextRoomDoor = door(-105, second, blood)
		val layout = ScannedDungeonLayout(
			1L,
			listOf(entrance, first, second, blood),
			listOf(firstDoor, currentRoomDoor, nextRoomDoor),
			listOf(entrance, first, second, blood),
			listOf(firstDoor, currentRoomDoor, nextRoomDoor)
		)

		assertEquals(BoxDoors.DoorStyleKind.BLOOD_RUSH, BoxDoors.classifyDoor(layout, first, firstDoor))
		assertEquals(BoxDoors.DoorStyleKind.BLOOD_RUSH, BoxDoors.classifyDoor(layout, first, currentRoomDoor))
		assertEquals(BoxDoors.DoorStyleKind.NEXT_BLOOD_RUSH, BoxDoors.classifyDoor(layout, first, nextRoomDoor))
		assertEquals(
			listOf(firstDoor, currentRoomDoor, nextRoomDoor),
			BoxDoors.selectDoors(layout, first, allDoors = false)
		)
	}

	@Test
	fun `entrance behaves as first room outside for filtering and next door`() {
		val entrance = room("Entrance", -185, -185, "ENTRANCE")
		val first = room("First", -153, -185)
		val second = room("Second", -121, -185)
		val entranceDoor = door(-169, entrance, first, DungeonDoorType.ENTRANCE)
		val firstWitherDoor = door(-137, first, second, DungeonDoorType.WITHER)
		val layout = ScannedDungeonLayout(
			1L,
			listOf(entrance, first, second),
			listOf(entranceDoor, firstWitherDoor),
			listOf(entrance, first, second),
			listOf(entranceDoor, firstWitherDoor)
		)

		val effectiveRoom = BoxDoors.effectiveCurrentRoom(layout, entrance)

		assertEquals(first.signature, effectiveRoom?.signature)
		assertEquals(listOf(entranceDoor, firstWitherDoor), BoxDoors.selectDoors(layout, effectiveRoom, allDoors = false))
		assertEquals(BoxDoors.DoorStyleKind.BLOOD_RUSH, BoxDoors.classifyDoor(layout, effectiveRoom, firstWitherDoor))
	}

	@Test
	fun `entrance falls back to its outside neighbor before full route loads`() {
		val entrance = room("Entrance", -185, -185, "ENTRANCE")
		val first = room("First", -153, -185)
		val entranceDoor = door(-169, entrance, first, DungeonDoorType.ENTRANCE)
		val layout = ScannedDungeonLayout(1L, listOf(entrance, first), listOf(entranceDoor), emptyList(), emptyList())

		assertEquals(first.signature, BoxDoors.effectiveCurrentRoom(layout, entrance)?.signature)
	}

	@Test
	fun `special doors use blood rush style while route is incomplete`() {
		val first = room("First", -153, -185)
		val blood = room("Blood", -121, -185)
		val bloodDoor = ScannedDungeonDoor(BlockPos(-137, 69, -185), DungeonDoorType.BLOOD, first, blood)
		val layout = ScannedDungeonLayout(1L, listOf(first, blood), listOf(bloodDoor), emptyList(), emptyList())

		assertEquals(BoxDoors.DoorStyleKind.BLOOD_RUSH, BoxDoors.classifyDoor(layout, first, bloodDoor))
	}

	@Test
	fun `current room scope only returns adjacent doors`() {
		val entrance = room("Entrance", -185, -185)
		val first = room("First", -153, -185)
		val second = room("Second", -121, -185)
		val firstDoor = door(-169, entrance, first)
		val secondDoor = door(-137, first, second)
		val layout = ScannedDungeonLayout(1L, listOf(entrance, first, second), listOf(firstDoor, secondDoor), emptyList(), emptyList())

		assertEquals(listOf(secondDoor), BoxDoors.selectDoors(layout, second, allDoors = false))
		assertEquals(listOf(firstDoor, secondDoor), BoxDoors.selectDoors(layout, second, allDoors = true))
	}

	@Test
	fun `door box matches three by four reference dimensions`() {
		val first = room("First", -185, -185)
		val second = room("Second", -153, -185)
		val box = BoxDoors.doorBox(door(-169, first, second))

		assertEquals(3.0, box.xsize)
		assertEquals(4.0, box.ysize)
		assertEquals(3.0, box.zsize)
		assertEquals(69.0, box.minY)
		assertTrue(box.contains(-168.5, 71.0, -184.5))
	}

	private fun room(name: String, x: Int, z: Int, type: String = "NORMAL") = ScannedDungeonRoom(
		key = name.lowercase(),
		displayName = name,
		type = type,
		shape = "1x1",
		core = name.hashCode(),
		mainX = x,
		mainZ = z,
		rotation = DungeonRoomRotation.TOPLEFT
	)

	private fun door(
		x: Int,
		first: ScannedDungeonRoom,
		second: ScannedDungeonRoom,
		type: DungeonDoorType = DungeonDoorType.NORMAL
	) = ScannedDungeonDoor(BlockPos(x, 69, -185), type, first, second)
}
