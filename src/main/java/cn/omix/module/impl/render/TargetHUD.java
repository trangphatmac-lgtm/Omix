package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.render.targethud.Exhibition;
import cn.omix.module.impl.render.targethud.Novoline;
import cn.omix.module.impl.render.targethud.Omix;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.ui.hud.Drag;
import cn.omix.util.render.ColorUtil;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class TargetHUD extends Drag {
    private static final DecimalFormat HEALTH_FORMAT = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat DIFF_FORMAT = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private static final long TARGET_MEMORY = 1500L;

    private final ModeValue implementation = new ModeValue("Implementation", "Classic", "Classic", "Omix");
    private final ModeValue omixStyle = new ModeValue(
            "Omix Style",
            "Novoline",
            () -> implementation.is("Omix"),
            "Novoline",
            "Omix",
            "Exhibition"
    );

    private final ModeValue colorMode = new ModeValue(
            "Color",
            "Default",
            () -> implementation.is("Classic"),
            "Default",
            "HUD"
    );
    private final ModeValue positionX = new ModeValue(
            "Position X",
            "Middle",
            () -> implementation.is("Classic"),
            "Left",
            "Middle",
            "Right"
    );
    private final ModeValue positionY = new ModeValue(
            "Position Y",
            "Middle",
            () -> implementation.is("Classic"),
            "Top",
            "Middle",
            "Bottom"
    );
    private final NumberValue scale = new NumberValue("Scale", 1, .5F, 1.5F, .05F, () -> implementation.is("Classic"));
    private final NumberValue offsetX = new NumberValue("Offset X", 0, -255, 255, 1, () -> implementation.is("Classic"));
    private final NumberValue offsetY = new NumberValue("Offset Y", 40, -255, 255, 1, () -> implementation.is("Classic"));
    private final NumberValue background = new NumberValue("Background", 25, 0, 100, 1, () -> implementation.is("Classic"));
    private final BoolValue head = new BoolValue("Head", true, () -> implementation.is("Classic"));
    private final BoolValue indicator = new BoolValue("Indicator", true, () -> implementation.is("Classic"));
    private final BoolValue outline = new BoolValue("Outline", false, () -> implementation.is("Classic"));
    private final BoolValue animations = new BoolValue("Animations", true, () -> implementation.is("Classic"));
    private final BoolValue shadow = new BoolValue("Shadow", true, () -> implementation.is("Classic"));
    private final BoolValue auraOnly = new BoolValue("Aura Only", true, () -> implementation.is("Classic"));
    private final BoolValue chatPreview = new BoolValue("Chat Preview", false, () -> implementation.is("Classic"));

    private LivingEntity lastTarget;
    private LivingEntity renderedTarget;
    private long lastAttackTime;
    private float displayedHealth;

    public TargetHUD() {
        super("TargetHUD");
        percentX = .5F;
        percentY = .8F;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!implementation.is("Classic")) return;

        if (event.getEntity() instanceof LivingEntity living && !(living instanceof ArmorStandEntity)) {
            lastTarget = living;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.options.hudHidden) return;

        if (implementation.is("Classic")) {
            setSuffix("Classic");
            renderClassic(event.getContext());
        } else if (getModule(HUD.class).getHudMode().is("Classic")) {
            updatePos();
            render(event.getContext());
        }
    }

    @Override
    public void render(DrawContext context) {
        if (!implementation.is("Omix") || mc.player == null || mc.world == null) return;

        setSuffix("Omix " + omixStyle.getValue());
        LivingEntity target = getOmixTarget();
        if (target == null) return;

        width = switch (omixStyle.getValue()) {
            case "Exhibition" -> Exhibition.getWidth(target);
            case "Omix" -> Omix.getWidth(target);
            default -> Novoline.getWidth(target);
        };

        height = switch (omixStyle.getValue()) {
            case "Exhibition" -> Exhibition.getHeight();
            case "Omix" -> Omix.getHeight();
            default -> Novoline.getHeight();
        };

        switch (omixStyle.getValue()) {
            case "Exhibition" -> Exhibition.render(context, target, renderX, renderY);
            case "Omix" -> Omix.render(context, target, renderX, renderY);
            default -> Novoline.render(context, target, renderX, renderY);
        }
    }

    private void renderClassic(DrawContext context) {
        LivingEntity target = resolveClassicTarget();
        if (target == null) {
            renderedTarget = null;
            return;
        }

        float targetHealth = (target.getHealth() + target.getAbsorptionAmount()) / 2;
        if (target != renderedTarget) {
            renderedTarget = target;
            displayedHealth = targetHealth;
        } else if (animations.getValue()) {
            displayedHealth += (targetHealth - displayedHealth) * .18F;
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
        float x = getClassicX(totalWidth);
        float y = getClassicY();

        drawClassic(context, targetColor, healthColor, differenceColor, healthRatio,
                name, health, status, differenceText, x, y, totalWidth, headOffset);
    }

    private void drawClassic(DrawContext context, Color targetColor, Color healthColor, Color differenceColor,
                             float healthRatio, String name, String health, String status, String differenceText,
                             float x, float y, float totalWidth, float headOffset) {
        float hudScale = scale.getValue();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(hudScale, hudScale);

        int backgroundColor = ColorUtil.applyAlpha(Color.BLACK.getRGB(), background.getValue() / 100F);
        Render2D.drawRect(context, x, y, totalWidth, 27, backgroundColor);
        if (outline.getValue()) {
            Render2D.drawOutline(context, x, y, totalWidth, 27, 1, targetColor.getRGB());
        }

        if (head.getValue()) {
            Render2D.drawRect(context, x + 2, y + 2, 23, 23, targetColor.getRGB());
        }

        float barWidth = totalWidth - headOffset - 4;
        Render2D.drawRect(context, x + headOffset + 2, y + 22, barWidth, 3, darker(healthColor, .45F).getRGB());
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
                    darker(differenceColor, .4F).getRGB(), shadow.getValue());
        }

        context.getMatrices().popMatrix();
    }

    private LivingEntity resolveClassicTarget() {
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

    private LivingEntity getOmixTarget() {
        if (mc.currentScreen instanceof ChatScreen) return mc.player;

        Aura aura = getModule(Aura.class);
        return aura.isEnabled() ? aura.getTarget() : null;
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
        if (ratio < .5F) {
            return new Color(ColorUtil.interpolate(Color.RED.getRGB(), Color.YELLOW.getRGB(), ratio * 2), true);
        }
        return new Color(ColorUtil.interpolate(
                Color.YELLOW.getRGB(),
                new Color(85, 255, 85).getRGB(),
                (ratio - .5F) * 2
        ), true);
    }

    private Color darker(Color color, float factor) {
        return new Color(
                Math.round(color.getRed() * factor),
                Math.round(color.getGreen() * factor),
                Math.round(color.getBlue() * factor),
                color.getAlpha()
        );
    }

    private float getClassicX(float totalWidth) {
        float hudScale = scale.getValue();
        float x = offsetX.getValue() / hudScale;
        if (positionX.is("Middle")) {
            x += mc.getWindow().getScaledWidth() / hudScale / 2 - totalWidth / 2;
        } else if (positionX.is("Right")) {
            x = mc.getWindow().getScaledWidth() / hudScale - totalWidth - x;
        }
        return x;
    }

    private float getClassicY() {
        float hudScale = scale.getValue();
        float y = offsetY.getValue() / hudScale;
        if (positionY.is("Middle")) {
            y += mc.getWindow().getScaledHeight() / hudScale / 2 - 13.5F;
        } else if (positionY.is("Bottom")) {
            y = mc.getWindow().getScaledHeight() / hudScale - 27 - y;
        }
        return y;
    }
}
