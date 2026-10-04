package cn.omix.util.setsuna;

import cn.omix.Client;
import cn.omix.ui.font.TrueTypeFont;
import cn.omix.util.render.Render2D;
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

import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.omix.util.setsuna.SetsunaLayout.*;

/** Native deferred GUI renderer replacing the source's Skija canvas. */
public final class SetsunaDraw {
    private static final Map<String, Font> RAW = new HashMap<>();
    private static final Map<String, TrueTypeFont> FONTS = new HashMap<>();
    private final int density;
    private final float opacity, daylight;
    public final int accent;

    public SetsunaDraw(int density, float opacity, float daylight, int accent) {
        this.density = density; this.opacity = opacity; this.daylight = smooth(daylight); this.accent = accent;
    }
    private SetsunaDraw(SetsunaDraw source, float amount) {
        density = source.density; opacity = source.opacity * amount; daylight = source.daylight; accent = source.accent;
    }
    public SetsunaDraw faded(float amount) { return new SetsunaDraw(this, amount); }
    public int panel() { return mix(0xf511171c, 0xf5f8fafc, daylight); }
    public int inner() { return mix(0xcd192026, 0xe0ebf0f3, daylight); }
    public int text() { return mix(0xfff3f7f8, 0xff1d282e, daylight); }
    public int dim() { return mix(0xffa3afb4, 0xff59686f, daylight); }
    public int faint() { return mix(0xff68767c, 0xff929da2, daylight); }
    public int track() { return mix(0xff3a464d, 0xffc9d3d8, daylight); }
    public int hover() { return mix(0x9b7ad6ee, 0x3a52aed6, daylight); }
    public int color(int argb) { return alpha(argb, opacity); }

    public TrueTypeFont font(int size, boolean bold) { return font(bold ? "MiSans-Semibold.ttf" : "MiSans-Medium.ttf", size); }
    private TrueTypeFont font(String name, int size) {
        return FONTS.computeIfAbsent(name + ":" + size + ":" + density, key -> {
            Font raw = RAW.computeIfAbsent(name, SetsunaDraw::loadFont);
            return new TrueTypeFont(raw.deriveFont((float) size * density),
                    List.of(new Font("Dialog", Font.PLAIN, size * density)), density, 1024, true);
        });
    }
    private static Font loadFont(String name) {
        String root = name.equals("lucide.ttf") ? "/assets/omix/setsuna/" : "/assets/omix/fonts/";
        try (var input = SetsunaDraw.class.getResourceAsStream(root + name)) {
            if (input != null) return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (Exception error) { Client.logger.warn("Cannot load Setsuna font {}", name, error); }
        return new Font("Dialog", Font.PLAIN, 10);
    }
    public void text(DrawContext c, String label, float x, float y, int color, int size, boolean bold) {
        font(size, bold).drawString(c, label, x, y, color(color));
    }
    public void label(DrawContext c, String label, Rect r, int color, int size, boolean bold) {
        var font = font(size, bold);
        scissor(c, r);
        font.drawString(c, label, r.x(), r.y() + (r.height() - font.getHeight()) / 2, color(color));
        c.disableScissor();
    }
    public void centered(DrawContext c, String label, float cx, float cy, int color, int size, boolean bold) {
        var font = font(size, bold);
        font.drawString(c, label, cx - font.getStringWidth(label) / 2, cy - font.getHeight() / 2, color(color));
    }
    public void icon(DrawContext c, String glyph, float cx, float cy, int color, int size) {
        var font = font("lucide.ttf", size);
        font.drawString(c, glyph, cx - font.getStringWidth(glyph) / 2, cy - font.getHeight() / 2, color(color));
    }
    public void right(DrawContext c, String label, Rect r, int color, int size) {
        var font = font(size, false);
        scissor(c, r);
        font.drawString(c, label, Math.max(r.x(), r.right() - font.getStringWidth(label)),
                r.y() + (r.height() - font.getHeight()) / 2, color(color));
        c.disableScissor();
    }
    public void rect(DrawContext c, float x, float y, float w, float h, int color) { Render2D.drawRect(c, x, y, w, h, color(color)); }
    public void rounded(DrawContext c, Rect r, float radius, int color) {
        if (r.width() <= 0 || r.height() <= 0) return;
        float safe = Math.min(radius, Math.min(r.width(), r.height()) / 2);
        addShape(c, r, safe, 0, color(color));
    }
    public void circle(DrawContext c, float x, float y, float radius, int color) {
        rounded(c, new Rect(x - radius, y - radius, radius * 2, radius * 2), radius, color);
    }
    public void ring(DrawContext c, float x, float y, float radius, int color) {
        addShape(c, new Rect(x - radius, y - radius, radius * 2, radius * 2), radius, 1, color(color));
    }
    public void panel(DrawContext c, Rect r, float radius, int edge) {
        rounded(c, new Rect(r.x() - 3, r.y() - 2, r.width() + 6, r.height() + 6), radius + 3, 0x20000000);
        rounded(c, new Rect(r.x() - 1, r.y() - 1, r.width() + 2, r.height() + 2), radius + 1, alpha(edge, .58f));
        rounded(c, r, radius, panel());
    }
    public void slider(DrawContext c, Rect r, float progress, int color) {
        float p = Math.clamp(progress, 0, 1), cy = r.y() + r.height() / 2;
        rounded(c, new Rect(r.x(), cy - 1, r.width(), 2), 1, track());
        rounded(c, new Rect(r.x(), cy - 1, r.width() * p, 2), 1, color);
        rounded(c, new Rect(r.x() + r.width() * p - 3, cy - 4, 6, 8), 3, color);
    }
    public void scrollbar(DrawContext c, Rect r, float content, float scroll, int color) {
        if (content <= r.height() || r.height() <= 0) return;
        float h = Math.min(r.height(), Math.max(18, r.height() * r.height() / content));
        float y = r.y() + scroll / (content - r.height()) * (r.height() - h);
        rounded(c, new Rect(r.right() - 3, r.y(), 2, r.height()), 1, track());
        rounded(c, new Rect(r.right() - 3, y, 2, h), 1, color);
    }
    public static void scissor(DrawContext c, Rect r) {
        c.enableScissor((int) Math.floor(r.x()), (int) Math.floor(r.y()), (int) Math.ceil(r.right()), (int) Math.ceil(r.bottom()));
    }
    public static int alpha(int argb, float opacity) { return Math.round((argb >>> 24) * Math.clamp(opacity, 0, 1)) << 24 | argb & 0xffffff; }
    public static int mix(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) result |= Math.round(lerp((a >>> shift) & 255, (b >>> shift) & 255, t)) << shift;
        return result;
    }

