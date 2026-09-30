package cn.omix.ui.skeet;

import cn.omix.config.Config;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.render.ClickGui;
import cn.omix.util.IMinecraft;
import cn.omix.util.misc.KeyUtil;
import cn.omix.util.render.Render2D;
import cn.omix.util.skeet.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

import static cn.omix.util.skeet.SkeetDraw.*;
import static cn.omix.util.skeet.SkeetLayout.*;

/** Native 1.21.11 port of Exhibition-Reborn's Gamesense/Skeet window and controls.
 * Source attribution and MIT license: assets/omix/skeet/LICENSE.
 */
public final class SkeetClickGuiScreen extends Screen implements IMinecraft {
    private static final Category[] CATEGORIES = {Category.Combat, Category.Move, Category.Player,
            Category.World, Category.Render, Category.Exploits};
    private static final String[] ICONS = {"E", "F", "J", "C", "I", "W"};
    // Retain window position, tab and scroll within this client session, like the source's singleton UI.
    private static float savedX = Float.NaN, savedY = Float.NaN;
    private static int savedTab;
    private static final float[] SCROLL = new float[CATEGORIES.length];
    private final SkeetSettings settings = new SkeetSettings();
    private final SkeetProfiles profiles = new SkeetProfiles();
    private SkeetModules.Layout modules;
    private Viewport viewport;
    private Rect content;
    private float x = savedX, y = savedY, opacity, dragX, dragY;
    private float selector, visualScroll;
    private int tab = savedTab, openingKey = GLFW.GLFW_KEY_UNKNOWN;
    private long lastFrame;
    private boolean dragging, closing;
    private Module binding;

    public SkeetClickGuiScreen() { super(Text.literal("Skeet ClickGUI")); selector = tab * 43; }

