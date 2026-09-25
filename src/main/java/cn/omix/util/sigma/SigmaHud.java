package cn.omix.util.sigma;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.render.HUD;
import cn.omix.ui.hud.Drag;
import cn.omix.util.IMinecraft;
import cn.omix.util.player.MovementUtil;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Adapts Jello GUI modules to one HUD with shared stacking and resources. */
public final class SigmaHud implements IMinecraft {
    public static int debugRightRows;
    private static final List<Category> CATEGORIES = List.of(Category.Move, Category.Player, Category.Combat, Category.Render, Category.World, Category.Exploits);
    private final Map<Module, SigmaAnimation> animations = new IdentityHashMap<>();
    private final float[] keyProgress = new float[6];
    private final boolean[] keyDown = new boolean[6];
    private int categoryIndex, moduleIndex;
    private boolean expanded;
    private float categorySelector, moduleSelector, categoryScroll;
    private final float[] categoryIndent = new float[CATEGORIES.size()];
    private final Map<Module, Float> moduleIndent = new IdentityHashMap<>();
    private final List<TabRipple> tabRipples = new ArrayList<>();
    private record TabRipple(boolean modules, long started) {}
    private long previous;

    public static HUD active() {
        if (instance == null || instance.getModuleManager() == null) return null;
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        return hud != null && hud.isNativeBehaviorActive() && hud.getHudMode().is("Sigma") ? hud : null;
    }

    public void render(HUD hud, DrawContext context) {
        if (mc.options.hudHidden || mc.player == null || mc.world == null) return;
        long now = System.nanoTime();
        float delta = previous == 0 ? 0 : Math.clamp((now - previous) / 1_000_000_000f, 0, .1f);
        previous = now;
        SigmaDraw.begin(context);
        try {
            SigmaDraw.image(context, "sigma/jello_watermark.png", mc.getDebugHud().shouldShowDebugHud() ? (SigmaDraw.width() - 170) / 2f : 0,
                    0, 170, 104, -1);
            if (hud.getSigmaActiveMods().getValue()) activeMods(context, hud, now);
            if (hud.getSigmaCompass().getValue()) compass(context);
            if (hud.getSigmaInfoHud().getValue()) info(context, hud);
            if (!mc.getDebugHud().shouldShowDebugHud()) {
                int offset = 90;
                if (hud.getSigmaTabGui().getValue()) offset = tab(context, offset, delta);
                if (hud.getSigmaMiniMap().getValue()) offset = miniMap(context, offset);
                if (hud.getSigmaKeyStrokes().getValue()) keys(context, offset, delta);
            }
        } finally { SigmaDraw.end(context); }
    }

