package com.ricedotwho.rsa.module.impl.dungeon;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.pathfinding.EtherwarpPathfinder;
import com.ricedotwho.rsa.component.impl.pathfinding.Goal;
import com.ricedotwho.rsa.component.impl.pathfinding.Path;
import com.ricedotwho.rsa.component.impl.pathfinding.PathfindingCalculationContext;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.Node;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.DynamicEtherwarpNode;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Receive;
import com.ricedotwho.rsm.event.impl.game.ServerTickEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.DefaultGroupSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ColourSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@ModuleInfo(aliases = "Dynamic Routes", id = "Dynamicroutes", category = Category.MOVEMENT)
public class DynamicRoutes extends Module {
   private final UniqueRoom EMPTY_UNIQUE;
   private final List<Node> nodes = new ArrayList<>();
   private static final BooleanSetting centerOnly = new BooleanSetting("Center Only", false);
   private final BooleanSetting oneUse = new BooleanSetting("Delete After Use", true);
   private final BooleanSetting editMode = new BooleanSetting("Edit Mode", false);
   private final DefaultGroupSetting render = new DefaultGroupSetting("Render", this);
   private static final BooleanSetting nodeDepth = new BooleanSetting("Node Depth", true);
   private static final ColourSetting nodeColor = new ColourSetting("Color", Colour.ORANGE);
   private final DefaultGroupSetting pathfinder = new DefaultGroupSetting("Pathfinding", this);
   private final NumberSetting heuristicThreshold = new NumberSetting("Heuristic Threshold", 0.1, 5.0, 0.5, 0.1);
   private final NumberSetting threadCount = new NumberSetting("Thead Count", 1.0, 64.0, 8.0, 1.0);
   private final NumberSetting nodeCost = new NumberSetting("Node Cost", 1.0, 10000.0, 500.0, 1.0);
   private final NumberSetting yawStep = new NumberSetting("Yaw Step", 0.1, 10.0, 4.0, 0.1);
   private final NumberSetting pitchStep = new NumberSetting("Pitch Step", 0.1, 10.0, 2.0, 0.1);
   private final DefaultGroupSetting instaclear = new DefaultGroupSetting("Insta Clear", this);
   private final NumberSetting tickOffset = new NumberSetting("Tick Offset", -5.0, 10.0, 1.0, 1.0);
   private EtherwarpPathfinder currentPathfinder;
   private Thread pathfinderThread;
   private final List<Goal> pathQueue;
   private int queueSequence = 0;
   private int tickTime = 0;
   private boolean isRouting = false;
   private byte awaitState = 0;

   public DynamicRoutes() {
      this.registerProperty(new Setting[]{this.editMode, centerOnly, this.oneUse, this.render, this.pathfinder, this.instaclear});
      this.pathQueue = new ArrayList<>();
      this.render.add(new Setting[]{nodeDepth, nodeColor});
      this.pathfinder.add(new Setting[]{this.threadCount, this.heuristicThreshold, this.nodeCost, this.yawStep, this.pitchStep});
      this.instaclear.add(new Setting[]{this.tickOffset});
      this.EMPTY_UNIQUE = UniqueRoom.emptyUnique();
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.nodes.clear();
      this.awaitState = 0;
   }

   @SubscribeEvent
   public void onRender(Extract event) {
      if (!this.nodes.isEmpty()) {
         this.nodes.forEach(n -> n.render((Boolean)nodeDepth.getValue()));
      }
   }

   @SubscribeEvent
   public void onReceivePacket(Receive event) {
      if (this.awaitState == 1 && event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
         this.awaitState = 2;
      }
   }

   @SubscribeEvent
   public void onServerTick(ServerTickEvent event) {
      if (this.awaitState >= 2) {
         this.awaitState++;
         if (this.awaitState > 9) {
            this.awaitState = 0;
         }
      }
   }

   @SubscribeEvent
   public void onClientTickStart(Start event) {
      this.tickTime++;
      if (this.awaitState == 0) {
         this.isRouting = false;
         if (!(Boolean)this.editMode.getValue() && MinecraftClient.getInstance().player != null && !this.nodes.isEmpty()) {
            Pos playerPos = new Pos(MinecraftClient.getInstance().player.getEntityPos());
            this.nodes.forEach(n -> n.updateNodeState(playerPos, this.tickTime));

            while (this.handleQueue(playerPos, this.nodes)) {
            }
         }
      }
   }

   @SubscribeEvent
   public void onPollInputs(InputPollEvent event) {
      if (this.isRouting()) {
         PlayerInput oldInputs = event.getClientInput();
         PlayerInput newInputs = new PlayerInput(
            oldInputs.forward(), oldInputs.backward(), oldInputs.left(), oldInputs.right(), oldInputs.jump(), true, oldInputs.sprint()
         );
         event.getInput().apply(newInputs);
      }
   }

