package cn.omix.util.sigma;

import cn.omix.config.Config;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Jello's 250x500 profile popover, inline editing and sliding row actions. */
public final class SigmaProfilesPanel implements IMinecraft {
    private final SigmaAnimation animation = new SigmaAnimation(), addAnimation = new SigmaAnimation();
    private final SigmaTextInput name = new SigmaTextInput(128);
    private final List<Row> rows = new ArrayList<>();
    private float x, y, height, scroll, scale = 1, addHeight, listScale = 1;
    private Row editing, menu;
    private String message = "";
    private boolean adding, closing;

    private static final class Row {
        final Config config;
        final SigmaAnimation hover = new SigmaAnimation(), slide = new SigmaAnimation(), remove = new SigmaAnimation();
        float y, height = 70, slideProgress;
        boolean deleting;
        Row(Config config) { this.config = config; }
    }

    public SigmaProfilesPanel() { refresh(); }
    private void refresh() {
        var configs = instance.getConfigManager().getConfigs();
        rows.removeIf(row -> !configs.contains(row.config));
        for (Config config : configs) if (rows.stream().noneMatch(row -> row.config == config)) rows.add(new Row(config));
    }
    public void close() { commitRename(); adding = false; closing = true; }
    public boolean closed() { return closing && animation.value() == 0; }

