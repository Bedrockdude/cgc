package com.ricedotwho.rsa.module.impl.dungeon.boss.p5;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.FastLeap;
import com.ricedotwho.rsa.utils.InteractUtils;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.task.TaskComponent;
import com.ricedotwho.rsm.data.DungeonClass;
import com.ricedotwho.rsm.data.DungeonPlayer;
import com.ricedotwho.rsm.data.Phase7;
import com.ricedotwho.rsm.data.Rotation;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.DefaultGroupSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ModeSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.StringSetting;
import com.ricedotwho.rsm.utils.DungeonUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import com.ricedotwho.rsm.utils.Utils;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.regex.Pattern;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.Formatting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.apache.commons.lang3.EnumUtils;

@ModuleInfo(aliases = "Relics", id = "Relics", category = Category.DUNGEONS)
public class Relics extends Module {
   private static final Pattern leapPattern = Pattern.compile("^You have teleported to (\\w+)!$");
   private final BooleanSetting aura = new BooleanSetting("Aura", false);
   private final BooleanSetting placeAura = new BooleanSetting("Place Aura", false);
   private final BooleanSetting look = new BooleanSetting("Look", false);
   private final NumberSetting auraRange = new NumberSetting("Range", 3.0, 5.0, 4.5, 0.1);
   private final NumberSetting delay = new NumberSetting("Delay", 0.0, 1000.0, 500.0, 50.0);
   private final DefaultGroupSetting leap = new DefaultGroupSetting("Leap", this);
   private final BooleanSetting leapInMenu = new BooleanSetting("Leap If In Menu", false);
   private final NumberSetting leapDelay = new NumberSetting("Leap Delay", 0.0, 20.0, 5.0, 1.0);
   private final BooleanSetting lookAfterLeap = new BooleanSetting("Look After Leap", false);
   private final ModeSetting orangePlayer = new ModeSetting("Orange", "Berserk", Arrays.asList("Archer", "Mage", "Berserk", "Healer", "Tank", "Custom"));
   private final StringSetting orangePlayerCustom = new StringSetting("Orange Custom", "", true, false, () -> this.orangePlayer.is("Custom"));
   private final ModeSetting redPlayer = new ModeSetting("Red", "Archer", Arrays.asList("Archer", "Mage", "Berserk", "Healer", "Tank", "Custom"));
   private final StringSetting redPlayerCustom = new StringSetting("Red Custom", "", true, false, () -> this.redPlayer.is("Custom"));
   private boolean walk = false;
   private long lastClick = 0L;
   private boolean leaping = false;
   private Relics.Type relic = Relics.Type.NONE;

   public Relics() {
      this.registerProperty(new Setting[]{this.aura, this.placeAura, this.look, this.auraRange, this.delay, this.leap});
      this.leap
         .add(
            new Setting[]{
               this.leapInMenu, this.lookAfterLeap, this.leapDelay, this.orangePlayer, this.orangePlayerCustom, this.redPlayer, this.redPlayerCustom
            }
         );
   }

   public void reset() {
      this.lastClick = 0L;
      this.leaping = false;
      this.walk = false;
      this.relic = Relics.Type.NONE;
   }

