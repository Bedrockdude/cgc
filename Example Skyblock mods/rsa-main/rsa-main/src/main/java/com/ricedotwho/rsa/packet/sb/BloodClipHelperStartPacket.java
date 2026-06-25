package com.ricedotwho.rsa.packet.sb;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload.Id;
import org.jetbrains.annotations.NotNull;

public record BloodClipHelperStartPacket(int roofHeight) implements CustomPayload {
   public static final PacketCodec<PacketByteBuf, BloodClipHelperStartPacket> CODEC = CustomPayload.codecOf(
      BloodClipHelperStartPacket::write, BloodClipHelperStartPacket::new
   );
   public static final Id<BloodClipHelperStartPacket> TYPE = new Id(Identifier.of("zero", "bloodcliphelper/start"));

   public BloodClipHelperStartPacket(PacketByteBuf buf) {
      this(buf.readVarInt());
   }

   public void write(PacketByteBuf buf) {
      buf.writeVarInt(this.roofHeight);
   }

   @NotNull
   public Id<BloodClipHelperStartPacket> getId() {
      return TYPE;
   }
}
