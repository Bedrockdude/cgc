package com.ricedotwho.rsa.screen;

import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.AutoRoutes;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.map.handler.DungeonInfo;
import com.ricedotwho.rsm.component.impl.map.map.Door;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.RoomState;
import com.ricedotwho.rsm.component.impl.map.map.Tile;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.data.Pair;
import com.ricedotwho.rsm.utils.Accessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class DungeonRouteMapScreen extends Screen implements Accessor {
   private static final int BACKGROUND = 0xD00E1118;
   private static final int PANEL_BACKGROUND = 0xEE151A22;
   private static final int PANEL_BORDER = 0xFF394152;
   private static final int ROUTEABLE_BORDER = 0xFF49D7C5;
   private static final int CURRENT_ROOM_BORDER = 0xFFF5F7FA;
   private static final int HOVER_BORDER = 0xFFFFD166;
   private static final int ROOM_OUTLINE = 0x22000000;
   private static final int TEXT = 0xFFE9EEF5;
   private static final int SUBTLE_TEXT = 0xFF97A3B4;
   private static final int INACTIVE_TEXT = 0xFF6E7684;
   private final AutoRoutes autoRoutes;
   private final List<RoomHitbox> hitboxes = new ArrayList<>();

   public DungeonRouteMapScreen(AutoRoutes autoRoutes) {
      super(Text.literal("Route Map"));
      this.autoRoutes = autoRoutes;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, BACKGROUND);

      MapLayout layout = this.createLayout();
      this.hitboxes.clear();

      this.drawPanel(context, layout);

      List<UniqueRoom> renderableRooms = this.getRenderableRooms();
      Set<UniqueRoom> routeableRooms = renderableRooms.stream().filter(this.autoRoutes::hasStartNode).collect(Collectors.toSet());
      Room currentRoom = Map.getCurrentRoom();

      if (!Location.getArea().is(Island.Dungeon) || Dungeon.isInBoss()) {
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Open this map inside a dungeon room."), this.width / 2, this.height / 2, TEXT);
         super.render(context, mouseX, mouseY, delta);
         return;
      }

      this.renderTiles(context, layout);
      Room hoveredRoom = this.findHoveredRoom(mouseX, mouseY);
      this.renderRoomHighlights(context, layout, currentRoom, hoveredRoom, renderableRooms, routeableRooms);
      this.renderRoomLabels(context, layout, renderableRooms, routeableRooms);
      this.renderFooter(context, layout, hoveredRoom, routeableRooms);

      super.render(context, mouseX, mouseY, delta);
   }

   private void drawPanel(DrawContext context, MapLayout layout) {
      int panelLeft = layout.mapX() - 24;
      int panelTop = layout.mapY() - 56;
      int panelRight = layout.mapX() + layout.mapWidth() + 24;
      int panelBottom = layout.mapY() + layout.mapHeight() + 56;
      context.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BACKGROUND);
      this.drawBorder(context, panelLeft, panelTop, panelRight, panelBottom, PANEL_BORDER);
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Dungeon Route Map"), this.width / 2, panelTop + 14, TEXT);
      context.drawCenteredTextWithShadow(
         this.textRenderer,
         Text.literal("Left click a room to path to its route start."),
         this.width / 2,
         panelTop + 30,
         SUBTLE_TEXT
      );
   }

   private void renderTiles(DrawContext context, MapLayout layout) {
      List<UniqueRoom> renderableRooms = this.getRenderableRooms();

      for (int gridY = 0; gridY <= 10; gridY++) {
         for (int gridX = 0; gridX <= 10; gridX++) {
            Tile tile = DungeonInfo.getDungeonList()[gridY * 11 + gridX];
            if (tile == null) {
               continue;
            }

            TileRect rect = this.getTileRect(layout, gridX, gridY, tile);
            if (rect.width() <= 0 || rect.height() <= 0) {
               continue;
            }

            if (tile instanceof Door door) {
               int color = this.adjustStateColor(door.getColor().getRGB(), door.getState());
               context.fill(rect.x1(), rect.y1(), rect.x2(), rect.y2(), color);
               this.drawBorder(context, rect.x1(), rect.y1(), rect.x2(), rect.y2(), ROOM_OUTLINE);
               continue;
            }

            if (!(tile instanceof Room room)) {
               continue;
            }

            if (room.isSeparator()) {
               continue;
            }

            if (room.getUniqueRoom() == null || room.getUniqueRoom().getMainRoom() == null || "Unknown".equalsIgnoreCase(room.getUniqueRoom().getName())) {
               continue;
            }

            int color = this.adjustStateColor(room.getColor().getRGB(), room.getState());
            context.fill(rect.x1(), rect.y1(), rect.x2(), rect.y2(), color);
            this.hitboxes.add(new RoomHitbox(room, rect));
         }
      }

      this.renderJoinedRooms(context, layout, renderableRooms);
   }

   private void renderRoomHighlights(
      DrawContext context,
      MapLayout layout,
      Room currentRoom,
      Room hoveredRoom,
      List<UniqueRoom> renderableRooms,
      Set<UniqueRoom> routeableRooms
   ) {
      UniqueRoom currentUnique = currentRoom == null ? null : currentRoom.getUniqueRoom();
      UniqueRoom hoveredUnique = hoveredRoom == null ? null : hoveredRoom.getUniqueRoom();

      for (UniqueRoom uniqueRoom : renderableRooms) {
         int borderColor = 0;
         if (currentUnique == uniqueRoom) {
            borderColor = CURRENT_ROOM_BORDER;
         }

         if (hoveredUnique == uniqueRoom) {
            borderColor = HOVER_BORDER;
         }

         if (borderColor == 0) {
            continue;
         }

         for (Room tile : uniqueRoom.getTiles()) {
            if (tile.isSeparator()) {
               continue;
            }

            Pair<Integer, Integer> arrayPos = tile.getArrayPosition();
            TileRect rect = this.getTileRect(layout, arrayPos.getFirst(), arrayPos.getSecond(), tile);
            if (rect.width() > 0 && rect.height() > 0) {
               this.drawBorder(context, rect.x1(), rect.y1(), rect.x2(), rect.y2(), borderColor);
            }
         }
      }
   }

   private void renderRoomLabels(DrawContext context, MapLayout layout, List<UniqueRoom> renderableRooms, Set<UniqueRoom> routeableRooms) {
      for (UniqueRoom uniqueRoom : renderableRooms) {
         Room mainRoom = uniqueRoom.getMainRoom();
         if (mainRoom == null) {
            continue;
         }

         TileRect bounds = this.getRoomBounds(layout, uniqueRoom);
         if (bounds == null) {
            continue;
         }

         int centerX = bounds.x1() + bounds.width() / 2;
         int centerY = bounds.y1() + bounds.height() / 2 - 4;
         int maxWidth = Math.max(24, bounds.width() - 10);
         String label = this.fitRoomName(uniqueRoom.getName(), maxWidth);
         if (label.isEmpty()) {
            continue;
         }

         int color = routeableRooms.contains(uniqueRoom) ? TEXT : SUBTLE_TEXT;
         if (mainRoom.getState() == RoomState.UNDISCOVERED || mainRoom.getState() == RoomState.UNOPENED) {
            color = INACTIVE_TEXT;
         }

         context.drawCenteredTextWithShadow(this.textRenderer, label, centerX, centerY, color);
      }
   }

   private void renderFooter(DrawContext context, MapLayout layout, Room hoveredRoom, Set<UniqueRoom> routeableRooms) {
      int footerY = layout.mapY() + layout.mapHeight() + 18;
      String footer = "ESC to close";
      int color = SUBTLE_TEXT;

      if (hoveredRoom != null && hoveredRoom.getUniqueRoom() != null && hoveredRoom.getUniqueRoom().getMainRoom() != null) {
         UniqueRoom uniqueRoom = hoveredRoom.getUniqueRoom();
         boolean hasRoute = routeableRooms.contains(uniqueRoom);
         footer = uniqueRoom.getName() + (hasRoute ? " | click to route" : " | no start node saved");
         color = hasRoute ? TEXT : INACTIVE_TEXT;
      }

      context.drawCenteredTextWithShadow(this.textRenderer, footer, this.width / 2, footerY, color);
   }

   private Room findHoveredRoom(double mouseX, double mouseY) {
      return this.hitboxes
         .stream()
         .filter(hitbox -> hitbox.contains(mouseX, mouseY))
         .map(RoomHitbox::room)
         .filter(room -> room.getUniqueRoom() != null && room.getUniqueRoom().getMainRoom() != null)
         .min(Comparator.comparingInt(room -> room.isSeparator() ? 1 : 0))
         .orElse(null);
   }

   private List<UniqueRoom> getRenderableRooms() {
      return DungeonInfo.getUniqueRooms()
         .stream()
         .filter(room -> room != null && room.getMainRoom() != null && !"Unknown".equalsIgnoreCase(room.getName()))
         .sorted(Comparator.comparing(UniqueRoom::getName, String.CASE_INSENSITIVE_ORDER))
         .toList();
   }

   private void renderJoinedRooms(DrawContext context, MapLayout layout, List<UniqueRoom> renderableRooms) {
      for (UniqueRoom uniqueRoom : renderableRooms) {
         for (Room room : uniqueRoom.getTiles()) {
            if (room.isSeparator()) {
               continue;
            }

            Pair<Integer, Integer> arrayPos = room.getArrayPosition();
            if (this.findTileInRoom(uniqueRoom, arrayPos.getFirst() + 2, arrayPos.getSecond()) != null) {
               this.fillJoin(context, layout, room, arrayPos.getFirst(), arrayPos.getSecond(), true);
            }

            if (this.findTileInRoom(uniqueRoom, arrayPos.getFirst(), arrayPos.getSecond() + 2) != null) {
               this.fillJoin(context, layout, room, arrayPos.getFirst(), arrayPos.getSecond(), false);
            }
         }
      }
   }

   private Room findTileInRoom(UniqueRoom uniqueRoom, int arrayX, int arrayY) {
      for (Room tile : uniqueRoom.getTiles()) {
         if (tile.isSeparator()) {
            continue;
         }

         Pair<Integer, Integer> pos = tile.getArrayPosition();
         if (pos.getFirst() == arrayX && pos.getSecond() == arrayY) {
            return tile;
         }
      }

      return null;
   }

   private void fillJoin(DrawContext context, MapLayout layout, Room room, int arrayX, int arrayY, boolean horizontal) {
      TileRect rect = this.getTileRect(layout, arrayX, arrayY, room);
      int color = this.adjustStateColor(room.getColor().getRGB(), room.getState());
      if (horizontal) {
         context.fill(rect.x2(), rect.y1(), rect.x2() + layout.connectorSize(), rect.y2(), color);
      } else {
         context.fill(rect.x1(), rect.y2(), rect.x2(), rect.y2() + layout.connectorSize(), color);
      }
   }

   private TileRect getRoomBounds(MapLayout layout, UniqueRoom uniqueRoom) {
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;

      for (Room tile : uniqueRoom.getTiles()) {
         if (tile.isSeparator()) {
            continue;
         }

         Pair<Integer, Integer> arrayPos = tile.getArrayPosition();
         TileRect rect = this.getTileRect(layout, arrayPos.getFirst(), arrayPos.getSecond(), tile);
         if (rect.width() <= 0 || rect.height() <= 0) {
            continue;
         }

         minX = Math.min(minX, rect.x1());
         minY = Math.min(minY, rect.y1());
         maxX = Math.max(maxX, rect.x2());
         maxY = Math.max(maxY, rect.y2());
      }

      return minX == Integer.MAX_VALUE ? null : new TileRect(minX, minY, maxX, maxY);
   }

   private TileRect getTileRect(MapLayout layout, int gridX, int gridY, Tile tile) {
      int baseX = layout.mapX() + (gridX >> 1) * layout.step();
      int baseY = layout.mapY() + (gridY >> 1) * layout.step();
      boolean xEven = (gridX & 1) == 0;
      boolean yEven = (gridY & 1) == 0;

      if (tile instanceof Door) {
         int doorSpan = layout.doorSpan();
         int doorwayOffset = (layout.roomSize() - doorSpan) / 2;
         if (xEven && !yEven) {
            return new TileRect(
               baseX + doorwayOffset,
               baseY + layout.roomSize(),
               baseX + doorwayOffset + doorSpan,
               baseY + layout.roomSize() + layout.connectorSize()
            );
         }

         if (!xEven && yEven) {
            return new TileRect(
               baseX + layout.roomSize(),
               baseY + doorwayOffset,
               baseX + layout.roomSize() + layout.connectorSize(),
               baseY + doorwayOffset + doorSpan
            );
         }

         return new TileRect(baseX, baseY, baseX, baseY);
      }

      if (xEven && yEven) {
         return new TileRect(baseX, baseY, baseX + layout.roomSize(), baseY + layout.roomSize());
      }

      if (!xEven && !yEven) {
         return new TileRect(baseX, baseY, baseX + layout.roomSize() + layout.connectorSize(), baseY + layout.roomSize() + layout.connectorSize());
      }

      if (!xEven) {
         return new TileRect(baseX + layout.roomSize(), baseY, baseX + layout.roomSize() + layout.connectorSize(), baseY + layout.roomSize());
      }

      return new TileRect(baseX, baseY + layout.roomSize(), baseX + layout.roomSize(), baseY + layout.roomSize() + layout.connectorSize());
   }

   private MapLayout createLayout() {
      int available = Math.min(this.width - 120, this.height - 180);
      int roomSize = Math.max(18, available / 8);
      int connectorSize = Math.max(4, roomSize / 4);

      while (roomSize * 6 + connectorSize * 5 > available && roomSize > 18) {
         roomSize--;
         connectorSize = Math.max(4, roomSize / 4);
      }

      int mapWidth = roomSize * 6 + connectorSize * 5;
      int mapX = (this.width - mapWidth) / 2;
      int mapY = (this.height - mapWidth) / 2;
      return new MapLayout(mapX, mapY, roomSize, connectorSize, mapWidth);
   }

   private void drawBorder(DrawContext context, int x1, int y1, int x2, int y2, int color) {
      context.fill(x1, y1, x2, y1 + 1, color);
      context.fill(x1, y2 - 1, x2, y2, color);
      context.fill(x1, y1, x1 + 1, y2, color);
      context.fill(x2 - 1, y1, x2, y2, color);
   }

   private int adjustStateColor(int color, RoomState state) {
      if (state == RoomState.UNDISCOVERED) {
         return this.multiplyColor(color, 0.35F);
      }

      if (state == RoomState.UNOPENED) {
         return this.multiplyColor(color, 0.55F);
      }

      return color;
   }

   private int multiplyColor(int color, float factor) {
      int alpha = color >>> 24;
      int red = (int)((color >> 16 & 255) * factor);
      int green = (int)((color >> 8 & 255) * factor);
      int blue = (int)((color & 255) * factor);
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   private String fitRoomName(String name, int maxWidth) {
      if (this.textRenderer.getWidth(name) <= maxWidth) {
         return name;
      }

      String initials = this.toInitials(name);
      if (!initials.isEmpty() && this.textRenderer.getWidth(initials) <= maxWidth) {
         return initials;
      }

      String trimmed = name;
      while (!trimmed.isEmpty() && this.textRenderer.getWidth(trimmed + "...") > maxWidth) {
         trimmed = trimmed.substring(0, trimmed.length() - 1);
      }
      return trimmed.isEmpty() ? "" : trimmed + "...";
   }

   private String toInitials(String name) {
      StringBuilder builder = new StringBuilder();
      for (String part : name.split(" ")) {
         if (!part.isEmpty()) {
            builder.append(part.charAt(0));
         }
      }
      return builder.toString();
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      if (click.button() == 0) {
         Room hoveredRoom = this.findHoveredRoom(click.x(), click.y());
         if (hoveredRoom != null && hoveredRoom.getUniqueRoom() != null) {
            boolean routed = this.autoRoutes.routeToRoomStart(hoveredRoom.getUniqueRoom());
            if (routed) {
               this.close();
            }
            return routed;
         }
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      if (keyCode == 256) {
         this.close();
         return true;
      }

      return super.keyPressed(input);
   }

   @Override
   public void close() {
      this.client.setScreen(null);
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   private record MapLayout(int mapX, int mapY, int roomSize, int connectorSize, int mapWidth) {
      private int step() {
         return this.roomSize + this.connectorSize;
      }

      private int mapHeight() {
         return this.mapWidth;
      }

      private int doorSpan() {
         return Math.max(6, this.roomSize / 3);
      }
   }

   private record TileRect(int x1, int y1, int x2, int y2) {
      private int width() {
         return this.x2 - this.x1;
      }

      private int height() {
         return this.y2 - this.y1;
      }
   }

   private record RoomHitbox(Room room, TileRect rect) {
      private boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.rect.x1() && mouseX <= this.rect.x2() && mouseY >= this.rect.y1() && mouseY <= this.rect.y2();
      }
   }
}