    @Override protected void init() { updateViewport(); layout(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    private void updateViewport() {
        var w = mc.getWindow();
        viewport = SkeetLayout.viewport(w.getWidth(), w.getHeight(), w.getFramebufferWidth(),
                w.getFramebufferHeight(), w.getScaledWidth(), w.getScaledHeight(), w.getScaleFactor());
        if (!Float.isFinite(x) || !Float.isFinite(y)) { x = (viewport.width() - WIDTH) / 2; y = (viewport.height() - HEIGHT) / 2; }
        x = Math.clamp(x, 4, Math.max(4, viewport.width() - WIDTH - 4));
        y = Math.clamp(y, 4, Math.max(4, viewport.height() - HEIGHT - 4));
        content = new Rect(x + SIDEBAR + 5, y + 23, WIDTH - SIDEBAR - 12, HEIGHT - 43);
    }

    private void layout() {
        if (tab == CATEGORIES.length) {
            profiles.layout(x + SIDEBAR + 15.5f, y + 30, HEIGHT - 68);
            settings.layout(List.of(), content, content);
            return;
        }
        List<Module> live = instance.getModuleManager().getModuleMap().values().stream()
                .filter(module -> module.getCategory() == CATEGORIES[tab])
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        if (binding != null && !live.contains(binding)) binding = null;
        modules = SkeetModules.layout(live, x + SIDEBAR + 15.5f, content.y(), visualScroll);
        SCROLL[tab] = SkeetLayout.scroll(SCROLL[tab], modules.height(), content.height());
        float clamped = SkeetLayout.scroll(visualScroll, modules.height(), content.height());
        if (clamped != visualScroll) {
            visualScroll = clamped;
            modules = SkeetModules.layout(live, x + SIDEBAR + 15.5f, content.y(), visualScroll);
        }
        settings.layout(modules.rows(), content, new Rect(x + SIDEBAR + 5, y + 8, WIDTH - SIDEBAR - 14, HEIGHT - 18));
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float elapsed = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        opacity = Math.clamp(opacity + elapsed * (closing ? -8 : 6), 0, 1);
        if (closing && opacity == 0) { mc.setScreen(null); return; }
        updateViewport();
        float smoothing = 1 - (float) Math.exp(-elapsed * 18);
        selector += (tab * 43 - selector) * smoothing;
        if (tab < CATEGORIES.length) visualScroll += (SCROLL[tab] - visualScroll) * smoothing;
        layout();
        SkeetDraw draw = new SkeetDraw(viewport.fontDensity(), opacity);
        float mx = viewport.mouseX(mouseX), my = viewport.mouseY(mouseY);
        c.getMatrices().pushMatrix();
        c.getMatrices().scale(viewport.scaleX(), viewport.scaleY());
        try {
            drawWindow(c, draw);
            drawTabs(c, draw, mx, my);
            String title = tab < CATEGORIES.length ? CATEGORIES[tab].getName() : "Configs";
            draw.text(c, title, x + SIDEBAR + 16, y + 9, TEXT);
            if (tab < CATEGORIES.length) drawModules(c, draw, mx, my);
            else profiles.draw(c, draw, mx, my);
            String footer = binding != null ? binding.getName() + ": press a key (Esc clears)" : settings.message();
            if (footer.isEmpty()) footer = "OMIX  /  SKEET";
            draw.clipped(c, footer, new Rect(x + SIDEBAR + 16, y + HEIGHT - 15, WIDTH - SIDEBAR - 28, 11), MUTED);
            // 1.21 GUI rendering is deferred; popup quads and text must be in a later root layer.
            c.createNewRootLayer();
            settings.drawOverlay(c, draw, mx, my);
        } finally { c.getMatrices().popMatrix(); }
    }

    private void drawWindow(DrawContext c, SkeetDraw draw) {
        draw.rect(c, x, y, WIDTH, HEIGHT, 0x0a0a0a);
        draw.rect(c, x + .5f, y + .5f, WIDTH - 1, HEIGHT - 1, 0x3c3c3c);
        draw.rect(c, x + 1, y + 1, WIDTH - 2, HEIGHT - 2, 0x282828);
        draw.rect(c, x + 3, y + 3, WIDTH - 6, HEIGHT - 6, 0x2f2f2f);
        Rect inner = new Rect(x + 3.5f, y + 3.5f, WIDTH - 7, HEIGHT - 7);
        draw.rect(c, inner.x(), inner.y(), inner.width(), inner.height(), 0x151515);
        SkeetDraw.scissor(c, inner);
        for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++)
            Render2D.drawTexture(c, BACKGROUND, inner.x() + ix * 325, inner.y() + iy * 275, 325, 275, draw.color(0xffffff));
        Render2D.endScissor(c);
        float hue = (float) (System.nanoTime() / 1_000_000_000d % 42 / 42);
        int a = Color.HSBtoRGB(hue, .4f, 1), b = Color.HSBtoRGB((hue + 1f / 3) % 1, .4f, 1), d = Color.HSBtoRGB((hue + 2f / 3) % 1, .4f, 1);
        float half = (WIDTH - 8) / 2;
        draw.gradient(c, x + 4, y + 4, half, 1, a, b, true);
        draw.gradient(c, x + 4 + half, y + 4, half, 1, b, d, true);
    }

    private Rect tabBounds(int index) { return new Rect(x + 3.5f, y + 19 + index * 43, SIDEBAR, 43); }

    private void drawTabs(DrawContext c, SkeetDraw draw, float mx, float my) {
        float sx = x + 3.5f, sy = y + 19 + selector;
        draw.rect(c, sx, y + 5, SIDEBAR, sy - y - 5, 0x0c0c0c);
        draw.rect(c, sx, sy + 43, SIDEBAR, y + HEIGHT - 3.5f - sy - 43, 0x0c0c0c);
        draw.rect(c, sx + SIDEBAR - 1, y + 5, 1, sy - y - 5, 0x303030);
        draw.rect(c, sx + SIDEBAR - 1, sy + 43, 1, y + HEIGHT - 3.5f - sy - 43, 0x303030);
        draw.rect(c, sx, sy, SIDEBAR, .5f, 0x303030);
        draw.rect(c, sx, sy + 43, SIDEBAR, .5f, 0x303030);
        for (int i = 0; i <= CATEGORIES.length; i++) {
            Rect r = tabBounds(i);
            var font = i == CATEGORIES.length ? draw.header() : draw.icons();
            String icon = i == CATEGORIES.length ? "[+]" : ICONS[i];
            int color = i == tab ? TEXT : r.contains(mx, my) ? 0xc0c0c0 : 0x686868;
            font.drawStringWithShadow(c, icon, r.x() + (r.width() - font.getStringWidth(icon)) / 2,
                    r.y() + (r.height() - font.getHeight()) / 2 - 2, draw.color(color));
            if (r.contains(mx, my)) draw.clipped(c, i == CATEGORIES.length ? "Configs" : CATEGORIES[i].getName(),
                    new Rect(r.x() + 3, r.y() + 31, r.width() - 6, 10), MUTED);
        }
    }

    private void drawModules(DrawContext c, SkeetDraw draw, float mx, float my) {
        SkeetDraw.scissor(c, content);
        for (SkeetModules.Group group : modules.groups()) {
            if (!group.bounds().intersects(content)) continue;
            Module module = group.module();
            draw.group(c, module.getName(), group.bounds());
            Rect enable = group.enable(), bind = group.bind();
            draw.checkbox(c, enable.x(), enable.y() + 2, module.isEnabled(), enable.contains(mx, my));
            draw.clipped(c, "Enable", new Rect(enable.x() + 12, enable.y(), enable.width() - 12, 12), TEXT);
            String key = binding == module ? "[...]" : "[" + KeyUtil.getKeyName(module.getKey()) + "]";
            draw.clipped(c, key, bind, binding == module ? ACCENT : MUTED);
            for (SkeetSettings.Row row : group.rows()) if (row.bounds().intersects(content)) settings.drawRow(c, draw, row, mx, my);
        }
        Render2D.endScissor(c);
        draw.scrollbar(c, content, modules.height(), visualScroll);
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        updateViewport(); layout();
        float mx = viewport.mouseX(click.x()), my = viewport.mouseY(click.y());
        if (settings.overlayClick(mx, my, click.button())) return true;
        binding = null;
        if (click.button() == 0) {
            for (int i = 0; i <= CATEGORIES.length; i++) if (tabBounds(i).contains(mx, my)) { selectTab(i); return true; }
            if (new Rect(x, y, WIDTH, 8).contains(mx, my)) {
                settings.reset(); profiles.blur(); dragging = true; dragX = mx - x; dragY = my - y; return true;
            }
        }
        if (tab == CATEGORIES.length) { profiles.click(mx, my, click.button()); return true; }
        if (settings.click(mx, my, click.button())) return true;
        if (!content.contains(mx, my)) return true;
        for (SkeetModules.Group group : modules.groups()) {
            if (group.bind().contains(mx, my)) {
                if (click.button() == 0) binding = group.module();
                else if (click.button() == 1) group.module().setKey(-1);
                return true;
            }
            if (group.enable().contains(mx, my)) {
                if (click.button() == 0) group.module().toggle();
                else if (click.button() == 2) binding = group.module();
                return true;
            }
        }
        return true;
    }

    private void selectTab(int selected) {
        settings.reset(); profiles.blur(); binding = null; dragging = false;
        tab = selected; visualScroll = tab < CATEGORIES.length ? SCROLL[tab] : 0;
        if (tab == CATEGORIES.length) profiles.refresh();
        layout();
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (closing || click.button() != 0) return true;
        float mx = viewport.mouseX(click.x()), my = viewport.mouseY(click.y());
        if (dragging) { x = mx - dragX; y = my - dragY; updateViewport(); layout(); }
        else settings.drag(mx, my);
        return true;
    }
    @Override public boolean mouseReleased(Click click) {
        if (click.button() == 0) { dragging = false; settings.release(); }
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (closing) return true;
        updateViewport(); layout();
        float px = viewport.mouseX(mx), py = viewport.mouseY(my);
        if (settings.scrollPopup(vertical)) return true;
        if (tab == CATEGORIES.length) profiles.scroll(px, py, vertical);
        else if (content.contains(px, py)) {
            settings.reset(); binding = null;
            SCROLL[tab] = SkeetLayout.scroll(SCROLL[tab] - (float) vertical * 28, modules.height(), content.height());
        }
        return true;
    }

    public void ignoreOpeningKeyUntilRelease(int key) { openingKey = key; }
    @Override public boolean keyReleased(KeyInput input) {
        if (input.key() == openingKey) { openingKey = GLFW.GLFW_KEY_UNKNOWN; return true; }
        return super.keyReleased(input);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (closing || input.key() == openingKey) return true;
        layout();
        if (binding != null) {
            int key = input.key();
            binding.setKey(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? -1 : key);
            binding = null; return true;
        }
        if (settings.key(input) || tab == CATEGORIES.length && profiles.key(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == instance.getModuleManager().getModule(ClickGui.class).getKey()) { close(); return true; }
        return super.keyPressed(input);
    }
    @Override public boolean charTyped(CharInput input) {
        if (closing) return true;
        layout();
        return tab == CATEGORIES.length ? profiles.type(input) : settings.type(input);
    }
    @Override public void close() { closing = true; dragging = false; binding = null; settings.reset(); profiles.blur(); }
    @Override public void removed() {
        savedX = x; savedY = y; savedTab = tab;
        settings.reset(); profiles.blur();
        Config current = instance.getConfigManager().getCurrentConfig();
        // Queue until the opening module's onEnable has finished and reset its transient enabled flag.
        if (current != null) mc.send(current::save);
    }
}
