package com.ricedotwho.rsa.module.impl.other.checks;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.module.impl.other.AntiCheat;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.StringHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

public class InvWalkCheck {
   public static boolean startChecking;
   private static final Pattern playerName = Pattern.compile("^(\\w+)\\s+activated a terminal");
   public static String username;
   private static final Map<BlockPos, Entity> inactiveTerminals = new HashMap<>();
   private static final List<String> termCompleter = new ArrayList<>();
   private static final List<Double> TermPos = new ArrayList<>();
   private static final List<Double> PlayerPos = new ArrayList<>();

   @SubscribeEvent
   public static void setRunning() {
      if ((Boolean)AntiCheat.termWalking.getValue()) {
         startChecking = true;
      }
   }

   @SubscribeEvent
   public static void Check1() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      ClientPlayerEntity player = mc.player;
      if (player != null && level != null) {
         Box searchBox = player.getBoundingBox().expand(192.0);
         List<Entity> entities = level.getOtherEntities(null, searchBox);
         Set<BlockPos> currentInactive = new HashSet<>();

         for (Entity entity : entities) {
            String name = entity.getName().getString();
            BlockPos pos = entity.getBlockPos();
            if (entity instanceof ArmorStandEntity) {
               if (name.contains("Inactive Terminal")) {
                  currentInactive.add(pos);
                  inactiveTerminals.putIfAbsent(pos, entity);
               } else if (name.contains("Terminal Active") && inactiveTerminals.containsKey(pos)) {
                  double TermX = entity.getX();
                  double TermY = entity.getY();
                  double TermZ = entity.getZ();
                  TermPos.add(TermX);
                  TermPos.add(TermY);
                  TermPos.add(TermZ);
                  inactiveTerminals.remove(pos);
               }
            }

            if (entity instanceof PlayerEntity && !termCompleter.isEmpty() && name.contains(termCompleter.getFirst())) {
               double playerx = entity.getX();
               double playery = entity.getY();
               double playerz = entity.getZ();
               PlayerPos.add(playerx);
               PlayerPos.add(playery);
               PlayerPos.add(playerz);
               termCompleter.removeFirst();
            }
         }

         if (!PlayerPos.isEmpty() && !TermPos.isEmpty()) {
            double xOffset = PlayerPos.getFirst() - TermPos.getFirst();
            double yOffset = PlayerPos.get(1) - TermPos.get(1);
            double zOffset = PlayerPos.get(2) - TermPos.get(2);
            PlayerPos.clear();
            TermPos.clear();
            if ((!(xOffset > 7.0) || !(xOffset < 40.0)) && (!(xOffset < -7.0) || !(xOffset > -40.0))) {
               if (yOffset > 13.0 || yOffset < -13.0) {
                  RSA.chat("§b" + username + " §7Failed InvWalk Check §4§lyOffSet§r§7: §8" + yOffset);
                  username = null;
               } else if ((!(zOffset > 7.0) || !(zOffset < 40.0)) && (!(zOffset < -7.0) || !(zOffset > -40.0))) {
                  if (xOffset > 40.0 || xOffset < -40.0) {
                     RSA.chat("§b" + username + " §7Failed AutoLeap Check §4§lzOffSet§r§7: §8" + xOffset);
                     username = null;
                  } else if (zOffset > 40.0 || zOffset < -40.0) {
                     RSA.chat("§b" + username + " §7Failed AutoLeap Check §4§lzOffSet§r§7: §8" + zOffset);
                     username = null;
                  }
               } else {
                  RSA.chat("§b" + username + " §7Failed InvWalk Check §4§lzOffSet§r§7: §8" + zOffset);
                  username = null;
               }
            } else {
               RSA.chat("§b" + username + " §7Failed InvWalk Check §4§lxOffSet§r§7: §8" + xOffset);
               username = null;
            }

            PlayerPos.clear();
            TermPos.clear();
         }

         termCompleter.clear();
         inactiveTerminals.keySet().retainAll(currentInactive);
      }
   }

   @SubscribeEvent
   public static void terminalCompletedMsg(Chat event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         String unformatted = StringHelper.stripTextFormat(event.getMessage().getString());
         Matcher matcher = playerName.matcher(unformatted);
         if (matcher.find()) {
            termCompleter.add(matcher.group(1));
            username = matcher.group(1);
         }
      }
   }
}
