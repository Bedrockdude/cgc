package com.ricedotwho.rsa.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalDungeonRoom;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalDungeonXYZ;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalXYZ;
import com.ricedotwho.rsa.module.impl.dungeon.DynamicRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.NodeType;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.command.Command;
import com.ricedotwho.rsm.command.api.CommandInfo;
import com.ricedotwho.rsm.component.impl.map.handler.DungeonInfo;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.utils.EtherUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;

@CommandInfo(name = "dynamicroute", aliases = "dr", description = "Handles creating dynamic routes.")
public class DynamicRouteCommand extends Command {
   public LiteralArgumentBuilder<ClientCommandSource> build() {
      return (LiteralArgumentBuilder<ClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal(
                                    this.name()
                                 )
                                 .then(literal("add").executes(DynamicRouteCommand::addNode)))
                              .then(literal("clear").executes(DynamicRouteCommand::clearNodes)))
                           .then(literal("stop").executes(DynamicRouteCommand::stopPathing)))
                        .then(
                           literal("path")
                              .then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> path(ctx, ctx.getArgument("pos", DefaultPosArgument.class))))
                        ))
                     .then(
                        literal("roompath")
                           .then(
                              argument("room", StringArgumentType.greedyString())
                                 .executes(ctx -> dungeonRoomPath(ctx, ctx.getArgument("room", String.class)))
                           )
                     ))
                  .then(
                     literal("insta")
                        .then(
                           argument("room1", StringArgumentType.string())
                              .then(
                                 argument("room2", StringArgumentType.string())
                                    .then(
                                       argument("room3", StringArgumentType.string())
                                          .executes(
                                             ctx -> insta(
                                                ctx,
                                                ctx.getArgument("room1", String.class),
                                                ctx.getArgument("room2", String.class),
                                                ctx.getArgument("room3", String.class)
                                             )
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  literal("roomfind")
                     .then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> dungeonPath(ctx, ctx.getArgument("pos", DefaultPosArgument.class))))
               ))
            .then(literal("cp").executes(DynamicRouteCommand::copyBlockPosLook)))
         .then(literal("remove").executes(DynamicRouteCommand::removeNode));
   }

   private static int stopPathing(CommandContext<ClientCommandSource> ctx) {
      boolean cancelled = ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).cancelPathing();
      if (cancelled) {
         RSA.chat("Cancelled pathing!");
         return 1;
      }

      RSA.chat("No pathing active!");
      return 0;
   }

   private static int copyBlockPosLook(CommandContext<ClientCommandSource> ctx) {
      MinecraftClient client = MinecraftClient.getInstance();
      Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
      float yaw = client.gameRenderer.getCamera().getYaw();
      float pitch = client.gameRenderer.getCamera().getPitch();
      Vec3d hitPos = EtherUtils.rayTraceBlock(61, yaw, pitch, cameraPos);
      Vec3d viewVector = hitPos.subtract(cameraPos).normalize();
      Vec3d nudgedHitPos = viewVector.multiply(0.001F).add(hitPos);
      BlockPos blockPos = BlockPos.ofFloored(nudgedHitPos);
      String clipboardText = blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ();
      client.keyboard.setClipboard(clipboardText);
      RSA.chat("Copied " + clipboardText);
      return 1;
   }

   private static BlockPos getPlayerStartPos(MinecraftClient client) {
      return BlockPos.ofFloored(client.player.getEntityPos().subtract(0.0, 0.001F, 0.0));
   }

   private static int path(CommandContext<ClientCommandSource> ctx, DefaultPosArgument pos) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return 0;
      }

      BlockPos blockPos = BlockPos.ofFloored(pos.x().value(), pos.y().value(), pos.z().value());
      BlockPos startPos = getPlayerStartPos(client);
      ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).executePath(startPos, new GoalXYZ(blockPos));
      return 1;
   }

   private static int insta(CommandContext<ClientCommandSource> ctx, String... roomNames) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return 0;
      }

      BlockPos startPos = getPlayerStartPos(client);
      List<GoalDungeonRoom> goals = new ArrayList<>();
      for (String roomName : roomNames) {
         UniqueRoom uniqueRoom = DungeonInfo.getRoomByName(roomName);
         if (uniqueRoom == null || uniqueRoom.getTiles().isEmpty()) {
            RSA.chat("Room not loaded!");
         }

         GoalDungeonRoom goal = GoalDungeonRoom.create(uniqueRoom);
         if (goal == null) {
            RSA.chat("Failed to create goal!");
            return 0;
         }

         goals.add(goal);
      }

      ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).pathGoals(startPos, goals);
      return 1;
   }

   private static int dungeonRoomPath(CommandContext<ClientCommandSource> ctx, String uniqueRoomName) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return 0;
      }

      UniqueRoom uniqueRoom = DungeonInfo.getRoomByName(uniqueRoomName);
      if (uniqueRoom == null || uniqueRoom.getTiles().isEmpty()) {
         RSA.chat("Room not loaded!");
      }

      BlockPos startPos = getPlayerStartPos(client);
      GoalDungeonRoom goal = GoalDungeonRoom.create(uniqueRoom);
      if (goal == null) {
         RSA.chat("Failed to create goal!");
         return 0;
      }

      ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).executePath(startPos, goal);
      return 1;
   }

   private static int dungeonPath(CommandContext<ClientCommandSource> ctx, DefaultPosArgument pos) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return 0;
      }

      BlockPos blockPos = BlockPos.ofFloored(pos.x().value(), pos.y().value(), pos.z().value());
      BlockPos startPos = getPlayerStartPos(client);
      GoalDungeonXYZ goal = GoalDungeonXYZ.create(blockPos);
      if (goal == null) {
         RSA.chat("Failed to create goal!");
         return 0;
      }

      ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).executePath(startPos, goal);
      return 1;
   }

   private static int clearNodes(CommandContext<ClientCommandSource> ctx) {
      if (!((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).clearNodes()) {
         RSA.chat("No nodes found!");
         return 0;
      }

      RSA.chat("Cleared all nodes!");
      return 1;
   }

   private static int removeNode(CommandContext<ClientCommandSource> ctx) {
      if (!((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).removeNearest()) {
         RSA.chat("No nodes found in this room!");
         return 0;
      }

      RSA.chat("Removed node!");
      return 1;
   }

   private static int addNode(CommandContext<ClientCommandSource> ctx) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return 0;
      }

      boolean added = ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).addNode(client.player);
      if (!added) {
         RSA.chat("Failed to raytrace etherwarp!");
         return 0;
      }

      RSA.chat("Added " + NodeType.ETHERWARP + " node!");
      return 1;
   }
}
