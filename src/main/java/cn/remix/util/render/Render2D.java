package cn.remix.util.render;

import cn.remix.util.IMinecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

@UtilityClass
public final class Render2D implements IMinecraft {
    public void init() {
        // Reserved for render setup that must happen during client initialization.
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

    public void drawTriangle(DrawContext context, float centerX, float centerY, float angle, float size, int color) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.rotate(angle);

        drawRect(context, -size * 0.55F, -1.0F, size * 0.75F, 2.0F, color);
        drawArrowWing(context, size * 0.35F, size * 0.45F, 2.35F, color);
        drawArrowWing(context, size * 0.35F, size * 0.45F, -2.35F, color);

        matrices.popMatrix();
    }

    private void drawArrowWing(DrawContext context, float tipX, float length, float angle, int color) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(tipX, 0.0F);
        matrices.rotate(angle);
        drawRect(context, 0.0F, -1.0F, length, 2.0F, color);
        matrices.popMatrix();
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
