package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.ChatScreenEvent;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.value.impl.*;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.ui.hud.Drag;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.misc.RomanNumeralUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Getter
public class HUD extends Module {
    private final ModeValue hudMode = new ModeValue("Mode", "Remix", "Classic", "Remix");
    private final ModeValue colorMode = new ModeValue("Color Setting", "Rainbow", () -> hudMode.is("Remix"), "Rainbow", "Fade", "Custom");
    private final ColorValue mainColor = new ColorValue("Main Color", Color.WHITE, () -> hudMode.is("Remix"));
    private final ColorValue secondColor = new ColorValue("Second Color", Color.WHITE, () -> hudMode.is("Remix") && colorMode.is("Fade"));

    public final MultiBoolValue hudOptionsProperty = new MultiBoolValue("HUD Options", () -> hudMode.is("Remix"),
            new BoolValue("TabGUI", true),
            new BoolValue("Watermark", true),
            new BoolValue("Potion Effects", true),
            new BoolValue("Display", true),
            new BoolValue("Position", true)
    );

    private final BoolValue noPotionIcons = new BoolValue("No Potion Icons", true, () -> hudMode.is("Remix"));
    private final BoolValue whiteMode = new BoolValue("White Mode", false, () -> hudMode.is("Remix"));
    private final NumberValue hudFps = new NumberValue("HUD FPS", 60, 5, 360);

