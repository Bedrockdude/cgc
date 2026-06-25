package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.gson.JsonObject;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.RoomUtils;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.FileUtils;

public abstract class Node {
   protected final Pos localPos;
   private final float radius;
   private final AwaitManager awaitManager;
   private final boolean start;
   private boolean triggered;
   private int lastTickTime;
   protected transient Pos realPos;

   public Node(Pos localPos) {
      this(localPos, null);
   }

   public Node(Pos localPos, AwaitManager awaitManager) {
      this(localPos, awaitManager, 0.5F, false);
   }

   public Node(Pos localPos, AwaitManager awaitManager, boolean start) {
      this(localPos, awaitManager, 0.5F, start);
   }

   public Node(Pos localPos, AwaitManager awaitManager, float radius, boolean start) {
      this.localPos = localPos;
      this.radius = radius;
      this.awaitManager = awaitManager;
      this.start = start;
      this.triggered = false;
      this.lastTickTime = -1;
   }

   public boolean hasAwaits() {
      return this.awaitManager != null && this.awaitManager.hasAwaits();
   }

   public boolean shouldAwait() {
      return this.awaitManager != null && this.awaitManager.shouldAwait(this);
   }

   public void calculate(UniqueRoom room) {
      this.realPos = RoomUtils.getRealPosition(this.localPos, room.getMainRoom());
      if (this.hasAwaits()) {
         this.getAwaitManager().resetAwaits();
      }
   }

   public abstract boolean run(Pos playerPos);

   public abstract void render(boolean depth);

   protected boolean cancel() {
      this.reset();
      return false;
   }

   public int getPriority() {
      return 8;
   }

   public boolean isInNode(Pos playerPos) {
      if (!AutoRoutes.getCenterOnly().getValue()) {
         return playerPos.squaredDistanceTo(this.realPos) <= this.radius * this.radius;
      }

      return this.realPos.x() == playerPos.x()
         && playerPos.y() >= this.realPos.y() - 0.05
         && playerPos.y() <= this.realPos.y() + 0.05
         && this.realPos.z() == playerPos.z();
   }

   public void updateLastTickTime(int lastTickTime) {
      this.lastTickTime = lastTickTime;
   }

   public boolean hasRanThisTick(int tickTime) {
      return tickTime <= this.lastTickTime;
   }

   public void preTrigger(int tickTime) {
      this.lastTickTime = tickTime;
      this.triggered = true;
   }

   public boolean updateNodeState(Pos playerPos, int tickTime) {
      if (tickTime <= this.lastTickTime) {
         return false;
      }

      boolean inNode = this.isInNode(playerPos);
      if (inNode && !this.triggered) {
         return true;
      }

      if (!inNode && this.triggered) {
         this.reset();
      }

      return false;
   }

   public abstract String getName();

   public abstract Colour getColour();

   public JsonObject serialize() {
      JsonObject json = new JsonObject();
      json.addProperty("type", this.getName());
      json.add("localPos", FileUtils.getGson().toJsonTree(this.localPos));
      json.addProperty("radius", this.radius);
      json.addProperty("start", this.start);
      if (this.awaitManager != null && this.awaitManager.hasAwaits()) {
         json.add("awaits", this.awaitManager.serialize());
      }

      return json;
   }

   public void reset() {
      this.triggered = false;
   }

   public float getRadius() {
      return this.radius;
   }

   public AwaitManager getAwaitManager() {
      return this.awaitManager;
   }

   public boolean isStart() {
      return this.start;
   }

   public boolean isTriggered() {
      return this.triggered;
   }

   public void setTriggered(boolean triggered) {
      this.triggered = triggered;
   }

   public int getLastTickTime() {
      return this.lastTickTime;
   }

   public Pos getRealPos() {
      return this.realPos;
   }
}
