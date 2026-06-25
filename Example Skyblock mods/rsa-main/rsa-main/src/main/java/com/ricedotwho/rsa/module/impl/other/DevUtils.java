package com.ricedotwho.rsa.module.impl.other;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ButtonSetting;
import com.ricedotwho.rsm.utils.ItemUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;

@ModuleInfo(aliases = "Dev Utils", id = "DevUtils", category = Category.OTHER)
public class DevUtils extends Module {
   private final ButtonSetting pos = new ButtonSetting("Your XYZ", "List Pos", () -> {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      MinecraftClient mc = MinecraftClient.getInstance();
      Keyboard keyboard = mc.keyboard;
      if (player != null) {
         double x = player.getX();
         double y = player.getY();
         double z = player.getZ();
         String xyz = x + ", " + y + ", " + z;
         RSA.chat(xyz);
         keyboard.setClipboard(xyz);
         RSA.chat("Copied to clipboard!");
      }
   });
   private final ButtonSetting yawPitch = new ButtonSetting("Yaw and Pitch", "Yaw/Pitch", () -> {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      MinecraftClient mc = MinecraftClient.getInstance();
      Keyboard keyboard = mc.keyboard;
      if (player != null) {
         float yaw = player.getYaw();
         float pitch = player.getPitch();
         String yp = yaw + ", " + pitch;
         RSA.chat(yp);
         keyboard.setClipboard(yp);
      }
   });
   private final ButtonSetting blockinfo = new ButtonSetting("Block info that you're lookin at", "Block Info", () -> {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      MinecraftClient mc = MinecraftClient.getInstance();
      Keyboard keyboard = mc.keyboard;
      HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
      if (player != null) {
         if (hitResult.getType() == Type.BLOCK) {
            MinecraftClient client = MinecraftClient.getInstance();
            BlockHitResult blockHit = (BlockHitResult)client.crosshairTarget;
            BlockPos pos = blockHit.getBlockPos();
            double x = pos.getX() + 0.5;
            int y = pos.getY();
            double z = pos.getZ() + 0.5;
            String BlockInfo = x + ", " + y + ", " + z;
            RSA.chat("XYZ: " + BlockInfo);
         }
      }
   });
   private final ButtonSetting entityinfo = new ButtonSetting(
      "Entity info that you're lookin at",
      "Entity Info",
      () -> {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
         if (player != null) {
            EntityHitResult entityHR = (EntityHitResult)hitResult;
            String entityInfo = entityHR.getEntity().getName().getString();
            String entityId = String.valueOf(entityHR.getEntity().getId());
            String simplePos = entityHR.getEntity().getBlockPos().getX()
               + ", "
               + entityHR.getEntity().getBlockPos().getY()
               + ", "
               + entityHR.getEntity().getBlockPos().getZ();
            RSA.chat("Name: " + entityInfo);
            RSA.chat("ID: " + entityId);
            RSA.chat("Pos: " + simplePos);
         }
      }
   );
   private final ButtonSetting getSbID = new ButtonSetting("Gets the SBID of the item you're holding", "Get SBID", () -> {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (player != null) {
         ItemStack stack = player.getMainHandStack();
         String sbid = ItemUtils.getID(stack);
         RSA.chat("SBID: " + sbid);
      }
   });

   public DevUtils() {
      this.registerProperty(new Setting[]{this.pos, this.yawPitch, this.blockinfo, this.entityinfo, this.getSbID});
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void reset() {
   }

   public ButtonSetting getPos() {
      return this.pos;
   }

   public ButtonSetting getYawPitch() {
      return this.yawPitch;
   }

   public ButtonSetting getBlockinfo() {
      return this.blockinfo;
   }

   public ButtonSetting getEntityinfo() {
      return this.entityinfo;
   }

   public ButtonSetting getGetSbID() {
      return this.getSbID;
   }
}
