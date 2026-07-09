package com.github.noamm9.event.impl

import com.github.noamm9.event.Event
import net.minecraft.world.entity.Entity
import java.awt.Color

class CheckEntityGlowEvent(val entity: Entity): Event() {
    var shouldGlow: Boolean = false
    var color: Color = Color.WHITE
        set(value) {
            this.shouldGlow = true
            field = value
        }
}