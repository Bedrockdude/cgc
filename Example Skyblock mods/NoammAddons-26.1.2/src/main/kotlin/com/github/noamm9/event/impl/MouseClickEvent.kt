package com.github.noamm9.event.impl

import com.github.noamm9.event.Event

class MouseClickEvent(val button: Int, val action: Int, val modifiers: Int): Event(true)