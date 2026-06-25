package com.ricedotwho.rsm.component.impl.moddedplayer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record ModdedPlayer(Component name, float xScale, float yScale, float zScale, ResourceLocation cape) {
}
