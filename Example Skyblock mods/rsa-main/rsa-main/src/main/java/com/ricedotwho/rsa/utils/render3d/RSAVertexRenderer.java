package com.ricedotwho.rsa.utils.render3d;

import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.utils.render.render3d.VertexRenderer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack.Entry;

public final class RSAVertexRenderer {
   public static void renderRing(Entry pose, VertexConsumer buffer, Vec3d pos, float radius, Colour colour, int slices, int layers) {
      if (slices >= 3) {
         pose.translate((float)pos.getX(), (float)pos.getY(), (float)pos.getZ());
         double h = radius * 2.0F / 3.0;
         float oneOverLayers = 1.0F / layers;
         float red = colour.getRedFloat();
         float green = colour.getGreenFloat();
         float blue = colour.getBlueFloat();

         for (int i = 0; i < layers; i++) {
            float yOffset = (float)(h * i / layers);
            float t = 1.0F - i * oneOverLayers;
            float alpha = t * t * t;
            if (!(alpha < 0.01F)) {
               VertexRenderer.circle(pose, buffer, radius, yOffset, alpha, red, green, blue, slices);
            }
         }

         pose.translate((float)(-pos.getX()), (float)(-pos.getY()), (float)(-pos.getZ()));
      }
   }

   private RSAVertexRenderer() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
