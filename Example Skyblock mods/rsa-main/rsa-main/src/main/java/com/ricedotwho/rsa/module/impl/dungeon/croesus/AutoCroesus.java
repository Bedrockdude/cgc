package com.ricedotwho.rsa.module.impl.dungeon.croesus;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.utils.InteractUtils;
import com.ricedotwho.rsm.component.impl.location.Floor;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.task.TaskComponent;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.game.GuiEvent.Loaded;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.MultiBoolSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.ItemUtils;
import com.ricedotwho.rsm.utils.NumberUtils;
import com.ricedotwho.rsm.utils.Utils;
import com.ricedotwho.rsm.utils.api.PriceData;
import com.ricedotwho.rsm.utils.api.PriceData.Price;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Formatting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.text.TextColor;
import net.minecraft.util.hit.HitResult.Type;

@ModuleInfo(aliases = "Auto Croesus", id = "AutoCroesus", category = Category.DUNGEONS)
public class AutoCroesus extends Module {
   private final NumberSetting clickDelay = new NumberSetting("Click Delay", 100.0, 1000.0, 300.0, 25.0);
   private final BooleanSetting chestKeys = new BooleanSetting("Use chest keys", false);
   private final NumberSetting chestKeyMinProfit = new NumberSetting("Key min Profit", 0.0, 2.0, 0.5, 0.01, "m");
   private final BooleanSetting kismets = new BooleanSetting("Use Kismet", false);
   private final NumberSetting kismetsMinProfit = new NumberSetting("Kismet min Profit", 1.0, 3.5, 2.0, 0.05, "m");
   private final MultiBoolSetting kismetFloors = new MultiBoolSetting(
      "Kismet Floors", List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7"), new ArrayList()
   );
   private final Pattern costPattern = Pattern.compile("^([\\d,]+) Coins$");
   private final Pattern bookPattern = Pattern.compile("^(?:§.)*Enchanted Book \\((§d§l)?([\\w ]+) (\\w+)(?:§.)*\\)$");
   private final Pattern essencePattern = Pattern.compile("^§d(\\w+) Essence §8x(\\d+)$");
   private final Map<String, String> ITEM_REPLACEMENTS = new HashMap<>();
   private static final double AURA_RANGE = 3.5;
   private static final TextColor ULT_COLOUR = TextColor.fromFormatting(Formatting.LIGHT_PURPLE);
   private boolean running = false;
   private AutoCroesus.Action action = AutoCroesus.Action.IDLE;
   private boolean kismetting = false;
   private int currentPage = 1;

   public AutoCroesus() {
      this.registerProperty(new Setting[]{this.clickDelay, this.chestKeys, this.chestKeyMinProfit, this.kismets, this.kismetsMinProfit, this.kismetFloors});
      this.ITEM_REPLACEMENTS.put("Shiny Wither Boots", "WITHER_BOOTS");
      this.ITEM_REPLACEMENTS.put("Shiny Wither Leggings", "WITHER_LEGGINGS");
      this.ITEM_REPLACEMENTS.put("Shiny Wither Chestplate", "WITHER_CHESTPLATE");
      this.ITEM_REPLACEMENTS.put("Shiny Wither Helmet", "WITHER_HELMET");
      this.ITEM_REPLACEMENTS.put("Shiny Necron's Handle", "NECRON_HANDLE");
      this.ITEM_REPLACEMENTS.put("Wither Shard", "SHARD_WITHER");
      this.ITEM_REPLACEMENTS.put("Thorn Shard", "SHARD_THORN");
      this.ITEM_REPLACEMENTS.put("Apex Dragon Shard", "SHARD_APEX_DRAGON");
      this.ITEM_REPLACEMENTS.put("Power Dragon Shard", "SHARD_POWER_DRAGON");
      this.ITEM_REPLACEMENTS.put("Scarf Shard", "SHARD_SCARF");
      this.ITEM_REPLACEMENTS.put("Necron Dye", "DYE_NECRON");
      this.ITEM_REPLACEMENTS.put("Livid Dye", "DYE_LIVID");
      CroesusLoader.load();
   }

   public void reset() {
      this.running = false;
      this.action = AutoCroesus.Action.IDLE;
      this.kismetting = false;
      this.currentPage = 1;
   }

