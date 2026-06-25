package com.ricedotwho.rsa.module.impl.render;

import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent;
import com.ricedotwho.rsm.event.impl.render.Render2DEvent;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.DragSetting;
import com.ricedotwho.rsm.utils.render.render2d.NVGUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.particle.CampfireSmokeParticle.CosySmokeFactory;
import net.minecraft.client.particle.CampfireSmokeParticle.SignalSmokeFactory;
import net.minecraft.client.particle.DragonBreathParticle;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.ExplosionEmitterParticle;
import net.minecraft.client.particle.ExplosionLargeParticle;
import net.minecraft.client.particle.LargeFireSmokeParticle;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SpellParticle.WitchFactory;
import net.minecraft.client.particle.FireSmokeParticle;
import net.minecraft.client.particle.WhiteSmokeParticle;
import org.joml.Vector2d;

@ModuleInfo(aliases = "Effects", id = "EffectsAndRender", category = Category.RENDER)
@Environment(EnvType.CLIENT)
public class EffectsAndRender extends Module {
   private final BooleanSetting Explosions = new BooleanSetting("Explosions", false, () -> true);
   private final BooleanSetting Fires = new BooleanSetting("Fires", false, () -> true);
   private final BooleanSetting EtherWarp = new BooleanSetting("EtherWarp", false, () -> true);
   private final BooleanSetting SMOKE = new BooleanSetting("SMOKE", false, () -> true);
   private final BooleanSetting Nausea = new BooleanSetting("Nausea", false, () -> true);
   private final BooleanSetting Blindness = new BooleanSetting("Blindness", false, () -> true);
   private final BooleanSetting Slowness = new BooleanSetting("Slowness", false, () -> true);
   private final BooleanSetting Haste = new BooleanSetting("Haste", false, () -> true);
   private final BooleanSetting Darkness = new BooleanSetting("Darkness", false, () -> true);
   private final BooleanSetting Mining_Fatigue = new BooleanSetting("Mining Fatigue", false, () -> true);
   private final BooleanSetting Speedness = new BooleanSetting("Speedness", false, () -> true);
   private final BooleanSetting FpsToggled = new BooleanSetting("Fps display", false, () -> true);
   private final DragSetting Fps = new DragSetting("Fps display", new Vector2d(50.0, 50.0), new Vector2d(50.0, 50.0));

   public EffectsAndRender() {
      this.registerProperty(
         new Setting[]{
            this.Explosions,
            this.Fires,
            this.EtherWarp,
            this.SMOKE,
            this.FpsToggled,
            this.Fps,
            this.Nausea,
            this.Blindness,
            this.Slowness,
            this.Haste,
            this.Speedness,
            this.Darkness,
            this.Mining_Fatigue
         }
      );
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         if ((Boolean)this.Nausea.getValue()) {
            mc.player.removeStatusEffect(StatusEffects.NAUSEA);
         }

         if ((Boolean)this.Blindness.getValue()) {
            mc.player.removeStatusEffect(StatusEffects.BLINDNESS);
         }

         if ((Boolean)this.Slowness.getValue()) {
            mc.player.removeStatusEffect(StatusEffects.SLOWNESS);
         }

         if ((Boolean)this.Haste.getValue()) {
            mc.player.removeStatusEffect(StatusEffects.HASTE);
         }

         if ((Boolean)this.Speedness.getValue()) {
            mc.player.removeStatusEffect(StatusEffects.SPEED);
         }

         if ((Boolean)this.Darkness.getValue()) {
            mc.player.removeStatusEffect(StatusEffects.DARKNESS);
         }

         if ((Boolean)this.Mining_Fatigue.getValue()) {
            mc.player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
         }
      }
   }

   public static void init() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.EXPLOSION,
            spriteSet -> {
               ExplosionLargeParticle.Factory originalFactory = new ExplosionLargeParticle.Factory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .Explosions
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.EXPLOSION_EMITTER,
            spriteSet -> {
               ExplosionEmitterParticle.Factory originalFactory = new ExplosionEmitterParticle.Factory();
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .Explosions
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.DRAGON_BREATH,
            spriteSet -> {
               DragonBreathParticle.Factory originalFactory = new DragonBreathParticle.Factory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .Fires
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.FLAME,
            spriteSet -> {
               FlameParticle.Factory originalFactory = new FlameParticle.Factory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .Fires
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.PORTAL,
            spriteSet -> {
               PortalParticle.Factory originalFactory = new PortalParticle.Factory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .EtherWarp
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.WITCH,
            spriteSet -> {
               WitchFactory originalFactory = new WitchFactory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .EtherWarp
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.LARGE_SMOKE,
            spriteSet -> {
               LargeFireSmokeParticle.Factory originalFactory = new LargeFireSmokeParticle.Factory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .SMOKE
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.SMOKE,
            spriteSet -> {
               FireSmokeParticle.Factory originalFactory = new FireSmokeParticle.Factory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .SMOKE
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.CAMPFIRE_COSY_SMOKE,
            spriteSet -> {
               CosySmokeFactory originalFactory = new CosySmokeFactory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .SMOKE
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
            spriteSet -> {
               SignalSmokeFactory originalFactory = new SignalSmokeFactory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .SMOKE
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
      ParticleFactoryRegistry.getInstance()
         .register(
            ParticleTypes.WHITE_SMOKE,
            spriteSet -> {
               WhiteSmokeParticle.Factory originalFactory = new WhiteSmokeParticle.Factory(spriteSet);
               return (simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource) -> ((EffectsAndRender)RSM.getModule(EffectsAndRender.class))
                     .SMOKE
                     .getValue()
                  ? null
                  : originalFactory.createParticle(simpleParticleType, clientLevel, d, e, f, g, h, i, randomSource);
            }
         );
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void reset() {
   }

   @SubscribeEvent
   public void onRender2D(Render2DEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      ClientWorld level = MinecraftClient.getInstance().world;
      if (player != null && level != null) {
         int fps = MinecraftClient.getInstance().getCurrentFps();
         String fpsString = "Fps: " + fps;
         if ((Boolean)this.FpsToggled.getValue()) {
            this.Fps.renderScaled(event.getGfx(), () -> NVGUtils.drawText(fpsString, 0.0F, 0.0F, 50.0F, Colour.blue, NVGUtils.NUNITO), 60.0F, 30.0F);
         }
      }
   }

   public BooleanSetting getExplosions() {
      return this.Explosions;
   }

   public BooleanSetting getFires() {
      return this.Fires;
   }

   public BooleanSetting getEtherWarp() {
      return this.EtherWarp;
   }

   public BooleanSetting getSMOKE() {
      return this.SMOKE;
   }

   public BooleanSetting getNausea() {
      return this.Nausea;
   }

   public BooleanSetting getBlindness() {
      return this.Blindness;
   }

   public BooleanSetting getSlowness() {
      return this.Slowness;
   }

   public BooleanSetting getHaste() {
      return this.Haste;
   }

   public BooleanSetting getDarkness() {
      return this.Darkness;
   }

   public BooleanSetting getMining_Fatigue() {
      return this.Mining_Fatigue;
   }

   public BooleanSetting getSpeedness() {
      return this.Speedness;
   }

   public BooleanSetting getFpsToggled() {
      return this.FpsToggled;
   }

   public DragSetting getFps() {
      return this.Fps;
   }
}
