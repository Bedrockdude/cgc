package com.ricedotwho.rsa.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.module.impl.movement.VelocityBuffer;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.command.Command;
import com.ricedotwho.rsm.command.api.CommandInfo;
import net.minecraft.client.network.ClientCommandSource;

@CommandInfo(name = "velobuffer", aliases = "vb", description = "Toggle and pop buffered velocity")
public class VelocityBufferCommand extends Command {
   public LiteralArgumentBuilder<ClientCommandSource> build() {
      return (LiteralArgumentBuilder<ClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal(this.name())
                  .then(literal("t").executes(ctx -> {
                     VelocityBuffer vb = (VelocityBuffer)RSM.getModule(VelocityBuffer.class);
                     vb.toggle();
                     RSA.chat("Velocity Buffer %s", vb.isEnabled() ? "enabled" : "disabled");
                     return 1;
                  })))
               .then(literal("on").executes(ctx -> {
                  ((VelocityBuffer)RSM.getModule(VelocityBuffer.class)).setEnabled(true);
                  RSA.chat("Velocity Buffer enabled");
                  return 1;
               })))
            .then(literal("off").executes(ctx -> {
               ((VelocityBuffer)RSM.getModule(VelocityBuffer.class)).setEnabled(false);
               RSA.chat("Velocity Buffer disabled");
               return 1;
            })))
         .then(literal("pop").executes(ctx -> {
            ((VelocityBuffer)RSM.getModule(VelocityBuffer.class)).popQueue();
            return 1;
         }));
   }
}
