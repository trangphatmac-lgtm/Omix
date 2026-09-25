package cn.omix.util.sigma;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** CPU clipping of map tiles against a rounded panel replaces the original GL stencil without extra framebuffers. */
public record SigmaTexturePolygon(Matrix3x2fc pose, float[] xy, TextureSetup textureSetup,
                                  float x, float y, float width, float height, int color,
                                  @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds) implements SimpleGuiElementRenderState {
    @Override public RenderPipeline pipeline() { return textureSetup == TextureSetup.empty() ? RenderPipelines.GUI : RenderPipelines.GUI_TEXTURED; }
    @Override public void setupVertices(VertexConsumer buffer) {
        for (int i = 2; i < xy.length - 2; i += 2) {
            vertex(buffer, 0); vertex(buffer, i + 2); vertex(buffer, i); vertex(buffer, 0);
        }
    }
    private void vertex(VertexConsumer buffer, int index) {
        var vertex = buffer.vertex(pose, xy[index], xy[index + 1]);
        if (textureSetup != TextureSetup.empty()) vertex.texture((xy[index] - x) / width, (xy[index + 1] - y) / height);
        vertex.color(color);
    }

    public record Clip(float x, float y, float width, float height, float radius) {
        public float[] polygon(float left, float top, float right, float bottom) {
            List<float[]> points = new ArrayList<>(40);
            for (int corner = 0; corner < 4; corner++) {
                float cx = corner == 0 || corner == 3 ? x + width - radius : x + radius;
                float cy = corner < 2 ? y + height - radius : y + radius;
                for (int part = 0; part <= 8; part++) {
                    double angle = Math.PI / 2 * (corner + part / 8.0);
                    points.add(new float[]{cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius});
                }
            }
            points = cut(points, 0, left, true); points = cut(points, 0, right, false);
            points = cut(points, 1, top, true); points = cut(points, 1, bottom, false);
            float[] result = new float[points.size() * 2];
            for (int i = 0; i < points.size(); i++) { result[i * 2] = points.get(i)[0]; result[i * 2 + 1] = points.get(i)[1]; }
            return result;
        }
        private static List<float[]> cut(List<float[]> input, int axis, float edge, boolean minimum) {
            List<float[]> result = new ArrayList<>(input.size() + 4);
            if (input.isEmpty()) return result;
            float[] previous = input.getLast(); boolean wasInside = minimum ? previous[axis] >= edge : previous[axis] <= edge;
            for (float[] current : input) {
                boolean inside = minimum ? current[axis] >= edge : current[axis] <= edge;
                if (inside != wasInside) {
                    float amount = (edge - previous[axis]) / (current[axis] - previous[axis]);
                    result.add(new float[]{previous[0] + (current[0] - previous[0]) * amount, previous[1] + (current[1] - previous[1]) * amount});
                }
                if (inside) result.add(current);
                previous = current; wasInside = inside;
            }
            return result;
        }
    }

    public static void draw(DrawContext context, TextureSetup texture, float x, float y, float width, float height, Clip clip, int color) {
        float[] polygon = clip.polygon(x, y, x + width, y + height);
        if (polygon.length < 6) return;
        var pose = new Matrix3x2f(context.getMatrices());
        var scissor = context.scissorStack.peekLast();
        var bounds = new ScreenRect((int) clip.x, (int) clip.y, (int) Math.ceil(clip.width), (int) Math.ceil(clip.height)).transformEachVertex(pose);
        if (scissor != null) bounds = scissor.intersection(bounds);
        context.state.addSimpleElement(new SigmaTexturePolygon(pose, polygon, texture, x, y, width, height, color, scissor, bounds));
    }
}
