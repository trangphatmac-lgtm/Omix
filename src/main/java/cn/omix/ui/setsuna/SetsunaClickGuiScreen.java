package cn.omix.ui.setsuna;

import cn.omix.config.Config;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.render.ClickGui;
import cn.omix.module.value.Value;
import cn.omix.util.IMinecraft;
import cn.omix.util.misc.KeyUtil;
import cn.omix.util.render.Render2D;
import cn.omix.util.setsuna.SetsunaDraw;
import cn.omix.util.setsuna.SetsunaSettings;
import cn.omix.util.sigma.SigmaBlur;
import cn.omix.util.sigma.SigmaDraw;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static cn.omix.util.setsuna.SetsunaLayout.*;

/** SetsunaClient's Pop radial menu, ported to Omix/1.21.11. See assets/omix/setsuna/NOTICE.txt. */
public final class SetsunaClickGuiScreen extends Screen implements IMinecraft {
    private static final Category[] CATEGORIES = {Category.Combat, Category.Exploits, Category.Render,
            Category.Move, Category.Player, Category.World};
    private static final String[] ICONS = {"\uE2B4", "\uE29C", "\uE1DD", "\uE3B9", "\uE19F", "\uE154"};
    private static final int[] COLORS = {0xff45b8ea, 0xff718af4, 0xff51d4c6, 0xff43c4e2, 0xff7781f2, 0xff4ad9a6};
    private static final Identifier LOGO = Identifier.of("omix", "setsuna/logo.png");
    private final SetsunaSettings settings = new SetsunaSettings();
    private final Map<Module, Float> enabled = new IdentityHashMap<>();
    private final float[] hover = new float[CATEGORIES.length];
    private Viewport viewport;
    private Panels panels;
    private Rect modulePanel;
    private List<Module> modules = List.of();
    private int category = -1, openingKey = GLFW.GLFW_KEY_UNKNOWN;
    private Module selected, binding;
    private boolean categoryRequested, settingsRequested, closing;
    private float intro, categoryProgress, settingsProgress, daylight, moduleScroll, settingScroll;
    private long lastFrame;

