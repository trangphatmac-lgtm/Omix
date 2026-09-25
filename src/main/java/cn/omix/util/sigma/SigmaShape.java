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

/** Convex UI shapes submitted as one GUI element rather than one draw command per scanline. */
public record SigmaShape(Matrix3x2fc pose, float[] coordinates, int color, @Nullable ScreenRect scissorArea,
                         @Nullable ScreenRect bounds) implements SimpleGuiElementRenderState {
    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.empty(); }

    @Override public void setupVertices(VertexConsumer buffer) {
        for (int i = 2; i < coordinates.length - 2; i += 2) {
            buffer.vertex(pose, coordinates[0], coordinates[1]).color(color);
            buffer.vertex(pose, coordinates[i + 2], coordinates[i + 3]).color(color);
            buffer.vertex(pose, coordinates[i], coordinates[i + 1]).color(color);
            buffer.vertex(pose, coordinates[0], coordinates[1]).color(color);
        }
    }

    public static void rounded(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        radius = Math.clamp(radius, 0, Math.min(w, h) / 2);
        float[] vertices = new float[4 * 9 * 2];
        int index = 0;
        for (int corner = 0; corner < 4; corner++) {
            float cx = corner == 0 || corner == 3 ? x + w - radius : x + radius;
            float cy = corner < 2 ? y + h - radius : y + radius;
            for (int segment = 0; segment <= 8; segment++) {
                double angle = Math.PI / 2 * (corner + segment / 8.0);
                vertices[index++] = cx + (float) Math.cos(angle) * radius;
                vertices[index++] = cy + (float) Math.sin(angle) * radius;
            }
        }
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect clip = context.scissorStack.peekLast();
        ScreenRect bounds = new ScreenRect((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1).transformEachVertex(pose);
        if (clip != null) bounds = clip.intersection(bounds);
        context.state.addSimpleElement(new SigmaShape(pose, vertices, color, clip, bounds));
    }
}
