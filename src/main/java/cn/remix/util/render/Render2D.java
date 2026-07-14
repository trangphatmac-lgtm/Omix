package cn.remix.util.render;

import cn.remix.util.IMinecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

@UtilityClass
public final class Render2D implements IMinecraft {
    private final RenderPipeline TRIANGLE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation(Identifier.of("remix", "pipeline/gui_triangles"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .build()
    );

    public void init() {
        // Forces the custom GUI pipeline to register before Minecraft reloads shaders.
    }

    public void drawRect(DrawContext context, float x, float y, float width, float height, int color) {
        drawGradient(context, x, y, width, height, color, color, false);
    }

    public void drawGradient(DrawContext context, float x, float y, float width, float height, int startColor, int endColor, boolean horizontal) {
        if (width <= 0 || height <= 0) return;

        context.state.addSimpleElement(new FloatQuadGuiElementRenderState(
                RenderPipelines.GUI, TextureSetup.empty(), new Matrix3x2f(context.getMatrices()),
                x, y, x + width, y + height, startColor, endColor, horizontal, context.scissorStack.peekLast()
        ));
    }

    public void drawOutline(DrawContext context, float x, float y, float width, float height, float thickness, int color) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;

        drawRect(context, x, y, width, thickness, color);
        drawRect(context, x, y + height - thickness, width, thickness, color);
        drawRect(context, x, y + thickness, thickness, height - thickness - thickness, color);
        drawRect(context, x + width - thickness, y + thickness, thickness, height - thickness - thickness, color);
    }

    public void drawTriangle(DrawContext context, float centerX, float centerY, float angle, float length, int color) {
        float left = angle + (float) Math.toRadians(26.25);
        float right = angle - (float) Math.toRadians(26.25);
        float leftX = centerX + length * (float) Math.cos(left);
        float leftY = centerY + length * (float) Math.sin(left);
        float rightX = centerX + length * (float) Math.cos(right);
        float rightY = centerY + length * (float) Math.sin(right);

        context.state.addSimpleElement(new TriangleGuiElementRenderState(
                TRIANGLE_PIPELINE, TextureSetup.empty(), new Matrix3x2f(context.getMatrices()),
                centerX, centerY, leftX, leftY, rightX, rightY, color, context.scissorStack.peekLast()
        ));
    }

    public static void beginScissor(DrawContext context, float x, float y, float width, float height) {
        context.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));
    }

    public void endScissor(DrawContext context) {
        context.disableScissor();
    }

    public void drawItem(DrawContext context, ItemStack stack, float x, float y) {
        if (stack.isEmpty()) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height) {
        drawTexture(context, texture, x, y, width, height, 0f, 0f, 1f, 1f, -1);
    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        drawTexture(context, texture, x, y, width, height, 0f, 0f, 1f, 1f, color);
    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, int color) {
        if (width <= 0 || height <= 0) return;

        var tex = mc.getTextureManager().getTexture(texture);
        var textureSetup = TextureSetup.of(tex.getGlTextureView(), tex.getSampler());
        context.state.addSimpleElement(new FloatQuadTexturedGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED, textureSetup, new Matrix3x2f(context.getMatrices()),
                x, y, x + width, y + height, u0, v0, u1, v1, color, context.scissorStack.peekLast()
        ));
    }

    public void drawModel(DrawContext context, LivingEntity entity, float x, float y) {
        if (entity == null) return;
        int x1 = (int) (x + 5);
        int y1 = (int) (y + 5);
        int x2 = (int) (x + 40);
        int y2 = (int) (y + 40);
        InventoryScreen.drawEntity(context, x1, y1, x2, y2, 16, 0.0625F, 0, 0, entity);
    }

    private record FloatQuadTexturedGuiElementRenderState(
            RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
            float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color,
            @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState {

        private FloatQuadTexturedGuiElementRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color, @Nullable ScreenRect scissorArea) {
            this(pipeline, textureSetup, pose, x0, y0, x1, y1, u0, v0, u1, v1, color, scissorArea, createBounds(x0, y0, x1, y1, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer v) {
            v.vertex(pose, x0, y0).texture(u0, v0).color(color);
            v.vertex(pose, x0, y1).texture(u0, v1).color(color);
            v.vertex(pose, x1, y1).texture(u1, v1).color(color);
            v.vertex(pose, x1, y0).texture(u1, v0).color(color);
        }

        private static @Nullable ScreenRect createBounds(float x0, float y0, float x1, float y1, Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
            ScreenRect rect = new ScreenRect(Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0)).transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(rect) : rect;
        }
    }

    private record TriangleGuiElementRenderState(
            RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
            float x1, float y1, float x2, float y2, float x3, float y3, int color,
            @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState {

        private TriangleGuiElementRenderState(
                RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                float x1, float y1, float x2, float y2, float x3, float y3, int color,
                @Nullable ScreenRect scissorArea
        ) {
            this(pipeline, textureSetup, pose, x1, y1, x2, y2, x3, y3, color, scissorArea,
                    createBounds(x1, y1, x2, y2, x3, y3, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer v) {
            v.vertex(pose, x1, y1).color(color);
            v.vertex(pose, x2, y2).color(color);
            v.vertex(pose, x3, y3).color(color);
        }

        private static @Nullable ScreenRect createBounds(
                float x1, float y1, float x2, float y2, float x3, float y3,
                Matrix3x2fc pose, @Nullable ScreenRect scissorArea
        ) {
            float minX = Math.min(x1, Math.min(x2, x3));
            float minY = Math.min(y1, Math.min(y2, y3));
            float maxX = Math.max(x1, Math.max(x2, x3));
            float maxY = Math.max(y1, Math.max(y2, y3));
            ScreenRect rect = new ScreenRect(Math.round(minX), Math.round(minY),
                    Math.max(1, Math.round(maxX - minX)), Math.max(1, Math.round(maxY - minY)))
                    .transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(rect) : rect;
        }
    }

    private record FloatQuadGuiElementRenderState(
            RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
            float x0, float y0, float x1, float y1, int col1, int col2, boolean horizontal,
            @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState {

        private FloatQuadGuiElementRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x0, float y0, float x1, float y1, int col1, int col2, boolean horizontal, @Nullable ScreenRect scissorArea) {
            this(pipeline, textureSetup, pose, x0, y0, x1, y1, col1, col2, horizontal, scissorArea, createBounds(x0, y0, x1, y1, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer v) {
            if (horizontal) {
                v.vertex(pose, x0, y0).color(col1);
                v.vertex(pose, x0, y1).color(col1);
                v.vertex(pose, x1, y1).color(col2);
                v.vertex(pose, x1, y0).color(col2);
            } else {
                v.vertex(pose, x0, y0).color(col1);
                v.vertex(pose, x0, y1).color(col2);
                v.vertex(pose, x1, y1).color(col2);
                v.vertex(pose, x1, y0).color(col1);
            }
        }

        private static @Nullable ScreenRect createBounds(float x0, float y0, float x1, float y1, Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
            ScreenRect rect = new ScreenRect(Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0)).transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(rect) : rect;
        }
    }
}
