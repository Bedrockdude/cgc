package com.ricedotwho.rsa.mixins;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.handler.PacketSizeLogger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientConnection.class)
public interface ConnectionAccessor {
   @Accessor("packetSizeLogger")
   @Nullable
   PacketSizeLogger getBandwidthDebugMonitor();
}
