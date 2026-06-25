package com.ricedotwho.rsa.module.impl.render;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.camera.CameraHandler;
import com.ricedotwho.rsm.component.impl.camera.CameraPositionProvider;
import com.ricedotwho.rsm.component.impl.camera.ClientRotationHandler;
import com.ricedotwho.rsm.component.impl.camera.ClientRotationProvider;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Start;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.RotationUtils;
import java.math.BigDecimal;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

@ModuleInfo(aliases = "Freecam", id = "Freecam", category = Category.RENDER, hasKeybind = true)
public class Freecam extends Module implements ClientRotationProvider, CameraPositionProvider {
   private static final String ENABLE_MSG = "Freecam " + Formatting.GREEN + "enabled!";
   private static final String DISABLE_MSG = "Freecam " + Formatting.RED + "disabled!";
   private final NumberSetting horizontalSpeed = new NumberSetting("Horizontal Speed", 0.0, 1.0, 0.35, 0.05);
   private final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed", 0.0, 0.5, 0.25, 0.025);
   private static Freecam INSTANCE;
   private Pos freecamPos = new Pos();

   public Freecam() {
      this.registerProperty(new Setting[]{this.horizontalSpeed, this.verticalSpeed});
   }

   public void onEnable() {
      RSA.chat(ENABLE_MSG);
      if (INSTANCE == null) {
         INSTANCE = (Freecam)RSM.getModule(Freecam.class);
      }

      this.freecamPos = new Pos(MinecraftClient.getInstance().gameRenderer.getCamera().getPos());
      CameraHandler.registerProvider(this);
      ClientRotationHandler.registerProvider(this);
   }

   public void onDisable() {
      RSA.chat(DISABLE_MSG);
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.setEnabled(false);
   }

   @SubscribeEvent
   public void onRenderWorld(Start event) {
      if (MinecraftClient.getInstance().getCameraEntity() != null) {
         GameOptions options = MinecraftClient.getInstance().options;
         boolean up = options.forwardKey.isPressed();
         boolean down = options.backKey.isPressed();
         boolean left = options.leftKey.isPressed();
         boolean right = options.rightKey.isPressed();
         float x = RotationUtils.calculateImpulse(up, down);
         float y = RotationUtils.calculateImpulse(left, right);
         Vec2f hori = Vec2f.ZERO;
         if (x != 0.0F || y != 0.0F) {
            hori = RotationUtils.rotateVector(y, x, -ClientRotationHandler.getClientYaw())
               .normalize()
               .multiply(((BigDecimal)this.horizontalSpeed.getValue()).floatValue());
         }

         float vertical = RotationUtils.calculateImpulse(options.jumpKey.isPressed(), options.sneakKey.isPressed())
            * ((BigDecimal)this.verticalSpeed.getValue()).floatValue();
         this.freecamPos.selfAdd(hori.x, vertical, hori.y);
      }
   }

   public static boolean isDetached() {
      return INSTANCE != null && INSTANCE.isEnabled();
   }

   public boolean shouldOverridePosition() {
      return this.isEnabled();
   }

   public boolean shouldOverrideHitPos() {
      return false;
   }

   public boolean shouldOverrideHitRot() {
      return false;
   }

   public boolean shouldBlockKeyboardMovement() {
      return true;
   }

   public Vec3d getCameraPosition() {
      return this.freecamPos.asVec3();
   }

   public Vec3d getPosForHit() {
      return null;
   }

   public Vec3d getRotForHit() {
      return null;
   }

   public boolean isClientRotationActive() {
      return this.isEnabled();
   }

   public boolean allowClientKeyInputs() {
      return false;
   }

   public NumberSetting getHorizontalSpeed() {
      return this.horizontalSpeed;
   }

   public NumberSetting getVerticalSpeed() {
      return this.verticalSpeed;
   }

   public Pos getFreecamPos() {
      return this.freecamPos;
   }
}
