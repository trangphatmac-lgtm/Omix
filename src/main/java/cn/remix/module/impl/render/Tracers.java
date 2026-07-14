package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.player.AntiBot;
import cn.remix.module.impl.player.Teams;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.Render2D;
import cn.remix.util.render.Render3D;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public final class Tracers extends Module {
    private final ModeValue colorMode = new ModeValue("Color", "Default", "Default", "Teams", "HUD");
    private final BoolValue lines = new BoolValue("Lines", true);
    private final BoolValue arrows = new BoolValue("Arrows", false);
    private final NumberValue opacity = new NumberValue("Opacity", 100, 0, 100, 1);
    private final NumberValue distance = new NumberValue("Distance", 512, 0, 512, 1);
    private final BoolValue players = new BoolValue("Players", true);
    private final BoolValue friends = new BoolValue("Friends", true);
    private final BoolValue enemies = new BoolValue("Enemies", true);
    private final BoolValue bots = new BoolValue("Bots", false);

    public Tracers() {
        super("Tracers", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!lines.getValue() || mc.player == null || mc.world == null) return;

        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d start = cameraEntity.getLerpedPos(tickDelta)
                .add(0.0, cameraEntity.getEyeHeight(cameraEntity.getPose()), 0.0);
        if (mc.options.getPerspective().isFirstPerson()) {
            start = start.add(cameraEntity.getRotationVec(tickDelta).multiply(0.25));
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRender(player, cameraEntity)) continue;

            Vec3d end = player.getLerpedPos(tickDelta)
                    .add(0.0, player.getEyeHeight(player.getPose()) - (player.isSneaking() ? 0.125 : 0.0), 0.0);
            Render3D.drawLine(event, start, end, getEntityColor(player, opacity.getValue() / 100.0F));
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!arrows.getValue() || mc.player == null || mc.world == null) return;

        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) return;

        float tickDelta = event.getPartialTicks();
        float centerX = mc.getWindow().getScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getScaledHeight() / 2.0F;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRender(player, cameraEntity)) continue;

            float yaw = getYawBetween(cameraEntity, player, tickDelta);
            if (mc.options.getPerspective().isFrontView()) yaw += 180.0F;

            float alpha = getArrowAlpha(yaw);
            if (alpha <= 0.0F) continue;

            double angle = Math.toRadians(yaw - 90.0F);
            float x = centerX + (float) Math.cos(angle) * 55.0F;
            float y = centerY + (float) Math.sin(angle) * 55.0F;
            Render2D.drawTriangle(event.getContext(), x, y, (float) angle, 10.0F,
                    getEntityColor(player, alpha).getRGB());
        }
    }

    private boolean shouldRender(PlayerEntity player, Entity cameraEntity) {
        if (player == mc.player || player == cameraEntity || !player.isAlive() || player.isSpectator()) return false;

        double maxDistance = distance.getValue();
        if (cameraEntity.squaredDistanceTo(player) > maxDistance * maxDistance) return false;

        if (isBot(player)) return bots.getValue();
        if (isFriend(player)) return friends.getValue();
        return isEnemy(player) ? enemies.getValue() : players.getValue();
    }

    private boolean isBot(PlayerEntity player) {
        boolean missingPlayerEntry = mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null;
        AntiBot antiBot = getModule(AntiBot.class);
        return missingPlayerEntry || antiBot.isEnabled() && antiBot.isBot(player);
    }

    private boolean isFriend(PlayerEntity player) {
        return instance.getFriendManager().isFriend(player.getName().getString());
    }

    private boolean isEnemy(PlayerEntity player) {
        Teams teams = getModule(Teams.class);
        return teams.isEnabled() && !teams.isTeam(player);
    }

    private float getYawBetween(Entity cameraEntity, PlayerEntity player, float tickDelta) {
        Vec3d self = cameraEntity.getLerpedPos(tickDelta);
        Vec3d target = player.getLerpedPos(tickDelta);
        float targetYaw = (float) Math.toDegrees(Math.atan2(target.z - self.z, target.x - self.x)) - 90.0F;
        return MathHelper.wrapDegrees(targetYaw - cameraEntity.getYaw(tickDelta));
    }

    private float getArrowAlpha(float yaw) {
        float alpha = opacity.getValue() / 100.0F;
        float absoluteYaw = Math.abs(MathHelper.wrapDegrees(yaw));
        if (absoluteYaw < 30.0F) return 0.0F;
        if (absoluteYaw < 60.0F) return alpha * (absoluteYaw - 30.0F) / 30.0F;
        return alpha;
    }

    private Color getEntityColor(PlayerEntity player, float alpha) {
        Color color;
        if (isFriend(player)) {
            color = new Color(85, 255, 85);
        } else if (isEnemy(player)) {
            color = new Color(255, 85, 85);
        } else {
            color = switch (colorMode.getValue()) {
                case "Teams" -> getModule(Teams.class).isTeam(player)
                        ? new Color(85, 85, 255)
                        : new Color(255, 85, 85);
                case "HUD" -> new Color(getModule(HUD.class).getColor(), true);
                default -> new Color(player.getTeamColorValue() | 0xFF000000, true);
            };
        }

        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F));
    }
}
