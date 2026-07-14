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
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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
        Camera camera = mc.gameRenderer.getCamera();
        if (cameraEntity == null || !camera.isReady()) return;

        float tickDelta = event.getTickDelta();
        Vec3d start = getLineStart(event, camera.getCameraPos());

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRender(player, cameraEntity, camera.getCameraPos())) continue;

            Vec3d end = player.getLerpedPos(tickDelta)
                    .add(0.0, player.getEyeHeight(player.getPose()) - (player.isSneaking() ? 0.125 : 0.0), 0.0);
            Render3D.drawLine(event, start, end, getEntityColor(player, opacity.getValue() / 100.0F));
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!arrows.getValue() || mc.player == null || mc.world == null) return;

        Entity cameraEntity = mc.getCameraEntity();
        Camera camera = mc.gameRenderer.getCamera();
        if (cameraEntity == null || !camera.isReady()) return;

        float tickDelta = event.getPartialTicks();
        float centerX = mc.getWindow().getScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getScaledHeight() / 2.0F;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRender(player, cameraEntity, camera.getCameraPos())) continue;

            float yaw = getYawBetween(camera, player, tickDelta);

            float alpha = getArrowAlpha(yaw);
            if (alpha <= 0.0F) continue;

            double yawRadians = Math.toRadians(yaw);
            float directionX = (float) Math.sin(yawRadians);
            float directionY = -(float) Math.cos(yawRadians);
            float x = centerX + directionX * 55.0F;
            float y = centerY + directionY * 55.0F;
            float angle = (float) Math.atan2(directionY, directionX);
            Render2D.drawTriangle(event.getContext(), x, y, angle, 10.0F,
                    getEntityColor(player, alpha).getRGB());
        }
    }

    private boolean shouldRender(PlayerEntity player, Entity cameraEntity, Vec3d cameraPos) {
        if (player == mc.player || player == cameraEntity || !player.isAlive() || player.isSpectator()) return false;

        double maxDistance = distance.getValue();
        if (cameraPos.squaredDistanceTo(player.getX(), player.getY(), player.getZ()) > maxDistance * maxDistance) return false;

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

    private float getYawBetween(Camera camera, PlayerEntity player, float tickDelta) {
        Vec3d self = camera.getCameraPos();
        Vec3d target = player.getLerpedPos(tickDelta);
        float targetYaw = (float) Math.toDegrees(Math.atan2(target.z - self.z, target.x - self.x)) - 90.0F;
        return MathHelper.wrapDegrees(targetYaw - camera.getYaw());
    }

    private Vec3d getLineStart(Render3DEvent event, Vec3d cameraPos) {
        Matrix4f inverseView = new Matrix4f(event.getModelViewMatrix()).invert();
        Vector4f relative = new Vector4f(0.0F, 0.0F, -0.25F, 1.0F).mul(inverseView);
        if (relative.w != 0.0F && relative.w != 1.0F) {
            relative.div(relative.w);
        }
        return cameraPos.add(relative.x, relative.y, relative.z);
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