    private void activeMods(DrawContext context, HUD hud, long now) {
        int size = switch (hud.getSigmaActiveModsSize().getValue()) { case "Small" -> 18; case "Tiny" -> 14; default -> 20; };
        var font = SigmaResources.light(size);
        var modules = instance.getModuleManager().getModuleMap().values();
        animations.keySet().retainAll(modules);
        List<Module> sorted = modules.stream().filter(module -> module != hud && !(module instanceof Drag) && !module.isHidden())
                .sorted(Comparator.comparingDouble((Module module) -> SigmaResources.light(20).getStringWidth(module.getName())).reversed()).toList();
        float y = mc.getDebugHud().shouldShowDebugHud() ? debugRightRows * 9 * mc.getWindow().getScaleFactor() + 7 : 6;
        int margin = size == 14 ? 7 : 10;
        for (Module module : sorted) {
            float progress = animations.computeIfAbsent(module, ignored -> new SigmaAnimation(module.isEnabled() ? 1 : 0)).update(module.isEnabled(), now, 150);
            if (!hud.getSigmaActiveModsAnimations().getValue()) progress = module.isEnabled() ? 1 : 0;
            if (progress <= 0) continue;
            String name = module.getName();
            float width = font.getStringWidth(name), x = SigmaDraw.width() - margin - width;
            float scale = .86f + .14f * progress;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x + width / 2, y + 12);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-x - width / 2, -y - 12);
            SigmaDraw.image(context, "alt/shadow.png", SigmaDraw.width() - width * 1.5f - margin - 20, y - 20,
                    width * 3, font.getHeight() + 41, SigmaColors.alpha(SigmaColors.WHITE, .36f * progress * (float) Math.sqrt(Math.min(1.2f, width / 63))));
            font.drawString(context, name, x, y, SigmaColors.alpha(-1, progress * .95f));
            context.getMatrices().popMatrix();
            y += (font.getHeight() + 1) * SigmaAnimation.easeInOut(progress);
        }
    }

    public static float scoreboardOffset(net.minecraft.scoreboard.ScoreboardObjective objective) {
        HUD hud = active();
        if (hud == null || !hud.getSigmaActiveMods().getValue() || mc.player == null || mc.options.hudHidden) return 0;
        int size = switch (hud.getSigmaActiveModsSize().getValue()) { case "Small" -> 18; case "Tiny" -> 14; default -> 20; };
        long count = instance.getModuleManager().getModuleMap().values().stream().filter(m -> m != hud && !(m instanceof Drag) && !m.isHidden() && m.isEnabled()).count();
        int scores = objective.getScoreboard().getScoreboardEntries(objective).size();
        float y = 23 + count * (SigmaResources.light(size).getHeight() + 1);
        float center = SigmaDraw.height() / 2f - 14 * (scores - 1);
        return Math.max(0, (y - center) / 2);
    }

    private void compass(DrawContext context) {
        int debug = mc.getDebugHud().shouldShowDebugHud() ? 60 : 0;
        float yaw = SigmaGeometry.wrapDegrees(mc.player.getYaw());
        int nearest = ((int) yaw + 7) / 15 * 15;
        float shift = (7 + yaw - nearest) / 15 * 60;
        SigmaDraw.image(context, "alt/shadow.png", SigmaDraw.width() / 2f - 450, -40, 900, 220 + debug, SigmaColors.alpha(SigmaColors.WHITE, .25f));
        String[] labels = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        for (int i = 0; i < 11; i++) {
            int angle = Math.floorMod(nearest - 75 + i * 15, 360);
            float alpha = (float) Math.min(Math.clamp(((i + 1) * 60 - shift) / 300, 0, 1),
                    Math.clamp(2.25 - ((i + 1) * 60 - shift) / 300, 0, 1)) * .8f;
            float x = SigmaDraw.width() / 2f + (i + 1) * 60 - (int) shift - 362;
            if (angle % 45 == 0) {
                String text = labels[angle / 45];
                var font = angle % 90 == 0 ? SigmaResources.medium(40) : SigmaResources.light(25);
                font.drawString(context, text, x + (60 - font.getStringWidth(text)) / 2, 30 + debug + (angle % 90 == 0 ? 10 : 20), SigmaColors.alpha(SigmaColors.WHITE, alpha));
            } else {
                Render2D.drawRect(context, x + 29, 58 + debug, 2, 10, SigmaColors.alpha(SigmaColors.WHITE, alpha * .5f));
                String text = Integer.toString(angle);
                var font = SigmaResources.light(18);
                font.drawString(context, text, x + (60 - font.getStringWidth(text)) / 2, 70 + debug, SigmaColors.alpha(SigmaColors.WHITE, alpha));
            }
        }
    }

    private void info(DrawContext context, HUD hud) {
        if (mc.currentScreen instanceof GameMenuScreen) return;
        int x = 14, bottom = SigmaDraw.height();
        if (hud.getSigmaInfoPlayer().getValue()) {
            // Special GUI elements do not use DrawContext's pose. Convert their viewport and size explicitly.
            int guiScale = mc.getWindow().getScaleFactor();
            var state = mc.getEntityRenderDispatcher().getAndUpdateRenderState(mc.player, 1);
            state.light = 0xf000f0; state.shadowPieces.clear(); state.outlineColor = 0; state.displayName = null;
            int top = bottom - 150, foot = bottom - 22;
            context.addEntity(state, 57f / guiScale, new org.joml.Vector3f(0, (foot - top) / 114f, 0),
                    new org.joml.Quaternionf().rotateZ((float) Math.PI), new org.joml.Quaternionf().rotateY((float) Math.PI),
                    0, top / guiScale, 114 / guiScale, foot / guiScale);
            x += 90;
        }
        if (hud.getSigmaInfoArmor().getValue()) {
            int count = 0;
            for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
                var stack = mc.player.getEquippedStack(slot);
                if (stack.isEmpty()) continue;
                int y = bottom - 14 - 32 * ++count;
                context.getMatrices().pushMatrix(); context.getMatrices().translate(x, y); context.getMatrices().scale(2, 2);
                context.drawItem(stack, 0, 0); context.getMatrices().popMatrix();
                float durability = stack.getMaxDamage() == 0 ? 1 : 1 - stack.getDamage() / (float) stack.getMaxDamage();
                if (durability < 1) {
                    Render2D.drawRect(context, x + 2, y + 28, 28, 5, 0x7f010101);
                    Render2D.drawRect(context, x + 2, y + 28, 28 * durability, 3, SigmaColors.alpha(durability > .2 ? -9581017 : -43691, .9f));
                }
            }
            x += count == 0 ? 3 : 42;
        }
        if (!hud.getSigmaInfoCoords().is("None")) {
            String coords = hud.getSigmaInfoCoords().is("Precise")
                    ? Math.round(mc.player.getX() * 10) / 10f + " " + Math.round(mc.player.getY() * 10) / 10f + " " + Math.round(mc.player.getZ() * 10) / 10f
                    : Math.round(mc.player.getX()) + " " + Math.round(mc.player.getY()) + " " + Math.round(mc.player.getZ());
            SigmaResources.medium(20).drawString(context, coords, x, bottom - 42, SigmaColors.alpha(SigmaColors.WHITE, .8f));
        }
    }

    private List<Module> categoryModules() {
        return instance.getModuleManager().getModuleMap().values().stream().filter(module -> module.getCategory() == CATEGORIES.get(categoryIndex)).toList();
    }

    public void key(int key) {
        List<Module> modules = categoryModules();
        switch (key) {
            case GLFW.GLFW_KEY_UP -> { if (expanded) moduleIndex = Math.max(0, moduleIndex - 1); else { categoryIndex = Math.floorMod(categoryIndex - 1, CATEGORIES.size()); moduleIndex = 0; } }
            case GLFW.GLFW_KEY_DOWN -> { if (expanded) moduleIndex = Math.min(Math.max(0, modules.size() - 1), moduleIndex + 1); else { categoryIndex = (categoryIndex + 1) % CATEGORIES.size(); moduleIndex = 0; } }
            case GLFW.GLFW_KEY_LEFT -> expanded = false;
            case GLFW.GLFW_KEY_RIGHT -> { tabRipples.add(new TabRipple(expanded, System.nanoTime())); if (expanded && !modules.isEmpty()) modules.get(Math.min(moduleIndex, modules.size() - 1)).toggle(); expanded = true; }
            case GLFW.GLFW_KEY_ENTER -> { if (expanded && !modules.isEmpty()) { modules.get(Math.min(moduleIndex, modules.size() - 1)).toggle(); tabRipples.add(new TabRipple(true, System.nanoTime())); } }
        }
    }

    private int tab(DrawContext context, int y, float delta) {
        float step = 1 - (float) Math.pow(.86, delta * 60);
        long now = System.nanoTime();
        tabRipples.removeIf(ripple -> now - ripple.started >= 250_000_000L);
        moduleIndent.keySet().retainAll(instance.getModuleManager().getModuleMap().values());
        categorySelector += (categoryIndex * 30 - categorySelector) * step;
        moduleSelector += (moduleIndex * 30 - moduleSelector) * step;
        categoryScroll += (Math.max(0, categoryIndex * 30 - 120) - categoryScroll) * step;
        panel(context, 10, y, 150, 154);
        Render2D.beginScissor(context, 10, y, 150, 154);
        selector(context, 10, y + categorySelector - categoryScroll, 150);
        ripple(context, 10, y + categorySelector - categoryScroll, 150, false, now);
        var categoryFont = SigmaResources.light(20);
        for (int i = 0; i < CATEGORIES.size(); i++) {
            categoryIndent[i] = Math.clamp(categoryIndent[i] + (i == categoryIndex ? 1 : -1) * delta * 60, 0, 14);
            categoryFont.drawString(context, CATEGORIES.get(i).getName(), 21 + categoryIndent[i],
                    y + i * 30 - categoryScroll + 17 - categoryFont.getHeight() / 2, SigmaColors.WHITE);
        }
        Render2D.endScissor(context);
        if (expanded) {
            List<Module> modules = categoryModules();
            int h = Math.min(modules.size() * 30 + 4, SigmaDraw.height() - y - 10);
            float scroll = Math.max(0, moduleSelector - h + 34);
            panel(context, 170, y, 170, h);
            Render2D.beginScissor(context, 170, y, 170, h);
            selector(context, 170, y + moduleSelector - scroll, 170);
            ripple(context, 170, y + moduleSelector - scroll, 170, true, now);
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                var font = module.isEnabled() ? SigmaResources.medium(20) : SigmaResources.light(20);
                float indent = Math.clamp(moduleIndent.getOrDefault(module, 0f) + (moduleIndex == i ? 1 : -1) * delta * 60, 0, 14);
                moduleIndent.put(module, indent);
                font.drawString(context, module.getName(), 181 + indent,
                        y + i * 30 - scroll + 15 - font.getHeight() / 2 + (module.isEnabled() ? 3 : 2), SigmaColors.WHITE);
            }
            Render2D.endScissor(context);
        }
        return y + 173;
    }

    private void ripple(DrawContext context, float x, float y, float width, boolean modules, long now) {
        Render2D.beginScissor(context, x, y, width, 34);
        for (TabRipple ripple : tabRipples) if (ripple.modules == modules) {
            float p = Math.clamp((now - ripple.started) / 250_000_000f, 0, 1), radius = width * SigmaAnimation.easeOut(p) + 3;
            SigmaShape.rounded(context, x - radius, y + 14 - radius, 2 * radius, 2 * radius, radius, SigmaColors.alpha(-1, (1 - p) * .14f));
        }
        Render2D.endScissor(context);
    }

    private void panel(DrawContext context, float x, float y, float w, float h) {
        SigmaBlur.draw(context, x, y, w, h, 1);
        Render2D.drawRect(context, x, y, w, h, SigmaColors.alpha(SigmaColors.GREY, .05f));
        SigmaDraw.shadow(context, x, y, w, h, 8, .7f);
    }
    private void selector(DrawContext context, float x, float y, float w) {
        Render2D.drawRect(context, x, y, w, 34, 0x10010101);
        SigmaDraw.image(context, "jello/shadow_top.png", x, y + 20, w, 14, SigmaColors.alpha(SigmaColors.WHITE, .3f));
        SigmaDraw.image(context, "jello/shadow_bottom.png", x, y, w, 14, SigmaColors.alpha(SigmaColors.WHITE, .3f));
    }

    private int miniMap(DrawContext context, int y) {
        Render2D.beginScissor(context, 10, y, 150, 150);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(85, y + 75);
        context.getMatrices().rotate((float) Math.toRadians(180 - mc.player.getYaw()));
        SigmaMapCache.get().draw(context, -113, -113, 226, 226, mc.player.getX(), mc.player.getZ(), 225 / 160f, false);
        context.getMatrices().popMatrix();
        Render2D.endScissor(context);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(86, y + 75);
        context.getMatrices().rotate((float) (MovementUtil.getDirection() - Math.toRadians(mc.player.getYaw())));
        SigmaResources.medium(20).drawString(context, "^", -5, -5, 0x70000000);
        SigmaResources.medium(20).drawString(context, "^", -5, -8, SigmaColors.WHITE);
        context.getMatrices().popMatrix();
        SigmaDraw.innerShadow(context, 10, y, 150, 150, 23, .75f);
        SigmaDraw.shadow(context, 10, y, 150, 150, 8, .7f);
        return y + 160;
    }

    private void keys(DrawContext context, int y, float delta) {
        KeyBinding[] keys = {mc.options.leftKey, mc.options.rightKey, mc.options.forwardKey, mc.options.backKey, mc.options.attackKey, mc.options.useKey};
        int[] xs = {0, 102, 51, 51, 0, 77}, ys = {51, 51, 0, 51, 102, 102}, widths = {48,48,48,48,74,73};
        for (int i = 0; i < keys.length; i++) {
            boolean down = keys[i].isPressed();
            if (down && !keyDown[i]) keyProgress[i] = .001f;
            keyDown[i] = down;
            if (keyProgress[i] > 0) keyProgress[i] = Math.min(1, keyProgress[i] + delta / .3f);
            if (down && keyProgress[i] > .7f) keyProgress[i] = .7f;
            float x = 10 + xs[i], ky = y + ys[i];
            Render2D.drawRect(context, x, ky, widths[i], 48, SigmaColors.alpha(down ? SigmaColors.WHITE : SigmaColors.BLACK, .5f));
            SigmaDraw.shadow(context, x, ky, widths[i], 48, 10, .75f);
            float progress = keyProgress[i];
            if (progress > 0 && progress < 1) {
                Render2D.beginScissor(context, x, ky, widths[i], 48);
                float radius = (widths[i] - 4) * progress + 4;
                SigmaShape.rounded(context, x + widths[i] / 2f - radius, ky + 24 - radius, radius * 2, radius * 2, radius,
                        SigmaColors.alpha(-5658199, (1 - progress * (.5f + progress * .5f)) * .8f));
                Render2D.endScissor(context);
            }
            String text = i == 4 ? "L" : i == 5 ? "R" : keys[i].getBoundKeyLocalizedText().getString();
            var font = SigmaResources.light(18);
            font.drawString(context, text, x + (widths[i] - font.getStringWidth(text)) / 2, ky + 12, SigmaColors.WHITE);
        }
    }
}
