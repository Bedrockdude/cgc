package com.ricedotwho.rsa.component.impl.managers;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.IMixin.IMultiPlayerGameMode;
import com.ricedotwho.rsm.data.Rotation;
import com.ricedotwho.rsm.utils.ItemUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.function.Predicate;
import net.minecraft.util.Hand;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;

public class SwapManager {
   private static int serverSlot;
   private static int lastSentServerSlot;
   private static boolean swappedThisTick = false;
   private static int requireSwap = -1;
   private static final Queue<Packet<?>> pendingC08Packets = new ArrayDeque<>();
   private static int currentTick = 0;
   private static int lastC08Tick = Integer.MIN_VALUE;
   private static int lastSwapTick = Integer.MIN_VALUE;
   private static boolean flushingQueuedC08 = false;

   public static void onPreTickStart() {
      currentTick++;
      swappedThisTick = false;
      requireSwap = -1;
      flushQueuedC08();
   }

   public static boolean onPostSendPacket(Packet<?> packet) {
      if (packet instanceof UpdateSelectedSlotC2SPacket slotPacket) {
         if (!swappedThisTick && slotPacket.getSelectedSlot() != lastSentServerSlot) {
            swappedThisTick = true;
            serverSlot = slotPacket.getSelectedSlot();
            lastSentServerSlot = slotPacket.getSelectedSlot();
            lastSwapTick = currentTick;
            return true;
         } else {
            RSA.chat("Prevented packet 0 tick swap! This shouldn't happen, tell hyper!");
            return false;
         }
      } else if (isC08Packet(packet)) {
         if (flushingQueuedC08) {
            lastC08Tick = currentTick;
            return true;
         } else if (!pendingC08Packets.isEmpty() || lastC08Tick == currentTick || lastSwapTick == currentTick) {
            pendingC08Packets.add(packet);
            return false;
         } else {
            lastC08Tick = currentTick;
            return true;
         }
      } else {
         return true;
      }
   }

   public static void onHandleLogin() {
      serverSlot = 0;
      lastSentServerSlot = 0;
      pendingC08Packets.clear();
      lastC08Tick = Integer.MIN_VALUE;
      lastSwapTick = Integer.MIN_VALUE;
      flushingQueuedC08 = false;
   }

   private static boolean isC08Packet(Packet<?> packet) {
      return packet instanceof PlayerInteractItemC2SPacket || packet instanceof PlayerInteractBlockC2SPacket;
   }

   private static void flushQueuedC08() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (pendingC08Packets.isEmpty() || lastC08Tick == currentTick || lastSwapTick == currentTick || client.getNetworkHandler() == null) {
         return;
      }

      Packet<?> packet = pendingC08Packets.poll();
      if (packet == null) {
         return;
      }

      flushingQueuedC08 = true;

