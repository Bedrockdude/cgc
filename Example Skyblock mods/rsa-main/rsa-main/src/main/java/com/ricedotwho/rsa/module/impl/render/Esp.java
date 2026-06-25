package com.ricedotwho.rsa.module.impl.render;

import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.DefaultGroupSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ColourSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ModeSetting;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledBox;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledOutlineBox;
import com.ricedotwho.rsm.utils.render.render3d.type.OutlineBox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.StringHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

@ModuleInfo(aliases = "Esp", id = "Esp", category = Category.RENDER)
public class Esp extends Module {
   private final ModeSetting renderMode = new ModeSetting("Mode", "Filled Outline", List.of("Filled Outline", "Filled", "Outline"));
   private final BooleanSetting showStarredMobs = new BooleanSetting("Starred Mobs", true);
   private final BooleanSetting onlyShowInCurrentRoom = new BooleanSetting("Current Room Only", true);
   private final BooleanSetting drawBloodMobs = new BooleanSetting("Blood Mobs", false);
   private final BooleanSetting withers = new BooleanSetting("Withers", true);
   private final BooleanSetting bats = new BooleanSetting("Bats", false);
   private final BooleanSetting depth = new BooleanSetting("Depth", false);
   private final DefaultGroupSetting colours = new DefaultGroupSetting("Colours", this);
   private final ColourSetting starredFill = new ColourSetting("Star Fill", new Colour(444137617));
   private final ColourSetting starredOutline = new ColourSetting("Star Outline", new Colour(-2752257));
   private final ColourSetting bloodFill = new ColourSetting("Blood Fill", new Colour(443678720));
   private final ColourSetting bloodOutline = new ColourSetting("Blood Outline", new Colour(-65536));
   private final ColourSetting witherFill = new ColourSetting("Wither Fill", new Colour(436221576));
   private final ColourSetting witherOutline = new ColourSetting("Wither Outline", new Colour(-16750849));
   private final ColourSetting batFill = new ColourSetting("Bat Fill", new Colour(173, 92, 173, 90));
   private final ColourSetting batOutline = new ColourSetting("Bat Outline", new Colour(173, 92, 173));
   private final Set<Integer> starredMobs = new HashSet<>();
   private final Set<Integer> bloodMobs = new HashSet<>();
   private final Set<Integer> batMobs = new HashSet<>();
   private final Set<Integer> bloodNames = new HashSet<>();
   private int wither = -1;
   private double witherDistance = Double.MAX_VALUE;
   public float updateInterval = 10.0F;

   public Esp() {
      this.addName("Revoker");
      this.addName("Psycho");
      this.addName("Reaper");
      this.addName("Cannibal");
      this.addName("Mute");
      this.addName("Ooze");
      this.addName("Putrid");
      this.addName("Freak");
      this.addName("Leech");
      this.addName("Tear");
      this.addName("Parasite");
      this.addName("Flamer");
      this.addName("Skull");
      this.addName("Mr. Dead");
      this.addName("Vader");
      this.addName("Frost");
      this.addName("Walker");
      this.addName("Wandering Soul");
      this.addName("Bonzo");
      this.addName("Scarf");
      this.addName("Livid");
      this.addName("Spirit Bear");
      this.registerProperty(
         new Setting[]{this.renderMode, this.showStarredMobs, this.onlyShowInCurrentRoom, this.drawBloodMobs, this.bats, this.withers, this.colours}
      );
      this.colours
         .add(
            new Setting[]{
               this.starredFill, this.starredOutline, this.bloodFill, this.bloodOutline, this.witherFill, this.witherOutline, this.batFill, this.batOutline
            }
         );
   }

   private void addName(String name) {
      this.bloodNames.add(name.hashCode());
   }

   public void onEnable() {
      this.reset();
   }

   public void reset() {
      this.starredMobs.clear();
      this.bloodMobs.clear();
      this.batMobs.clear();
      this.wither = -1;
      this.witherDistance = Double.MAX_VALUE;
   }