   @SubscribeEvent
   public void onSendPacket(Send event) {
      if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet
         && mc.player != null
         && (Boolean)this.look.getValue()
         && Location.getArea().is(Island.Dungeon)
         && DungeonUtils.isPhase(Phase7.P5)
         && Dungeon.isInBoss()
         && !this.hasRelic()) {
         try {
            Field idField = PlayerInteractEntityC2SPacket.class.getDeclaredField("entityId");
            idField.setAccessible(true);
            int id = idField.getInt(packet);
            if (!(mc.world.getEntityById(id) instanceof ArmorStandEntity stand)) {
               return;
            }

            String name = Formatting.strip(stand.getEquippedStack(EquipmentSlot.HEAD).getName().getString());
            Relics.Type type = Relics.Type.getTypeByName(name);
            if (type == Relics.Type.NONE) {
               return;
            }

            if (Utils.equalsOneOf(type, new Object[]{Relics.Type.ORANGE, Relics.Type.RED})) {
               this.doRelicLook(type);
               return;
            }

            if ((Boolean)this.leapInMenu.getValue() && mc.currentScreen != null && mc.currentScreen.getTitle().getString().equals("Spirit Leap")) {
               DungeonPlayer player = Dungeon.getClazz(this.getClassForRelic(type));
               if (player == null) {
                  RSA.chat("Failed to find player!");
                  return;
               }

               TaskComponent.onTick(((BigDecimal)this.leapDelay.getValue()).longValue(), () -> {
                  if (FastLeap.doLeapFromOpenMenu(player) && (Boolean)this.lookAfterLeap.getValue()) {
                     this.relic = type;
                     this.leaping = true;
                     TaskComponent.onTick(10L, () -> this.leaping = false);
                  }
               });
            }
         } catch (IllegalAccessException | NoSuchFieldException exception) {
            RSA.getLogger().error("Error while finding entityId!", exception);
         }
      }
   }

   @SubscribeEvent
   public void onChat(Chat event) {
      if (this.leaping && mc.player != null && (Boolean)this.lookAfterLeap.getValue() && this.relic != Relics.Type.NONE) {
         String message = Formatting.strip(event.getMessage().getString());
         if (leapPattern.matcher(message).find()) {
            this.leaping = false;
            this.doRelicLook(this.relic);
            this.relic = Relics.Type.NONE;
         }
      }
   }

   private boolean hasRelic() {
      return mc.player == null || Relics.Type.getTypeByName(mc.player.getInventory().getStack(8).getName().getString()) == Relics.Type.NONE;
   }

   private DungeonClass getClassForRelic(Relics.Type type) {
      return switch (type) {
         case GREEN -> DungeonClass.ARCHER;
         case BLUE, PURPLE -> DungeonClass.BERSERKER;
         default -> DungeonClass.NONE;
      };
   }

   private void doRelicLook(Relics.Type type) {
      if (type != Relics.Type.NONE) {
         Rotation rot = RotationUtils.getRotation(
            mc.player.getEntityPos().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0), type.place
         );
         mc.player.setPitch(rot.getPitch());
         mc.player.setYaw(rot.getYaw());
         this.walk = true;
      }
   }

   @SubscribeEvent
   public void onPollInputs(InputPollEvent event) {
      if (this.walk && Location.getArea().is(Island.Dungeon) && DungeonUtils.isPhase(Phase7.P5) && Dungeon.isInBoss()) {
         PlayerInput input = event.getClientInput();
         if (!input.forward() && !input.backward() && !input.left() && !(input.right() | input.sneak())) {
            event.getInput().apply(new PlayerInput(true, false, false, false, false, false, true));
         } else {
            this.walk = false;
            RSA.chat("Relic look cancelled");
         }
      }
   }

   @SubscribeEvent
   public void onTick(Start event) {
      if ((Boolean)this.aura.getValue()
         && Location.getArea().is(Island.Dungeon)
         && DungeonUtils.isPhase(Phase7.P5)
         && Dungeon.isInBoss()
         && mc.player != null
         && mc.world != null) {
         long now = System.currentTimeMillis();
         if (now - this.lastClick >= ((BigDecimal)this.delay.getValue()).longValue()) {
            double max = ((BigDecimal)this.auraRange.getValue()).doubleValue() * ((BigDecimal)this.auraRange.getValue()).doubleValue();
            if ((Boolean)this.placeAura.getValue()) {
               Relics.Type type = Relics.Type.getTypeByName(mc.player.getInventory().getStack(8).getName().getString());
               if (type != Relics.Type.NONE && mc.player.squaredDistanceTo(type.place) < max) {
                  SwapManager.swapSlot(8);
                  InteractUtils.interactOnBlock(BlockPos.ofFloored(type.place), true);
                  this.lastClick = now;
                  this.walk = false;
                  return;
               }
            }

            if ((Boolean)this.aura.getValue() && !this.hasRelic()) {
               Vec3d eye = mc.player.getEntityPos().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
               Box box = new Box(eye, eye).expand(4.5, 4.5, 4.5);

               for (ArmorStandEntity stand : mc.world.getNonSpectatingEntities(ArmorStandEntity.class, box)) {
                  String name = Formatting.strip(stand.getEquippedStack(EquipmentSlot.HEAD).getName().getString());
                  Relics.Type type = Relics.Type.getTypeByName(name);
                  if (type != Relics.Type.NONE) {
                     double dist = mc.player.squaredDistanceTo(stand);
                     if (!(dist > max)) {
                        InteractUtils.interactOnEntity(stand);
                        this.lastClick = now;
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   public void test(String in) {
      Relics.Type type = (Relics.Type)EnumUtils.getEnum(Relics.Type.class, in.toUpperCase(), Relics.Type.ORANGE);
      this.doRelicLook(type);
   }

   public BooleanSetting getAura() {
      return this.aura;
   }

   public BooleanSetting getPlaceAura() {
      return this.placeAura;
   }

   public BooleanSetting getLook() {
      return this.look;
   }

   public NumberSetting getAuraRange() {
      return this.auraRange;
   }

   public NumberSetting getDelay() {
      return this.delay;
   }

   public DefaultGroupSetting getLeap() {
      return this.leap;
   }

   public BooleanSetting getLeapInMenu() {
      return this.leapInMenu;
   }

   public NumberSetting getLeapDelay() {
      return this.leapDelay;
   }

   public BooleanSetting getLookAfterLeap() {
      return this.lookAfterLeap;
   }

   public ModeSetting getOrangePlayer() {
      return this.orangePlayer;
   }

   public StringSetting getOrangePlayerCustom() {
      return this.orangePlayerCustom;
   }

   public ModeSetting getRedPlayer() {
      return this.redPlayer;
   }

   public StringSetting getRedPlayerCustom() {
      return this.redPlayerCustom;
   }

   public boolean isWalk() {
      return this.walk;
   }

   public long getLastClick() {
      return this.lastClick;
   }

   public boolean isLeaping() {
      return this.leaping;
   }

   public Relics.Type getRelic() {
      return this.relic;
   }

   private static enum Type {
      RED(new Vec3d(51.5, 7.5, 42.5), new Vec3d(20.0, 6.0, 59.0)),
      ORANGE(new Vec3d(57.5, 7.5, 42.5), new Vec3d(92.0, 6.0, 56.0)),
      GREEN(new Vec3d(49.5, 7.5, 44.5), new Vec3d(20.0, 6.0, 94.0)),
      BLUE(new Vec3d(59.5, 7.5, 44.5), new Vec3d(91.0, 6.0, 94.0)),
      PURPLE(new Vec3d(54.5, 7.5, 41.5), new Vec3d(56.0, 8.0, 132.0)),
      NONE(null, null);

      public final Vec3d pickup;
      public final Vec3d place;

      private Type(Vec3d place, Vec3d pickup) {
         this.place = place;
         this.pickup = pickup;
      }

      public static Relics.Type getTypeByName(String itemName) {
         String name = Formatting.strip(itemName.toLowerCase());
         if (name.contains("corrupted") && name.contains("relic")) {
            for (Relics.Type t : values()) {
               if (name.contains(t.name().toLowerCase())) {
                  return t;
               }
            }

            return NONE;
         } else {
            return NONE;
         }
      }
   }
}
