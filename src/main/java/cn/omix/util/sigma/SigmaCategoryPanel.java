package cn.omix.util.sigma;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Original 200 × 320 Jello panel, with 60-pixel heading and 30-pixel module rows. */
public final class SigmaCategoryPanel implements IMinecraft {
    public final Category category;
    private final List<Module> modules = new ArrayList<>();
    public float x, y, scroll;
    private float renderedScroll;
    private long lastFrame;
    private String filter = "";

    public SigmaCategoryPanel(Category category, float x, float y) { this.category = category; this.x = x; this.y = y; refresh(""); }
    public void refresh(String filter) {
        this.filter = filter;
        modules.clear();
        for (Module module : instance.getModuleManager().getModuleMap().values()) if (module.getCategory() == category
                && module.getName().toLowerCase(java.util.Locale.ROOT).contains(filter)) modules.add(module);
        clampScroll();
    }
    public void draw(DrawContext context, float mx, float my, float alpha) {
        long now = System.nanoTime(); float dt = lastFrame == 0 ? 1 : Math.clamp((now - lastFrame) / 1_000_000_000f, 0, .1f); lastFrame = now;
        renderedScroll += (scroll - renderedScroll) * (1 - (float) Math.exp(-20 * dt));
        SigmaDraw.shadow(context, x, y, 200, 320, 20, alpha);
        Render2D.drawRect(context, x, y, 200, 60, SigmaColors.alpha(SigmaColors.WHITE, alpha * .9f));
        Render2D.drawRect(context, x, y + 60, 200, 260, SigmaColors.alpha(SigmaColors.WHITE, alpha));
        var title = SigmaResources.light(25);
        title.drawString(context, category.getName(), x + 20, y + 30 - title.getHeight() / 2, SigmaColors.alpha(SigmaColors.BLACK, alpha * .5f));
        Render2D.beginScissor(context, x, y + 60, 200, 260);
        try {
            var font = SigmaResources.light(20);
            for (int i = 0; i < modules.size(); i++) {
                float rowY = y + 60 + i * 30 - renderedScroll;
                if (rowY + 30 < y + 60 || rowY > y + 320) continue;
                Module module = modules.get(i);
                boolean hover = SigmaSettingsPanel.inside(mx, my, x, rowY, 200, 30);
                int color = module.isEnabled() ? (hover ? -14042881 : SigmaSettingsPanel.BLUE) : hover ? 0 : 1895167477;
                Render2D.drawRect(context, x, rowY, 200, 30, SigmaColors.alpha(color, alpha));
                font.drawString(context, module.getName(), x + (module.isEnabled() ? 30 : 22), rowY + 15 - font.getHeight() / 2,
                        SigmaColors.alpha(module.isEnabled() ? SigmaColors.WHITE : SigmaColors.BLACK, alpha));
            }
        } finally { Render2D.endScissor(context); }
        if (renderedScroll > .1f) SigmaDraw.image(context, "jello/shadow_bottom.png", x, y + 60, 200, 18, SigmaColors.alpha(SigmaColors.WHITE, .5f * alpha));
        if (modules.size() * 30 > 260) {
            float thumb = 260 * 260f / (modules.size() * 30);
            SigmaShape.rounded(context, x + 196, y + 60 + renderedScroll / Math.max(1, modules.size() * 30 - 260) * (260 - thumb), 3, thumb, 1.5f, SigmaColors.alpha(SigmaColors.BLACK, .18f * alpha));
        }
    }
    public Module hit(float mx, float my) {
        if (!SigmaSettingsPanel.inside(mx, my, x, y + 60, 200, 260)) return null;
        int index = (int) ((my - y - 60 + renderedScroll) / 30);
        return index >= 0 && index < modules.size() ? modules.get(index) : null;
    }
    public void scroll(double delta) { scroll -= (float) delta * 30; clampScroll(); }
    private void clampScroll() { scroll = Math.clamp(scroll, 0, Math.max(0, modules.size() * 30 - 260)); }
    public String filter() { return filter; }
}
