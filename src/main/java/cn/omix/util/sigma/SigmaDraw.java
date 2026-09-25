package cn.omix.util.sigma;

import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;

/** Jello uses window pixels rather than Minecraft's GUI scale. */
public final class SigmaDraw implements IMinecraft {
    private SigmaDraw() {}

    public static void begin(DrawContext context) {
        context.getMatrices().pushMatrix();
        float scale = 1f / mc.getWindow().getScaleFactor();
        context.getMatrices().scale(scale, scale);
    }

    public static void end(DrawContext context) { context.getMatrices().popMatrix(); }
    public static int width() { return mc.getWindow().getWidth(); }
    public static int height() { return mc.getWindow().getHeight(); }

    public static void image(DrawContext context, String path, float x, float y, float w, float h, int color) {
        Render2D.drawTexture(context, SigmaResources.texture(path), x, y, w, h, color);
    }

    public static void innerShadow(DrawContext context, float x, float y, float w, float h, float size, float opacity) {
        int color = SigmaColors.alpha(SigmaColors.WHITE, opacity);
        image(context, "jello/shadow_right.png", x, y, size, h, color);
        image(context, "jello/shadow_left.png", x + w - size, y, size, h, color);
        image(context, "jello/shadow_bottom.png", x, y, w, size, color);
        image(context, "jello/shadow_top.png", x, y + h - size, w, size, color);
    }

    /** The original method named drawRoundedRect actually draws this nine-slice outer shadow. */
    public static void shadow(DrawContext context, float x, float y, float w, float h, float size, float opacity) {
        int color = SigmaColors.alpha(SigmaColors.WHITE, opacity);
        image(context, "jello/shadow_corner.png", x - size, y - size, size, size, color);
        image(context, "jello/shadow_corner_2.png", x + w, y - size, size, size, color);
        image(context, "jello/shadow_corner_3.png", x - size, y + h, size, size, color);
        image(context, "jello/shadow_corner_4.png", x + w, y + h, size, size, color);
        image(context, "jello/shadow_left.png", x - size, y, size, h, color);
        image(context, "jello/shadow_right.png", x + w, y, size, h, color);
        image(context, "jello/shadow_top.png", x, y - size, w, size, color);
        image(context, "jello/shadow_bottom.png", x, y + h, w, size, color);
    }
}
