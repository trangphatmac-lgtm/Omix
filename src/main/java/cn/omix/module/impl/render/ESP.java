package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.ColorValue;
import cn.omix.util.sigma.*;
import cn.omix.ui.font.TrueTypeFont;
import cn.omix.util.player.EntityUtil;
import cn.omix.util.render.ColorUtil;
import cn.omix.util.render.ProjectUtil;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class ESP extends Module {
    private final ModeValue implementation = new ModeValue("Implementation", "Classic", "Classic", "Sigma");
    public final BoolValue esp2d = new BoolValue("2D ESP", true, () -> implementation.is("Classic"));
    public final BoolValue healthBar = new BoolValue("Health Bar", true, () -> implementation.is("Classic"));
    public final BoolValue armorBar = new BoolValue("Armor Bar", true, () -> implementation.is("Classic"));
    public final BoolValue nametags = new BoolValue("Name Tags", true, () -> implementation.is("Classic"));
    private final ModeValue sigmaMode = new ModeValue("Sigma Mode", "Shadow", () -> implementation.is("Sigma"), "Shadow", "Sims", "Box Outline", "Vanilla");
    private final BoolValue sigmaPlayers = new BoolValue("Show Players", true, () -> implementation.is("Sigma"));
    private final BoolValue sigmaMobs = new BoolValue("Show Mobs", false, () -> implementation.is("Sigma"));
    private final BoolValue sigmaPassives = new BoolValue("Show Passives", false, () -> implementation.is("Sigma"));
    private final BoolValue sigmaInvisibles = new BoolValue("Show Invisibles", true, () -> implementation.is("Sigma"));
    private final ColorValue sigmaColor = new ColorValue("Sigma Color", new Color(SigmaColors.WHITE),
            () -> implementation.is("Sigma") && (sigmaMode.is("Shadow") || sigmaMode.is("Box Outline")));
    private final List<RenderData> targets = new ArrayList<>();

    public ESP() {
        super("ESP", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent e) {
        targets.clear();
        if (mc.player == null || mc.world == null) return;
        setSuffix(implementation.is("Sigma") ? sigmaMode.getValue() : "Classic");
        if (implementation.is("Sigma")) {
            renderSigma(e);
            return;
        }
        for (Entity entity : instance.getTargetManager().getTargets()) {
            if (EntityUtil.isSelected(entity)) {
                Vector4f pos = getEntityPos(entity, e.getProjectionMatrix(), e.getModelViewMatrix());
                if (pos != null) {
                    targets.add(new RenderData(entity, pos));
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent e) {
        if (implementation.is("Sigma")) return;
        if (targets.isEmpty()) return;

        for (RenderData data : targets) {
            if (!(data.entity instanceof LivingEntity living)) continue;
            Vector4f pos = data.pos;

            if (esp2d.getValue()) {
                drawBox(e.getContext(), pos.x, pos.y, pos.z, pos.w);
            }

            if (healthBar.getValue()) {
                drawHealthBar(e.getContext(), living, pos.x, pos.y, pos.w);
            }

            if (armorBar.getValue()) {
                drawArmorBar(e.getContext(), living, pos.z, pos.y, pos.w);
            }

            if (nametags.getValue()) {
                drawNametag(e.getContext(), living, pos.x, pos.y, pos.z);
            }
        }
    }

    private Vector4f getEntityPos(Entity entity, Matrix4f projMat, Matrix4f modMat) {
        Vec3d[] vectors = ProjectUtil.getVectors(entity);
        if (vectors.length == 0) return null;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean visible = false;

        for (Vec3d vector : vectors) {
            vector = ProjectUtil.worldSpaceToScreenSpace(vector, projMat, modMat);
            if (vector.z > 0 && vector.z < 1) {
                minX = Math.min((float) vector.x, minX);
                minY = Math.min((float) vector.y, minY);
                maxX = Math.max((float) vector.x, maxX);
                maxY = Math.max((float) vector.y, maxY);
                visible = true;
            }
        }

        return visible ? new Vector4f(minX, minY, maxX, maxY) : null;
    }

    private void drawNametag(DrawContext context, Entity entity, float x, float y, float right) {
        if (!(entity instanceof LivingEntity living) || mc.player == null) return;
        TrueTypeFont font = instance.getFontManager().getFont(12);
        String text = living.getName().getString() + String.format(" §7[§a%.1f§7]", living.getHealth()) + String.format(" §f%dm", (int) mc.player.distanceTo(living));
        float width = font.getStringWidth(text);
        float height = font.getHeight();
        float middle = x + (right - x) / 2;
        Render2D.drawRect(context, middle - (width / 2) - 2, y - height - 4, width + 4, height + 2, new Color(0, 0, 0, 120).getRGB());
        font.drawStringWithShadow(context, text, middle - (width / 2), y - height - 3, -1);
    }

    private void drawBox(DrawContext context, float x, float y, float right, float bottom) {
        float width = right - x;
        float height = bottom - y;
        Render2D.drawRect(context, x - .5f, y - .5f, width + 1f, 1.5f, Color.BLACK.getRGB());
        Render2D.drawRect(context, x - .5f, bottom - 1f, width + 1f, 1.5f, Color.BLACK.getRGB());
        Render2D.drawRect(context, x - .5f, y + 1f, 1.5f, height - 2f, Color.BLACK.getRGB());
        Render2D.drawRect(context, right - 1f, y + 1f, 1.5f, height - 2f, Color.BLACK.getRGB());
        Render2D.drawRect(context, x, y, width, .5f, getModule(HUD.class).getColor(1));
        Render2D.drawRect(context, x, y, .5f, height, getModule(HUD.class).getColor(2));
        Render2D.drawRect(context, x, bottom - .5f, width, .5f, getModule(HUD.class).getColor(3));
        Render2D.drawRect(context, right - .5f, y, .5f, height, getModule(HUD.class).getColor(4));
    }

    private void drawHealthBar(DrawContext context, @NotNull LivingEntity entity, float x, float y, float bottom) {
        float healthValue = entity.getHealth() / entity.getMaxHealth();
        float height = (bottom - y) + 1;
        Render2D.drawRect(context, x - 3.5f, y - .5f, 2, height + 1, new Color(0, 0, 0, 180).getRGB());
        Render2D.drawRect(context, x - 3f, y, 1, height, ColorUtil.applyAlpha(getHealthColor(healthValue * 100).getRGB(), .3f));
        Render2D.drawRect(context, x - 3f, y + (height - height * healthValue), 1, height * healthValue, getHealthColor(healthValue * 100).getRGB());
    }

    private void drawArmorBar(DrawContext context, @NotNull LivingEntity entity, float right, float y, float bottom) {
        float armorValue = entity.getArmor() / 20f;
        float height = (bottom - y) + 1;
        Render2D.drawRect(context, right + 1.5f, y - .5f, 2, height + 1, new Color(0, 0, 0, 180).getRGB());
        Render2D.drawRect(context, right + 2, y, 1, height, ColorUtil.applyAlpha(new Color(135, 206, 250).getRGB(), .3f));
        Render2D.drawRect(context, right + 2, y + (height - height * armorValue), 1, height * armorValue, new Color(135, 206, 250).getRGB());
    }

    private Color getHealthColor(float healthPercent) {
        return healthPercent > 75 ? new Color(66, 246, 123) : healthPercent > 50 ? new Color(228, 255, 105) : healthPercent > 35 ? new Color(236, 100, 64) : new Color(255, 65, 68);
    }

    private record RenderData(Entity entity, Vector4f pos) {}

    public boolean sigmaTarget(Entity entity) {
        if (!isNativeBehaviorActive() || !implementation.is("Sigma")) return false;
        // Jello's two player effects have their own filters, independent of the parent switches.
        if (sigmaMode.is("Shadow") || sigmaMode.is("Sims")) return entity instanceof net.minecraft.entity.player.PlayerEntity
                && entity != mc.player && !SigmaEntityFilter.bot(entity) && (!sigmaMode.is("Shadow") || !entity.isInvisible());
        return SigmaEntityFilter.matches(entity,
                sigmaPlayers.getValue(), sigmaMobs.getValue(), sigmaPassives.getValue(), sigmaInvisibles.getValue());
    }

    public boolean sigmaOutline(Entity entity) {
        return sigmaMode.is("Vanilla") && sigmaTarget(entity);
    }

    public int sigmaOutlineColor(Entity entity) {
        return sigmaMode.is("Vanilla") ? entity.getTeamColorValue() | 0xff000000 : sigmaColor.getValue().getRGB();
    }

    private void renderSigma(Render3DEvent event) {
        if (sigmaMode.is("Vanilla")) return;
        if (sigmaMode.is("Sims")) {
            // Depth is disabled for these markers; far-to-near order is the source's occlusion rule.
            mc.world.getPlayers().stream().filter(this::sigmaTarget)
                    .sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.squaredDistanceTo(mc.player)).reversed())
                    .forEach(entity -> SigmaSims.render(event, entity));
            return;
        }
        List<SigmaMaskEffect.ColoredBox> boxes = new ArrayList<>();
        List<Entity> shadowEntities = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!sigmaTarget(entity)) continue;
            if (sigmaMode.is("Box Outline")) {
                Vec3d position = entity.getLerpedPos(event.getTickDelta());
                var box = entity.getBoundingBox().offset(position.x - entity.getX(), position.y - entity.getY(), position.z - entity.getZ()).expand(.1);
                boxes.add(new SigmaMaskEffect.ColoredBox(box, SigmaColors.alpha(sigmaColor.getValue().getRGB(), .35f)));
                shadowEntities.add(entity);
            } else if (sigmaMode.is("Shadow")) {
                shadowEntities.add(entity);
            }
        }
        if (sigmaMode.is("Box Outline")) SigmaMaskEffect.boxes(event, boxes, shadowEntities);
        else if (sigmaMode.is("Shadow")) SigmaMaskEffect.entities(event, shadowEntities, sigmaColor.getValue().getRGB());
    }

    @Override
    public void onDisable() { targets.clear(); }
}
