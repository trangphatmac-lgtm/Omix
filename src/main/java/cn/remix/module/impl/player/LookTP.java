package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.util.Util;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.pathfinder.MainPathFinder;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;

public final class LookTP extends Module {
    private static final long TELEPORT_COOLDOWN_MS = 200L;
    private static final double RAYCAST_DISTANCE = 10_000.0;

    private final BoolValue tpOnGroundPacket = new BoolValue("TP On Ground Packet", true);
    private final BoolValue clientsideTeleport = new BoolValue("Clientside Teleport", false);
    private final TimerUtil teleportTimer = new TimerUtil();

    private ArrayList<Vec3d> clientsidePath;
    private int clientsidePathIndex;
    private int clientsidePathSize;
    private int clientsideProgress;
    private Vec3d clientsideLockPosition;
    private Vec3d clientsideTargetPosition;
    private boolean waitingForClientsideTeleport;

    public LookTP() {
        super("LookTP", Category.Player);
    }

    @Override
    public void onEnable() {
        resetClientsideTeleport();
    }

    @Override
    public void onDisable() {
        resetClientsideTeleport();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost() || mc.player == null || !isMovingServerSide()) return;

        lockClientPosition();
        Vec3d pathPosition = clientsidePath.get(clientsidePathIndex);
        event.setX(pathPosition.x);
        event.setY(pathPosition.y);
        event.setZ(pathPosition.z);
        event.setOnGround(tpOnGroundPacket.getValue());

        clientsidePathIndex++;
        clientsideProgress = clientsidePathSize <= 0
                ? 0
                : Math.min(100, Math.round((float) clientsidePathIndex / clientsidePathSize * 100.0F));
        setSuffix(clientsideProgress + "%");

        if (clientsidePathIndex >= clientsidePath.size()) {
            clientsidePath = null;
            clientsidePathIndex = 0;
            waitingForClientsideTeleport = true;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (waitingForClientsideTeleport) {
            if (clientsideTargetPosition != null) {
                mc.player.setVelocity(Vec3d.ZERO);
                mc.player.setPosition(clientsideTargetPosition);
            }
            resetClientsideTeleport();
            notifySuccess();
            teleportTimer.reset();
            return;
        }

        if (isMovingServerSide()) {
            lockClientPosition();
            return;
        }

        setSuffix("");
        if (!teleportTimer.hasTimeElapsed(TELEPORT_COOLDOWN_MS)
                || !InputUtil.isKeyPressed(mc.getWindow(), InputUtil.GLFW_KEY_LEFT_ALT)
                || !mc.options.useKey.isPressed()) {
            return;
        }

        BlockHitResult hit = raycastTarget();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || mc.world.getBlockState(hit.getBlockPos()).isAir()) {
            return;
        }

        Vec3d targetPosition = hit.getPos();
        ArrayList<Vec3d> path = MainPathFinder.computePath(mc.player.getEntityPos(), targetPosition);
        if (path.isEmpty()) {
            Util.log("&cLookTP: Failed to teleport");
            teleportTimer.reset();
            return;
        }

        if (clientsideTeleport.getValue()) {
            beginClientsideTeleport(path, targetPosition);
            return;
        }

        for (Vec3d position : path) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    position,
                    tpOnGroundPacket.getValue(),
                    mc.player.horizontalCollision
            ));
        }
        mc.player.setPosition(targetPosition);
        notifySuccess();
        teleportTimer.reset();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!shouldStopClientInput()) return;

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJumping(false);
        event.setSneaking(false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || event.getType() != PacketEvent.Type.Received) return;

        if ((isMovingServerSide() || waitingForClientsideTeleport)
                && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            resetClientsideTeleport();
            Util.log("&cLookTP: Failed to teleport");
            teleportTimer.reset();
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetClientsideTeleport();
    }

    public boolean shouldStopClientInput() {
        return isEnabled() && (isMovingServerSide() || waitingForClientsideTeleport);
    }

    public boolean isClientsideTeleporting() {
        return isMovingServerSide() || waitingForClientsideTeleport;
    }

    public int getTeleportProgressPercent() {
        return isClientsideTeleporting() ? clientsideProgress : 0;
    }

    private BlockHitResult raycastTarget() {
        Vec3d eyePosition = mc.player.getEyePos();
        Vec3d targetPosition = eyePosition.add(mc.player.getRotationVec(0.1F).multiply(RAYCAST_DISTANCE));
        RaycastContext context = new RaycastContext(
                eyePosition,
                targetPosition,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        return mc.world.raycast(context);
    }

    private void beginClientsideTeleport(ArrayList<Vec3d> path, Vec3d targetPosition) {
        clientsidePath = new ArrayList<>(path);
        clientsidePathIndex = 0;
        clientsidePathSize = clientsidePath.size();
        clientsideProgress = 0;
        clientsideLockPosition = mc.player.getEntityPos();
        clientsideTargetPosition = targetPosition;
        waitingForClientsideTeleport = false;
        mc.player.setVelocity(Vec3d.ZERO);
        setSuffix("0%");
        Util.log("LookTP: Teleporting...");
        teleportTimer.reset();
    }

    private void lockClientPosition() {
        if (clientsideLockPosition == null || mc.player == null) return;

        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.setPosition(clientsideLockPosition);
    }

    private boolean isMovingServerSide() {
        return clientsidePath != null && clientsidePathIndex < clientsidePath.size();
    }

    private void resetClientsideTeleport() {
        clientsidePath = null;
        clientsidePathIndex = 0;
        clientsidePathSize = 0;
        clientsideProgress = 0;
        clientsideLockPosition = null;
        clientsideTargetPosition = null;
        waitingForClientsideTeleport = false;
        setSuffix("");
    }

    private void notifySuccess() {
        Util.log("&aLookTP: Teleported!");
    }
}
