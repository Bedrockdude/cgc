package com.ricedotwho.rsa.module.impl.render;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.utils.render.render3d.type.Beacon;
import com.ricedotwho.rsm.utils.render.render3d.type.Circle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.StringHelper;
import net.minecraft.client.network.ClientPlayerEntity;

@ModuleInfo(aliases = "PWaypoints", id = "PresetWaypoints", category = Category.RENDER)
public class PresetWaypoints extends Module {
   private final BooleanSetting AbiPhones = new BooleanSetting("AbiPhones", false, () -> true);
   private final BooleanSetting Dean = new BooleanSetting("Dean", false, () -> true);
   private final BooleanSetting Dailies = new BooleanSetting("Dailies", false, () -> true);
   private final BooleanSetting Keys = new BooleanSetting("Kuudra Keys", false, () -> true);
   private final BooleanSetting BlackSmiths = new BooleanSetting("BlackSmiths", false, () -> true);
   private final BooleanSetting MinionShops = new BooleanSetting("Minion Shops", false, () -> true);
   private final BooleanSetting ChickenCoop = new BooleanSetting("Chicken Coop", false, () -> true);
   private final BooleanSetting Dojo = new BooleanSetting("Dojo", false, () -> true);
   private final BooleanSetting Matriarch = new BooleanSetting("Matriarch", false, () -> true);
   private final BooleanSetting MiniBosses = new BooleanSetting("MiniBosses", false, () -> true);
   private final BooleanSetting Duels = new BooleanSetting("Duels", false, () -> true);
   private final BooleanSetting BaarBerserkers = new BooleanSetting("Baar Npc (+250 rep)", false, () -> true);
   Pos AbiphoneMage = new Pos(-78.5, 107.0, -791.5);
   Pos AbiphoneHub = new Pos(66.5, 72.0, -63.5);
   Pos BeerLocation = new Pos(-637.5, 123.0, -792.0);
   Pos DeanLocation = new Pos(-16.5, 123.0, -882.5);
   Pos DailiesLocationMage = new Pos(-124.5, 92.0, -754.5);
   Pos DailiesLocationBers = new Pos(-579.5, 100.0, -687.5);
   Pos KeyLocationMage = new Pos(-132.5, 89.0, -721.5);
   Pos KeyLocationBers = new Pos(-581.5, 99.0, -711.5);
   Pos BersBlacksmith = new Pos(-548.5, 98.0, -707.5);
   Pos MageBlacksmith = new Pos(-81.5, 92.0, -734.5);
   Pos BersMinionShop = new Pos(-645.5, 101.0, -825.5);
   Pos MageMinionShop = new Pos(-45.5, 107.0, -779.5);
   Pos BersMiniBoss = new Pos(-535.5, 117.0, -904.5);
   Pos MageMiniBoss = new Pos(-180.5, 105.0, -859.5);
   Pos Ashfang = new Pos(-485.5, 135.0, -1016.5);
   Pos MagmaBoss = new Pos(-367.5, 63.0, -792.5);
   Pos BladeSoul = new Pos(-296.5, 82.0, -517.5);
   Pos BersDuels = new Pos(-597.5, 113.0, -638.5);
   Pos MageDuels = new Pos(149.5, 106.0, -852.5);
   Pos DojoLocation = new Pos(-235.5, 108.0, -597.5);
   Pos ChickenCoopLocation = new Pos(-32.5, 93.0, -816.5);
   Pos MatriarchLocation = new Pos(-531.5, 40.0, -889.5);

