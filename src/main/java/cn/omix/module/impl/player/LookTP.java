package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.Util;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.pathfinder.MainPathFinder;
import cn.omix.util.player.pathfinder.PathFinder;
import net.minecraft.client.util.InputUtil;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;

public final class LookTP extends Module {
    private static final long TELEPORT_COOLDOWN_MS = 200L;
    private static final double RAYCAST_DISTANCE = 10_000.0;

    private final BoolValue tpOnGroundPacket = new BoolValue("TP On Ground Packet", true);
    private final BoolValue clientsideTeleport = new BoolValue("Clientside Teleport", false);
    private final BoolValue alwaysTop = new BoolValue("Always Top", false);
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

        Vec3d targetPosition = getTargetPosition(hit);
        if (targetPosition == null) {
            Util.log("&cLookTP: No free space above target");
            teleportTimer.reset();
            return;
        }

        teleportTo(targetPosition);
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

    public boolean teleportToSurface() {
        if (mc.player == null || mc.world == null) {
            Util.log("&cLookTP: Player or world is unavailable");
            return false;
        }
        if (isClientsideTeleporting()) {
            Util.log("&cLookTP: A teleport is already in progress");
            return false;
        }

        int x = mc.player.getBlockX();
        int z = mc.player.getBlockZ();
        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        for (int feetY = Math.min(topY, mc.world.getTopYInclusive() - 1);
             feetY > mc.world.getBottomY(); feetY--) {
            if (PathFinder.isValid(x, feetY, z, true)) {
                return teleportTo(new Vec3d(mc.player.getX(), feetY, mc.player.getZ()));
            }
        }

        Util.log("&cLookTP: No safe surface found at the current X/Z");
        return false;
    }

    public boolean teleportTo(Vec3d targetPosition) {
        if (mc.player == null || mc.world == null || targetPosition == null) {
            Util.log("&cLookTP: Invalid teleport target");
            return false;
        }

        ArrayList<Vec3d> path = MainPathFinder.computePath(mc.player.getEntityPos(), targetPosition);
        if (path.isEmpty()) {
            Util.log("&cLookTP: Failed to teleport");
            teleportTimer.reset();
            return false;
        }

        if (clientsideTeleport.getValue() && isEnabled()) {
            beginClientsideTeleport(path, targetPosition);
            return true;
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
        return true;
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

    private Vec3d getTargetPosition(BlockHitResult hit) {
        if (!alwaysTop.getValue()) return hit.getPos();

        BlockPos blockPos = hit.getBlockPos();
        BlockState blockState = mc.world.getBlockState(blockPos);
        VoxelShape collisionShape = blockState.getCollisionShape(mc.world, blockPos);
        double topOffset = collisionShape.isEmpty() ? 1.0 : collisionShape.getMax(Direction.Axis.Y);

        BlockPos spaceAbove = blockPos.up();
        if (hasTwoFreeBlocks(spaceAbove)) {
            return new Vec3d(
                    blockPos.getX() + 0.5,
                    blockPos.getY() + topOffset,
                    blockPos.getZ() + 0.5
            );
        }

        int highestFeetY = mc.world.getTopYInclusive() - 1;
        for (BlockPos candidate = spaceAbove.up(); candidate.getY() <= highestFeetY; candidate = candidate.up()) {
            if (hasTwoFreeBlocks(candidate)) {
                return new Vec3d(
                        blockPos.getX() + 0.5,
                        candidate.getY(),
                        blockPos.getZ() + 0.5
                );
            }
        }

        return null;
    }

    private boolean hasTwoFreeBlocks(BlockPos feetPosition) {
        return isFree(feetPosition) && isFree(feetPosition.up());
    }

    private boolean isFree(BlockPos position) {
        BlockState state = mc.world.getBlockState(position);
        return state.getCollisionShape(mc.world, position).isEmpty();
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