    private final ModeValue classicColorMode = new ModeValue("Classic Color", "Custom", () -> hudMode.is("Classic"),
            "Rainbow", "Chroma", "Astolfo", "Custom", "Fade", "Triple");
    private final NumberValue classicColorSpeed = new NumberValue("Classic Color Speed", 1, .5f, 1.5f, .05f,
            () -> hudMode.is("Classic") && !classicColorMode.is("Custom"));
    private final NumberValue classicSaturation = new NumberValue("Classic Saturation", 50, 0, 100, 1,
            () -> hudMode.is("Classic"));
    private final NumberValue classicBrightness = new NumberValue("Classic Brightness", 100, 0, 100, 1,
            () -> hudMode.is("Classic"));
    private final ColorValue classicColor1 = new ColorValue("Classic Color 1", Color.WHITE,
            () -> hudMode.is("Classic") && (classicColorMode.is("Custom") || classicColorMode.is("Fade") || classicColorMode.is("Triple")));
    private final ColorValue classicColor2 = new ColorValue("Classic Color 2", Color.WHITE,
            () -> hudMode.is("Classic") && (classicColorMode.is("Fade") || classicColorMode.is("Triple")));
    private final ColorValue classicColor3 = new ColorValue("Classic Color 3", Color.WHITE,
            () -> hudMode.is("Classic") && classicColorMode.is("Triple"));
    private final ModeValue classicPositionX = new ModeValue("Classic Position X", "Left", () -> hudMode.is("Classic"), "Left", "Right");
    private final ModeValue classicPositionY = new ModeValue("Classic Position Y", "Top", () -> hudMode.is("Classic"), "Top", "Bottom");
    private final NumberValue classicOffsetX = new NumberValue("Classic Offset X", 2, 0, 255, 1, () -> hudMode.is("Classic"));
    private final NumberValue classicOffsetY = new NumberValue("Classic Offset Y", 2, 0, 255, 1, () -> hudMode.is("Classic"));
    private final NumberValue classicScale = new NumberValue("Classic Scale", 1, .5f, 1.5f, .05f, () -> hudMode.is("Classic"));
    private final NumberValue classicBackground = new NumberValue("Classic Background", 25, 0, 100, 1, () -> hudMode.is("Classic"));
    private final BoolValue classicBar = new BoolValue("Classic Bar", true, () -> hudMode.is("Classic"));
    private final BoolValue classicShadow = new BoolValue("Classic Shadow", true, () -> hudMode.is("Classic"));
    private final BoolValue classicSuffixes = new BoolValue("Classic Suffixes", true, () -> hudMode.is("Classic"));
    private final BoolValue classicLowerCase = new BoolValue("Classic Lower Case", false, () -> hudMode.is("Classic"));
    private final BoolValue classicDisablerQueue = new BoolValue("Classic Disabler Queue", true, () -> hudMode.is("Classic"));
    private final EasingAnimation yAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 250);
    private final EasingAnimation selectorAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final EasingAnimation moduleAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final EasingAnimation expandAnimationX = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final EasingAnimation expandAnimationY = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final List<Category> categories = Arrays.stream(Category.values()).toList();

    private int current, moduleIndex;
    private boolean expanded;

    public HUD() {
        super("HUD", Category.Render);
        setEnabled(true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (hudMode.is("Classic")) {
            renderClassic(event.getContext());
            return;
        }

        DrawContext context = event.getContext();
        TrueTypeFont font20 = instance.getFontManager().getFont(20);
        TrueTypeFont font16 = instance.getFontManager().getFont(16);
        TrueTypeFont font18b = instance.getFontManager().getFont(18);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float height = font20.getHeight();

        for (Module module : instance.getModuleManager().getModuleMap().values()) {
            if (module instanceof Drag drag && drag.isEnabled()) {
                drag.render(context);
                drag.updatePos();
            }
        }

        yAnimation.run(mc.currentScreen instanceof ChatScreen ? 14 : 0);

        if (hudOptionsProperty.isEnabled("Display")) {
            Disabler disabler = getModule(Disabler.class);
            if (disabler.isEnabled()) {
                String text;

                if (disabler.isWaiting()) {
                    text = Formatting.RED + "You are playing Cubecraft with disabler disabled!";
                    disabler.refresh();
                } else {
                    text = String.valueOf(disabler.getPacketQueue().size());
                }

                float textWidth = font18b.getStringWidth(text);
                float textX = (sw - textWidth) / 2f;
                float textY = 60;

                font18b.drawStringWithShadow(context, text, textX, textY, getWhiteMode().getValue() ? -1 : getColor());
            }
        }

        if (hudOptionsProperty.isEnabled("Watermark")) {
            String text = Client.name + " " + Client.version;

            String firstChar = text.substring(0, 1);
            font20.drawStringWithShadow(context, firstChar, 2, 0, getColor());

            String remainder = Formatting.GRAY + text.substring(1);
            font20.drawStringWithShadow(context, remainder, 2 + font20.getStringWidth(firstChar), 0, -1);
        }

        if (hudOptionsProperty.isEnabled("Position")) {
            int fps = mc.getCurrentFps();
            int tps = (int) mc.world.getTickManager().getTickRate();

            String xyzText = "XYZ: ";
            String xyzVal = Math.round(mc.player.getX()) + " " + Math.round(mc.player.getY()) + " " + Math.round(mc.player.getZ()) + " ";
            String fpsText = "FPS: ";
            String fpsVal = fps + " ";
            String tpsText = "TPS: ";
            String tpsVal = String.valueOf(tps);

            float px = 2;
            float py = sh - height - yAnimation.getValue().floatValue();

            font20.drawStringWithShadow(context, xyzText, px, py, getModule(HUD.class).getWhiteMode().getValue() ? -1 : getColor());
            px += font20.getStringWidth(xyzText);

            font20.drawStringWithShadow(context, Formatting.GRAY + xyzVal, px, py, -1);
            px += font20.getStringWidth(xyzVal);

            font20.drawStringWithShadow(context, fpsText, px, py, getModule(HUD.class).getWhiteMode().getValue() ? -1 : getColor());
            px += font20.getStringWidth(fpsText);

            font20.drawStringWithShadow(context, Formatting.GRAY + fpsVal, px, py, -1);
            px += font20.getStringWidth(fpsVal);

            font20.drawStringWithShadow(context, tpsText, px, py, getModule(HUD.class).getWhiteMode().getValue() ? -1 : getColor());
            px += font20.getStringWidth(tpsText);

            font20.drawStringWithShadow(context, Formatting.GRAY + tpsVal, px, py, -1);
        }

        if (hudOptionsProperty.isEnabled("Potion Effects")) {
            List<StatusEffectInstance> potions = new ArrayList<>(mc.player.getStatusEffects());
            potions.sort(Comparator.comparingDouble(e -> -font20.getStringWidth(I18n.translate(e.getEffectType().value().getTranslationKey()))));

            float fontH = font20.getHeight();
            float basePy = sh - height - 2 - yAnimation.getValue().floatValue();

            String infoText = "Requires MC 1.8-1.21";
            font20.drawStringWithShadow(context, infoText, sw - font20.getStringWidth(infoText) - 2, basePy, -1);

            int count = 0;
            for (StatusEffectInstance effect : potions) {
                StatusEffect potion = effect.getEffectType().value();
                String name = I18n.translate(potion.getTranslationKey()) + (effect.getAmplifier() > 0 ? " " + RomanNumeralUtil.generate(effect.getAmplifier() + 1) : "");

                String durationStr;
                if (effect.getDuration() >= 100000000 || effect.isInfinite() || effect.getDuration() < 0) {
                    durationStr = "**:**";
                } else {
                    durationStr = StatusEffectUtil.getDurationText(effect, 1, mc.world.getTickManager().getTickRate()).getString();
                }

                String text = name + Formatting.WHITE + ": " + Formatting.GRAY + durationStr;
                float py = basePy - fontH - (fontH * count);
                font20.drawStringWithShadow(context, text, sw - font20.getStringWidth(text) - 2, py, potion.getColor() | 0xFF000000);
                count++;
            }
        }

        if (hudOptionsProperty.isEnabled("TabGUI")) {
            int x = 2, y = (int) font20.getHeight(), categoryWidth = 80, itemHeight = 13;
            int categoryHeight = categories.size() * itemHeight;
            selectorAnimation.run(current * itemHeight);

            Render2D.drawRect(context, x, y, categoryWidth, categoryHeight, new Color(23, 23, 23).getRGB());
            Render2D.drawGradient(context, x, y + selectorAnimation.getValue().floatValue(), categoryWidth, itemHeight, getColor(), getColor(4), true);
            for (int i = 0; i < categories.size(); i++) {
                font16.drawStringWithShadow(context, categories.get(i).name(), x + 4, y + 1.5f + i * itemHeight, -1);
            }

            List<Module> modules = instance.getModuleManager().getModuleMap().values().stream().filter(m -> m.getCategory() == categories.get(current)).toList();
            if (modules.isEmpty()) return;

            int width = 0;
            for (Module m : modules) width = (int) Math.max(font16.getStringWidth(m.getName()) + 5, width);

            expandAnimationX.run(expanded ? width : 0);
            expandAnimationY.run(expanded ? modules.size() * itemHeight : 0);
            moduleAnimation.run(moduleIndex * itemHeight);

            float expandX = expandAnimationX.getValue().floatValue(), expandY = expandAnimationY.getValue().floatValue(), moduleY = moduleAnimation.getValue().floatValue();
            if (expandX < 1 || expandY < 1) return;

            float boxX = x + categoryWidth, boxY = y + current * itemHeight;
            Render2D.drawRect(context, boxX, boxY, expandX, expandY, new Color(0, 0, 0, 180).getRGB());
            Render2D.beginScissor(context, boxX, boxY, expandX, expandY);
            if (expanded)
                Render2D.drawRect(context, boxX, boxY + moduleY, width, 12, new Color(0, 0, 0, 120).getRGB());
            for (int i = 0; i < modules.size(); i++) {
                font16.drawStringWithShadow(context, modules.get(i).getName(), boxX + 2, boxY + i * itemHeight + 1.5f, modules.get(i).isEnabled() ? -1 : Color.LIGHT_GRAY.getRGB());
            }
            Render2D.endScissor(context);

        }
    }

    @EventTarget
    public void onChatScreen(ChatScreenEvent event) {
        if (mc.player == null || mc.world == null || hudMode.is("Classic")) return;

        for (Module module : instance.getModuleManager().getModuleMap().values()) {
            if (module instanceof Drag drag && drag.isEnabled()) {
                drag.onChatGUI(event.getMouseX(), event.getMouseY(), GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
            }
        }
    }

    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (mc.currentScreen != null || hudMode.is("Classic")) return;
        int code = event.getKey();
        List<Module> modules = instance.getModuleManager().getModuleMap().values().stream().filter(m -> m.getCategory() == categories.get(current)).toList();

        if (!expanded) {
            if (code == GLFW.GLFW_KEY_DOWN && current < categories.size() - 1) { current++; expandAnimationX.setValue(0); expandAnimationY.setValue(0); }
            if (code == GLFW.GLFW_KEY_UP && current > 0) { current--; expandAnimationX.setValue(0); expandAnimationY.setValue(0); }
            if (code == GLFW.GLFW_KEY_RIGHT) { expanded = true; moduleIndex = 0; }
        } else {
            if (code == GLFW.GLFW_KEY_DOWN && modules.size() > moduleIndex + 1) moduleIndex++;
            if (code == GLFW.GLFW_KEY_UP && moduleIndex > 0) moduleIndex--;
            if (code == GLFW.GLFW_KEY_LEFT) { expanded = false; moduleIndex = 0; }
            if ((code == GLFW.GLFW_KEY_RIGHT || code == GLFW.GLFW_KEY_ENTER) && !modules.isEmpty()) modules.get(moduleIndex).toggle();
        }
    }

    public int getColor() {
        return getColor(0);
    }

    public int getColor(int counter) {
        return getColor(counter, 255);
    }

    public int getColor(int counter, int alpha) {
        if (hudMode.is("Classic")) {
            return ColorUtil.applyAlpha(getClassicColor(System.currentTimeMillis(), counter).getRGB(), alpha);
        }

        return switch (colorMode.getValue()) {
            case "Rainbow" -> ColorUtil.getRainbow(counter, alpha);
            case "Fade" -> ColorUtil.getFade(counter, alpha);
            default -> ColorUtil.getCustom(alpha);
        };
    }

    private void renderClassic(DrawContext context) {
        if (mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;

        float scale = classicScale.getValue();
        float fontHeight = mc.textRenderer.fontHeight;
        float barOffset = 1 + (classicBar.getValue() ? (classicShadow.getValue() ? 2 : 1) : 0);
        float x = classicOffsetX.getValue() + barOffset * scale;
        float y = classicOffsetY.getValue() + scale;
        boolean right = classicPositionX.is("Right");
        boolean bottom = classicPositionY.is("Bottom");

        if (right) x = mc.getWindow().getScaledWidth() - x;
        if (bottom) y = mc.getWindow().getScaledHeight() - y - fontHeight * scale;

        List<Module> activeModules = instance.getModuleManager().getModuleMap().values().stream()
                .filter(module -> module != this && !(module instanceof ModuleList))
                .filter(module -> module.isEnabled() && !module.isHidden())
                .sorted(Comparator.comparingInt(this::getClassicModuleWidth).reversed().thenComparing(Module::getName))
                .toList();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        long time = System.currentTimeMillis();
        int row = 0;

        for (Module module : activeModules) {
            String name = getClassicModuleName(module);
            String suffix = getClassicModuleSuffix(module);
            int width = getClassicModuleWidth(module);
            float drawX = right ? (x - width * scale) / scale : x / scale;
            float drawY = y / scale;
            int color = getClassicColor(time, row).getRGB();

            if (classicBackground.getValue() > 0) {
                int background = Math.round(255 * classicBackground.getValue() / 100) << 24;
                Render2D.drawRect(context, drawX - 2, drawY - 1, width + 4, fontHeight + 2, background);
            }

            if (classicBar.getValue()) {
                Render2D.drawRect(context, right ? drawX + width + 2 : drawX - 4,
                        drawY - 1, 2, fontHeight + 2, color);
            }

            context.drawText(mc.textRenderer, name, Math.round(drawX), Math.round(drawY), color, classicShadow.getValue());
            if (classicSuffixes.getValue() && !suffix.isEmpty()) {
                int suffixX = Math.round(drawX + mc.textRenderer.getWidth(name) + 3);
                context.drawText(mc.textRenderer, suffix, suffixX, Math.round(drawY), 0xFFAAAAAA, classicShadow.getValue());
            }

            y += (fontHeight + 1) * scale * (bottom ? -1 : 1);
            row++;
        }

        Disabler disabler = getModule(Disabler.class);

        if (classicDisablerQueue.getValue() && disabler.isEnabled()) {
            drawClassicOverlay(context, String.valueOf(disabler.getPacketQueue().size()), time, row);
        }

        if (disabler.isEnabled()) {
            String text;

            if (disabler.isWaiting()) {
                disabler.refresh();
                drawClassicOverlay(context, "You are playing Cubecraft with disabler disabled!", time, row);
            }
        }
        context.getMatrices().popMatrix();
    }

    private void drawClassicOverlay(DrawContext context, String text, long time, int colorOffset) {
        float scale = classicScale.getValue();
        int color = getClassicColor(time, colorOffset).getRGB() & 0x00FFFFFF | 0xBF000000;
        int drawX = Math.round((mc.getWindow().getScaledWidth() / 2f - mc.textRenderer.getWidth(text) * scale / 2f) / scale);
        int drawY = Math.round((mc.getWindow().getScaledHeight() / 5f * 3f) / scale);
        context.drawText(mc.textRenderer, text, drawX, drawY, color, classicShadow.getValue());
    }

    private String getClassicModuleName(Module module) {
        String name = module.getName();
        return classicLowerCase.getValue() ? name.toLowerCase(java.util.Locale.ROOT) : name;
    }

    private String getClassicModuleSuffix(Module module) {
        if (!classicSuffixes.getValue() || module.getSuffix().isEmpty()) return "";
        return classicLowerCase.getValue() ? module.getSuffix().toLowerCase(java.util.Locale.ROOT) : module.getSuffix();
    }

    private int getClassicModuleWidth(Module module) {
        String name = getClassicModuleName(module);
        String suffix = getClassicModuleSuffix(module);
        return mc.textRenderer.getWidth(name) + (suffix.isEmpty() ? 0 : 3 + mc.textRenderer.getWidth(suffix));
    }

    private Color getClassicColor(long time, long offset) {
        float cycle = getClassicColorCycle(time, offset);
        Color color = switch (classicColorMode.getValue()) {
            case "Rainbow" -> Color.getHSBColor(cycle, 1, 1);
            case "Chroma" -> Color.getHSBColor(getClassicColorCycle(time / 3, 0), 1, 1);
            case "Astolfo" -> {
                float hue = cycle % 1 < .5f ? 1 - cycle % 1 : cycle;
                yield Color.getHSBColor(hue, 1, 1);
            }
            case "Fade" -> new Color(ColorUtil.interpolate(
                    classicColor1.getValue().getRGB(), classicColor2.getValue().getRGB(), classicTriangle(cycle)), true);
            case "Triple" -> {
                float triangle = classicTriangle(cycle);
                yield triangle <= .5f
                        ? new Color(ColorUtil.interpolate(classicColor1.getValue().getRGB(), classicColor2.getValue().getRGB(), triangle * 2), true)
                        : new Color(ColorUtil.interpolate(classicColor2.getValue().getRGB(), classicColor3.getValue().getRGB(), (triangle - .5f) * 2), true);
            }
            default -> classicColor1.getValue();
        };

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(hsb[0], hsb[1] * classicSaturation.getValue() / 100,
                hsb[2] * classicBrightness.getValue() / 100);
    }

    private float getClassicColorCycle(long time, long offset) {
        long speed = (long) (3000 / Math.pow(Math.clamp(classicColorSpeed.getValue(), .5f, 1.5f), 3));
        return 1 - (float) (Math.abs(time - offset * 300) % speed) / speed;
    }

    private float classicTriangle(float cycle) {
        return (float) (2 * Math.abs(cycle - Math.floor(cycle + .5)));
    }
}