   @SubscribeEvent
   public void onUnload(Load event) {
      if (!this.action.equals(AutoCroesus.Action.IDLE)) {
         modMessage("Stopping!");
      }

      this.reset();
   }

   public static void modMessage(String text) {
      RSA.chat(Formatting.YELLOW + "AutoCroesus » " + Formatting.RESET + text);
   }

   public void start() {
      this.start(true);
   }

   public void start(boolean checkPrice) {
      if (!this.isEnabled()) {
         modMessage("Module is not Enabled!");
      } else if (!Location.getArea().is(Island.DungeonHub)) {
         modMessage("You are not in the dungeon hub!");
      } else if (this.running) {
         modMessage("Already claiming!");
      } else if (checkPrice && System.currentTimeMillis() - PriceData.getLastFetched() > 1800000L) {
         modMessage("Updating price data from the API...");
         PriceData.updatePrices(this::start);
      } else {
         this.running = true;
         this.action = AutoCroesus.Action.CROESUS;
         if (!this.clickCroesus()) {
            this.running = false;
            modMessage("Failed to click Croesus!");
            this.reset();
         }
      }
   }

   private boolean clickCroesus() {
      if (this.action != AutoCroesus.Action.CROESUS) {
         return false;
      } else {
         PlayerEntity entity = this.findCroesus();
         if (entity == null) {
            modMessage("No croesus entity returned!");
            return false;
         } else {
            double dist = entity.squaredDistanceTo(mc.player);
            if (dist > 16.0) {
               modMessage("Croesus too far! " + dist);
               return false;
            } else if (MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult entityHitResult && entityHitResult.getType() != Type.MISS) {
               Entity e = entityHitResult.getEntity();
               if (entity.squaredDistanceTo(e) > 9.0) {
                  RSA.chat(Formatting.RED + "Blocked by entity!");
                  return false;
               } else {
                  PacketOrderManager.register(PacketOrderManager.STATE.ATTACK, () -> InteractUtils.attackEntity(entity));
                  return true;
               }
            } else {
               RSA.chat(Formatting.RED + "Not looking at an entity");
               return false;
            }
         }
      }
   }

   private PlayerEntity findCroesus() {
      Vec3d eyePos = mc.player.getEntityPos().add(0.0, mc.player.getStandingEyeHeight(), 0.0);
      Box box = new Box(eyePos, eyePos).expand(3.5, 3.5, 3.5);
      List<ArmorStandEntity> stands = mc.world.getEntitiesByClass(ArmorStandEntity.class, box, e -> e.getDisplayName().getString().contains("Croesus"));
      if (stands.isEmpty()) {
         modMessage("Failed to find an entity named Croesus!");
         return null;
      } else if (stands.size() > 1) {
         modMessage("found mode than one croesus stand??");
         return null;
      } else {
         ArmorStandEntity stand = stands.getFirst();
         mc.world.getEntitiesByClass(PlayerEntity.class, box, e -> e.squaredDistanceTo(stand) == 0.0);
         List<PlayerEntity> list = mc.world.getEntitiesByClass(PlayerEntity.class, box, e -> e.squaredDistanceTo(stand) == 0.0);
         if (list.isEmpty()) {
            modMessage("no croesus?");
            return null;
         } else if (list.size() > 1) {
            modMessage("Found multiple croesus?");
            return null;
         } else {
            return list.getFirst();
         }
      }
   }

   @SubscribeEvent
   public void onGuiOpen(Loaded event) {
      if (mc.player != null
         && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu
         && this.running
         && this.action == AutoCroesus.Action.CROESUS
         && mc.currentScreen != null
         && mc.currentScreen.getTitle().getString().equals("Croesus")
         && Location.getArea().is(Island.DungeonHub)) {
         this.currentPage = this.getPage(menu.slots);

         for (Slot slot : menu.slots) {
            ItemStack stack = slot.getStack();
            if (stack.getItem().equals(Items.PLAYER_HEAD)) {
               AutoCroesus.RunType type = AutoCroesus.RunType.findByDisplayName(stack.getName().getString());
               if (type != AutoCroesus.RunType.NONE && !ItemUtils.getCleanLore(stack).stream().noneMatch(s -> s.contains("No chests opened yet!"))) {
                  TaskComponent.onMilli(((BigDecimal)this.getClickDelay().getValue()).longValue(), () -> {
                     this.action = AutoCroesus.Action.REWARDS;
                     this.click(slot.id, this.inCroesus());
                  });
                  return;
               }
            }
         }

         ItemStack nextArrow = ((Slot)menu.slots.get(53)).getStack();
         if (nextArrow.getItem() == Items.ARROW) {
            this.clickOnDelay(53, this::inCroesus);
         } else {
            modMessage("All chests looted!");
            this.reset();
            this.close();
         }
      }
   }

