package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings;

import com.google.gson.JsonObject;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.RingType;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.Argument;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.ArgumentManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions.SubActionType;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.utils.Accessor;
import com.ricedotwho.rsm.utils.FileUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledBox;
import com.ricedotwho.rsm.utils.render.render3d.type.OutlineBox;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public abstract class Ring implements Accessor {
   private final Box box;
   private final Box renderBox;
   private final Box fillBox;
   private final Box inlineBox;
   private boolean triggered;
   private boolean active = false;
   private final SubActionManager subManager;
   private final ArgumentManager argManager;

   protected Ring(Pos pos, double radius, double renderOffset) {
      this(pos.subtract(radius, 0.0, radius), pos.add(radius, radius * 2.0, radius), renderOffset, null, null);
   }

   protected Ring(Vec3d pos, double radius, double renderOffset) {
      this(pos.subtract(radius, 0.0, radius), pos.add(radius, radius * 2.0, radius), renderOffset);
   }

   protected Ring(Vec3d min, Vec3d max, double renderOffset) {
      this.box = new Box(min, max);
      this.renderBox = this.box.shrink(renderOffset, renderOffset, renderOffset);
      this.triggered = false;
      this.subManager = null;
      this.argManager = null;
      this.fillBox = new Box(
         min.getX(), min.getY(), min.getZ(), max.getX(), min.getY() + 0.05, max.getZ()
      );
      Vec3d diff = max.subtract(min).multiply(0.15, 0.0, 0.15);
      this.inlineBox = new Box(
         min.getX() + diff.getX(),
         min.getY(),
         min.getZ() + diff.getZ(),
         max.getX() - diff.getX(),
         min.getY() + 0.05,
         max.getZ() - diff.getZ()
      );
   }

   protected Ring(Pos min, Pos max, double renderOffset, ArgumentManager manager, SubActionManager subManager) {
      this.box = new Box(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
      this.renderBox = this.box.shrink(renderOffset, renderOffset, renderOffset);
      this.triggered = false;
      this.subManager = subManager;
      this.argManager = manager;
      this.fillBox = new Box(min.x(), min.y(), min.z(), max.x(), min.y() + 0.05, max.z());
      Pos diff = max.subtract(min).multiply(0.15, 0.0, 0.15);
      this.inlineBox = new Box(min.x() + diff.x(), min.y(), min.z() + diff.z(), max.x() - diff.x(), min.y() + 0.05, max.z() - diff.z());
   }

   public boolean isInNode(Vec3d curr, Vec3d prev) {
      Box feet = new Box(
         curr.x - 0.2, curr.y, curr.z - 0.2, curr.x + 0.3, curr.y + 0.5, curr.z
      );
      boolean intercept = this.box.intersects(curr, prev);
      boolean intersects = this.box.intersects(feet);
      return intercept || intersects;
   }

   public void setTriggered() {
      this.triggered = true;
   }

   public void setActive() {
      this.active = true;
   }

   public void setInactive() {
      this.active = false;
   }

   public boolean updateState(Vec3d playerPos, Vec3d oldPos) {
      boolean inNode = this.isInNode(playerPos, oldPos);
      if (inNode && !this.triggered) {
         return true;
      } else {
         if (!inNode && this.triggered) {
            this.reset();
         }

         return false;
      }
   }

   public float getDistanceSq(Vec3d vec3) {
      float dx = (float)((this.box.maxX + this.box.minX) / 2.0 - vec3.x);
      float dy = (float)((this.box.maxY + this.box.minY) / 2.0 - vec3.y);
      float dz = (float)((this.box.maxZ + this.box.minZ) / 2.0 - vec3.z);
      return dx * dx + dy * dy + dz * dz;
   }

   public abstract RingType getType();

   public void reset() {
      this.triggered = false;
      if (this.argManager != null) {
         this.argManager.reset();
      }
   }

   public void render(boolean depth) {
      Renderer3D.addTask(new FilledBox(this.fillBox, this.getColour().alpha(50.0F), depth));
      Renderer3D.addTask(new OutlineBox(this.inlineBox, this.getColour(), depth));
   }

   public abstract boolean run();

   public boolean execute() {
      if (this.subManager != null) {
         this.subManager.run();
      }

      return this.run();
   }

   public boolean checkArg() {
      return this.argManager != null && this.argManager.check();
   }

   public abstract Colour getColour();

   public abstract int getPriority();

   public abstract boolean tick(MutableInput input, PlayerInput playerInput, AutoP3 module);

   public abstract void feedback();

   public boolean isStop() {
      return false;
   }

   public boolean shouldStop() {
      return this.subManager != null && this.subManager.has(SubActionType.STOP);
   }

   public <T> void consumeArg(Class<? extends Argument<T>> clazz, T value) {
      if (this.argManager != null) {
         this.argManager.consume(clazz, value);
      }
   }

   public JsonObject serialize() {
      JsonObject obj = new JsonObject();
      obj.addProperty("type", this.getType().name());
      obj.add("min", FileUtils.getGson().toJsonTree(new Pos(this.box.minX, this.box.minY, this.box.minZ)));
      obj.add("max", FileUtils.getGson().toJsonTree(new Pos(this.box.maxX, this.box.maxY, this.box.maxZ)));
      if (this.argManager != null && !this.argManager.getArgs().isEmpty()) {
         obj.add("args", this.argManager.serialize());
      }

      if (this.subManager != null && !this.subManager.getActions().isEmpty()) {
         obj.add("sub", this.subManager.serialize());
      }

      return obj;
   }

   public Box getBox() {
      return this.box;
   }

   public Box getRenderBox() {
      return this.renderBox;
   }

   public boolean isTriggered() {
      return this.triggered;
   }

   public boolean isActive() {
      return this.active;
   }

   public SubActionManager getSubManager() {
      return this.subManager;
   }

   public ArgumentManager getArgManager() {
      return this.argManager;
   }
}
