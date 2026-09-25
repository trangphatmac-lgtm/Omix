package cn.omix.util.move;

import cn.omix.module.value.impl.NumberValue;
import cn.omix.ui.font.TrueTypeFont;
import cn.omix.util.IMinecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public final class TimerBalanceHud implements TimerBalance.DragPosition, TimerBalance.HudRenderer, IMinecraft {
    private final NumberValue positionX;
    private final NumberValue positionY;
    private float initialX, initialY, width = 140, height = 32;
    private float grabX, grabY;
    private boolean dragging;
    private DrawContext context;

    public TimerBalanceHud(NumberValue positionX, NumberValue positionY) {
        this.positionX = positionX;
        this.positionY = positionY;
    }

    public void initialize(float x, float y) {
        initialX = x;
        initialY = y;
    }

    public float x() { return positionX.getValue() < 0 ? initialX : positionX.getValue() * mc.getWindow().getScaledWidth(); }
    public float y() { return positionY.getValue() < 0 ? initialY : positionY.getValue() * mc.getWindow().getScaledHeight(); }
    public void width(float width) { this.width = width; }
    public void height(float height) { this.height = height; }

    public void stopDragging() { dragging = false; }

    public void drag(float mouseX, float mouseY) {
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            stopDragging();
            return;
        }
        if (!dragging && mouseX >= x() && mouseX <= x() + width && mouseY >= y() && mouseY <= y() + height) {
            dragging = true;
            grabX = mouseX - x();
            grabY = mouseY - y();
        }
        if (dragging) {
            float sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            positionX.setValue(Math.max(0, Math.min(sw - width, mouseX - grabX)) / sw);
            positionY.setValue(Math.max(0, Math.min(sh - height, mouseY - grabY)) / sh);
        }
    }

    public void render(DrawContext context, TimerBalance balance) {
        if (!(mc.currentScreen instanceof ChatScreen) || !"Balance".equalsIgnoreCase(balance.settings.mode)) stopDragging();
        this.context = context;
        try {
            balance.onRender(this);
        } finally {
            this.context = null;
        }
    }

    public void save() { context.getMatrices().pushMatrix(); }
    public void restore() { context.getMatrices().popMatrix(); }
    public void translate(float x, float y) { context.getMatrices().translate(x, y); }
    public void scale(float scale) { context.getMatrices().scale(scale, scale); }

    public void roundedRect(float x, float y, float width, float height, float radius, Color color) {
        if (width <= 0 || height <= 0 || color.getAlpha() == 0) return;
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect bounds = new ScreenRect((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(width) + 1, (int) Math.ceil(height) + 1).transformEachVertex(pose);
        ScreenRect scissor = context.scissorStack.peekLast();
        if (scissor != null) bounds = bounds.intersection(scissor);
        context.state.addSimpleElement(new RoundedPanel(pose, x, y, width, height,
                Math.min(radius, Math.min(width, height) / 2), color.getRGB(), scissor, bounds));
    }

    public void shadow(float x, float y, float width, float height, float radius, Color color) {
        // The reference uses Skija blur. Approximate that host primitive with soft
        // concentric geometry; preserve its tint, radius, alpha and call ordering.
        for (int layer = 12; layer >= 1; layer--) {
            float spread = radius * layer / 12;
            roundedRect(x - spread, y - spread, width + spread * 2, height + spread * 2,
                    radius + spread, TimerBalance.alpha(color, 0.035F + (12 - layer) * 0.004F));
        }
    }

    public Object font(float size) { return instance.getFontManager().getFont(Math.round(size * 2)); }
    public float textWidth(String text, Object font) { return ((TrueTypeFont) font).getStringWidth(text); }
    public void text(String text, float x, float y, Color color, Object font) {
        TrueTypeFont face = (TrueTypeFont) font;
        face.drawString(context, text, x, y - face.getHeight(), color.getRGB());
    }

    private record RoundedPanel(Matrix3x2fc pose, float x, float y, float width, float height,
                                float radius, int color, ScreenRect scissorArea, ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        public TextureSetup textureSetup() { return TextureSetup.empty(); }

        public void setupVertices(VertexConsumer vertices) {
            for (int corner = 0; corner < 4; corner++) {
                float cx = x + (corner == 0 || corner == 3 ? radius : width - radius);
                float cy = y + (corner < 2 ? radius : height - radius);
                float start = (float) Math.PI * (1 + corner * 0.5F);
                float px = cx + (float) Math.cos(start) * radius;
                float py = cy + (float) Math.sin(start) * radius;
                for (int step = 1; step <= 8; step++) {
                    float angle = start + (float) Math.PI * 0.5F * step / 8;
                    float nx = cx + (float) Math.cos(angle) * radius;
                    float ny = cy + (float) Math.sin(angle) * radius;
                    triangle(vertices, px, py, nx, ny);
                    px = nx;
                    py = ny;
                }
                // Connect the arc to the following corner along the straight edge.
                float nx = switch (corner) { case 0 -> x + width - radius; case 1 -> x + width; case 2 -> x + radius; default -> x; };
                float ny = switch (corner) { case 0 -> y; case 1 -> y + height - radius; case 2 -> y + height; default -> y + radius; };
                triangle(vertices, px, py, nx, ny);
            }
        }

        private void triangle(VertexConsumer vertices, float ax, float ay, float bx, float by) {
            vertices.vertex(pose, x + width / 2, y + height / 2).color(color);
            vertices.vertex(pose, bx, by).color(color);
            vertices.vertex(pose, ax, ay).color(color);
            vertices.vertex(pose, ax, ay).color(color);
        }
    }
}
