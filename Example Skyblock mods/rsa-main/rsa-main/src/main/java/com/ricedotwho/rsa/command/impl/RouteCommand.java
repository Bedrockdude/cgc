package com.ricedotwho.rsa.command.impl;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AutoRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitCondition;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AwaitManager;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.Node;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.NodeType;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitClick;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitEWRaytrace;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitSecrets;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.UseNode;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.command.Command;
import com.ricedotwho.rsm.command.api.CommandInfo;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.client.network.ClientCommandSource;

@CommandInfo(name = "autoroute", aliases = {"r", "ar", "route"}, description = "Handles creating autoroutes")
public class RouteCommand extends Command {
   public LiteralArgumentBuilder<ClientCommandSource> build() {
      return (LiteralArgumentBuilder<ClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal(
                           this.name()
                        )
                        .then(
                           literal("add")
                              .then(
                                 ((RequiredArgumentBuilder)((RequiredArgumentBuilder)argument("node", RouteCommand.NodeArgumentType.nodeArgument())
                                          .executes(ctx -> addNode(ctx, 0, false, false, false)))
                                       .then(
                                          ((RequiredArgumentBuilder)argument("await secrets", IntegerArgumentType.integer(0))
                                                .executes(ctx -> addNode(ctx, IntegerArgumentType.getInteger(ctx, "await secrets"), false, false, false)))
                                             .then(
                                                ((RequiredArgumentBuilder)((RequiredArgumentBuilder)argument("await click", BoolArgumentType.bool())
                                                         .executes(
                                                            ctx -> addNode(
                                                               ctx,
                                                               IntegerArgumentType.getInteger(ctx, "await secrets"),
                                                               BoolArgumentType.getBool(ctx, "await click"),
                                                               false,
                                                               false
                                                            )
                                                         ))
                                                      .then(
                                                         ((RequiredArgumentBuilder)argument("start", BoolArgumentType.bool())
                                                               .executes(
                                                                  ctx -> addNode(
                                                                     ctx,
                                                                     IntegerArgumentType.getInteger(ctx, "await secrets"),
                                                                     BoolArgumentType.getBool(ctx, "await click"),
                                                                     BoolArgumentType.getBool(ctx, "start"),
                                                                     false
                                                                  )
                                                               ))
                                                            .then(
                                                               ((RequiredArgumentBuilder)argument("await ew raytrace", BoolArgumentType.bool())
                                                                     .executes(
                                                                        ctx -> addNode(
                                                                           ctx,
                                                                           IntegerArgumentType.getInteger(ctx, "await secrets"),
                                                                           BoolArgumentType.getBool(ctx, "await click"),
                                                                           BoolArgumentType.getBool(ctx, "start"),
                                                                           BoolArgumentType.getBool(ctx, "await ew raytrace")
                                                                        )
                                                                     ))
                                                                  .then(
                                                                     argument("extra", StringArgumentType.string())
                                                                        .executes(
                                                                           ctx -> addNode(
                                                                              ctx,
                                                                              IntegerArgumentType.getInteger(ctx, "await secrets"),
                                                                              BoolArgumentType.getBool(ctx, "await click"),
                                                                              BoolArgumentType.getBool(ctx, "start"),
                                                                              BoolArgumentType.getBool(ctx, "await ew raytrace")
                                                                           )
                                                                        )
                                                                  )
                                                            )
                                                      ))
                                                   .executes(
                                                      ctx -> addNode(
                                                         ctx,
                                                         IntegerArgumentType.getInteger(ctx, "await secrets"),
                                                         BoolArgumentType.getBool(ctx, "await click"),
                                                         false,
                                                         false
                                                      )
                                                   )
                                             )
                                       ))
                                    .then(
                                       argument("start", BoolArgumentType.bool())
                                          .executes(ctx -> addNode(ctx, 0, false, BoolArgumentType.getBool(ctx, "start"), false))
                                    )
                              )
                        ))
                     .then(literal("clear").executes(RouteCommand::clearNodes)))
                  .then(literal("remove").executes(RouteCommand::removeNode)))
               .then(literal("load").executes(RouteCommand::loadNodes)))
            .then(literal("redo").executes(RouteCommand::redoNode)))
         .then(literal("undo").executes(RouteCommand::undoNode));
   }

   private static int loadNodes(CommandContext<ClientCommandSource> ctx) {
      ((AutoRoutes)RSM.getModule(AutoRoutes.class)).load();
      RSA.chat("Loaded nodes");
      return 1;
   }

   private static int clearNodes(CommandContext<ClientCommandSource> ctx) {
      Room room = Map.getCurrentRoom();
      if (room != null && room.getUniqueRoom() != null) {
         if (!((AutoRoutes)RSM.getModule(AutoRoutes.class)).clearNodes(room.getUniqueRoom())) {
            RSA.chat("No nodes found in this room!");
            return 0;
         } else {
            RSA.chat("Cleared all nodes!");
            return 1;
         }
      } else {
         RSA.chat("Failed to find room!");
         return 0;
      }
   }

   private static int removeNode(CommandContext<ClientCommandSource> ctx) {
      Room room = Map.getCurrentRoom();
      if (room != null && room.getUniqueRoom() != null) {
         if (!((AutoRoutes)RSM.getModule(AutoRoutes.class)).removeNearest(room.getUniqueRoom())) {
            RSA.chat("No nodes found in this room!");
            return 0;
         } else {
            RSA.chat("Removed node!");
            return 1;
         }
      } else {
         RSA.chat("Failed to find room!");
         return 0;
      }
   }

   private static int undoNode(CommandContext<ClientCommandSource> ctx) {
      Room room = Map.getCurrentRoom();
      if (room != null && room.getUniqueRoom() != null) {
         if (!((AutoRoutes)RSM.getModule(AutoRoutes.class)).undoNode(room.getUniqueRoom())) {
            RSA.chat("No nodes found in this room!");
            return 0;
         } else {
            return 1;
         }
      } else {
         RSA.chat("Failed to find room!");
         return 0;
      }
   }

   private static int redoNode(CommandContext<ClientCommandSource> ctx) {
      Room room = Map.getCurrentRoom();
      if (room != null && room.getUniqueRoom() != null) {
         if (!((AutoRoutes)RSM.getModule(AutoRoutes.class)).redoNode(room.getUniqueRoom())) {
            RSA.chat("No nodes found in this room!");
            return 0;
         } else {
            return 1;
         }
      } else {
         RSA.chat("Failed to find room!");
         return 0;
      }
   }

   private static int addNode(CommandContext<ClientCommandSource> ctx, int secrets, boolean click, boolean start, boolean raytrace) {
      Room room = Map.getCurrentRoom();
      if (!Location.getArea().is(Island.Dungeon) || room == null) {
         RSA.chat("Failed to add node, please enter a dungeon!");
         return 0;
      } else if (room.getUniqueRoom() == null) {
         RSA.chat("Null unique room!");
         return 0;
      } else {
         List<AwaitCondition<?>> conditions = new ArrayList<>();
         if (secrets > 0) {
            conditions.add(new AwaitSecrets(secrets));
         }

         if (click) {
            conditions.add(new AwaitClick());
         }

         if (raytrace) {
            conditions.add(new AwaitEWRaytrace());
         }

         AwaitManager awaits = null;
         if (!conditions.isEmpty()) {
            awaits = new AwaitManager(conditions);
         }

         NodeType type = RouteCommand.NodeArgumentType.getNode(ctx, "node");
         Node node = type.supply(room.getUniqueRoom(), awaits, start);
         if (node == null) {
            RSA.chat("Failed to add node, invalid player information!");
            return 0;
         } else {
            if (node instanceof UseNode n && ctx.getInput().toLowerCase().contains(" sneak")) {
               n.setSneak(true);
            }

            ((AutoRoutes)RSM.getModule(AutoRoutes.class)).addNode(node, room.getUniqueRoom());
            RSA.chat("Added " + type + " node!");
            return 1;
         }
      }
   }

   private static class NodeArgumentType implements ArgumentType<NodeType> {
      private static final Collection<String> EXAMPLES = Stream.of(NodeType.ETHERWARP, NodeType.BOOM).map(NodeType::getName).collect(Collectors.toList());
      private static final NodeType[] VALUES = NodeType.values();
      private static final DynamicCommandExceptionType INVALID_NODE_EXCEPTION = new DynamicCommandExceptionType(
         node -> Text.literal("Invalid node type : " + node)
      );

      public NodeType parse(StringReader stringReader) throws CommandSyntaxException {
         String string = stringReader.readUnquotedString();
         NodeType node = NodeType.byName(string);
         if (node == null) {
            throw INVALID_NODE_EXCEPTION.createWithContext(stringReader, string);
         } else {
            return node;
         }
      }

      public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
         return context.getSource() instanceof CommandSource ? CommandSource.suggestMatching(Arrays.stream(VALUES).map(NodeType::getName), builder) : Suggestions.empty();
      }

      public Collection<String> getExamples() {
         return EXAMPLES;
      }

      public static RouteCommand.NodeArgumentType nodeArgument() {
         return new RouteCommand.NodeArgumentType();
      }

      public static NodeType getNode(CommandContext<ClientCommandSource> context, String name) {
         return (NodeType)context.getArgument(name, NodeType.class);
      }
   }
}
