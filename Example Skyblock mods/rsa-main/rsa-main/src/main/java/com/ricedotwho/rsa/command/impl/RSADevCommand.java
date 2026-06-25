package com.ricedotwho.rsa.command.impl;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.TickFreeze;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p5.Relics;
import com.ricedotwho.rsa.utils.Util;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.command.Command;
import com.ricedotwho.rsm.command.api.CommandInfo;
import com.ricedotwho.rsm.component.impl.task.TaskComponent;
import net.minecraft.client.network.ClientCommandSource;

@CommandInfo(name = "rdev", description = "Developer")
public class RSADevCommand extends Command {
   public LiteralArgumentBuilder<ClientCommandSource> build() {
      return (LiteralArgumentBuilder<ClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal(this.name())
               .then(((LiteralArgumentBuilder)literal("tickrate").then(argument("tick rate", FloatArgumentType.floatArg(0.0F, 20.0F)).executes(ctx -> {
                  Util.setTickRate(FloatArgumentType.getFloat(ctx, "tick rate"));
                  TaskComponent.onMilli(2500L, () -> Util.setTickRate(20.0F, false));
                  return 1;
               }))).then(literal("freeze").executes(ctx -> {
                  TickFreeze.freeze(5000L);
                  return 1;
               }))))
            .then(literal("iszero").executes(ctx -> {
               RSA.chat("Zero: %s", Util.isZero());
               return 1;
            })))
         .then(literal("reliclook").then(argument("relic", StringArgumentType.string()).executes(ctx -> {
            ((Relics)RSM.getModule(Relics.class)).test(StringArgumentType.getString(ctx, "relic"));
            return 1;
         })));
   }
}
