package com.github.noamm9.mixin;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundInteractPacket.class)
public interface IServerboundInteractPacket {
    @Accessor("entityId")
    int getEntityId();
}