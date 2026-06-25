package com.ricedotwho.rsa.module.impl.dungeon.boss;

import com.ricedotwho.rsa.IMixin.IConnection;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.BlinkRing;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.render.Render2DEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.DragSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.ChatUtils;
import java.math.BigDecimal;
import java.util.LinkedList;
import net.minecraft.util.PlayerInput;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import org.joml.Vector2d;

@ModuleInfo(aliases = "Blink", id = "Blink", category = Category.MOVEMENT, hasKeybind = true)
public class Blink extends Module {
   private static Blink INSTANCE;
   private PlayerInput lastInput;
   private final DragSetting gui = new DragSetting("Blink Hud", new Vector2d(100.0, 100.0), new Vector2d(144.0, 80.0));
   private final NumberSetting maxBlinkPacket = new NumberSetting("Max Blink Ticks", 1.0, 30.0, 17.0, 1.0);
   private BlinkRing currentRing;
   private final LinkedList<Packet<?>> queue = new LinkedList<>();
   private boolean flushing = false;
   private int packetCount = 0;

   public Blink() {
      this.registerProperty(new Setting[]{this.maxBlinkPacket, this.gui});
   }

   @SubscribeEvent
   public void onRenderGui(Render2DEvent event) {
      if (!this.queue.isEmpty()) {
         this.gui
            .renderScaled(
               event.getGfx(),
               () -> event.getGfx()
                  .drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "Blinking", (int)this.gui.getPosition().x, (int)this.gui.getPosition().y, -1),
               10.0F,
               10.0F
            );
      }
   }

   @SubscribeEvent
   public void onSendPacket(Send event) {
      if (event.getPacket() instanceof PlayerInputC2SPacket inputPacket) {
         if (this.lastInput != null && this.inputEquals(inputPacket.input(), this.lastInput)) {
            event.setCancelled(true);
         }

         this.lastInput = inputPacket.input();
      }
   }

   private boolean inputEquals(PlayerInput input1, PlayerInput input2) {
      return input1.sneak() == input2.sneak()
         && input1.forward() == input2.forward()
         && input1.backward() == input2.backward()
         && input1.left() == input2.left()
         && input1.right() == input2.right()
         && input1.jump() == input2.jump()
         && input1.sprint() == input2.sprint();
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      synchronized (this.queue) {
         this.queue.clear();
         if (this.isEnabled()) {
            this.setEnabled(false);
         }
      }
   }

   public static boolean onSendPacket(Packet<?> packet) {
      if (INSTANCE == null) {
         INSTANCE = (Blink)RSM.getModule(Blink.class);
      }

      return INSTANCE.onPreSendPacket(packet);
   }

   private boolean onPreSendPacket(Packet<?> packet) {
      if (MinecraftClient.getInstance().player != null && this.isEnabled()) {
         synchronized (this.queue) {
            if (this.flushing) {
               return false;
            } else {
               boolean bl = true;
               if (this.currentRing != null
                  && (this.packetCount >= ((BigDecimal)this.maxBlinkPacket.getValue()).intValue() || this.currentRing.isDonePlaying())) {
                  if (packet instanceof PlayerMoveC2SPacket || packet instanceof PlayerInputC2SPacket) {
                     return true;
                  }

                  if (packet instanceof TeleportConfirmC2SPacket) {
                     if (this.isEnabled()) {
                        this.onKeyToggle();
                     }

                     return false;
                  }
               }

               if (packet instanceof ClientTickEndC2SPacket) {
                  this.packetCount++;
                  if (this.currentRing != null) {
                     if (this.packetCount >= ((BigDecimal)this.maxBlinkPacket.getValue()).intValue()) {
                        bl = false;
                        this.packetCount--;
                     }
                  } else if (this.packetCount >= ((BigDecimal)this.maxBlinkPacket.getValue()).intValue()) {
                     this.onKeyToggle();
                     return false;
                  }
               }

               if (bl) {
                  this.queue.add(packet);
                  return true;
               } else {
                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   public int getChargedCount() {
      return this.packetCount;
   }

   public void clearMovements() {
      this.queue.removeIf(p -> p instanceof PlayerInputC2SPacket || p instanceof PlayerMoveC2SPacket);
   }

   public void actuallySendImmediately(Packet<?> packet) {
      if (MinecraftClient.getInstance().getNetworkHandler() != null) {
         synchronized (this.queue) {
            this.flushing = true;
            ((IConnection)MinecraftClient.getInstance().getNetworkHandler().getConnection()).sendPacketImmediately(packet);
            this.flushing = true;
         }
      }
   }

   public void actuallySend(Packet<?> packet) {
      if (MinecraftClient.getInstance().getNetworkHandler() != null) {
         synchronized (this.queue) {
            this.flushing = true;
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(packet);
            this.flushing = true;
         }
      }
   }

   public void onEnable() {
      super.onEnable();
      this.flush();
      this.currentRing = null;
      this.lastInput = null;
      this.packetCount = 0;
   }

   public void onDisable() {
      super.onDisable();
      ChatUtils.chat("Packets : " + this.queue.stream().filter(p -> p instanceof PlayerMoveC2SPacket).count(), new Object[0]);
      this.flush();
      this.currentRing = null;
      this.lastInput = null;
      this.packetCount = 0;
   }

   private void flushTick() {
      if (MinecraftClient.getInstance().getNetworkHandler() != null) {
         synchronized (this.queue) {
            this.flushing = true;
            if (this.queue.isEmpty()) {
               this.flushing = false;
               this.setEnabled(false);
            } else {
               while (!this.queue.isEmpty()) {
                  Packet<?> packet = this.queue.poll();
                  ((IConnection)MinecraftClient.getInstance().getNetworkHandler().getConnection()).sendPacketImmediately(packet);
                  if (packet instanceof ClientTickEndC2SPacket) {
                     this.flushing = false;
                     return;
                  }
               }

               this.flushing = false;
            }
         }
      }
   }

   private void flush() {
      if (MinecraftClient.getInstance().getNetworkHandler() != null) {
         synchronized (this.queue) {
            this.flushing = true;
            if (this.queue.isEmpty()) {
               this.flushing = false;
            } else {
               this.queue.forEach(packet -> ((IConnection)MinecraftClient.getInstance().getNetworkHandler().getConnection()).sendPacketImmediately((Packet<?>)packet));
               this.queue.clear();
               this.flushing = false;
            }
         }
      }
   }

   public void setCurrentRing(BlinkRing currentRing) {
      this.currentRing = currentRing;
   }
}
