package com.ricedotwho.rsa.IMixin;

import net.minecraft.network.packet.Packet;

public interface IConnection {
   void sendPacketImmediately(Packet<?> packet);
}
