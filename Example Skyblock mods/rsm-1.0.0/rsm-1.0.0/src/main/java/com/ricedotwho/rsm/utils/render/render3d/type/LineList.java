package com.ricedotwho.rsm.utils.render.render3d.type;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.utils.render.render3d.VertexRenderer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class LineList extends RenderTask {
    private final List<Vec3> positions;
    private final Colour start;
    private final Colour end;

    public LineList(List<Vec3> from, Colour start, Colour end, boolean depth) {
        super(RenderType.LINE, depth);
        this.positions = from;
        this.start = start;
        this.end = end;
    }

    @Override
    public void render(PoseStack stack, VertexConsumer buffer, RenderType source) {
        int size = positions.size() - 1;
        int s = start.getRGB(), e = end.getRGB();
        for (int i = 0; i < size; i++) {
            Vec3 f = positions.get(i);
            Vec3 d = positions.get(i + 1).subtract(f);
            float t0 = (float) i / size;
            float t1 = (float) (i + 1) / size;
            int c0 = Colour.lerpARGB(s, e, t0);
            int c1 = Colour.lerpARGB(s, e, t1);
            VertexRenderer.renderLine(
                    stack.last(),
                    buffer,
                    f,
                    d,
                    c0,
                    c1
            );
        }
    }
}