   public void pathGoals(BlockPos startPos, List<? extends Goal> goals) {
      if (this.pathQueue.isEmpty() && !goals.isEmpty()) {
         this.pathQueue.addAll(goals);
         this.pathNextQueued(startPos);
      }
   }

   private void pathNextQueued(BlockPos pos) {
      if (!this.pathQueue.isEmpty()) {
         Goal goal = this.pathQueue.removeFirst();
         PathfindingCalculationContext ctx = new PathfindingCalculationContext(
            pos.mutableCopy(),
            ((BigDecimal)this.threadCount.getValue()).intValue(),
            ((BigDecimal)this.yawStep.getValue()).floatValue(),
            ((BigDecimal)this.pitchStep.getValue()).floatValue(),
            ((BigDecimal)this.nodeCost.getValue()).floatValue(),
            ((BigDecimal)this.heuristicThreshold.getValue()).floatValue()
         );
         this.executePath(new EtherwarpPathfinder(ctx, goal), path -> this.pathNextQueued(path.getEndNode().getPos()));
      }
   }

   public void executePath(BlockPos startPos, Goal goal) {
      PathfindingCalculationContext ctx = new PathfindingCalculationContext(
         startPos.mutableCopy(),
         ((BigDecimal)this.threadCount.getValue()).intValue(),
         ((BigDecimal)this.yawStep.getValue()).floatValue(),
         ((BigDecimal)this.pitchStep.getValue()).floatValue(),
         ((BigDecimal)this.nodeCost.getValue()).floatValue(),
         ((BigDecimal)this.heuristicThreshold.getValue()).floatValue()
      );
      this.executePath(new EtherwarpPathfinder(ctx, goal), null);
   }

   public void executePath(EtherwarpPathfinder pathfinder, Consumer<Path> callback) {
      if (this.currentPathfinder != null) {
         RSA.chat("Pathfinder already active!");
      } else {
         this.queueSequence = 0;
         this.currentPathfinder = pathfinder;
         this.pathfinderThread = new Thread(() -> {
            Path path = pathfinder.calculate();
            if (path == null) {
               this.cancelPathing();
            } else {
               this.queueSequence = path.consumeNodes(this::addNode, DynamicEtherwarpNode::fromBlockPos, this.queueSequence);
               this.currentPathfinder = null;
               if (callback != null) {
                  callback.accept(path);
               }
            }
         });
         this.pathfinderThread.start();
      }
   }

   public boolean isPathing() {
      return this.currentPathfinder != null;
   }

   public boolean cancelPathing() {
      this.pathQueue.clear();
      if (this.currentPathfinder == null) {
         return false;
      } else {
         this.currentPathfinder.cancel();
         this.currentPathfinder = null;
         return true;
      }
   }

   public boolean clearNodes() {
      this.nodes.clear();
      return true;
   }

   public boolean removeNearest() {
      if (MinecraftClient.getInstance().player == null) {
         return false;
      } else if (this.nodes.isEmpty()) {
         return false;
      } else {
         int bestIndex = -1;
         double bestDistance = Double.MAX_VALUE;
         Vec3d playerPos = MinecraftClient.getInstance().player.getEntityPos();

         for (int i = 0; i < this.nodes.size(); i++) {
            double d = this.nodes.get(i).getRealPos().squaredDistanceTo(playerPos);
            if (!(d >= bestDistance)) {
               bestIndex = i;
               bestDistance = d;
            }
         }

         if (bestIndex < 0) {
            return false;
         } else {
            this.nodes.remove(bestIndex);
            return true;
         }
      }
   }

   public boolean addNode(ClientPlayerEntity player) {
      Node node = DynamicEtherwarpNode.supply(this.EMPTY_UNIQUE, player);
      this.addNode(node);
      return true;
   }

   public void addNode(Node node) {
      node.calculate(this.EMPTY_UNIQUE);
      this.nodes.add(node);
   }

   public boolean handleQueue(Pos playerPos, List<Node> nodes) {
      int bestIndex = -1;
      int bestPriority = Integer.MIN_VALUE;

      for (int i = 0; i < nodes.size(); i++) {
         Node n = nodes.get(i);
         if (n.isInNode(playerPos)) {
            this.isRouting = true;
            if (!n.isTriggered() && !n.hasRanThisTick(this.tickTime) && n.getPriority() >= bestPriority) {
               bestPriority = n.getPriority();
               bestIndex = i;
            }
         }
      }

      if (bestIndex < 0) {
         return false;
      } else {
         Node node = nodes.get(bestIndex);
         node.preTrigger(this.tickTime);
         boolean bl = node.run(playerPos);
         if (bl) {
            if ((Boolean)this.oneUse.getValue()) {
               nodes.remove(bestIndex);
            }

            if (node.shouldAwait()) {
               this.awaitState = 1;
               return false;
            }
         }

         return bl;
      }
   }

   public List<Node> getNodes() {
      return this.nodes;
   }

   public static ColourSetting getNodeColor() {
      return nodeColor;
   }

   public boolean isRouting() {
      return this.isRouting;
   }
}