      try {
         client.getNetworkHandler().sendPacket(packet);
      } finally {
         flushingQueuedC08 = false;
      }
   }

   public static boolean onEnsureHasSentCarriedItem(int managerServerSlot) {
      if (MinecraftClient.getInstance().player == null) {
         return false;
      } else {
         if (serverSlot != managerServerSlot) {
            RSA.chat("Slot mismatch! Tell Hyper if you see this!");
            RSA.chat("SwapManger : " + serverSlot);
            RSA.chat("GameMode : " + managerServerSlot);
         }

         int i = MinecraftClient.getInstance().player.getInventory().getSelectedSlot();
         if (!swappedThisTick && requireSwap > -1 && i != requireSwap) {
            if (requireSwap == managerServerSlot) {
               return false;
            }

            MinecraftClient.getInstance().player.getInventory().setSelectedSlot(requireSwap);
            i = requireSwap;
         }

         if (i != managerServerSlot && !swappedThisTick) {
            serverSlot = i;
            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean reserveSwap0(int index) {
      if (index < 0 || index > 8) {
         return false;
      } else if (!canSwap()) {
         return index == getNextUpdateIndex();
      } else {
         requireSwap = index;
         return true;
      }
   }

   public static boolean reserveSwap(int index) {
      if (!reserveSwap0(index)) {
         return false;
      } else {
         swapSlot(index);
         return true;
      }
   }

   public static int getNextUpdateIndex() {
      if (swappedThisTick) {
         return serverSlot;
      } else if (requireSwap > -1) {
         return requireSwap;
      } else {
         return MinecraftClient.getInstance().player == null ? 0 : MinecraftClient.getInstance().player.getInventory().getSelectedSlot();
      }
   }

   public static boolean canSwap() {
      return !swappedThisTick && requireSwap < 0;
   }

   public static boolean sendAirC08(float yaw, float pitch, boolean syncSlots, boolean swing) {
      if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().player.getGameMode() == GameMode.SPECTATOR) {
         return false;
      } else if (MinecraftClient.getInstance().interactionManager != null && MinecraftClient.getInstance().world != null) {
         IMultiPlayerGameMode manager = (IMultiPlayerGameMode)MinecraftClient.getInstance().interactionManager;
         int i = MinecraftClient.getInstance().player.getInventory().getSelectedSlot();
         if (syncSlots) {
            manager.syncSlot();
         }

         if (syncSlots && !checkServerSlot(i)) {
            RSA.chat("Failed to swap to slot : " + i);
            return false;
         } else {
            manager.sendPacketSequenced(MinecraftClient.getInstance().world, sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));
            if (swing) {
               MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean isDesynced() {
      return getNextUpdateIndex() != serverSlot;
   }

   public static boolean sendAirC08(float yaw, float pitch, boolean syncSlots) {
      return sendAirC08(yaw, pitch, syncSlots, false);
   }

   public static boolean sendAirC08(Rotation rot, boolean syncSlots) {
      return sendAirC08(rot.getYaw(), rot.getPitch(), syncSlots, false);
   }

   public static boolean sendAirC08(Rotation rot, boolean syncSlots, boolean swing) {
      return sendAirC08(rot.getYaw(), rot.getPitch(), syncSlots, swing);
   }

   public static boolean sendBlockC08(BlockHitResult result, boolean swing, boolean syncSlot) {
      if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().player.getGameMode() == GameMode.SPECTATOR) {
         return false;
      } else if (MinecraftClient.getInstance().interactionManager != null && MinecraftClient.getInstance().world != null) {
         if (syncSlot) {
            IMultiPlayerGameMode manager = (IMultiPlayerGameMode)MinecraftClient.getInstance().interactionManager;
            int i = MinecraftClient.getInstance().player.getInventory().getSelectedSlot();
            manager.syncSlot();
            if (!checkServerSlot(i)) {
               RSA.chat("Failed to swap to slot : " + i);
               return false;
            }
         }

         ((IMultiPlayerGameMode)MinecraftClient.getInstance().interactionManager)
            .sendPacketSequenced(MinecraftClient.getInstance().world, sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, sequence));
         if (swing) {
            MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND);
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean sendBlockC08(float yaw, float pitch, boolean swing, boolean syncSlot) {
      HitResult result = RotationUtils.getBlockHitResult(
         MinecraftClient.getInstance().player.getContainerInteractionRange(), yaw, pitch, MinecraftClient.getInstance().player.getEntityPos().add(0.0, 1.54F, 0.0)
      );
      if (result.getType() != Type.BLOCK) {
         RSA.chat("Failed to send block C08!");
      }

      return sendBlockC08((BlockHitResult)result, swing, syncSlot);
   }

   public static boolean sendBlockC08(Vec3d pos, Direction direction, boolean swing, boolean syncSlot) {
      return sendBlockC08(new BlockHitResult(pos, direction, BlockPos.ofFloored(pos), false), swing, syncSlot);
   }

   public static boolean sendC07(BlockPos result, Action action, Direction face, boolean swing, boolean syncSlot) {
      if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().player.getGameMode() == GameMode.SPECTATOR) {
         return false;
      } else if (MinecraftClient.getInstance().interactionManager != null && MinecraftClient.getInstance().world != null) {
         if (syncSlot) {
            IMultiPlayerGameMode manager = (IMultiPlayerGameMode)MinecraftClient.getInstance().interactionManager;
            int i = MinecraftClient.getInstance().player.getInventory().getSelectedSlot();
            manager.syncSlot();
            if (!checkServerSlot(i)) {
               RSA.chat("Failed to swap to slot : " + i);
               return false;
            }
         }

         ((IMultiPlayerGameMode)MinecraftClient.getInstance().interactionManager)
            .sendPacketSequenced(MinecraftClient.getInstance().world, sequence -> new PlayerActionC2SPacket(action, result, face, sequence));
         if (swing) {
            MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND);
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean reserveSwap(Item item) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && item != null) {
         if (!canSwap()) {
            return item == player.getInventory().getStack(getNextUpdateIndex()).getItem();
         } else {
            for (int i = 0; i < 9; i++) {
               ItemStack stack = player.getInventory().getStack(i);
               if (stack.getItem() == item) {
                  boolean bl = swapSlot(i);
                  if (bl) {
                     reserveSwap0(i);
                  }

                  return bl;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean reserveSwap(Predicate<ItemStack> predicate) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return false;
      } else if (!canSwap()) {
         return predicate.test(player.getInventory().getStack(getNextUpdateIndex()));
      } else {
         for (int i = 0; i < 9; i++) {
            if (predicate.test(player.getInventory().getStack(i))) {
               boolean bl = swapSlot(i);
               if (bl) {
                  reserveSwap0(i);
               }

               return bl;
            }
         }

         return false;
      }
   }

   public static boolean reserveSwap(String... sbId) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && sbId != null && sbId.length != 0) {
         if (!canSwap()) {
            String next = ItemUtils.getID(player.getInventory().getStack(getNextUpdateIndex()));
            return Arrays.stream(sbId).anyMatch(idx -> !idx.isBlank() && next.equals(idx));
         } else {
            for (int i = 0; i < 9; i++) {
               String id = ItemUtils.getID(player.getInventory().getStack(i));
               if (!Arrays.stream(sbId).noneMatch(id1 -> !id1.isBlank() && id.equals(id1))) {
                  boolean bl = swapSlot(i);
                  if (bl) {
                     reserveSwap0(i);
                  }

                  return bl;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean swapItem(Item item) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && item != null) {
         if (item == player.getInventory().getStack(getNextUpdateIndex()).getItem()) {
            return true;
         } else if (!canSwap()) {
            return false;
         } else {
            for (int i = 0; i < 9; i++) {
               ItemStack stack = player.getInventory().getStack(i);
               if (stack.getItem() == item) {
                  return swapSlot(i);
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean swapItem(String... sbId) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && sbId != null && sbId.length != 0) {
         String heldId = ItemUtils.getID(player.getInventory().getStack(getNextUpdateIndex()));
         if (Arrays.stream(sbId).anyMatch(idx -> !idx.isBlank() && heldId.equals(idx))) {
            return true;
         } else if (!canSwap()) {
            return false;
         } else {
            for (int i = 0; i < 9; i++) {
               String id = ItemUtils.getID(player.getInventory().getStack(i));
               if (!Arrays.stream(sbId).noneMatch(id1 -> !id1.isBlank() && id.equals(id1))) {
                  return swapSlot(i);
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean swapItem(Predicate<ItemStack> predicate) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return false;
      } else if (predicate.test(player.getInventory().getStack(getNextUpdateIndex()))) {
         return true;
      } else if (!canSwap()) {
         return false;
      } else {
         for (int i = 0; i < 9; i++) {
            if (predicate.test(player.getInventory().getStack(i))) {
               return swapSlot(i);
            }
         }

         return false;
      }
   }

   public static boolean swapSlot(int slot) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (slot == getNextUpdateIndex()) {
         return true;
      } else if (player == null || swappedThisTick) {
         return false;
      } else if (slot >= 0 && slot <= 8) {
         player.getInventory().setSelectedSlot(slot);
         return true;
      } else {
         RSA.getLogger().error("Invalid swap slot! : {}", slot);
         return false;
      }
   }

   public static boolean checkServerSlot(int slot) {
      return serverSlot == slot;
   }

   public static boolean checkServerItem(Item item) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && serverSlot >= 0 && serverSlot <= 8) {
         ItemStack stack = player.getInventory().getStack(serverSlot);
         return stack.getItem() == item;
      } else {
         return false;
      }
   }

   public static boolean checkServerItem(String... sbId) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && serverSlot >= 0 && serverSlot <= 8 && sbId.length != 0) {
         String heldId = ItemUtils.getID(player.getInventory().getStack(serverSlot));
         return Arrays.stream(sbId).anyMatch(id -> !id.isBlank() && heldId.equals(id));
      } else {
         return false;
      }
   }

   public static boolean checkClientItem(Item item) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return false;
      } else {
         ItemStack stack = player.getInventory().getStack(player.getInventory().getSelectedSlot());
         return stack.getItem() == item;
      }
   }

   public static boolean checkClientItem(String... sbId) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && sbId.length != 0) {
         String heldId = ItemUtils.getID(player.getInventory().getStack(player.getInventory().getSelectedSlot()));
         return Arrays.stream(sbId).anyMatch(id -> !id.isBlank() && heldId.equals(id));
      } else {
         return false;
      }
   }

   public static int getItemSlot(Item item) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && item != null) {
         for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   public static int getItemSlot(String... id) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && id != null && id.length != 0) {
         for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (Arrays.stream(id).anyMatch(s -> s.equals(ItemUtils.getID(stack)))) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   public static int getServerSlot() {
      return serverSlot;
   }
}
