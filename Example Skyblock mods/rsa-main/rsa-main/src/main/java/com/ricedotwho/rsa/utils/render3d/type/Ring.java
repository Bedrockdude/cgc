package com.ricedotwho.rsa.utils.render3d.type;

import com.ricedotwho.rsa.utils.render3d.RSAVertexRenderer;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.utils.render.render3d.type.RenderTask;
import com.ricedotwho.rsm.utils.render.render3d.type.RenderType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;

public class Ring extends RenderTask {
   private final Vec3d pos;
   private final float radius;
   private final Colour colour;
   private final int slices;
   private final int layers;

   public Ring(Vec3d pos, boolean depth, float radius, Colour colour) {
      this(pos, depth, radius, colour, 64, 16);
   }

   public Ring(Vec3d pos, boolean depth, float radius, Colour colour, int slices, int layers) {
      super(RenderType.LINE, depth);
      this.pos = pos;
      this.radius = radius;
      this.colour = colour;
      this.slices = slices;
      this.layers = layers;
   }

   private int getFactor() {
      Entity camera = MinecraftClient.getInstance().getCameraEntity();
      double dist = camera.squaredDistanceTo(this.pos);
      if (dist > 4096.0) {
         return 0;
      } else if (dist > 2304.0) {
         return 8;
      } else if (dist > 1024.0) {
         return 4;
      } else {
         return dist > 256.0 ? 2 : 1;
      }
   }

   public void render(MatrixStack stack, VertexConsumer buffer, RenderType source) {
      int factor = this.getFactor();
      if (factor != 0) {
         int slices = this.slices / factor;
         int layers = this.layers / factor;
         RSAVertexRenderer.renderRing(stack.peek(), buffer, this.pos, this.radius, this.colour, slices, layers);
      }
   }
}