    public SetsunaClickGuiScreen() { super(Text.literal("Setsuna ClickGUI")); }
    private ClickGui options() { return instance.getModuleManager().getModule(ClickGui.class); }
    @Override protected void init() {
        if (lastFrame == 0) daylight = options().setsunaDaylight() ? 1 : 0;
        lastFrame = System.nanoTime(); layout();
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    public int blurRadius() { return Math.round(options().setsunaBlur() * 4 * smooth(intro)); }
    public boolean usesBlur() { return options().setsunaBlur() > 0; }

    private List<Value> values() { return selected == null ? List.of() : selected.getValues(); }
    private void layout() {
        var window = mc.getWindow();
        viewport = viewport(width, height, window.getFramebufferWidth(), window.getFramebufferHeight(), window.getScaleFactor(), options().setsunaScale());
        modules = category < 0 ? List.of() : instance.getModuleManager().getModuleMap().values().stream()
                .filter(module -> module.getCategory() == CATEGORIES[category])
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        enabled.keySet().removeIf(module -> !modules.contains(module));
        if (selected != null && !modules.contains(selected)) { selected = null; settingsRequested = false; settings.reset(); }
        if (binding != null && !modules.contains(binding)) binding = null;
        panels = panels(viewport.width(), viewport.height(), settingsProgress);
        modulePanel = category < 0 ? panels.modules() : expand(bubble(category, CATEGORIES.length, viewport.width(), viewport.height()), panels.modules(), categoryProgress);
        moduleScroll = scroll(moduleScroll, modules.size() * MODULE_ROW, modulePanel.body().height());
        settingScroll = scroll(settingScroll, settings.contentHeight(values()), panels.settings().body().height());
        settings.layout(values(), panels.settings().body(), settingScroll);
    }
    @Override public void render(DrawContext c, int mouseX, int mouseY, float tickDelta) {
        long now = System.nanoTime();
        float delta = Math.clamp((now - lastFrame) / 1_000_000_000f, 0, .05f); lastFrame = now;
        intro = animate(intro, closing ? 0 : 1, closing ? 13.5f : 7.5f, delta);
        categoryProgress = animate(categoryProgress, categoryRequested ? 1 : 0, closing ? 16.5f : 9.5f, delta);
        settingsProgress = animate(settingsProgress, settingsRequested ? 1 : 0, closing ? 18 : 10.5f, delta);
        daylight = animate(daylight, options().setsunaDaylight() ? 1 : 0, 7, delta);
        if (closing && intro < .005f) { mc.setScreen(null); return; }
        if (!categoryRequested && categoryProgress < .01f) { category = -1; selected = null; }
        if (!settingsRequested && settingsProgress < .01f) { selected = null; settingScroll = 0; }
        layout();
        if (usesBlur()) {
            if (mc.world == null) SigmaBlur.capture();
            SigmaDraw.begin(c);
            SigmaBlur.draw(c, 0, 0, SigmaDraw.width(), SigmaDraw.height(), smooth(intro));
            SigmaDraw.end(c);
        }
        float mx = viewport.mouse(mouseX), my = viewport.mouse(mouseY);
        var draw = new SetsunaDraw(viewport.density(), smooth(intro), daylight, options().setsunaAccent());
        c.getMatrices().pushMatrix();
        c.getMatrices().scale(viewport.renderX(), viewport.renderY());
        try {
            draw.rect(c, 0, 0, viewport.width() + 1, viewport.height() + 1, 0x4602080c);
            c.getMatrices().pushMatrix();
            if (closing) {
                float scale = lerp(.16f, 1, smooth(intro));
                c.getMatrices().translate(viewport.width() / 2, viewport.height() / 2);
                c.getMatrices().scale(scale, scale);
                c.getMatrices().translate(-viewport.width() / 2, -viewport.height() / 2);
            }
            drawRing(c, draw, mx, my, delta);
            if (category >= 0) {
                c.createNewRootLayer();
                drawModules(c, draw, mx, my, delta);
                if (selected != null && settingsProgress > .01f) {
                    c.createNewRootLayer();
                    drawSettings(c, draw, mx, my, delta);
                }
            }
            c.getMatrices().popMatrix();
            c.createNewRootLayer();
            String hint = binding != null ? binding.getName() + ": press a key / Esc cancels / Delete clears"
                    : category < 0 ? "Choose a category / Esc closes" : settings.hint();
            draw.centered(c, hint, viewport.width() / 2, viewport.height() - 5, draw.dim(), 7, false);
        } finally { c.getMatrices().popMatrix(); }
    }
    private void drawRing(DrawContext c, SetsunaDraw draw, float mx, float my, float delta) {
        float cx = viewport.width() / 2, cy = viewport.height() / 2, collapse = smooth(categoryProgress), opening = smooth(intro);
        draw.ring(c, cx, cy, ringRadius(viewport.width(), viewport.height()) * opening, SetsunaDraw.alpha(draw.accent, .27f * (1 - collapse)));
        for (int i = 0; i < CATEGORIES.length; i++) {
            Point point = bubble(i, CATEGORIES.length, viewport.width(), viewport.height());
            boolean active = i == category;
            float disappear = active ? 1 : 1 - collapse;
            hover[i] = animate(hover[i], category < 0 && Math.hypot(mx - point.x(), my - point.y()) <= BUBBLE_RADIUS + 6 ? 1 : 0, 12, delta);
            float radius = BUBBLE_RADIUS + hover[i] * 4;
            float x = lerp(cx, point.x(), opening), y = lerp(cy, point.y(), opening);
            if (active) {
                x = lerp(x, panels.modules().x() + panels.modules().width() / 2, collapse);
                y = lerp(y, panels.modules().y() + HEADER / 2, collapse);
                radius = lerp(radius, 15, collapse);
            }
            SetsunaDraw bubbleDraw = draw.faded(disappear);
            bubbleDraw.circle(c, x, y, radius + 1, SetsunaDraw.alpha(COLORS[i], .47f));
            bubbleDraw.circle(c, x, y, radius, bubbleDraw.panel());
            float iconAlpha = active ? 1 - smooth(collapse) : 1;
            bubbleDraw.faded(iconAlpha).icon(c, ICONS[i], x, y - 6, COLORS[i], 14);
            if (!active || collapse < .35f) bubbleDraw.faded(iconAlpha).centered(c, CATEGORIES[i].getName(), x, y + 9, draw.text(), 7, true);
        }
        if (category < 0) {
            float size = 38 * lerp(.78f, 1, opening);
            Render2D.drawTexture(c, LOGO, cx - size / 2, cy - size / 2, size, size, draw.color(0xffffffff));
        }
    }
    private void header(DrawContext c, SetsunaDraw draw, Rect r, String title, boolean module) {
        int color = COLORS[category];
        draw.circle(c, r.x() + 22, r.y() + 22, 14, SetsunaDraw.alpha(color, .8f));
        if (module) drawKeyBadge(c, draw, r.x() + 22, r.y() + 22);
        else draw.icon(c, ICONS[category], r.x() + 22, r.y() + 22, draw.text(), 11);
        draw.label(c, title, new Rect(r.x() + 44, r.y(), r.width() - 56, HEADER), draw.text(), 9, true);
        draw.rect(c, r.x() + 12, r.y() + HEADER - 1, r.width() - 24, 1, SetsunaDraw.alpha(color, .41f));
    }
    private void drawKeyBadge(DrawContext c, SetsunaDraw draw, float x, float y) {
        if (binding == selected || selected.getKey() > 0) {
            String label = binding == selected ? "..." : KeyUtil.getKeyName(selected.getKey());
            SetsunaDraw.scissor(c, new Rect(x - 11, y - 8, 22, 16));
            draw.centered(c, label, x, y, draw.text(), label.length() <= 2 ? 8 : 6, true);
            c.disableScissor();
            return;
        }
        draw.rounded(c, new Rect(x - 7.5f, y - 5, 15, 10), 2, draw.text());
        draw.rounded(c, new Rect(x - 6.3f, y - 3.8f, 12.6f, 7.6f), 1.2f, draw.panel());
        for (int row = 0; row < 2; row++) for (int col = 0; col < 4; col++)
            draw.rounded(c, new Rect(x - 5.2f + col * 2.8f, y - 2.8f + row * 2.8f, 1.7f, 1.5f), .4f, draw.text());
        draw.rounded(c, new Rect(x - 3.7f, y + 2.4f, 7.4f, 1), .4f, draw.text());
    }
    private void drawModules(DrawContext c, SetsunaDraw draw, float mx, float my, float delta) {
        float progress = smooth(categoryProgress);
        draw.faded(progress).panel(c, modulePanel, lerp(BUBBLE_RADIUS, 9, progress), COLORS[category]);
        if (progress < .36f) return;
        SetsunaDraw content = draw.faded(smooth((progress - .36f) / .64f));
        header(c, content, modulePanel, CATEGORIES[category].getName(), false);
        Rect body = modulePanel.body();
        SetsunaDraw.scissor(c, body);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            Rect r = new Rect(body.x(), body.y() + i * MODULE_ROW - moduleScroll, body.width(), MODULE_ROW);
            if (!r.intersects(body)) continue;
            float on = animate(enabled.getOrDefault(module, module.isEnabled() ? 1f : 0f), module.isEnabled() ? 1 : 0, 12, delta);
            enabled.put(module, on);
            boolean hovered = r.contains(mx, my) && body.contains(mx, my);
            if (hovered || on > .01f || selected == module) content.rounded(c,
                    new Rect(r.x() + 7, r.y() + 2, r.width() - 14, r.height() - 4), 5,
                    hovered ? content.hover() : SetsunaDraw.alpha(COLORS[category], selected == module ? .3f : .11f + on * .14f));
            content.label(c, module.getName(), new Rect(r.x() + 14, r.y(), r.width() - 45, r.height()),
                    SetsunaDraw.mix(content.dim(), content.text(), on), 8, false);
            content.circle(c, r.right() - 18, r.y() + MODULE_ROW / 2, 2.5f + on,
                    SetsunaDraw.mix(content.faint(), COLORS[category], on));
        }
        if (modules.isEmpty()) content.centered(c, "No modules", body.x() + body.width() / 2, body.y() + 22, content.dim(), 8, false);
        c.disableScissor();
        content.scrollbar(c, body, modules.size() * MODULE_ROW, moduleScroll, COLORS[category]);
    }
    private void drawSettings(DrawContext c, SetsunaDraw draw, float mx, float my, float delta) {
        float progress = smooth(settingsProgress);
        Rect r = panels.settings();
        draw.faded(progress).panel(c, r, 9, COLORS[category]);
        if (progress < .18f) return;
        SetsunaDraw content = draw.faded(smooth((progress - .18f) / .82f));
        header(c, content, r, selected.getName(), true);
        settings.draw(c, content, mx, my, delta);
        content.scrollbar(c, r.body(), settings.contentHeight(values()), settingScroll, COLORS[category]);
        if (settings.contentHeight(values()) == 0) content.centered(c, "No settings", r.x() + r.width() / 2, r.y() + HEADER + 22, content.dim(), 8, false);
        if (new Rect(r.x() + 8, r.y() + 8, 28, 28).contains(mx, my)) {
            c.createNewRootLayer();
            Rect tip = new Rect(r.x() + 8, r.y() + 33, r.width() - 16, 16);
            content.rounded(c, tip, 4, content.inner());
            content.label(c, binding == selected ? "Press a key..." : "Bind: " + KeyUtil.getKeyName(selected.getKey()),
                    new Rect(tip.x() + 4, tip.y(), tip.width() - 8, tip.height()), content.text(), 8, false);
        }
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        layout();
        float mx = viewport.mouse(click.x()), my = viewport.mouse(click.y()); int button = click.button();
        if (category < 0) {
            if (button == 0) for (int i = 0; i < CATEGORIES.length; i++) {
                Point point = bubble(i, CATEGORIES.length, viewport.width(), viewport.height());
                if (Math.hypot(mx - point.x(), my - point.y()) <= BUBBLE_RADIUS + 6) {
                    category = i; categoryRequested = true; moduleScroll = 0; break;
                }
            }
            return true;
        }
        // A key value may deliberately capture a mouse button; dispatch before other UI actions.
        if (settings.captureMouse(button)) return true;
        binding = null;
        if (settingsRequested && settingsProgress > .7f && selected != null) {
            Rect r = panels.settings();
            if (new Rect(r.x() + 8, r.y() + 8, 28, 28).contains(mx, my)) {
                settings.reset();
                if (button == 0) binding = selected;
                else if (button == 1) selected.setKey(-1);
                return true;
            }
            if (settings.click(mx, my, button)) return true;
        }
        settings.reset();
        if (categoryProgress <= .75f || !categoryRequested) return true;
        if (new Rect(modulePanel.x() + 8, modulePanel.y() + 8, 28, 28).contains(mx, my)) {
            categoryRequested = false; settingsRequested = false; return true;
        }
        Rect body = modulePanel.body();
        if (body.contains(mx, my)) {
            int index = (int) ((my - body.y() + moduleScroll) / MODULE_ROW);
            if (index >= 0 && index < modules.size()) {
                Module module = modules.get(index);
                if (button == 0) module.toggle();
                else if (button == 1) {
                    if (selected == module && settingsRequested) settingsRequested = false;
                    else { selected = module; settingsRequested = true; settingScroll = 0; }
                } else if (button == 2) binding = module;
            }
        }
        return true;
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (!closing && click.button() == 0) { layout(); settings.drag(viewport.mouse(click.x())); }
        return true;
    }
    @Override public boolean mouseReleased(Click click) { if (click.button() == 0) settings.release(); return true; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (closing) return true;
        layout(); float mx = viewport.mouse(x), my = viewport.mouse(y);
        settings.reset(); binding = null;
        if (settingsRequested && settingsProgress > .7f && panels.settings().body().contains(mx, my))
            settingScroll = scroll(settingScroll - (float) vertical * 24, settings.contentHeight(values()), panels.settings().body().height());
        else if (categoryRequested && categoryProgress > .75f && modulePanel.body().contains(mx, my))
            moduleScroll = scroll(moduleScroll - (float) vertical * 24, modules.size() * MODULE_ROW, modulePanel.body().height());
        layout(); return true;
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
            if (key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_UNKNOWN)
                binding.setKey(key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? -1 : key);
            binding = null; return true;
        }
        if (settings.key(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (settingsRequested) { settingsRequested = false; settings.reset(); }
            else if (categoryRequested) { categoryRequested = false; settings.reset(); }
            else close();
            return true;
        }
        if (input.key() == options().getKey()) { close(); return true; }
        return super.keyPressed(input);
    }
    @Override public boolean charTyped(CharInput input) { if (closing) return true; layout(); return settings.type(input); }
    @Override public void close() { closing = true; categoryRequested = false; settingsRequested = false; binding = null; settings.reset(); }
    @Override public void removed() {
        settings.reset(); binding = null;
        Config current = instance.getConfigManager().getCurrentConfig();
        // Defer until ClickGui.onEnable has reset its transient enabled state, including screen replacements.
        if (current != null) mc.send(current::save);
    }
}
