package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.pathfinding.Goal;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalDungeonXYZ;
import com.ricedotwho.rsa.screen.DungeonRouteMapScreen;
import com.ricedotwho.rsa.module.impl.dungeon.DynamicRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitClick;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitSecrets;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BatNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BreakNode;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.RoomData;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Receive;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.game.DungeonEvent.ChangeRoom;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.DefaultGroupSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ColourSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.SaveSetting;
import com.ricedotwho.rsm.utils.Accessor;
import com.ricedotwho.rsm.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.Formatting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.jetbrains.annotations.NotNull;

@ModuleInfo(aliases = "Auto Routes", id = "Autoroutes", category = Category.DUNGEONS)
public class AutoRoutes extends Module implements Accessor {
   private final HashMap<RoomData, List<Node>> activeNodes = new HashMap<>();
   private final HashMap<String, List<Node>> redoMap = new HashMap<>();
   private static final BooleanSetting centerOnly = new BooleanSetting("Center Only", false);
   private static final BooleanSetting zeroTickBreak = new BooleanSetting("0t Break", false);
   private static final BooleanSetting use1_8Height = new BooleanSetting("Use 1.8 height for placing node", false);
   private final BooleanSetting editMode = new BooleanSetting("Edit Mode", false);
   private final KeybindSetting triggerBind = new KeybindSetting("Trigger Bind", new Keybind(0, true, this::onTrigger));
   private final KeybindSetting addBlockBind = new KeybindSetting("Add Block Bind", new Keybind(59, false, this::addBlockToInNode));
   private final KeybindSetting routeStartBind = new KeybindSetting("Route to start Bind", new Keybind(257, false, this::routeToStart));
   private final KeybindSetting fullScreenMapBind = new KeybindSetting("Fullscreen Map Bind", new Keybind(-1, false, this::openFullscreenMap));
   private final DefaultGroupSetting render = new DefaultGroupSetting("Render", this);
   private static final BooleanSetting startDepth = new BooleanSetting("Start Depth", false);
   private static final BooleanSetting nodeDepth = new BooleanSetting("Node Depth", true);
   private static final ColourSetting startColour = new ColourSetting("Start", Colour.GREEN.copy());
   private static final ColourSetting etherwarpColour = new ColourSetting("Etherwarp", Colour.CYAN.copy());
   private static final ColourSetting breakColour = new ColourSetting("Break", Colour.YELLOW.copy());
   private static final ColourSetting boomColour = new ColourSetting("Boom", Colour.RED.copy());
   private static final ColourSetting batColour = new ColourSetting("Bat", Colour.BLUE.copy());
   private static final ColourSetting aotvColour = new ColourSetting("Aotv", Colour.MAGENTA.copy());
   private static final ColourSetting useColour = new ColourSetting("Use", Colour.WHITE.copy());
   private final SaveSetting<HashMap<String, List<Node>>> data = new SaveSetting(
      "Nodes",
      "routes",
      "routes.json",
      HashMap::new,
      (new TypeToken<HashMap<String, List<Node>>>() {}).getType(),
      new GsonBuilder().registerTypeHierarchyAdapter(Node.class, new NodeAdapter()).setPrettyPrinting().create(),
      true,
      this::reload,
      null
   );
   private int tickTime = 0;
   private boolean forceNextNotSneak = false;
   private Node inNode;
   private boolean isRouting = false;
   private byte crouchDataShiftRegister = 0;
   public int lastBlockC08 = 0;
   private Class<? extends Node> lastType = null;
   private static final Set<String> SECRET_NAMES = Set.of(
      "Health Potion VIII Splash Potion",
      "Healing Potion 8 Splash Potion",
      "Healing Potion VIII Splash Potion",
      "Healing VIII Splash Potion",
      "Healing 8 Splash Potion",
      "Decoy",
      "Inflatable Jerry",
      "Spirit Leap",
      "Trap",
      "Training Weights",
      "Defuse Kit",
      "Dungeon Chest Key",
      "Treasure Talisman",
      "Revive Stone",
      "Architect's First Draft",
      "Secret Dye",
      "Candycomb"
   );

