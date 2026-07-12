package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class TargetHUD extends Module {
    private static final DecimalFormat HEALTH_FORMAT = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat DIFF_FORMAT = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private static final long TARGET_MEMORY = 1500L;

    private final ModeValue colorMode = new ModeValue("Color", "Default", "Default", "HUD");
    private final ModeValue positionX = new ModeValue("Position X", "Middle", "Left", "Middle", "Right");
    private final ModeValue positionY = new ModeValue("Position Y", "Middle", "Top", "Middle", "Bottom");
    private final NumberValue scale = new NumberValue("Scale", 1, .5f, 1.5f, .05f);
    private final NumberValue offsetX = new NumberValue("Offset X", 0, -255, 255, 1);
    private final NumberValue offsetY = new NumberValue("Offset Y", 40, -255, 255, 1);
    private final NumberValue background = new NumberValue("Background", 25, 0, 100, 1);
    private final BoolValue head = new BoolValue("Head", true);
    private final BoolValue indicator = new BoolValue("Indicator", true);
    private final BoolValue outline = new BoolValue("Outline", false);
    private final BoolValue animations = new BoolValue("Animations", true);
    private final BoolValue shadow = new BoolValue("Shadow", true);
    private final BoolValue auraOnly = new BoolValue("Aura Only", true);
    private final BoolValue chatPreview = new BoolValue("Chat Preview", false);

    private LivingEntity lastTarget;
    private LivingEntity renderedTarget;
    private long lastAttackTime;
    private float displayedHealth;

    public TargetHUD() {
        super("TargetHUD", Category.Render);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.getEntity() instanceof LivingEntity living && !(living instanceof ArmorStandEntity)) {
            lastTarget = living;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.options.hudHidden) return;

        LivingEntity target = resolveTarget();
        if (target == null) {
            renderedTarget = null;
            return;
        }

        float targetHealth = (target.getHealth() + target.getAbsorptionAmount()) / 2;
        if (target != renderedTarget) {
            renderedTarget = target;
            displayedHealth = targetHealth;
        } else if (animations.getValue()) {
            displayedHealth += (targetHealth - displayedHealth) * .18f;
        } else {
            displayedHealth = targetHealth;
        }

        float maxHealth = Math.max(1, target.getMaxHealth() / 2);
        float healthRatio = Math.clamp(displayedHealth / maxHealth, 0, 1);
        Color targetColor = getTargetColor(target);
        Color healthColor = colorMode.is("Default") ? getHealthColor(healthRatio) : targetColor;
        float selfHealth = (mc.player.getHealth() + mc.player.getAbsorptionAmount()) / 2;
        float difference = selfHealth - targetHealth;
        Color differenceColor = getHealthColor(Math.clamp((difference + 1) / 2, 0, 1));

        String name = target.getName().getString();
        String health = "§f" + HEALTH_FORMAT.format(targetHealth)
                + (target.getAbsorptionAmount() > 0 ? "§6" : "§c") + "❤§r";
        String status = difference == 0 ? "D" : difference > 0 ? "W" : "L";
        String differenceText = difference == 0 ? "0.0" : DIFF_FORMAT.format(difference);
        float contentWidth = Math.max(mc.textRenderer.getWidth(name), mc.textRenderer.getWidth(health))
                + (indicator.getValue() ? 28 : 0);
        float headOffset = head.getValue() ? 25 : 0;
        float totalWidth = Math.max(headOffset + 70, headOffset + contentWidth + 6);
        float x = getX(totalWidth);
        float y = getY();

        render(event.getContext(), targetColor, healthColor, differenceColor, healthRatio,
                name, health, status, differenceText, x, y, totalWidth, headOffset);
    }

    private void render(DrawContext context, Color targetColor, Color healthColor, Color differenceColor,
                        float healthRatio, String name, String health, String status, String differenceText,
                        float x, float y, float totalWidth, float headOffset) {
        float hudScale = scale.getValue();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(hudScale, hudScale);

        int backgroundColor = ColorUtil.applyAlpha(Color.BLACK.getRGB(), background.getValue() / 100f);
        Render2D.drawRect(context, x, y, totalWidth, 27, backgroundColor);
        if (outline.getValue()) {
            Render2D.drawOutline(context, x, y, totalWidth, 27, 1, targetColor.getRGB());
        }

        if (head.getValue()) {
            Render2D.drawRect(context, x + 2, y + 2, 23, 23, targetColor.getRGB());
        }

        float barWidth = totalWidth - headOffset - 4;
        Render2D.drawRect(context, x + headOffset + 2, y + 22, barWidth, 3, darker(healthColor, .45f).getRGB());
        Render2D.drawRect(context, x + headOffset + 2, y + 22, barWidth * healthRatio, 3, healthColor.getRGB());

        int textX = Math.round(x + headOffset + 2);
        context.drawText(mc.textRenderer, name, textX, Math.round(y + 2), Color.WHITE.getRGB(), shadow.getValue());
        context.drawText(mc.textRenderer, health, textX, Math.round(y + 12), Color.WHITE.getRGB(), shadow.getValue());

        if (indicator.getValue()) {
            context.drawText(mc.textRenderer, status,
                    Math.round(x + totalWidth - 2 - mc.textRenderer.getWidth(status)), Math.round(y + 2),
                    differenceColor.getRGB(), shadow.getValue());
            context.drawText(mc.textRenderer, differenceText,
                    Math.round(x + totalWidth - 2 - mc.textRenderer.getWidth(differenceText)), Math.round(y + 12),
                    darker(differenceColor, .4f).getRGB(), shadow.getValue());
        }

        context.getMatrices().popMatrix();
    }

    private LivingEntity resolveTarget() {
        Aura aura = getModule(Aura.class);
        if (aura.isEnabled() && isValid(aura.getTarget())) {
            return aura.getTarget();
        }

        if (chatPreview.getValue() && mc.currentScreen instanceof ChatScreen) {
            return mc.player;
        }

        if (auraOnly.getValue()) return null;

        if (isValid(lastTarget) && System.currentTimeMillis() - lastAttackTime < TARGET_MEMORY) {
            return lastTarget;
        }

        return isValid(mc.targetedEntity) ? (LivingEntity) mc.targetedEntity : null;
    }

    private boolean isValid(Object entity) {
        return entity instanceof LivingEntity living
                && !(living instanceof ArmorStandEntity)
                && living.isAlive();
    }

    private Color getTargetColor(LivingEntity target) {
        if (colorMode.is("HUD")) {
            return new Color(getModule(HUD.class).getColor(), true);
        }

        if (target instanceof PlayerEntity player) {
            if (instance.getFriendManager().isFriend(player.getName().getString())) {
                return new Color(85, 255, 85);
            }

            int teamColor = player.getTeamColorValue();
            if ((teamColor & 0xFFFFFF) != 0xFFFFFF) {
                return new Color(teamColor | 0xFF000000, true);
            }
        }

        return Color.WHITE;
    }

    private Color getHealthColor(float ratio) {
        ratio = Math.clamp(ratio, 0, 1);
        if (ratio < .5f) {
            return new Color(ColorUtil.interpolate(Color.RED.getRGB(), Color.YELLOW.getRGB(), ratio * 2), true);
        }
        return new Color(ColorUtil.interpolate(Color.YELLOW.getRGB(), new Color(85, 255, 85).getRGB(), (ratio - .5f) * 2), true);
    }

    private Color darker(Color color, float factor) {
        return new Color(Math.round(color.getRed() * factor), Math.round(color.getGreen() * factor),
                Math.round(color.getBlue() * factor), color.getAlpha());
    }

    private float getX(float totalWidth) {
        float hudScale = scale.getValue();
        float x = offsetX.getValue() / hudScale;
        if (positionX.is("Middle")) {
            x += mc.getWindow().getScaledWidth() / hudScale / 2 - totalWidth / 2;
        } else if (positionX.is("Right")) {
            x = mc.getWindow().getScaledWidth() / hudScale - totalWidth - x;
        }
        return x;
    }

    private float getY() {
        float hudScale = scale.getValue();
        float y = offsetY.getValue() / hudScale;
        if (positionY.is("Middle")) {
            y += mc.getWindow().getScaledHeight() / hudScale / 2 - 13.5f;
        } else if (positionY.is("Bottom")) {
            y = mc.getWindow().getScaledHeight() / hudScale - 27 - y;
        }
        return y;
    }
}
