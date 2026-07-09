package com.github.noamm9.event.impl

import com.github.noamm9.event.Event
import net.minecraft.network.chat.Component

class BossBarUpdateEvent(val name: Component, val progress: Float): Event(false)