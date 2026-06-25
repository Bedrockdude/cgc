package com.ricedotwho.rsa.IMixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.SequencedPacketCreator;

public interface IMultiPlayerGameMode {
   void sendPacketSequenced(ClientWorld world, SequencedPacketCreator packetCreator);

   void syncSlot();
}
