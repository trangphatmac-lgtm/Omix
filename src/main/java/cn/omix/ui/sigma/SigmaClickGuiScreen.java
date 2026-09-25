package cn.omix.ui.sigma;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.render.ClickGui;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import cn.omix.util.sigma.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Jello's native panel UI. Music is deliberately absent; profiles use Omix's configuration manager. */
public final class SigmaClickGuiScreen extends Screen implements IMinecraft {
    private final List<SigmaCategoryPanel> panels = new ArrayList<>();
    private final SigmaBrainFreeze snow = new SigmaBrainFreeze();
    private final SigmaAnimation animation = new SigmaAnimation();
    private SigmaSettingsPanel settings;
    private SigmaProfilesPanel profiles;
    private SigmaCategoryPanel dragging;
    private Module binding;
    private float dragX, dragY, uiScale = 1;
    private boolean closing;
    private int openingKey = GLFW.GLFW_KEY_UNKNOWN;

    public SigmaClickGuiScreen() {
        super(Text.literal("Jello ClickGUI"));
        Category[] order = {Category.Combat, Category.Move, Category.Player, Category.World, Category.Render, Category.Exploits};
        for (int i = 0; i < order.length; i++) panels.add(new SigmaCategoryPanel(order[i], 30 + i % 4 * 210, 30 + i / 4 * 330));
    }
    @Override protected void init() { SigmaGuiLayout.read(panels); }
    public int blurRadius() { return Math.round(Math.min(1, animation.value() * 4) * 20); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { /* drawn below */ }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float progress = animation.update(!closing, System.nanoTime(), closing ? 125 : 450);
        if (closing && progress == 0) { mc.setScreen(null); return; }
        if (mc.world == null) SigmaBlur.capture();
        SigmaDraw.begin(context);
        try {
            SigmaBlur.draw(context, 0, 0, SigmaDraw.width(), SigmaDraw.height(), progress);
            Render2D.drawRect(context, 0, 0, SigmaDraw.width(), SigmaDraw.height(), SigmaColors.alpha(SigmaColors.BLACK, .2f * progress));
            var hud = SigmaHud.active();
            if (hud != null && hud.getSigmaBrainFreeze().getValue()) snow.draw(context, progress);
            float elastic = progress == 0 || progress == 1 ? progress : (float) (Math.pow(2, -10 * progress) * Math.sin((progress - .2) * Math.PI * 2 / .8) + 1);
            uiScale = closing ? SigmaAnimation.easeOut(progress) : .5f + elastic * .5f;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(SigmaDraw.width() / 2f, SigmaDraw.height() / 2f);
            context.getMatrices().scale(uiScale, uiScale);
            context.getMatrices().translate(-SigmaDraw.width() / 2f, -SigmaDraw.height() / 2f);
            float mx = panelMouse(mouseX, true), my = panelMouse(mouseY, false);
            for (SigmaCategoryPanel panel : panels) panel.draw(context, settings == null && profiles == null ? mx : -1, settings == null && profiles == null ? my : -1, progress);
            context.getMatrices().popMatrix();
            SigmaDraw.image(context, "jello/options.png", SigmaDraw.width() - 69, SigmaDraw.height() - 55, 55, 41, SigmaColors.alpha(SigmaColors.WHITE, .3f * progress));
            var config = instance.getConfigManager().getCurrentConfig();
            if (config != null) SigmaResources.light(20).drawString(context, config.getName(), 20, SigmaDraw.height() - 36, SigmaColors.alpha(SigmaColors.WHITE, .6f * progress));
            if (binding != null) SigmaResources.light(25).drawString(context, binding.getName() + ": press a key", 20, SigmaDraw.height() - 70, SigmaColors.WHITE);
            if (profiles != null) { profiles.draw(context, mouse(mouseX), mouse(mouseY), progress); if (profiles.closed()) profiles = null; }
            if (settings != null) { settings.draw(context, mouse(mouseX), mouse(mouseY), progress); if (settings.closed()) settings = null; }
        } finally { SigmaDraw.end(context); }
    }
    private float mouse(double coordinate) { return (float) coordinate * mc.getWindow().getScaleFactor(); }
    private float panelMouse(double coordinate, boolean horizontal) {
        float center = (horizontal ? SigmaDraw.width() : SigmaDraw.height()) / 2f;
        return (mouse(coordinate) - center) / Math.max(.01f, uiScale) + center;
    }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        float mx = mouse(click.x()), my = mouse(click.y());
        if (settings != null) { if (!settings.click(mx, my, click.button())) settings.close(); return true; }
        if (profiles != null) { if (!profiles.click(mx, my, click.button())) profiles.close(); return true; }
        if (SigmaSettingsPanel.inside(mx, my, SigmaDraw.width() - 69, SigmaDraw.height() - 55, 55, 41)) { profiles = new SigmaProfilesPanel(); return true; }
        mx = panelMouse(click.x(), true); my = panelMouse(click.y(), false);
        for (int i = panels.size() - 1; i >= 0; i--) {
            SigmaCategoryPanel panel = panels.get(i);
            if (!SigmaSettingsPanel.inside(mx, my, panel.x, panel.y, 200, 320)) continue;
            panels.remove(i); panels.add(panel);
            if (my < panel.y + 60 && click.button() == 0) { dragging = panel; dragX = mx - panel.x; dragY = my - panel.y; }
            else {
                Module module = panel.hit(mx, my);
                if (module != null) {
                    if (click.button() == 0) module.toggle();
                    else if (click.button() == 1) settings = new SigmaSettingsPanel(module);
                    else if (click.button() == 2) binding = module;
                }
            }
            return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (settings != null) return settings.drag(mouse(click.x()), mouse(click.y()));
        if (dragging == null) return false;
        dragging.x = Math.clamp(panelMouse(click.x(), true) - dragX, 0, Math.max(0, SigmaDraw.width() - 200));
        dragging.y = Math.clamp(panelMouse(click.y(), false) - dragY, 0, Math.max(0, SigmaDraw.height() - 60));
        return true;
    }
    @Override public boolean mouseReleased(Click click) { dragging = null; if (settings != null) settings.release(); return true; }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (settings != null) { settings.scroll(vertical); return true; }
        if (profiles != null) { profiles.scroll(vertical); return true; }
        for (int i = panels.size() - 1; i >= 0; i--) { var panel = panels.get(i); if (SigmaSettingsPanel.inside(panelMouse(mx, true), panelMouse(my, false), panel.x, panel.y, 200, 320)) { panel.scroll(vertical); return true; } }
        return true;
    }
    /** Ignore auto-repeat from the press that opened this screen, until the physical key is released. */
    public void ignoreOpeningKeyUntilRelease(int key) { openingKey = key; }

    @Override public boolean keyReleased(KeyInput input) {
        if (input.key() == openingKey) { openingKey = GLFW.GLFW_KEY_UNKNOWN; return true; }
        return super.keyReleased(input);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (openingKey != GLFW.GLFW_KEY_UNKNOWN && input.key() == openingKey) return true;
        if (binding != null) { binding.setKey(input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_DELETE || input.key() == GLFW.GLFW_KEY_BACKSPACE ? -1 : input.key()); binding = null; return true; }
        if (settings != null && settings.key(input) || profiles != null && profiles.key(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (settings != null) settings.close(); else if (profiles != null) profiles.close(); else close(); return true;
        }
        if (settings == null && profiles == null && input.key() == instance.getModuleManager().getModule(ClickGui.class).getKey()) { close(); return true; }
        return super.keyPressed(input);
    }
    @Override public boolean charTyped(CharInput input) { return settings != null && settings.type(input) || profiles != null && profiles.type(input); }
    @Override public void close() { closing = true; }
    @Override public void removed() { SigmaGuiLayout.write(panels); SigmaProfileStorage.saveCurrent(); }
}
