package cn.omix.module.impl.player;

import cn.omix.event.BlockCollisionEventGuard;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.BlockCollisionEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.PlayerPositionLookEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.MovementUtil;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Phase extends Module {
    private static final double VANILLA_SERVER_COLLISION_EPSILON = 1.0E-5;
    private static final double VANILLA_ENTRY_FRACTION = 0.75;
    private static final double VANILLA_CONTACT_SEARCH_DISTANCE = 0.25;
    private static final int VANILLA_CONTACT_SEARCH_ITERATIONS = 24;
    private static final double VANILLA_SCAN_STEP = 0.03125;
    private static final double VANILLA_MAX_DISTANCE = 8.0;
    private static final int VANILLA_PENDING_TICKS = 10;
    private static final int VANILLA_CORRECTION_COOLDOWN_TICKS = 5;

    private final ModeValue mode = new ModeValue("Mode", "NCP", "Vanilla", "NCP", "AAC 4", "Hypixel", "Intave");

    private boolean isClipping = false;
    private int phaseTicks;
    private Float cachedDirection = null;
    private boolean mining = false;
    private boolean vanillaClipping = false;
    private int vanillaPendingTicks = 0;
    private int vanillaCorrectionCooldown = 0;
    private String activeMode = "NCP";
    private boolean timerModified;

    public Phase() {
        super("Phase", Category.Player);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    public void onDisable() {
        resetState();

        var player = mc.player;
        if (player == null) return;

        if (mode.is("AAC 4")) {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            boolean horizontalCollision = player.horizontalCollision;
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y - 0.00000001, z, false, horizontalCollision));
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y - 1, z, false, horizontalCollision));
        }
    }

    @Override
    public void onEnable() {
        resetState();
        activeMode = mode.getValue();
        if (mc.player == null) return;
        if (mode.is("AAC 4")) setEnabled(false);
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetState();
    }

    @EventTarget
    public void onBlock(BlockCollisionEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.is("hypixel") && isClipping && event.getPos().getY() != player.getBlockPos().down().getY())
            event.setCancelled();
    }

    @EventTarget
    public void onPlayerPositionLook(PlayerPositionLookEvent event) {
        if (mode.is("Vanilla") && vanillaClipping) {
            resetVanillaPhase();
            vanillaCorrectionCooldown = VANILLA_CORRECTION_COOLDOWN_TICKS;
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        syncMode();
        if (!isEnabled()) return;
        var player = mc.player;
        var level = mc.world;
        if (player == null || level == null) return;

        if (mode.is("hypixel")) {
            if (!player.horizontalCollision && !isClipping) return;

            phaseTicks++;

            if (phaseTicks >= 3) {
                if (phaseTicks >= 20) {
                    isClipping = false;
                    phaseTicks = 0;
                    cachedDirection = null;
                }
            } else if (phaseTicks >= 1) {
                float direction = cachedDirection == null ? (float) MovementUtil.getDirection() : cachedDirection;
                double sin = MathHelper.sin(direction);
                double cos = MathHelper.cos(direction);

                double xPos = event.getX() - sin * -0.25;
                double zPos = event.getZ() + cos * -0.25;
                Double closestSurfaceY = null;

                var box = player.getBoundingBox();
                double playerX = box.getCenter().x - sin * -0.25;
                double playerZ = box.getCenter().z + cos * -0.25;
                double playerFeetY = box.minY;

                for (int y = MathHelper.floor(playerFeetY); y >= Math.max(MathHelper.floor(playerFeetY) - 10, level.getBottomY()); y--) {
                    BlockPos blockPos = new BlockPos((int) Math.floor(playerX), y, (int) Math.floor(playerZ));
                    var blockState = level.getBlockState(blockPos);

                    if (!blockState.isAir()) {
                        var shape = blockState.getCollisionShape(level, blockPos);
                        double blockMaxY = Double.NEGATIVE_INFINITY;
                        for (var collisionBox : shape.getBoundingBoxes()) {
                            if (collisionBox.maxY > blockMaxY)
                                blockMaxY = collisionBox.maxY;
                        }
                        if (blockMaxY == Double.NEGATIVE_INFINITY)
                            blockMaxY = 1.0;
                        double surfaceY = y + blockMaxY;

                        if (surfaceY <= playerFeetY) {
                            closestSurfaceY = surfaceY;
                            break;
                        }
                    }
                }

                if (phaseTicks >= 2) {
                    if (closestSurfaceY != null) {
                        event.setOnGround(true);
                        event.setX(xPos);
                        event.setY(closestSurfaceY - 0.07);
                        event.setZ(zPos);
                    }
                    phaseTicks++;
                } else if (closestSurfaceY != null) {
                    stopHorizontalMovement();
                    isClipping = true;
                    if (cachedDirection == null)
                        cachedDirection = direction;
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        syncMode();
        if (!isEnabled()) return;
        var player = mc.player;
        if (player == null || mc.world == null) {
            resetState();
            return;
        }

        if (mode.is("AAC 4")) {
            setEnabled(false);
            return;
        }

        if (mode.is("vanilla")) {
            updateVanillaPhase(player);
            return;
        }

        if (mode.is("intave")) {
            if (mc.options.attackKey.isPressed() && player.getPitch() > 80) {
                PacketUtil.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                        player.getBlockPos().down(),
                        Direction.UP
                ));

                mining = true;
            } else {
                mining = false;
            }

            if (mining)
                player.setPosition(player.getX(), player.getY() - 0.0042, player.getZ());

            if (player.isSneaking()) {
                float distance = 0.005f;
                double rotation = Math.toRadians(player.getYaw());

                if (mc.options.forwardKey.isPressed()) move(player, rotation, distance, 1, 1);
                else if (mc.options.backKey.isPressed()) move(player, rotation, -distance, 1, -1);
                else if (mc.options.leftKey.isPressed()) move(player, rotation, distance, -1, 1);
                else if (mc.options.rightKey.isPressed()) move(player, rotation, -distance, -1, -1);
            }
            return;
        }

        if (mode.is("ncp")) {
            if (player.horizontalCollision) isClipping = true;
            if (!isClipping) return;

            phaseTicks++;

            if (phaseTicks >= 3) {
                stopHorizontalMovement();
                resetTimer();
                phaseTicks = 0;
                isClipping = false;
                cachedDirection = null;
            } else if (phaseTicks >= 1) {
                double offset = (phaseTicks >= 2) ? 1.7 : 0.06;
                float direction = cachedDirection == null ? (float) MovementUtil.getDirection() : cachedDirection;
                double sin = MathHelper.sin(direction);
                double cos = MathHelper.cos(direction);

                Vec3d newPos = new Vec3d(
                        player.getX() + (-sin * offset),
                        player.getY(),
                        player.getZ() + (cos * offset)
                );

                TimerSpeedUtil.setTimerSpeed(0.3f);
                timerModified = true;
                stopHorizontalMovement();
                player.setPosition(newPos.x, newPos.y, newPos.z);
                if (cachedDirection == null)
                    cachedDirection = direction;
            }
        }
    }

    private void move(ClientPlayerEntity player, double rotation, float distance, int xMultiplier, int zMultiplier) {
        double xx = Math.cos(rotation) * distance * xMultiplier;
        double zz = Math.sin(rotation) * distance * zMultiplier;

        player.setPosition(player.getX() + xx, player.getY(), player.getZ() + zz);
    }

    private void updateVanillaPhase(ClientPlayerEntity player) {
        if (mc.world == null || player.hasVehicle() || player.isSpectator()) {
            resetVanillaPhase();
            return;
        }

        if (vanillaCorrectionCooldown > 0) {
            vanillaCorrectionCooldown--;
            return;
        }

        if (vanillaClipping) {
            if (--vanillaPendingTicks <= 0)
                resetVanillaPhase();
            return;
        }

        if (!player.horizontalCollision || !MovementUtil.isMoving())
            return;

        Vec3d movementDirection = getMovementVector();
        if (movementDirection.horizontalLengthSquared() == 0.0)
            return;

        VanillaPhasePath path = findVanillaPath(player, movementDirection);
        if (path == null)
            return;

        boolean onGround = player.isOnGround();
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                path.boundaryPosition.x,
                path.boundaryPosition.y,
                path.boundaryPosition.z,
                onGround,
                player.horizontalCollision
        ));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                path.entryPosition.x,
                path.entryPosition.y,
                path.entryPosition.z,
                onGround,
                true
        ));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                path.exitPosition.x,
                path.exitPosition.y,
                path.exitPosition.z,
                onGround,
                false
        ));

        stopHorizontalMovement();
        player.setPosition(path.exitPosition.x, path.exitPosition.y, path.exitPosition.z);
        vanillaClipping = true;
        vanillaPendingTicks = VANILLA_PENDING_TICKS;
    }

    private VanillaPhasePath findVanillaPath(ClientPlayerEntity player, Vec3d movementDirection) {
        Box playerBox = player.getBoundingBox();
        Vec3d playerPosition = player.getEntityPos();

        if (intersectsOriginalCollision(player, playerBox)) {
            Vec3d exitPosition = findVanillaExit(player, playerBox, playerPosition, movementDirection, 0.0);
            return exitPosition == null ? null : new VanillaPhasePath(playerPosition, playerPosition, exitPosition);
        }

        CollisionBoundary exactBoundary = findCollisionBoundary(player, playerBox, movementDirection);
        CollisionBoundary deflatedBoundary = findCollisionBoundary(
                player,
                playerBox.contract(VANILLA_SERVER_COLLISION_EPSILON),
                movementDirection
        );
        if (exactBoundary == null || deflatedBoundary == null)
            return null;

        double entryWindow = deflatedBoundary.collidingDistance - exactBoundary.collidingDistance;
        if (entryWindow <= 0.0)
            return null;

        double entryDistance = exactBoundary.collidingDistance + entryWindow * VANILLA_ENTRY_FRACTION;
        Box entryBox = moveBox(playerBox, movementDirection, entryDistance);

        if (!intersectsOriginalCollision(player, entryBox)
                || intersectsOriginalCollision(player, entryBox.contract(VANILLA_SERVER_COLLISION_EPSILON)))
            return null;

        Vec3d exitPosition = findVanillaExit(
                player,
                playerBox,
                playerPosition,
                movementDirection,
                exactBoundary.collidingDistance
        );
        if (exitPosition == null)
            return null;

        Vec3d boundaryPosition = playerPosition.add(movementDirection.multiply(exactBoundary.clearDistance));
        Vec3d entryPosition = playerPosition.add(movementDirection.multiply(entryDistance));
        return new VanillaPhasePath(boundaryPosition, entryPosition, exitPosition);
    }

    private CollisionBoundary findCollisionBoundary(ClientPlayerEntity player, Box playerBox, Vec3d movementDirection) {
        double clearDistance = 0.0;
        double collidingDistance = VANILLA_SCAN_STEP;

        while (collidingDistance <= VANILLA_CONTACT_SEARCH_DISTANCE
                && !intersectsOriginalCollision(player, moveBox(playerBox, movementDirection, collidingDistance))) {
            clearDistance = collidingDistance;
            collidingDistance += VANILLA_SCAN_STEP;
        }

        if (collidingDistance > VANILLA_CONTACT_SEARCH_DISTANCE)
            return null;

        for (int i = 0; i < VANILLA_CONTACT_SEARCH_ITERATIONS; i++) {
            double middle = (clearDistance + collidingDistance) * 0.5;
            if (intersectsOriginalCollision(player, moveBox(playerBox, movementDirection, middle)))
                collidingDistance = middle;
            else
                clearDistance = middle;
        }

        return new CollisionBoundary(clearDistance, collidingDistance);
    }

    private Vec3d findVanillaExit(ClientPlayerEntity player, Box playerBox, Vec3d playerPosition,
                                 Vec3d movementDirection, double startDistance) {
        double firstDistance = Math.max(VANILLA_SCAN_STEP, startDistance + VANILLA_SCAN_STEP);
        for (double distance = firstDistance; distance <= VANILLA_MAX_DISTANCE; distance += VANILLA_SCAN_STEP) {
            Box candidateBox = moveBox(playerBox, movementDirection, distance);
            if (!intersectsOriginalCollision(player, candidateBox))
                return playerPosition.add(movementDirection.multiply(distance));
        }

        return null;
    }

    private Box moveBox(Box box, Vec3d direction, double distance) {
        return box.offset(direction.x * distance, 0.0, direction.z * distance);
    }

    private boolean intersectsOriginalCollision(ClientPlayerEntity player, Box boundingBox) {
        if (mc.world == null)
            return false;

        int minX = MathHelper.floor(boundingBox.minX);
        int maxX = MathHelper.floor(boundingBox.maxX);
        int minY = Math.max(mc.world.getBottomY(), MathHelper.floor(boundingBox.minY));
        int maxY = Math.min(mc.world.getTopYInclusive(), MathHelper.floor(boundingBox.maxY));
        int minZ = MathHelper.floor(boundingBox.minZ);
        int maxZ = MathHelper.floor(boundingBox.maxZ);
        ShapeContext context = ShapeContext.of(player);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    var blockState = mc.world.getBlockState(blockPos);
                    if (blockState.isAir())
                        continue;

                    var shape = BlockCollisionEventGuard.getOriginalShape(context, blockState, mc.world, blockPos);
                    for (Box localBox : shape.getBoundingBoxes()) {
                        if (localBox.offset(blockPos).intersects(boundingBox))
                            return true;
                    }
                }
            }
        }

        return false;
    }

    private void resetVanillaPhase() {
        vanillaClipping = false;
        vanillaPendingTicks = 0;
        vanillaCorrectionCooldown = 0;
    }

    private void syncMode() {
        if (!activeMode.equals(mode.getValue())) {
            resetState();
            activeMode = mode.getValue();
        }
    }

    private void resetState() {
        resetTimer();
        resetVanillaPhase();
        isClipping = false;
        phaseTicks = 0;
        cachedDirection = null;
        mining = false;
    }

    private void resetTimer() {
        if (timerModified) {
            TimerSpeedUtil.reset();
            timerModified = false;
        }
    }

    private void stopHorizontalMovement() {
        if (mc.player != null) {
            mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        }
    }

    private Vec3d getMovementVector() {
        double direction = MovementUtil.getDirection();
        return new Vec3d(-Math.sin(direction), 0.0, Math.cos(direction)).normalize();
    }

    private record CollisionBoundary(double clearDistance, double collidingDistance) {
    }

    private record VanillaPhasePath(Vec3d boundaryPosition, Vec3d entryPosition, Vec3d exitPosition) {
    }
}
