package com.ricedotwho.rsa.module.impl.movement;

import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.render.Render2DEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.DragSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import org.joml.Vector2d;

@ModuleInfo(aliases = "VelocityBuffer", id = "VelocityBuffer", category = Category.MOVEMENT, hasKeybind = true)
public class VelocityBuffer extends Module {
   private static VelocityBuffer INSTANCE;
   private final KeybindSetting popKey = new KeybindSetting("Queue Pop Key", new Keybind(-1, false, this::popQueue));
   private final DragSetting gui = new DragSetting("Velocity Buffer Hud", new Vector2d(100.0, 100.0), new Vector2d(144.0, 80.0));
   private int bufferedCount = 0;
   private static final Set<Class<? extends Packet<?>>> PACKET_SET = Set.of(CommonPingS2CPacket.class, BundleS2CPacket.class);
   private final ConcurrentLinkedQueue<Packet<?>> queue = new ConcurrentLinkedQueue<>();

   public VelocityBuffer() {
      this.registerProperty(new Setting[]{this.popKey, this.gui});
   }

   @SubscribeEvent
   public void onRenderGui(Render2DEvent event) {
      if (!this.queue.isEmpty()) {
         this.gui
            .renderScaled(
               event.getGfx(),
               () -> event.getGfx().drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "Buffered Packets : " + this.bufferedCount, 0, 0, -1),
               10.0F,
               10.0F
            );
      }
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      synchronized (this.queue) {
         this.queue.clear();
         this.bufferedCount = 0;
         if (this.isEnabled()) {
            this.setEnabled(false);
         }
      }
   }

   public static boolean onReceivePacketPre(Packet<?> packet) {
      if (INSTANCE == null) {
         INSTANCE = (VelocityBuffer)RSM.getModule(VelocityBuffer.class);
      }

      return INSTANCE.onReceivePacket(packet);
   }

   private boolean onReceivePacket(Packet<?> packet) {
      synchronized (this.queue) {
         if (MinecraftClient.getInstance().player == null || !this.isEnabled()) {
            return false;
         } else if (packet instanceof PlayerPositionLookS2CPacket) {
            this.onKeyToggle();
            return false;
         } else if (this.isMotionPacket(packet, MinecraftClient.getInstance().player)) {
            this.queue.add(packet);
            this.bufferedCount++;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master((SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.5F, 0.5F));
            return true;
         } else {
            if (packet instanceof BundleS2CPacket bundlePacket) {
               bundlePacket.getPackets().forEach(p -> System.out.println(p.getClass()));
            }

            if (!PACKET_SET.contains(packet.getClass())) {
               return false;
            } else {
               synchronized (this.queue) {
                  if (this.queue.isEmpty()) {
                     return false;
                  }

                  this.queue.add(packet);
               }

               return true;
            }
         }
      }
   }

   public void onEnable() {
      super.onEnable();
      this.flush();
   }

   public void onDisable() {
      this.flush();
      super.onDisable();
   }

   public void popQueue() {
      if (MinecraftClient.getInstance().player != null) {
         synchronized (this.queue) {
            if (this.queue.isEmpty()) {
               return;
            }

            while (!this.queue.isEmpty()) {
               Packet<?> packet = this.queue.poll();
               this.receivePacket(packet);
               if (this.isMotionPacket(packet, MinecraftClient.getInstance().player)) {
                  this.bufferedCount--;
                  if (!this.queue.stream().anyMatch(p -> this.isMotionPacket((Packet<?>)p, MinecraftClient.getInstance().player))) {
                     this.flush();
                     if (this.isEnabled()) {
                        this.onKeyToggle();
                     }
                  }
                  break;
               }
            }
         }

         MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master((SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 2.0F, 2.0F));
      }
   }

   private void receivePacket(Packet<?> packet) {
      if (MinecraftClient.getInstance().getNetworkHandler() != null) {
         ((Packet)packet).apply(MinecraftClient.getInstance().getNetworkHandler());
      }
   }

   private boolean isMotionPacket(Packet<?> packet, ClientPlayerEntity player) {
      return packet instanceof EntityVelocityUpdateS2CPacket motionPacket && motionPacket.getEntityId() == player.getId();
   }

   private void flush() {
      synchronized (this.queue) {
         if (!this.queue.isEmpty()) {
            this.queue.forEach(this::receivePacket);
         }

         this.queue.clear();
      }

      this.bufferedCount = 0;
   }
}
