package com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.DungeonBreaker;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AutoRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.Node;
import com.ricedotwho.rsa.utils.InteractUtils;
import com.ricedotwho.rsa.utils.render3d.type.Ring;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.RoomUtils;
import com.ricedotwho.rsm.component.impl.task.TaskComponent;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.Accessor;
import com.ricedotwho.rsm.utils.FileUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledBox;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;

public class BreakNode extends Node implements Accessor {
   private final List<Pos> blocks;
   private List<Pos> rotated = null;
   private boolean running = false;

   public BreakNode(Pos localPos, AwaitManager awaits, boolean start) {
      super(localPos, awaits, start);
      this.blocks = new ArrayList<>();
   }

   public BreakNode(Pos localPos, List<Pos> blocks, AwaitManager awaits, boolean start) {
      super(localPos, awaits, start);
      this.blocks = blocks;
   }

   @Override
   public void calculate(UniqueRoom room) {
      super.calculate(room);
      this.rotated = new ArrayList<>();
      this.rotated = this.blocks.stream().map(pos -> RoomUtils.getRealPositionFixed(pos, room.getMainRoom())).toList();
   }

   @Override
   public boolean run(Pos playerPos) {
      if (!SwapManager.reserveSwap("DUNGEONBREAKER")) {
         return this.cancel();
      } else if (this.running) {
         return this.cancel();
      } else {
         List<Pos> f = this.rotated
            .stream()
            .filter(
               p -> {
                  BlockPos bp = p.asBlockPos();
                  BlockState state = mc.world.getBlockState(bp);
                  VoxelShape shape = state.getOutlineShape(mc.world, bp);
                  return !shape.isEmpty()
                     && DungeonBreaker.canInstantMine(state)
                     && InteractUtils.faceDistance(
                           p.asVec3(), mc.player.getEntityPos().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0)
                        )
                        <= 25.0;
               }
            )
            .toList();
         if (f.isEmpty()) {
            return true;
         } else {
            this.running = true;
            if ((Boolean)AutoRoutes.getZeroTickBreak().getValue()) {
               for (Pos pos : f) {
                  InteractUtils.breakBlock(pos, true, SwapManager.isDesynced());
               }

               this.running = false;
            } else {
               for (int i = 0; i < f.size(); i++) {
                  Pos block = f.get(i);
                  TaskComponent.onTick(i, () -> InteractUtils.breakBlock(block, true, SwapManager.isDesynced()));
               }

               TaskComponent.onTick(f.size(), () -> this.running = false);
            }

            return this.cancel();
         }
      }
   }

   @Override
   public boolean cancel() {
      this.reset();
      return false;
   }

   @Override
   public void render(boolean depth) {
      Renderer3D.addTask(new Ring(this.getRealPos().asVec3(), depth, this.getRadius(), this.getColour()));
      if (this.rotated != null && !this.rotated.isEmpty()) {
         Colour colour = AutoRoutes.getBreakColour().getValue().alpha(90.0F);

         for (Pos pos : this.rotated) {
            BlockPos bp = pos.asBlockPos();
            BlockState state = mc.world.getBlockState(bp);
            VoxelShape shape = state.getOutlineShape(mc.world, bp);
            if (!shape.isEmpty()) {
               Box aabb = shape.getBoundingBox().offset(bp);
               Renderer3D.addTask(new FilledBox(aabb, colour, true));
            }
         }
      }
   }

   @Override
   public int getPriority() {
      return 18;
   }

   @Override
   public String getName() {
      return "break";
   }

   @Override
   public Colour getColour() {
      return this.isStart() ? AutoRoutes.getStartColour().getValue() : AutoRoutes.getBreakColour().getValue();
   }

   @Override
   public JsonObject serialize() {
      JsonObject json = super.serialize();
      json.add("blocks", FileUtils.getGson().toJsonTree(this.blocks));
      return json;
   }

   public static BreakNode supply(UniqueRoom fullRoom, ClientPlayerEntity player, AwaitManager awaits, boolean start) {
      Room mainRoom = fullRoom.getMainRoom();
      Pos playerRelative = RoomUtils.getRelativePosition(new Pos(player.getEntityPos()), mainRoom);
      return new BreakNode(playerRelative, awaits, start);
   }

   public void addOrRemoveBlock() {
      if (Map.getCurrentRoom() == null) {
         RSA.chat(Formatting.RED + "Room is null!");
      }

      if (MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult blockHitResult && blockHitResult.getType() != Type.MISS) {
         Pos pos = new Pos(blockHitResult.getBlockPos());
         Pos relPos = RoomUtils.getRelativePositionFixed(pos, Map.getCurrentRoom().getUniqueRoom().getMainRoom());
         if (this.blocks.contains(relPos)) {
            this.blocks.remove(relPos);
            RSA.chat(Formatting.RED + "Removed " + relPos.toChatString() + " from break node");
         } else {
            this.blocks.add(relPos);
            RSA.chat(Formatting.GREEN + "Added " + relPos.toChatString() + " to break node!");
         }

         this.calculate(Map.getCurrentRoom().getUniqueRoom());
         ((AutoRoutes)RSM.getModule(AutoRoutes.class)).save();
      } else {
         RSA.chat(Formatting.RED + "Not looking at a block");
      }
   }

   public List<Pos> getBlocks() {
      return this.blocks;
   }
}
