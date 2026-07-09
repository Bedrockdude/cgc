package com.github.noamm9.utils.dungeons.map.core

import com.github.noamm9.NoammAddons.mc
import com.github.noamm9.event.EventBus
import com.github.noamm9.event.impl.DungeonEvent
import com.github.noamm9.features.impl.dungeon.map.MapConfig
import com.github.noamm9.utils.dungeons.DungeonListener
import com.github.noamm9.utils.dungeons.map.DungeonInfo
import com.github.noamm9.utils.dungeons.map.handlers.DungeonScanner
import com.github.noamm9.utils.dungeons.map.utils.ScanUtils
import java.awt.Color
import kotlin.properties.Delegates

class Room(override val x: Int, override val z: Int, var data: RoomData): Tile {
    var core = 0
    var isSeparator = false
    var uniqueRoom: UniqueRoom? = null

    override var state: RoomState by Delegates.observable(RoomState.UNDISCOVERED) { _, oldValue, newValue ->
        if (uniqueRoom?.mainRoom != this) return@observable
        if (oldValue == newValue) return@observable
        if (data.name == "Unknown") return@observable
        if (MapConfig.dungeonMapCheater.value && oldValue == RoomState.UNOPENED && newValue == RoomState.UNDISCOVERED) return@observable
        if (MapConfig.dungeonMapCheater.value && newValue == RoomState.UNOPENED && oldValue == RoomState.UNDISCOVERED) return@observable

        val roomPlayers = DungeonListener.dungeonTeammates.filter {
            val pos = if (it.entity == mc.player) mc.player !!.position() else it.getRealPos()
            ScanUtils.getRoomFromPos(pos)?.data?.name == data.name
        }

        EventBus.post(DungeonEvent.RoomEvent.onStateChange(uniqueRoom !!, oldValue, newValue, roomPlayers))
    }

    override val color: Color
        get() {
            val color = if (state == RoomState.UNOPENED) MapConfig.colorUnopened
            else when (data.type) {
                RoomType.BLOOD -> MapConfig.colorBlood
                RoomType.CHAMPION -> MapConfig.colorMiniboss
                RoomType.ENTRANCE -> MapConfig.colorEntrance
                RoomType.FAIRY -> MapConfig.colorFairy
                RoomType.PUZZLE -> MapConfig.colorPuzzle
                RoomType.RARE -> MapConfig.colorRare
                RoomType.TRAP -> MapConfig.colorTrap
                else -> MapConfig.colorRoom
            }

            return if (MapConfig.dungeonMapCheater.value && state == RoomState.UNDISCOVERED) color.value.darker().darker() else color.value
        }

    fun getArrayPosition(): Pair<Int, Int> {
        return Pair((x - DungeonScanner.startX) / 16, (z - DungeonScanner.startZ) / 16)
    }

    fun addToUnique(row: Int, column: Int, roomName: String = data.name) {
        val unique = DungeonInfo.uniqueRooms[roomName]

        if (unique == null) {
            UniqueRoom(column, row, this).let {
                DungeonInfo.uniqueRooms[data.name] = it
                uniqueRoom = it
            }
        }
        else {
            unique.addTile(column, row, this)
            uniqueRoom = unique
        }
    }
}