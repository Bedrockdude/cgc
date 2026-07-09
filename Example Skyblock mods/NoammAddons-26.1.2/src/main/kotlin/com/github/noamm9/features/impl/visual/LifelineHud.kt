package com.github.noamm9.features.impl.visual

/*
useless now ):

object LifelineHud: Feature("Lifeline HUD") {
    override fun init() {
        hudElement("Lifeline Display") { context, example ->
            val player = mc.player ?: return@hudElement 0f to 0f

            val currentHealth = player.health + player.absorptionAmount
            val threshold = player.maxHealth * 0.2f
            val isLow = currentHealth < threshold

            if (! example && ! isLow) return@hudElement 0f to 0f

            val text = "&4&lLifeLine!"

            Render2D.drawString(context, text, 0, 0)

            return@hudElement text.width().toFloat() to 9f
        }
    }
}*/