   public AutoRoutes() {
      this.registerProperty(
         new Setting[]{
            this.editMode,
            centerOnly,
            zeroTickBreak,
            use1_8Height,
            this.triggerBind,
            this.addBlockBind,
            this.routeStartBind,
            this.fullScreenMapBind,
            this.data,
            this.render
         }
      );
      this.render.add(new Setting[]{startDepth, nodeDepth, startColour, etherwarpColour, breakColour, boomColour, batColour, aotvColour});
      this.inNode = null;
      this.createBackup();
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.inNode = null;
      this.activeNodes.clear();
      this.crouchDataShiftRegister = 0;
      this.lastBlockC08 = 0;
   }

   @SubscribeEvent
   public void onRoomEnter(ChangeRoom event) {
      if (event.unique != null && event.room != null && event.oldRoom != null) {
         Room room = event.getRoom();
         this.inNode = null;
         if (!this.activeNodes.containsKey(room.getData())) {
            this.cacheRoomNodes(room);
         }
      }
   }

   @SubscribeEvent
   public void onRender(Extract event) {
      if (Location.getArea().is(Island.Dungeon) && Map.getCurrentRoom() != null) {
         Room currentRoom = Map.getCurrentRoom();
         List<Node> nodes = this.activeNodes.get(currentRoom.getData());
         if (nodes != null && !nodes.isEmpty()) {
            nodes.forEach(n -> n.render((Boolean)nodeDepth.getValue() && (!n.isStart() || (Boolean)startDepth.getValue())));
         }
      }
   }

   @SubscribeEvent
   public void onClientTickStart(Start event) {
      this.lastBlockC08--;
      this.isRouting = false;
      if (!Location.getArea().is(Island.Dungeon)) {
         return;
      }

      this.tickTime++;
      if (this.hasGuiOpen() || (Boolean)this.editMode.getValue() || Map.getCurrentRoom() == null || MinecraftClient.getInstance().player == null) {
         return;
      }

      Room currentRoom = Map.getCurrentRoom();
      List<Node> nodes = this.activeNodes.get(currentRoom.getData());
      if (nodes == null || nodes.isEmpty()) {
         this.inNode = null;
         return;
      }

      Pos playerPos = new Pos(MinecraftClient.getInstance().player.getEntityPos());
      nodes.forEach(n -> n.updateNodeState(playerPos, this.tickTime));
      this.lastType = null;
      while (this.handleQueue(playerPos, nodes)) {
      }
   }

   public boolean willBeCrouchingForEtherwarpEvaluation() {
      return (this.crouchDataShiftRegister >> 1 & 1) == 1;
   }

   @SubscribeEvent
   public void onPollInputs(InputPollEvent event) {
      if (this.isRouting() && Location.getArea().is(Island.Dungeon) && !this.hasGuiOpen()) {
         PlayerInput oldInputs = event.getClientInput();
         PlayerInput newInputs = new PlayerInput(
            oldInputs.forward(),
            oldInputs.backward(),
            oldInputs.left(),
            oldInputs.right(),
            oldInputs.jump(),
            !this.forceNextNotSneak,
            oldInputs.sprint()
         );
         this.forceNextNotSneak = false;
         event.getInput().apply(newInputs);
      }
   }

   private void cacheRoomNodes(Room room) {
      List<Node> nodes = this.getSavedNodes().get(room.getData().name());
      if (nodes != null && !nodes.isEmpty()) {
         UniqueRoom uniqueRoom = room.getUniqueRoom();
         nodes.forEach(n -> n.calculate(uniqueRoom));
         this.activeNodes.put(room.getData(), nodes);
      }
   }

   public void load() {
      this.data.load();
   }

