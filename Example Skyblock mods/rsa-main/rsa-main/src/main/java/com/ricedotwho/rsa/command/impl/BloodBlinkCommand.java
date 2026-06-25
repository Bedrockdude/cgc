package com.ricedotwho.rsa.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.module.impl.dungeon.BloodBlink;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.command.Command;
import com.ricedotwho.rsm.command.api.CommandInfo;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.map.RoomType;
import net.minecraft.client.network.ClientCommandSource;

@CommandInfo(name = "bloodblink", aliases = "bb", description = "Handles blood blinking rooms")
public class BloodBlinkCommand extends Command {
   public LiteralArgumentBuilder<ClientCommandSource> build() {
      return (LiteralArgumentBuilder<ClientCommandSource>)literal(this.name()).executes(source -> {
         if (!Location.getArea().is(Island.Dungeon)) {
            RSA.chat("I don't think there's a blood room outside dungeons yo");
            return 0;
         } else {
            BloodBlink bloodBlink = (BloodBlink)RSM.getModule(BloodBlink.class);
            if (!bloodBlink.isEnabled()) {
               RSA.chat("Please enable blood blink!");
               return 0;
            } else if (Map.getCurrentRoom() != null && Map.getCurrentRoom().getData().type() == RoomType.ENTRANCE) {
               RSA.chat("Trying blood blinking!");
               bloodBlink.doBlink();
               return 1;
            } else {
               RSA.chat("You can't blood blink outside of entrance!");
               return 0;
            }
         }
      });
   }
}
