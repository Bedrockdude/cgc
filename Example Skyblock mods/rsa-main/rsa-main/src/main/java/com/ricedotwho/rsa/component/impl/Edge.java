package com.ricedotwho.rsa.component.impl;

import com.google.common.collect.Streams;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsm.component.api.ModComponent;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import java.math.BigDecimal;
import java.util.stream.Stream;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

public class Edge extends ModComponent {
   private static boolean edge = false;

   public Edge() {
      super("Edge");
   }

   public static void edge() {
      edge = true;
   }

   @SubscribeEvent
   public void onInput(InputPollEvent event) {
      if (edge && mc.player != null && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
         if (!mc.player.isSneaking() && !mc.options.sneakKey.isPressed()) {
            double dist = ((BigDecimal)AutoP3.getEdgeDist().getDefaultValue()).doubleValue();
            Box box = mc.player.getBoundingBox();
            Box adjustedBox = box.offset(0.0, -0.5, 0.0).expand(-dist, 0.0, -dist);
            Stream<VoxelShape> blockCollisions = Streams.stream(mc.world.getBlockCollisions(mc.player, adjustedBox));
            if (!blockCollisions.findAny().isPresent()) {
               edge = false;
               event.getInput().jump(true);
            }
         }
      }
   }

   public static boolean isEdge() {
      return edge;
   }
}