   private void reload() {
      this.activeNodes.clear();
      this.inNode = null;
      if (Location.getArea().is(Island.Dungeon)) {
         Room room = Map.getCurrentRoom();
         if (room != null && room.getUniqueRoom() != null) {
            this.cacheRoomNodes(room);
         }
      }
   }

   private boolean hasGuiOpen() {
      return MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().currentScreen instanceof HandledScreen;
   }

   @SuppressWarnings("unchecked")
   private HashMap<String, List<Node>> getSavedNodes() {
      return (HashMap<String, List<Node>>)this.data.getValue();
   }

   public boolean clearNodes(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player == null || !this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         return false;
      }

      List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
      if (nodes.isEmpty()) {
         return false;
      }

      nodes.clear();
      this.save();
      return true;
   }

   public boolean removeNearest(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player == null || !this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         return false;
      }

      List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
      if (nodes.isEmpty()) {
         return false;
      }

      int bestIndex = -1;
      double bestDistance = Double.MAX_VALUE;
      Vec3d playerPos = MinecraftClient.getInstance().player.getEntityPos();

      for (int i = 0; i < nodes.size(); i++) {
         double distance = nodes.get(i).getRealPos().squaredDistanceTo(playerPos);
         if (distance < bestDistance) {
            bestIndex = i;
            bestDistance = distance;
         }
      }

      if (bestIndex < 0) {
         return false;
      }

      nodes.remove(bestIndex);
      this.save();
      return true;
   }

   public boolean undoNode(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player == null || !this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         return false;
      }

      List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
      if (nodes.isEmpty()) {
         return false;
      }

      if (!this.redoMap.containsKey(uniqueRoom.getName())) {
         this.redoMap.put(uniqueRoom.getName(), new ArrayList<>());
      }

      Node node = nodes.removeLast();
      this.redoMap.get(uniqueRoom.getName()).add(node);
      this.save();
      RSA.chat("Undid %s at %s", node.getName(), node.getRealPos().toChatString());
      return true;
   }

   public boolean redoNode(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player == null || !this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         return false;
      }

      List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
      if (!this.redoMap.containsKey(uniqueRoom.getName())) {
         return false;
      }

      List<Node> redo = this.redoMap.get(uniqueRoom.getName());
      if (redo.isEmpty()) {
         return false;
      }

      Node node = redo.removeLast();
      nodes.add(node);
      this.save();
      RSA.chat("Redid %s at %s", node.getName(), node.getRealPos().toChatString());
      return true;
   }

   public void addNode(Node node, UniqueRoom uniqueRoom) {
      HashMap<String, List<Node>> savedNodes = this.getSavedNodes();
      savedNodes.putIfAbsent(uniqueRoom.getName(), new ArrayList());
      List<Node> nodes = savedNodes.get(uniqueRoom.getName());
      node.calculate(uniqueRoom);
      nodes.add(node);
      if (!this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         this.activeNodes.put(uniqueRoom.getMainRoom().getData(), nodes);
      }

      this.save();
   }

   public void setForceSneak(boolean forceNotSneak) {
      this.forceNextNotSneak = forceNotSneak;
   }

   public void onTrigger() {
      if (this.isEnabled() && Location.getArea().is(Island.Dungeon) && Map.getCurrentRoom() != null) {
         if (this.inNode instanceof BatNode) {
            this.inNode.setTriggered(true);
         }

         if (this.inNode != null && this.inNode.hasAwaits()) {
            this.inNode.getAwaitManager().consume(AwaitClick.class, true);
            this.inNode.getAwaitManager().consume(AwaitSecrets.class, 100);
         }
      }
   }

   @SubscribeEvent
   public void onSendPacket(Send event) {
      if (Location.getArea().is(Island.Dungeon) && MinecraftClient.getInstance().world != null) {
         if (event.getPacket() instanceof PlayerInputC2SPacket packet) {
            PlayerInput input = packet.input();
            this.crouchDataShiftRegister = (byte)(this.crouchDataShiftRegister << 1);
            this.crouchDataShiftRegister = (byte)(this.crouchDataShiftRegister | (byte)(input.sneak() ? 1 : 0));
         } else if (this.canConsumeSecretAwaits() && event.getPacket() instanceof PlayerInteractBlockC2SPacket useItemOnPacket) {
            Block block = MinecraftClient.getInstance().world.getBlockState(useItemOnPacket.getBlockHitResult().getBlockPos()).getBlock();
            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.PLAYER_HEAD || block == Blocks.LEVER) {
               this.inNode.getAwaitManager().consume(AwaitSecrets.class, 1);
               this.lastBlockC08 = 2;
            }
         }
      }
   }

   private boolean canConsumeSecretAwaits() {
      return this.inNode != null
         && Map.getCurrentRoom() != null
         && this.inNode.hasAwaits()
         && this.inNode.getAwaitManager().hasAwait(AwaitType.SECRETS);
   }

   @SubscribeEvent
   public void onReceivePacket(Receive event) {
      if (Location.getArea().is(Island.Dungeon)
         && Map.getCurrentRoom() != null
         && this.inNode != null
         && mc.world != null
         && this.canConsumeSecretAwaits()) {
         if (event.getPacket() instanceof ItemPickupAnimationS2CPacket packet) {
            if (MinecraftClient.getInstance().world == null) {
               return;
            }

            if (!(MinecraftClient.getInstance().world.getEntityById(packet.getEntityId()) instanceof ItemEntity itemEntity)) {
               return;
            }

            String name = Formatting.strip(itemEntity.getStack().getName().getString());
            if (!SECRET_NAMES.contains(name)) {
               return;
            }

            this.inNode.getAwaitManager().consume(AwaitSecrets.class, 1);
         } else if (event.getPacket() instanceof EntitiesDestroyS2CPacket packet) {
            packet.getEntityIds()
               .forEach(
                  id -> {
                     Entity entity = mc.world.getEntityById(id);
                     if (entity instanceof ItemEntity itemEntityx
                        && entity.squaredDistanceTo(mc.player) < 64.0
                        && SECRET_NAMES.contains(Formatting.strip(itemEntityx.getStack().getName().getString()))) {
                        this.inNode.getAwaitManager().consume(AwaitSecrets.class, 1);
                     }
                  }
               );
         }
      }
   }

   private void trySetInNode(Node node) {
      if (this.inNode != node) {
         this.inNode = node;
         if (node.hasAwaits()) {
            node.getAwaitManager().onEnterNode();
         }
      }
   }

   public boolean handleQueue(Pos playerPos, List<Node> nodes) {
      List<Node> eligibleNodes = new ArrayList<>();

      for (Node node : nodes) {
         if (node.isInNode(playerPos)) {
            this.isRouting = true;
            if (!node.isTriggered() && !node.hasRanThisTick(this.tickTime)) {
               eligibleNodes.add(node);
            }
         }
      }

      if (eligibleNodes.isEmpty()) {
         this.inNode = null;
         return false;
      } else {
         eligibleNodes.sort(Comparator.<Node>comparingInt(node -> node.getPriority()).reversed());
         Node node = eligibleNodes.getFirst();
         this.trySetInNode(node);
         if (!node.shouldAwait() && this.lastBlockC08 <= 0 && (this.lastType == null || this.lastType == node.getClass())) {
            node.preTrigger(this.tickTime);
            boolean ran = node.run(playerPos);
            if (ran) {
               this.lastType = (Class<? extends Node>)node.getClass();
            }

            return ran;
         }

         return false;
      }
   }

   private void addBlockToInNode() {
      Room currentRoom = Map.getCurrentRoom();
      if (Location.getArea().is(Island.Dungeon)
         && !Dungeon.isInBoss()
         && currentRoom != null
         && !this.activeNodes.isEmpty()
         && mc.player != null
         && this.activeNodes.containsKey(currentRoom.getData())) {
         Pos playerPos = new Pos(mc.player.getEntityPos());
         Optional<BreakNode> opt = this.activeNodes
            .get(currentRoom.getData())
            .stream()
            .filter(n -> n.isInNode(playerPos) && n instanceof BreakNode)
            .map(n -> (BreakNode)n)
            .findFirst();
         if (opt.isEmpty()) {
            RSA.chat("Not in break node");
         } else {
            opt.get().addOrRemoveBlock();
         }
      }
   }

   private void routeToStart() {
      if (mc.player == null || this.hasGuiOpen()) {
         return;
      }

      Room currentRoom = Map.getCurrentRoom();
      if (currentRoom != null) {
         this.routeToRoomStart(currentRoom.getUniqueRoom());
      }
   }

   private void openFullscreenMap() {
      if (mc.player == null || this.hasGuiOpen()) {
         return;
      }

      if (!Location.getArea().is(Island.Dungeon) || Dungeon.isInBoss()) {
         RSA.chat("You must be in a dungeon room to open the route map.");
         return;
      }

      mc.setScreen(new DungeonRouteMapScreen(this));
   }

   public boolean routeToRoomStart(UniqueRoom uniqueRoom) {
      if (mc.player == null) {
         return false;
      }

      if (!Location.getArea().is(Island.Dungeon) || Dungeon.isInBoss()) {
         RSA.chat("You must be in a dungeon room to route.");
         return false;
      }

      if (uniqueRoom == null || uniqueRoom.getMainRoom() == null) {
         RSA.chat("That room is not loaded yet.");
         return false;
      }

      Node closestStart = this.findClosestStartNode(uniqueRoom);
      if (closestStart == null) {
         RSA.chat("Couldn't find a start node for %s.", uniqueRoom.getName());
         return false;
      }

      DynamicRoutes dynamicRoutes = (DynamicRoutes)RSM.getModule(DynamicRoutes.class);
      if (!dynamicRoutes.isEnabled()) {
         RSA.chat("Couldn't use dynamic routes (disabled).");
         return false;
      }

      Pos goalPos = closestStart.getRealPos();
      Goal goal = GoalDungeonXYZ.create(goalPos.asBlockPos().down(goalPos.y % 1.0 == 0.0 ? 1 : 0));
      if (goal == null) {
         return false;
      }

      BlockPos startPos = mc.player.getBlockPos().down();
      dynamicRoutes.cancelPathing();
      dynamicRoutes.executePath(startPos, goal);
      RSA.chat("Routing to %s start.", uniqueRoom.getName());
      return true;
   }

   public boolean hasStartNode(UniqueRoom uniqueRoom) {
      return this.findRoomNodes(uniqueRoom).stream().anyMatch(Node::isStart);
   }

   private Node findClosestStartNode(UniqueRoom uniqueRoom) {
      if (mc.player == null) {
         return null;
      }

      BlockPos startPos = mc.player.getBlockPos().down();
      return this.findRoomNodes(uniqueRoom)
         .stream()
         .filter(Node::isStart)
         .min(Comparator.comparingDouble(n -> n.getRealPos().squaredDistanceTo(startPos.toCenterPos())))
         .orElse(null);
   }

   private List<Node> findRoomNodes(UniqueRoom uniqueRoom) {
      if (uniqueRoom == null || uniqueRoom.getMainRoom() == null) {
         return List.of();
      }

      RoomData roomData = uniqueRoom.getMainRoom().getData();
      if (roomData == null) {
         return List.of();
      }

      List<Node> nodes = this.activeNodes.get(roomData);
      if (nodes == null || nodes.isEmpty()) {
         nodes = this.getSavedNodes().get(roomData.name());
         if (nodes == null || nodes.isEmpty()) {
            return List.of();
         }

         this.activeNodes.put(roomData, nodes);
      }

      nodes.forEach(n -> n.calculate(uniqueRoom));
      return nodes;
   }

   public void save() {
      this.data.save();
   }

   public void createBackup() {
      File backupDir = FileUtils.getCategoryFolder(this.data.getPath() + "/backups");
      List<Long> timestamps = getTimeStamps(backupDir);
      pruneBackups(backupDir, timestamps, 9);
      File newBackup = new File(backupDir, System.currentTimeMillis() + ".json");

      try {
         Files.copy(this.data.getFile().toPath(), newBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException exception) {
         RSA.getLogger().error("Failed to create autoroute backup!", exception);
         return;
      }

      RSA.getLogger().info("Created autoroute backup");
   }

   @NotNull
   private static List<Long> getTimeStamps(File backupDir) {
      List<Long> timestamps = new ArrayList<>();

      for (File file : Objects.requireNonNull(backupDir.listFiles())) {
         String name = file.getName();
         if (name.endsWith(".json")) {
            String timeString = name.substring(0, name.length() - 5);
            if (!timeString.isEmpty()) {
               try {
                  timestamps.add(Long.parseLong(timeString));
               } catch (NumberFormatException ignored) {
               }
            }
         }
      }

      return timestamps;
   }

   private static void pruneBackups(File backupDir, List<Long> timestamps, int maxSize) {
      if (timestamps.size() > maxSize) {
         timestamps.sort(Long::compareTo);

         for (int i = 0; i < timestamps.size() - maxSize; i++) {
            Long ts = timestamps.get(i);
            File file = new File(backupDir, ts + ".json");
            if (file.exists() && !file.delete()) {
               RSA.getLogger().error("Failed to delete old backup: {}", file.getName());
            }
         }
      }
   }

   public HashMap<RoomData, List<Node>> getActiveNodes() {
      return this.activeNodes;
   }

   public HashMap<String, List<Node>> getRedoMap() {
      return this.redoMap;
   }

   public BooleanSetting getEditMode() {
      return this.editMode;
   }

   public KeybindSetting getTriggerBind() {
      return this.triggerBind;
   }

   public KeybindSetting getAddBlockBind() {
      return this.addBlockBind;
   }

   public KeybindSetting getRouteStartBind() {
      return this.routeStartBind;
   }

   public KeybindSetting getFullScreenMapBind() {
      return this.fullScreenMapBind;
   }

   public DefaultGroupSetting getRender() {
      return this.render;
   }

   public SaveSetting<HashMap<String, List<Node>>> getData() {
      return this.data;
   }

   public int getTickTime() {
      return this.tickTime;
   }

   public boolean isForceNextNotSneak() {
      return this.forceNextNotSneak;
   }

   public Node getInNode() {
      return this.inNode;
   }

   public byte getCrouchDataShiftRegister() {
      return this.crouchDataShiftRegister;
   }

   public int getLastBlockC08() {
      return this.lastBlockC08;
   }

   public Class<? extends Node> getLastType() {
      return this.lastType;
   }

   public static BooleanSetting getCenterOnly() {
      return centerOnly;
   }

   public static BooleanSetting getZeroTickBreak() {
      return zeroTickBreak;
   }

   public static BooleanSetting getUse1_8Height() {
      return use1_8Height;
   }

   public static BooleanSetting getStartDepth() {
      return startDepth;
   }

   public static BooleanSetting getNodeDepth() {
      return nodeDepth;
   }

   public static ColourSetting getStartColour() {
      return startColour;
   }

   public static ColourSetting getEtherwarpColour() {
      return etherwarpColour;
   }

   public static ColourSetting getBreakColour() {
      return breakColour;
   }

   public static ColourSetting getBoomColour() {
      return boomColour;
   }

   public static ColourSetting getBatColour() {
      return batColour;
   }

   public static ColourSetting getAotvColour() {
      return aotvColour;
   }

   public static ColourSetting getUseColour() {
      return useColour;
   }

   public boolean isRouting() {
      return this.isRouting;
   }
}