   @SubscribeEvent
   public void onRewards(Loaded event) {
      if (mc.player != null
         && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu
         && this.running
         && this.action == AutoCroesus.Action.REWARDS
         && mc.currentScreen != null
         && Location.getArea().is(Island.DungeonHub)) {
         String title = mc.currentScreen.getTitle().getString();
         AutoCroesus.RunType type = AutoCroesus.RunType.findByTitle(title);
         if (type != AutoCroesus.RunType.NONE) {
            Floor floor = Floor.findByIndex(NumberUtils.convertRomanToArabic(title.split("- Floor")[1].trim()));
            if (type == AutoCroesus.RunType.MASTER_CATACOMBS) {
               floor = Floor.findByIndex(floor.getIndex() + 7);
            }

            List<AutoCroesus.Reward> chests = new ArrayList<>();

            for (Slot slot : menu.slots) {
               if (menu.slots.indexOf(slot) > 45) {
                  break;
               }

               ItemStack stack = slot.getStack();
               if (stack.getItem().equals(Items.PLAYER_HEAD)) {
                  List<Text> components = ItemUtils.getLore(stack);
                  List<String> lore = ItemUtils.getCleanLore(stack);
                  AutoCroesus.ChestType chestType = (AutoCroesus.ChestType)Utils.findEnumByName(
                     AutoCroesus.ChestType.class, Formatting.strip(stack.getName().getString()), AutoCroesus.ChestType.NONE
                  );
                  if (chestType != AutoCroesus.ChestType.NONE) {
                     int costLine = lore.indexOf("Cost");
                     AutoCroesus.Reward chest = this.getRewards(lore.subList(1, costLine - 1), lore.get(costLine + 1), components.subList(1, costLine - 1));
                     if (chest == null) {
                        return;
                     }

                     chest.slot = slot.id;
                     chest.name = stack.getName().getString();
                     chest.chest.type = chestType;
                     chests.add(chest);
                  }
               }
            }

            Optional<AutoCroesus.Reward> bedrock = chests.stream().filter(c -> c.chest.type == AutoCroesus.ChestType.BEDROCK).findFirst();
            Optional<AutoCroesus.Reward> alwaysBuy = chests.stream().filter(c -> c.alwaysBuy).findFirst();
            ItemStack modifiers = ((Slot)menu.slots.get(32)).getStack();
            List<Text> modiLore = ItemUtils.getLore(modifiers);
            Optional<Text> kismetLine = modiLore.stream().filter(s -> s.getString().contains("Kismet Feather")).findAny();
            boolean canKismet = kismetLine.isPresent()
               && kismetLine.get().getSiblings().size() > 1
               && !((Text)kismetLine.get().getSiblings().get(1)).getStyle().isStrikethrough();
            if (bedrock.isPresent()
               && (Boolean)this.getKismets().getValue()
               && canKismet
               && this.getKismetFloors().get(floor.getName())
               && bedrock.get().chest.profit < ((BigDecimal)this.getKismetsMinProfit().getValue()).floatValue() * 1000.0F) {
               this.kismetting = true;
               this.action = AutoCroesus.Action.CHEST;
               if (bedrock.get().slot >= 0 && bedrock.get().slot <= 45) {
                  this.clickOnDelay(bedrock.get().slot, this::inRewards);
               } else {
                  modMessage(Formatting.DARK_RED + "Invalid slot! (" + bedrock.get().slot + ")");
                  this.reset();
               }
            } else {
               AutoCroesus.Reward best = alwaysBuy.orElseGet(() -> this.getBestProfit(chests));
               if (best.slot >= 0 && best.slot <= 45) {
                  modMessage("Claiming the " + best.name + Formatting.RESET + " chest, Profit: " + best.chest.profit);
                  this.action = AutoCroesus.Action.CHEST;
                  this.clickOnDelay(best.slot, this::inRewards);
                  CroesusLoader.addRunLog(best.chest);
               } else {
                  modMessage(Formatting.DARK_RED + "Invalid slot! (" + best.slot + ")");
                  this.reset();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onChest(Loaded event) {
      if (mc.player != null
         && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu
         && this.running
         && this.action == AutoCroesus.Action.CHEST
         && mc.currentScreen != null
         && Location.getArea().is(Island.DungeonHub)) {
         String title = mc.currentScreen.getTitle().getString();
         AutoCroesus.ChestType chestType = (AutoCroesus.ChestType)Utils.findEnumByName(
            AutoCroesus.ChestType.class, Formatting.strip(title.split(" ")[0]), AutoCroesus.ChestType.NONE
         );
         if (chestType != AutoCroesus.ChestType.NONE) {
            if (this.kismetting) {
               this.kismetting = false;
               ItemStack kismetStack = ((Slot)menu.slots.get(50)).getStack();
               if (ItemUtils.getCleanLore(kismetStack).stream().anyMatch(s -> s.contains("Bring a Kismet Feather"))) {
                  modMessage(Formatting.RED + "No kismets!");
                  this.reset();
                  this.close();
               } else {
                  this.action = AutoCroesus.Action.REWARDS;
                  this.clickOnDelay(50, this::inChest);
               }
            } else {
               this.clickOnDelay(31, this::inChest);
               this.action = AutoCroesus.Action.CROESUS;
               TaskComponent.onMilli(((BigDecimal)this.getClickDelay().getValue()).longValue() * 2L, () -> TaskComponent.onTick(0L, this::clickCroesus));
            }
         }
      }
   }

   @SubscribeEvent
   public void onClose(Send event) {
      if (event.getPacket() instanceof CloseHandledScreenC2SPacket && Location.getArea().is(Island.DungeonHub)) {
         if (!this.action.equals(AutoCroesus.Action.IDLE)) {
            modMessage("Stopped!");
            this.reset();
         }
      }
   }

   private boolean inCroesus() {
      return mc.currentScreen != null && mc.currentScreen.getTitle().getString().equals("Croesus");
   }

   private boolean inRewards() {
      if (mc.currentScreen == null) {
         return false;
      } else {
         AutoCroesus.RunType type = AutoCroesus.RunType.findByTitle(mc.currentScreen.getTitle().getString());
         return type != AutoCroesus.RunType.NONE;
      }
   }

   private boolean inChest() {
      if (mc.currentScreen == null) {
         return false;
      } else {
         String title = Formatting.strip(mc.currentScreen.getTitle().getString());
         return Utils.findEnumByName(AutoCroesus.ChestType.class, title.split(" ")[0].trim(), AutoCroesus.ChestType.NONE) != AutoCroesus.ChestType.NONE;
      }
   }

   private void close() {
      TaskComponent.onTick(0L, () -> {
         if (mc.player != null) {
            mc.player.closeHandledScreen();
         }
      });
   }

   private int getPage(DefaultedList<Slot> inv) {
      ItemStack nextArrow = ((Slot)inv.get(53)).getStack();
      ItemStack lastArrow = ((Slot)inv.get(45)).getStack();
      if (nextArrow.getItem() == Items.ARROW) {
         String line = Formatting.strip((String)ItemUtils.getCleanLore(nextArrow).getFirst());
         return Integer.parseInt(line.split(" ")[1]) - 1;
      } else if (lastArrow.getItem() == Items.ARROW) {
         String line = Formatting.strip((String)ItemUtils.getCleanLore(lastArrow).getFirst());
         return Integer.parseInt(line.split(" ")[1]) + 1;
      } else {
         return 1;
      }
   }

   private void click(int slot, boolean inWindow) {
      if (mc.player != null && mc.interactionManager != null && inWindow) {
         int wid = mc.player.currentScreenHandler.syncId;
         if (wid >= 0 && wid <= 100) {
            mc.interactionManager.clickSlot(wid, slot, 0, SlotActionType.PICKUP, mc.player);
         }
      }
   }

   private void clickOnDelay(int slot, BooleanSupplier supplier) {
      TaskComponent.onMilli(((BigDecimal)this.getClickDelay().getValue()).longValue(), () -> this.click(slot, supplier.getAsBoolean()));
   }

   private AutoCroesus.Reward getBestProfit(List<AutoCroesus.Reward> rewards, AutoCroesus.Reward... excluding) {
      AutoCroesus.Reward best = null;

      for (AutoCroesus.Reward reward : rewards) {
         boolean skip = false;

         for (AutoCroesus.Reward r : excluding) {
            if (r.equals(reward)) {
               skip = true;
               break;
            }
         }

         if (!skip) {
            if (best == null) {
               best = reward;
            } else if (reward.chest.profit > best.chest.profit) {
               best = reward;
            }
         }
      }

      return best;
   }

   private AutoCroesus.Reward getRewards(List<String> itemLines, String cost, List<Text> components) {
      AutoCroesus.ChestInfo chestInfo = new AutoCroesus.ChestInfo();
      if (!cost.equals("§aFREE")) {
         Matcher matcher = this.costPattern.matcher(Formatting.strip(cost));
         if (matcher.find()) {
            chestInfo.cost = Integer.parseInt(matcher.group(1).replace(",", ""));
         }
      }

      boolean alwaysBuy = false;

      for (int i = 0; i < itemLines.size(); i++) {
         String itemLine = itemLines.get(i);
         Text component = components.get(i);
         AutoCroesus.ChestItem item = this.parseItem(itemLine, component);
         if (item != null) {
            double price = this.getSellPrice(item.getId(), true);
            if (price == -1.0) {
               modMessage(Formatting.DARK_RED + "Failed to get a price! Exiting early");
               return null;
            }

            chestInfo.value = chestInfo.value + price * item.getQuantity();
            chestInfo.items.add(item);
            if (CroesusLoader.getAlwaysBuy().contains(item.getId())) {
               alwaysBuy = true;
            }
         }
      }

      chestInfo.profit = chestInfo.value - chestInfo.cost;
      return new AutoCroesus.Reward(chestInfo, alwaysBuy);
   }

   private double getSellPrice(String sbId, boolean sellOrder) {
      if (CroesusLoader.getWorthless().contains(sbId)) {
         return 0.0;
      } else if (PriceData.getBazaarCache().containsKey(sbId)) {
         Price price = (Price)PriceData.getBazaarCache().get(sbId);
         return sellOrder ? price.order() : price.instant();
      } else if (PriceData.getBinCache().containsKey(sbId)) {
         return (Double)PriceData.getBinCache().get(sbId);
      } else {
         modMessage(
            Formatting.RED
               + "Failed to get price for "
               + sbId
               + "! ("
               + PriceData.getBazaarCache().size()
               + " items in bazaar, "
               + PriceData.getBinCache().size()
               + " items in bin)"
         );
         return 0.0;
      }
   }

   private AutoCroesus.ChestItem parseItem(String item, Text component) {
      if (item.contains("Enchanted Book")) {
         Matcher matcher = this.bookPattern.matcher(item);
         if (!matcher.find()) {
            return null;
         } else {
            boolean ult = false;
            if (component.getSiblings().size() > 1) {
               Text comp = (Text)component.getSiblings().get(1);
               ult = comp.getStyle().isBold() && comp.getStyle().getColor() != null && comp.getStyle().getColor().equals(ULT_COLOUR);
            }

            String bookName = matcher.group(2);
            String levelNumeral = matcher.group(3);
            int tier;
            if (!NumberUtils.isInteger(levelNumeral)) {
               tier = NumberUtils.convertRomanToArabic(levelNumeral);
            } else {
               tier = Integer.parseInt(levelNumeral);
            }

            String id = ("ENCHANTMENT_" + (ult ? "ULTIMATE_" : "") + bookName.toUpperCase().replace(" ", "_") + "_" + tier)
               .replace("ULTIMATE_ULTIMATE_", "ULTIMATE_");
            return new AutoCroesus.ChestItem(id, 1);
         }
      } else if (item.contains(" Essence ")) {
         Matcher matcher = this.essencePattern.matcher(item);
         if (!matcher.find()) {
            return null;
         } else {
            String type = matcher.group(1);
            String amount = matcher.group(2);
            return new AutoCroesus.ChestItem("ESSENCE_" + type.toUpperCase(), Integer.parseInt(amount));
         }
      } else {
         String ite = Formatting.strip(item);
         if (this.ITEM_REPLACEMENTS.containsKey(ite)) {
            return new AutoCroesus.ChestItem(this.ITEM_REPLACEMENTS.get(ite), 1);
         } else {
            Map<String, String> items = PriceData.getItemCache();

            for (Entry<String, String> entry : items.entrySet()) {
               if (Objects.equals(entry.getValue(), ite) && !entry.getKey().startsWith("STARRED_")) {
                  return new AutoCroesus.ChestItem(entry.getKey(), 1);
               }
            }

            modMessage("Failed to find id for " + ite);
            return null;
         }
      }
   }

   public NumberSetting getClickDelay() {
      return this.clickDelay;
   }

   public BooleanSetting getChestKeys() {
      return this.chestKeys;
   }

   public NumberSetting getChestKeyMinProfit() {
      return this.chestKeyMinProfit;
   }

   public BooleanSetting getKismets() {
      return this.kismets;
   }

   public NumberSetting getKismetsMinProfit() {
      return this.kismetsMinProfit;
   }

   public MultiBoolSetting getKismetFloors() {
      return this.kismetFloors;
   }

   public Pattern getCostPattern() {
      return this.costPattern;
   }

   public Pattern getBookPattern() {
      return this.bookPattern;
   }

   public Pattern getEssencePattern() {
      return this.essencePattern;
   }

   public Map<String, String> getITEM_REPLACEMENTS() {
      return this.ITEM_REPLACEMENTS;
   }

   public boolean isRunning() {
      return this.running;
   }

   public AutoCroesus.Action getAction() {
      return this.action;
   }

   public boolean isKismetting() {
      return this.kismetting;
   }

   public int getCurrentPage() {
      return this.currentPage;
   }

   private static enum Action {
      CROESUS,
      REWARDS,
      CHEST,
      IDLE;
   }

   public static class ChestInfo {
      public AutoCroesus.ChestType type = AutoCroesus.ChestType.NONE;
      public double cost = 0.0;
      public transient double value = 0.0;
      public transient double profit = 0.0;
      public final List<AutoCroesus.ChestItem> items = new ArrayList<>();
   }

   public static class ChestItem {
      private final String id;
      private final int quantity;

      public ChestItem(String id, int quantity) {
         this.id = id;
         this.quantity = quantity;
      }

      public String getId() {
         return this.id;
      }

      public int getQuantity() {
         return this.quantity;
      }
   }

   public static enum ChestType {
      BEDROCK,
      OBSIDIAN,
      DIAMOND,
      GOLD,
      WOOD,
      NONE;
   }

   private static class Reward {
      public final AutoCroesus.ChestInfo chest;
      private int slot;
      private String name;
      private final boolean alwaysBuy;

      public Reward(AutoCroesus.ChestInfo chest, boolean alwaysBuy) {
         this.chest = chest;
         this.alwaysBuy = alwaysBuy;
         this.slot = -1;
         this.name = "unknown";
      }
   }

   private static enum RunType {
      MASTER_CATACOMBS("Master Mode The Catacombs", "Master Catacombs - Floor "),
      CATACOMBS("The Catacombs", "Catacombs - Floor "),
      KUUDRA("?", "?"),
      NONE("None", "None");

      private final String displayName;
      private final String rewardsTitle;

      private RunType(String displayName, String rewardsTitle) {
         this.displayName = displayName;
         this.rewardsTitle = rewardsTitle;
      }

      public static AutoCroesus.RunType findByDisplayName(String nameFormatted) {
         String name = Formatting.strip(nameFormatted);
         return Arrays.stream(values()).filter(type -> name.equals(type.getDisplayName())).findFirst().orElse(NONE);
      }

      public static AutoCroesus.RunType findByTitle(String title) {
         String name = Formatting.strip(title);
         return Arrays.stream(values()).filter(type -> name.startsWith(type.getRewardsTitle())).findFirst().orElse(NONE);
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public String getRewardsTitle() {
         return this.rewardsTitle;
      }
   }
}
