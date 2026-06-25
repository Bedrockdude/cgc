package com.ricedotwho.rsa.packet.sb;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload.Id;
import org.jetbrains.annotations.NotNull;

public record BloodClipHelperStopPacket() implements CustomPayload {
   public static final PacketCodec<PacketByteBuf, BloodClipHelperStopPacket> CODEC = CustomPayload.codecOf(
      BloodClipHelperStopPacket::write, BloodClipHelperStopPacket::new
   );
   public static final Id<BloodClipHelperStopPacket> TYPE = new Id(Identifier.of("zero", "bloodcliphelper/stop"));

   public BloodClipHelperStopPacket(PacketByteBuf buf) {
      this();
   }

   public void write(PacketByteBuf buf) {
   }

   @NotNull
   public Id<BloodClipHelperStopPacket> getId() {
      return TYPE;
   }
}
