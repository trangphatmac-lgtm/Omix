package cn.omix.util.sigma;

import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

/** Jello's 3:1 sampled, radius-10 Gaussian map zoom glass, refreshed at most ten times per second. */
public final class SigmaMapZoom implements IMinecraft, AutoCloseable {
    private static final AtomicInteger IDS = new AtomicInteger();
    private final Identifier id = Identifier.of("omix", "sigma/map_zoom/" + IDS.incrementAndGet());
    private NativeImageBackedTexture texture;
    private long updated, pressed;
    private boolean plus;

    public void press(boolean plus) { this.plus = plus; pressed = System.nanoTime(); }
    public void draw(DrawContext context, float x, float y, double worldX, double worldZ, float pixelsPerBlock) {
        long now = System.nanoTime();
        if (texture == null || now - updated > 100_000_000L) {
            BufferedImage sampled = new BufferedImage(13, 30, BufferedImage.TYPE_INT_RGB);
            for (int sy = 0; sy < 30; sy++) for (int sx = 0; sx < 13; sx++) sampled.setRGB(sx, sy,
                    SigmaMapCache.get().colorAt((int) Math.floor(worldX + (sx * 3 + 1) / pixelsPerBlock), (int) Math.floor(worldZ + (sy * 3 + 1) / pixelsPerBlock)));
            var blurred = SigmaImageBlur.blur(sampled, 10);
            if (texture == null) {
                texture = new NativeImageBackedTexture(id::toString, new NativeImage(13, 30, false));
                mc.getTextureManager().registerTexture(id, texture);
            }
            for (int sy = 0; sy < 30; sy++) for (int sx = 0; sx < 13; sx++) texture.getImage().setColorArgb(sx, sy, blurred.getRGB(sx, sy));
            texture.upload(); updated = now;
        }
        SigmaDraw.shadow(context, x + 8, y + 8, 24, 74, 20, .5f);
        SigmaDraw.shadow(context, x + 8, y + 8, 24, 74, 14, 1);
        SigmaTexturePolygon.draw(context, TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                x, y, 40, 90, new SigmaTexturePolygon.Clip(x, y, 40, 90, 8), SigmaColors.WHITE);
        if (pressed != 0 && now - pressed < 333_333_333L) {
            float p = (now - pressed) / 333_333_333f, radius = 4 + 36 * p;
            Render2D.beginScissor(context, x, y + (plus ? 0 : 45), 40, 45);
            SigmaShape.rounded(context, x + 20 - radius, y + (plus ? 22.5f : 67.5f) - radius, radius * 2, radius * 2, radius,
                    SigmaColors.alpha(SigmaColors.WHITE, (1 - p * (.5f + .5f * p)) * .4f));
            Render2D.endScissor(context);
        }
        SigmaShape.rounded(context, x, y, 40, 90, 6, SigmaColors.alpha(SigmaColors.BLACK, .3f));
        SigmaResources.medium(20).drawString(context, "+", x + 14, y + 8, SigmaColors.alpha(SigmaColors.WHITE, .8f));
        Render2D.drawRect(context, x + 16, y + 65, 8, 2, SigmaColors.alpha(SigmaColors.WHITE, .8f));
    }
    @Override public void close() { if (texture != null) { mc.getTextureManager().destroyTexture(id); texture = null; } }
}