    public void draw(DrawContext context, float mx, float my, float alpha) {
        long now = System.nanoTime();
        float p = animation.update(!closing, now, closing ? 100 : 300);
        float eased = closing ? SigmaAnimation.bezier(p, .38, .73, 0, 1) : SigmaAnimation.bezier(p, .37, 1.48, .17, .99);
        scale = .8f + .2f * eased;
        height = Math.min(500, SigmaDraw.height() - 28);
        x = SigmaDraw.width() - 264; y = SigmaDraw.height() - 14 - height;
        mx = local(mx, true); my = local(my, false); alpha *= p;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x + 250, y + height); context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-x - 250, -y - height);
        try {
            SigmaDraw.shadow(context, x + 5, y + 5, 240, height - 10, 35, alpha);
            SigmaShape.rounded(context, x, y, 250, height, 10, SigmaColors.alpha(-723724, SigmaAnimation.easeOut(p)));
            SigmaResources.light(25).drawString(context, "Profiles", x + 25, y + 20, SigmaColors.alpha(SigmaColors.BLACK, .8f * alpha));
            SigmaResources.light(25).drawString(context, "+", x + 195, y + 20, SigmaColors.alpha(SigmaColors.BLACK, .8f * alpha));
            Render2D.drawRect(context, x + 25, y + 69, 200, 1, SigmaColors.alpha(SigmaColors.BLACK, .05f * alpha));
            float add = addAnimation.update(adding, now, adding ? 300 : 200);
            addHeight = 200 * (adding ? SigmaAnimation.bezier(add, .1, .81, .14, 1) : SigmaAnimation.bezier(add, .61, .01, .87, .16));
            listScale = .9f + .1f * (1 - (adding ? SigmaAnimation.bezier(add, 0, .96, .69, .99) : SigmaAnimation.bezier(add, .61, .01, .87, .16)));
            context.getMatrices().pushMatrix();
            float cy = y + 80 + (height - 90) / 2;
            context.getMatrices().translate(x + 125, cy); context.getMatrices().scale(listScale, listScale);
            context.getMatrices().translate(-x - 125, -cy);
            drawRows(context, (mx - x - 125) / listScale + x + 125, (my - cy) / listScale + cy, alpha, now);
            context.getMatrices().popMatrix();
            if (add > 0) {
                SigmaDraw.image(context, "jello/shadow_bottom.png", x, y + 69 + addHeight, 250, 50, SigmaColors.alpha(-1, .3f * alpha * add));
                Render2D.beginScissor(context, x, y + 69, 250, addHeight);
                Render2D.drawRect(context, x, y + 69, 250, 200, SigmaColors.alpha(-723724, alpha));
                SigmaResources.light(20).drawString(context, "Blank", x + 25, y + 72, SigmaColors.alpha(SigmaColors.BLACK, .9f * alpha));
                var font = SigmaResources.light(20);
                font.drawString(context, "Duplicate", x + 225 - font.getStringWidth("Duplicate"), y + 72, SigmaColors.alpha(SigmaColors.BLACK, .9f * alpha));
                // Sigma's online presets target a different client schema; local Omix profiles are authoritative.
                SigmaResources.light(14).drawString(context, "No Default Profiles Available", x + 40, y + 179, SigmaColors.alpha(0xff888888, alpha));
                Render2D.endScissor(context);
            }
            if (!message.isEmpty()) {
                Render2D.beginScissor(context, x + 10, y + height - 25, 230, 22);
                SigmaResources.light(14).drawString(context, message, x + 15, y + height - 25, SigmaColors.alpha(0xffd04c4c, alpha));
                Render2D.endScissor(context);
            }
        } finally { context.getMatrices().popMatrix(); }
    }

    private void drawRows(DrawContext context, float mx, float my, float alpha, long now) {
        float top = y + 80 - scroll, content = 0;
        Render2D.beginScissor(context, x + 10, y + 80, 230, height - 90);
        for (Row row : rows) {
            float removal = row.remove.update(row.deleting, now, 200);
            row.height = 70 * (1 - SigmaAnimation.bezier(removal, .1, .81, .14, 1));
            row.y = top + content; content += row.height;
            boolean hovered = !adding && !closing && SigmaSettingsPanel.inside(mx, my, x + 10, row.y, 230, row.height);
            if (!hovered && menu == row) menu = null;
            float hover = row.hover.update(hovered, now, 100), slide = row.slide.update(menu == row, now, 290);
            row.slideProgress = menu == row ? SigmaAnimation.bezier(slide, .28, 1.26, .33, 1.04) : slide * slide * slide;
            if (row.y + row.height < y + 80 || row.y > y + height - 10 || row.height <= 0) continue;
            float opacity = alpha * (1 - removal), shift = row.slideProgress * 230;
            Render2D.beginScissor(context, x + 10, row.y, 230, row.height);
            SigmaShape.rounded(context, x + 10, row.y, 230, row.height, 6, SigmaColors.alpha(SigmaColors.BLACK, .04f * hover * opacity));
            if (editing == row) name.draw(context, SigmaResources.light(24), x + 26 - shift, row.y + 18, 170, SigmaColors.alpha(SigmaColors.BLACK, .9f * opacity), "Profile name");
            else SigmaResources.light(24).drawString(context, row.config.getName(), x + 30 - shift, row.y + 18, SigmaColors.alpha(SigmaColors.BLACK, .9f * opacity));
            if (row.config == instance.getConfigManager().getCurrentConfig()) SigmaDraw.image(context, "alt/active.png", x + 205 - shift, row.y + 27, 17, 13, SigmaColors.alpha(-1, (1 - slide) * opacity));
            if (slide > 0) {
                float width = Math.max(0, 184 * row.slideProgress), left = x + 240 - width;
                Render2D.drawRect(context, left, row.y, width / 2, row.height, SigmaColors.alpha(-11371052, opacity));
                Render2D.drawRect(context, left + width / 2, row.y, width / 2, row.height, SigmaColors.alpha(-3254955, opacity));
                var font = SigmaResources.light(18);
                font.drawString(context, "Rename", left + (width / 2 - font.getStringWidth("Rename")) / 2, row.y + 23, SigmaColors.alpha(-1, opacity));
                font.drawString(context, "Delete", left + width / 2 + (width / 2 - font.getStringWidth("Delete")) / 2, row.y + 23, SigmaColors.alpha(-1, opacity));
            }
            Render2D.endScissor(context);
        }
        Render2D.endScissor(context);
        scroll = Math.clamp(scroll, 0, Math.max(0, content - height + 90));
        rows.removeIf(row -> row.deleting && row.remove.value() == 1);
    }

    private float local(float coordinate, boolean horizontal) {
        float origin = horizontal ? x + 250 : y + height;
        return (coordinate - origin) / Math.max(.01f, scale) + origin;
    }
    public boolean click(float mx, float my, int button) {
        if (closing) return true;
        mx = local(mx, true); my = local(my, false);
        if (!SigmaSettingsPanel.inside(mx, my, x, y, 250, height)) return false;
        if (SigmaSettingsPanel.inside(mx, my, x + 180, y, 60, 69)) { commitRename(); adding = !adding; menu = null; message = ""; return true; }
        if (adding) {
            if (my > y + 69 + addHeight) adding = false;
            else if (my >= y + 69 && my < y + 99 && button == 0) {
                try { if (mx < x + 125) SigmaProfileStorage.blank(); else SigmaProfileStorage.duplicate(); adding = false; message = ""; refresh(); }
                catch (IOException error) { message = error.getMessage(); }
            }
            return true;
        }
        float cy = y + 80 + (height - 90) / 2;
        mx = (mx - x - 125) / listScale + x + 125; my = (my - cy) / listScale + cy;
        if (my < y + 80 || my >= y + height - 10) return true;
        for (Row row : rows) if (!row.deleting && my >= row.y && my < row.y + row.height) {
            if (editing == row) { name.focus(true); return true; }
            commitRename();
            if (button == 1) { menu = row; return true; }
            if (button != 0) return true;
            if (menu == row && row.slide.value() == 1 && mx >= x + 56) {
                if (mx < x + 148) {
                    if (row.config.getName().equalsIgnoreCase("Default")) message = "Default cannot be renamed";
                    else { editing = row; name.set(row.config.getName()); name.focus(true); name.selectAll(); message = ""; }
                } else if (instance.getConfigManager().deleteConfig(row.config.getName())) { row.deleting = true; message = ""; }
                else message = "Default cannot be deleted";
                menu = null;
            } else if (row.slide.value() == 0) { SigmaProfileStorage.activate(row.config); message = ""; }
            else menu = null;
            return true;
        }
        return true;
    }
    private void commitRename() {
        if (editing == null) return;
        try { SigmaProfileStorage.rename(editing.config, name.value()); message = ""; }
        catch (IOException error) { message = error.getMessage(); }
        editing = null; name.focus(false); refresh();
    }
    public void scroll(double delta) {
        if (!adding) scroll = (float) Math.clamp(scroll - delta * 35, 0, Math.max(0, rows.stream().mapToDouble(row -> row.height).sum() - height + 90));
    }
    public boolean key(KeyInput input) {
        if (editing != null) {
            if (input.key() == GLFW.GLFW_KEY_ENTER) commitRename();
            else if (input.key() == GLFW.GLFW_KEY_ESCAPE) { editing = null; name.focus(false); }
            else name.key(input);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && (adding || menu != null)) { adding = false; menu = null; return true; }
        return false;
    }
    public boolean type(CharInput input) { return editing != null && name.type(input); }
}