   @SubscribeEvent
   public void onRender3dEvent(Extract event) {
      if (mc.player != null && mc.world != null) {
         if (Location.getArea() == Island.Dungeon) {
            float partialTicks = event.getContext().tickCounter().getTickProgress(false);
            if ((Boolean)this.showStarredMobs.getValue() && !this.starredMobs.isEmpty()) {
               this.handleRender(this.starredMobs, this.getStarredOutline().getValue(), this.getStarredFill().getValue(), partialTicks);
            }

            if ((Boolean)this.drawBloodMobs.getValue() && !this.bloodMobs.isEmpty()) {
               this.handleRender(this.bloodMobs, this.getBloodOutline().getValue(), this.getBloodFill().getValue(), partialTicks);
            }

            if ((Boolean)this.bats.getValue() && !this.batMobs.isEmpty()) {
               this.handleRender(this.batMobs, this.getBatOutline().getValue(), this.getBatFill().getValue(), partialTicks);
            }

            if ((Boolean)this.withers.getValue() && this.wither != -1) {
               Entity entity = mc.world.getEntityById(this.wither);
               if (entity != null) {
                  this.renderEntityBox(entity, this.getWitherOutline().getValue(), this.getWitherFill().getValue(), partialTicks);
               } else {
                  this.wither = -1;
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onTick(Start event) {
      if (mc.world != null && mc.player != null && Location.getArea().is(Island.Dungeon)) {
         if ((float)event.getTime() % this.updateInterval == 0.0F) {
            this.updateTrackedEntities(mc.world);
         }
      }
   }

   private void updateTrackedEntities(ClientWorld level) {
      this.starredMobs.clear();
      this.bloodMobs.clear();
      this.wither = -1;
      this.witherDistance = Double.MAX_VALUE;

      for (Entity entity : level.getEntities()) {
         if ((Boolean)this.showStarredMobs.getValue() && entity instanceof ArmorStandEntity stand) {
            if (this.isValidStarredEntity(stand)) {
               Entity mob = this.getMobEntity(stand, level);
               if (mob != null) {
                  this.starredMobs.add(mob.getId());
                  stand.setCustomNameVisible(true);
                  mob.setInvisible(false);
               }
            }
         } else if ((Boolean)this.showStarredMobs.getValue() && entity instanceof PlayerEntity && !(entity instanceof ClientPlayerEntity)) {
            String name = entity.getName().getString().trim();
            if (name.hashCode() == -662331259) {
               this.starredMobs.add(entity.getId());
               entity.setInvisible(false);
            }
         } else if ((Boolean)this.showStarredMobs.getValue() && entity instanceof EndermanEntity) {
            if (entity.getName().getString().hashCode() == -1005553066) {
               entity.setInvisible(false);
            }
         } else if ((Boolean)this.drawBloodMobs.getValue() && entity instanceof PlayerEntity && !(entity instanceof ClientPlayerEntity)) {
            String name = entity.getName().getString().trim();
            if (this.bloodNames.contains(name.hashCode())) {
               this.bloodMobs.add(entity.getId());
               entity.setInvisible(false);
            }
         } else if ((Boolean)this.drawBloodMobs.getValue() && entity instanceof GiantEntity && !Dungeon.isInBoss()) {
            this.bloodMobs.add(entity.getId());
            entity.setInvisible(false);
         } else if ((Boolean)this.bats.getValue() && entity instanceof BatEntity && !entity.isInvisible()) {
            this.batMobs.add(entity.getId());
         } else if ((Boolean)this.withers.getValue() && entity instanceof WitherEntity e && !entity.isInvisible()) {
            ClientPlayerEntity Player = MinecraftClient.getInstance().player;
            if (e.getMaxHealth() != 300.0F) {
               if (this.wither == -1) {
                  this.wither = entity.getId();
               } else {
                  double dist = entity.squaredDistanceTo(Player);
                  if (dist < this.witherDistance) {
                     this.witherDistance = dist;
                     this.wither = entity.getId();
                  }
               }
            }
         }
      }
   }

   private void handleRender(Set<Integer> entityIds, Colour outlineColor, Colour fillColor, float partialTicks) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         List<Integer> toRemove = new ArrayList<>();

         for (int entityId : entityIds) {
            Entity entity = level.getEntityById(entityId);
            if (entity != null && !(entity instanceof LivingEntity living && living.isDead())) {
               this.renderEntityBox(entity, outlineColor, fillColor, partialTicks);
            } else {
               toRemove.add(entityId);
            }
         }

         toRemove.forEach(entityIds::remove);
      }
   }

   private void renderEntityBox(Entity entity, Colour outline, Colour fill, float partialTicks) {
      Vec3d interpolatedPos = entity.getLerpedPos(partialTicks);
      float width = entity.getWidth();
      float height = entity.getHeight();
      Box aabb = new Box(
         interpolatedPos.x - width / 2.0F,
         interpolatedPos.y,
         interpolatedPos.z - width / 2.0F,
         interpolatedPos.x + width / 2.0F,
         interpolatedPos.y + height,
         interpolatedPos.z + width / 2.0F
      );
      switch (this.renderMode.getIndex()) {
         case 0:
            Renderer3D.addTask(new FilledOutlineBox(aabb, fill, outline, (Boolean)this.getDepth().getValue()));
            break;
         case 1:
            Renderer3D.addTask(new FilledBox(aabb, fill, (Boolean)this.getDepth().getValue()));
            break;
         default:
            Renderer3D.addTask(new OutlineBox(aabb, outline, (Boolean)this.getDepth().getValue()));
      }
   }

   private boolean isValidStarredEntity(ArmorStandEntity entity) {
      if (!entity.hasCustomName()) {
         return false;
      } else {
         String name = StringHelper.stripTextFormat(Objects.requireNonNull(entity.getCustomName()).getString());
         return name.contains("✯ ") && name.endsWith("❤");
      }
   }

   private Entity getMobEntity(ArmorStandEntity stand, ClientWorld level) {
      Box searchBox = stand.getBoundingBox().offset(0.0, -1.0, 0.0);
      return level.getOtherEntities(stand, searchBox)
         .stream()
         .filter(e -> e instanceof LivingEntity && !(e instanceof ArmorStandEntity) && !(e instanceof ClientPlayerEntity) && (!(e instanceof WitherEntity) || !e.isInvisible()))
         .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(stand)))
         .orElse(null);
   }

   public ModeSetting getRenderMode() {
      return this.renderMode;
   }

   public BooleanSetting getShowStarredMobs() {
      return this.showStarredMobs;
   }

   public BooleanSetting getOnlyShowInCurrentRoom() {
      return this.onlyShowInCurrentRoom;
   }

   public BooleanSetting getDrawBloodMobs() {
      return this.drawBloodMobs;
   }

   public BooleanSetting getWithers() {
      return this.withers;
   }

   public BooleanSetting getBats() {
      return this.bats;
   }

   public BooleanSetting getDepth() {
      return this.depth;
   }

   public DefaultGroupSetting getColours() {
      return this.colours;
   }

   public ColourSetting getStarredFill() {
      return this.starredFill;
   }

   public ColourSetting getStarredOutline() {
      return this.starredOutline;
   }

   public ColourSetting getBloodFill() {
      return this.bloodFill;
   }

   public ColourSetting getBloodOutline() {
      return this.bloodOutline;
   }

   public ColourSetting getWitherFill() {
      return this.witherFill;
   }

   public ColourSetting getWitherOutline() {
      return this.witherOutline;
   }

   public ColourSetting getBatFill() {
      return this.batFill;
   }

   public ColourSetting getBatOutline() {
      return this.batOutline;
   }

   public Set<Integer> getStarredMobs() {
      return this.starredMobs;
   }

   public Set<Integer> getBloodMobs() {
      return this.bloodMobs;
   }

   public Set<Integer> getBatMobs() {
      return this.batMobs;
   }

   public Set<Integer> getBloodNames() {
      return this.bloodNames;
   }

   public int getWither() {
      return this.wither;
   }

   public double getWitherDistance() {
      return this.witherDistance;
   }

   public float getUpdateInterval() {
      return this.updateInterval;
   }
}