   public PresetWaypoints() {
      this.registerProperty(
         new Setting[]{
            this.BlackSmiths,
            this.MinionShops,
            this.ChickenCoop,
            this.AbiPhones,
            this.Dean,
            this.Dailies,
            this.Keys,
            this.Dojo,
            this.Matriarch,
            this.MiniBosses,
            this.Duels,
            this.BaarBerserkers
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
   public void onChat(Chat event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         String unformatted = StringHelper.stripTextFormat(event.getMessage().getString());
         if ((Boolean)this.BaarBerserkers.getValue()) {
            if (unformatted.contains("Hello there, adventurer!")) {
               RSA.chat("Hello BAAR!!");
            }

            if (unformatted.contains("To start out I'll need some generic gold to experiment on, could you get me a stack?")) {
               RSA.chat("Baar: 64x Gold ingots.");
            }

            if (unformatted.contains("Next I need some flat gold to test how reflective gold is, could you try forging gold into 5 gold plates?")) {
               RSA.chat("Baar: 5x Golden Plates. (/bz Golden plate)");
            }

            if (unformatted.contains(
               "I heard there exist golden boots that helps you swim faster in water. I would like to test their magical properties, could you bring me them?"
            )) {
               RSA.chat("Baar: Divers Boots. (/ahs Diver's Boots)");
            }

            if (unformatted.contains("It seems like the first piece you brought me is only 25% of the magical power, could you get me the chestplate?")) {
               RSA.chat("Baar: Divers Shirt. (/ahs Diver's Shirt)");
            }

            if (unformatted.contains("Now I need a lot of compacted gold, it has to be extremely dense. A half stack should do.")) {
               RSA.chat("Baar: 32x Enchanted Gold Block. (/bz Enchanted Gold Block)");
            }

            if (unformatted.contains("There is a fine-grained gold substance somewhere in the Hub, I'll need 5 of that.")) {
               RSA.chat("Baar: 5x Golden Powder. (/bz Golden Powder)");
            }

            if (unformatted.contains(
               "Next I'm going to need a vegetable that is made out of solid gold. I want to experiment with how gold interacts with organics, maybe you can find some, like a half stack?"
            )) {
               RSA.chat("Baar: 32x Enchanted Golden Carrot (/bz Enchanted Golden Carrot)");
            }

            if (unformatted.contains(
               "I just need one last thing, there's an extremely dangerous scientist who sells an assortment of items, he has a special rounded type of gold. Try to convince him to sell you it."
            )) {
               RSA.chat("Baar: 1x Golden Ball (/bz Golden Ball)");
            }

            if (unformatted.contains("As promised, here is your reward.")) {
               RSA.chat("yw for the help :)");
            }
         }
      }
   }

   @SubscribeEvent
   public void onRender3D(Extract event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if ((Boolean)this.AbiPhones.getValue()) {
            if (Location.getArea() == Island.CrimsonIsle) {
               Renderer3D.addTask(new Beacon(this.AbiphoneMage, Colour.MAGENTA));
               Renderer3D.addTask(new Circle(this.AbiphoneMage, false, 0.5F, Colour.MAGENTA, 12));
            }

            if (Location.getArea() == Island.Hub) {
               Renderer3D.addTask(new Beacon(this.AbiphoneHub, Colour.MAGENTA));
               Renderer3D.addTask(new Circle(this.AbiphoneHub, false, 0.5F, Colour.MAGENTA, 12));
            }
         }

         if ((Boolean)this.BaarBerserkers.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.BeerLocation, Colour.RED));
            Renderer3D.addTask(new Circle(this.BeerLocation, false, 0.5F, Colour.RED, 12));
         }

         if ((Boolean)this.Dean.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.DeanLocation, Colour.PINK));
            Renderer3D.addTask(new Circle(this.DeanLocation, false, 0.5F, Colour.PINK, 12));
         }

         if ((Boolean)this.Dailies.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.DailiesLocationMage, Colour.YELLOW));
            Renderer3D.addTask(new Circle(this.DailiesLocationMage, false, 0.5F, Colour.YELLOW, 12));
            Renderer3D.addTask(new Beacon(this.DailiesLocationBers, Colour.YELLOW));
            Renderer3D.addTask(new Circle(this.DailiesLocationBers, false, 0.5F, Colour.YELLOW, 12));
         }

         if ((Boolean)this.Keys.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.KeyLocationMage, Colour.ORANGE));
            Renderer3D.addTask(new Circle(this.KeyLocationMage, false, 0.5F, Colour.ORANGE, 12));
            Renderer3D.addTask(new Beacon(this.KeyLocationBers, Colour.ORANGE));
            Renderer3D.addTask(new Circle(this.KeyLocationBers, false, 0.5F, Colour.ORANGE, 12));
         }

         if ((Boolean)this.BlackSmiths.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.BersBlacksmith, Colour.GRAY));
            Renderer3D.addTask(new Circle(this.BersBlacksmith, false, 0.5F, Colour.GRAY, 12));
            Renderer3D.addTask(new Beacon(this.MageBlacksmith, Colour.GRAY));
            Renderer3D.addTask(new Circle(this.MageBlacksmith, false, 0.5F, Colour.GRAY, 12));
         }

         if ((Boolean)this.MinionShops.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.BersMinionShop, Colour.GREEN));
            Renderer3D.addTask(new Circle(this.BersMinionShop, false, 0.5F, Colour.GREEN, 12));
            Renderer3D.addTask(new Beacon(this.MageMinionShop, Colour.GREEN));
            Renderer3D.addTask(new Circle(this.MageMinionShop, false, 0.5F, Colour.GREEN, 12));
         }

         if ((Boolean)this.MiniBosses.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.BersMiniBoss, Colour.RED));
            Renderer3D.addTask(new Circle(this.BersMiniBoss, false, 0.5F, Colour.RED, 12));
            Renderer3D.addTask(new Beacon(this.MageMiniBoss, Colour.RED));
            Renderer3D.addTask(new Circle(this.MageMiniBoss, false, 0.5F, Colour.RED, 12));
            Renderer3D.addTask(new Beacon(this.Ashfang, Colour.RED));
            Renderer3D.addTask(new Circle(this.Ashfang, false, 0.5F, Colour.RED, 12));
            Renderer3D.addTask(new Beacon(this.MagmaBoss, Colour.RED));
            Renderer3D.addTask(new Circle(this.MagmaBoss, false, 0.5F, Colour.RED, 12));
            Renderer3D.addTask(new Beacon(this.BladeSoul, Colour.RED));
            Renderer3D.addTask(new Circle(this.BladeSoul, false, 0.5F, Colour.RED, 12));
         }

         if ((Boolean)this.Duels.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.BersDuels, Colour.BLUE));
            Renderer3D.addTask(new Circle(this.BersDuels, false, 0.5F, Colour.BLUE, 12));
            Renderer3D.addTask(new Beacon(this.MageDuels, Colour.BLUE));
            Renderer3D.addTask(new Circle(this.MageDuels, false, 0.5F, Colour.BLUE, 12));
         }

         if ((Boolean)this.Dojo.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.DojoLocation, Colour.CYAN));
            Renderer3D.addTask(new Circle(this.DojoLocation, false, 0.5F, Colour.CYAN, 12));
         }

         if ((Boolean)this.ChickenCoop.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.ChickenCoopLocation, Colour.WHITE));
            Renderer3D.addTask(new Circle(this.ChickenCoopLocation, false, 0.5F, Colour.WHITE, 12));
         }

         if ((Boolean)this.Matriarch.getValue() && Location.getArea() == Island.CrimsonIsle) {
            Renderer3D.addTask(new Beacon(this.MatriarchLocation, Colour.ORANGE));
            Renderer3D.addTask(new Circle(this.MatriarchLocation, false, 0.5F, Colour.ORANGE, 12));
         }
      }
   }

   public BooleanSetting getAbiPhones() {
      return this.AbiPhones;
   }

   public BooleanSetting getDean() {
      return this.Dean;
   }

   public BooleanSetting getDailies() {
      return this.Dailies;
   }

   public BooleanSetting getKeys() {
      return this.Keys;
   }

   public BooleanSetting getBlackSmiths() {
      return this.BlackSmiths;
   }

   public BooleanSetting getMinionShops() {
      return this.MinionShops;
   }

   public BooleanSetting getChickenCoop() {
      return this.ChickenCoop;
   }

   public BooleanSetting getDojo() {
      return this.Dojo;
   }

   public BooleanSetting getMatriarch() {
      return this.Matriarch;
   }

   public BooleanSetting getMiniBosses() {
      return this.MiniBosses;
   }

   public BooleanSetting getDuels() {
      return this.Duels;
   }

   public BooleanSetting getBaarBerserkers() {
      return this.BaarBerserkers;
   }

   public Pos getAbiphoneMage() {
      return this.AbiphoneMage;
   }

   public Pos getAbiphoneHub() {
      return this.AbiphoneHub;
   }

   public Pos getBeerLocation() {
      return this.BeerLocation;
   }

   public Pos getDeanLocation() {
      return this.DeanLocation;
   }

   public Pos getDailiesLocationMage() {
      return this.DailiesLocationMage;
   }

   public Pos getDailiesLocationBers() {
      return this.DailiesLocationBers;
   }

   public Pos getKeyLocationMage() {
      return this.KeyLocationMage;
   }

   public Pos getKeyLocationBers() {
      return this.KeyLocationBers;
   }

   public Pos getBersBlacksmith() {
      return this.BersBlacksmith;
   }

   public Pos getMageBlacksmith() {
      return this.MageBlacksmith;
   }

   public Pos getBersMinionShop() {
      return this.BersMinionShop;
   }

   public Pos getMageMinionShop() {
      return this.MageMinionShop;
   }

   public Pos getBersMiniBoss() {
      return this.BersMiniBoss;
   }

   public Pos getMageMiniBoss() {
      return this.MageMiniBoss;
   }

   public Pos getAshfang() {
      return this.Ashfang;
   }

   public Pos getMagmaBoss() {
      return this.MagmaBoss;
   }

   public Pos getBladeSoul() {
      return this.BladeSoul;
   }

   public Pos getBersDuels() {
      return this.BersDuels;
   }

   public Pos getMageDuels() {
      return this.MageDuels;
   }

   public Pos getDojoLocation() {
      return this.DojoLocation;
   }

   public Pos getChickenCoopLocation() {
      return this.ChickenCoopLocation;
   }

   public Pos getMatriarchLocation() {
      return this.MatriarchLocation;
   }
}