    private static void addShape(DrawContext c, Rect rect, float radius, float stroke, int color) {
        if ((color >>> 24) == 0) return;
        var pose = new Matrix3x2f(c.getMatrices());
        var clip = c.scissorStack.peekLast();
        // Include the half-pixel antialias fringe in deferred rendering's bounds.
        var bounds = new ScreenRect((int) Math.floor(rect.x() - 1), (int) Math.floor(rect.y() - 1),
                (int) Math.ceil(rect.width() + 3), (int) Math.ceil(rect.height() + 3)).transformEachVertex(pose);
        c.state.addSimpleElement(new Shape(pose, rect, radius, stroke, color, clip,
                clip == null ? bounds : clip.intersection(bounds)));
    }
    private record Shape(Matrix3x2fc pose, Rect rect, float radius, float stroke, int color,
                         @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds) implements SimpleGuiElementRenderState {
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.empty(); }
        private Point point(int index, float inset) {
            int corner = index / 17;
            double angle = Math.toRadians(corner * 90 + (index % 17) * 90f / 16);
            float cx = corner == 0 || corner == 3 ? rect.right() - radius : rect.x() + radius;
            float cy = corner < 2 ? rect.bottom() - radius : rect.y() + radius;
            return new Point(cx + (float) Math.cos(angle) * Math.max(0, radius - inset),
                    cy + (float) Math.sin(angle) * Math.max(0, radius - inset));
        }
        private void vertex(VertexConsumer v, Point p, int argb) { v.vertex(pose, p.x(), p.y()).color(argb); }
        @Override public void setupVertices(VertexConsumer v) {
            Point center = new Point(rect.x() + rect.width() / 2, rect.y() + rect.height() / 2);
            for (int i = 0; i < 68; i++) {
                int next = (i + 1) % 68;
                Point a = point(i, 0), b = point(next, 0);
                vertex(v, a, color); vertex(v, stroke > 0 ? point(i, stroke) : center, color);
                vertex(v, stroke > 0 ? point(next, stroke) : center, color); vertex(v, b, color);
                vertex(v, point(i, -.35f), color & 0xffffff); vertex(v, a, color);
                vertex(v, b, color); vertex(v, point(next, -.35f), color & 0xffffff);
            }
        }
    }
}
