package cn.omix.util.player.velocity;

import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.RotationRequestEvent;
import cn.omix.management.rotation.RotationRequest;
import cn.omix.management.movement.MovementCorrection;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.combat.Velocity;
import cn.omix.module.impl.move.Fly;
import cn.omix.module.impl.move.LongJump;
import cn.omix.module.impl.player.AntiBot;
import cn.omix.util.IMinecraft;
import cn.omix.util.Util;
import cn.omix.util.player.RotationUtil;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Port of Heypixel Test; intentionally preserves its targeting and packet arithmetic. */
public final class HeypixelReduce implements IMinecraft {
    private final Velocity module;
    private float[] rotations;

    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    private Entity target;
    private Entity renderTarget;
    private Vec3d renderTargetPos;
    private int attackQueue;
    private boolean receiveDamage;
    private int alinkTicks = -1;
    private String releaseReason;
    private double velocityStrength;
    private boolean attacking;
    private int hitSelectSkips;

    public HeypixelReduce(Velocity module) {
        this.module = module;
    }

    public synchronized void onReceivePacket(final PacketEvent event) {
        if (mc.player == null || mc.world == null || (module.getModule(LongJump.class).isEnabled() || module.getModule(Fly.class).isEnabled())) {
            return;
        }

        final Packet<?> packet = event.getPacket();

        if (alinkTicks >= 0) {
            if (packet instanceof ChatMessageS2CPacket || packet instanceof GameMessageS2CPacket) {
                return;
            }

            if (packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {
                releaseReason = "disconnect";
                return;
            }

            if (packet instanceof PlayerPositionLookS2CPacket) {
                releaseReason = "flag";
                return;
            }

            if (packet instanceof CommonPingS2CPacket || packet instanceof EntityS2CPacket || packet instanceof EntityPositionS2CPacket || packet instanceof EntityPositionSyncS2CPacket) {
                updateRenderTargetPosition(packet);
                event.setCancelled(true);
                packets.add(packet);
                return;
            }
        }

        if (packet instanceof EntityDamageS2CPacket damagePacket && damagePacket.entityId() == mc.player.getId()) {
            receiveDamage = true;
            return;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()
                && receiveDamage) {
            receiveDamage = false;

            if (mc.player.isUsingItem()) {
                return;
            }

            if (module.getRequireKillAura().getValue()) {
                final Aura killAura = module.getModule(Aura.class);
                if (!killAura.isEnabled() || killAura.getTarget() == null) {
                    return;
                }
            }

            findTarget();
            if (renderTarget == null) {
                return;
            }

            final Vec3d vel = velocityPacket.getVelocity();
            velocityStrength = velocityStrength(vel);
            final int currentAttackCount = getCurrentAttackCount();
            if (currentAttackCount <= 0) {
                return;
            }

            hitSelectSkips = currentAttackCount;

            if (target == null || !mc.player.isSprinting()) {
                if (module.getDebug().getValue()) {
                    debug(!mc.player.isSprinting() ? "Alink... (not sprinting)" : "Alink...");
                }
                renderTargetPos = getEntityPos(renderTarget);
                alinkTicks = module.getAlinkMaxDelay().getValue().intValue();
                releaseReason = null;
                event.setCancelled(true);
                packets.add(packet);
            } else {
                attackQueue = currentAttackCount;
                if (module.getDebug().getValue()) {
                    debug("Attack count: " + attackQueue);
                }
            }
        }
    }

    public synchronized void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || alinkTicks < 0 || releaseReason != null) {
            return;
        }

        if (alinkTicks > 0) {
            alinkTicks--;
        }

        findTarget();

        if (alinkTicks == 0) {
            releaseReason = "max delay";
            return;
        }

        if (mc.player.getAbilities().flying || mc.player.isSpectator()) {
            releaseReason = "spectator";
            return;
        }

        if (renderTargetPos == null || getPlayerPos().distanceTo(renderTargetPos) > module.getAlinkTargetRange().getValue().doubleValue()) {
            releaseReason = "out of range";
            return;
        }

        if (target != null) {
            event.setForward(1.0F);
            event.setStrafe(0.0F);
            releaseReason = "";
        }
    }

    public synchronized void onPreTick() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        attacking = false;
        rotations = null;

        if (releaseReason != null) {
            flushQueuedPackets();
            alinkTicks = -1;
            renderTarget = null;
            renderTargetPos = null;

            if (releaseReason.isEmpty()) {
                attackQueue = getCurrentAttackCount();
                if (module.getDebug().getValue()) {
                    debug("Finish alink");
                    debug("Attack count: " + attackQueue);
                }
            } else if (module.getDebug().getValue()) {
                debug("Finish alink (" + releaseReason + ")");
            }

            releaseReason = null;
        }

        if (attackQueue <= 0) {
            return;
        }

        if (target == null || target.isRemoved()) {
            attackQueue = 0;
            return;
        }

        if (!rotateToTarget(target)) {
            return;
        }

        attacking = true;
        if (module.getAttackMode().is("OneTime")) {
            while (attackQueue > 0) {
                attackOnce();
                attackQueue--;
            }
        } else {
            attackOnce();
            attackQueue--;
        }

        if (attackQueue <= 0) {
            target = null;
            velocityStrength = 0.0D;
        }
    }

    private void attackOnce() {
        if (target == null || mc.player == null || mc.interactionManager == null) {
            return;
        }

        // The original mouse handler applies the player rotation before attacking,
        // while keeping a separate camera rotation. Omix uses silent requests instead.
        float cameraYaw = mc.player.getYaw();
        float cameraPitch = mc.player.getPitch();
        try {
            mc.player.setYaw(rotations[0]);
            mc.player.setPitch(rotations[1]);
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.setVelocity(mc.player.getVelocity().multiply(0.6D, 1.0D, 0.6D));
        } finally {
            mc.player.setYaw(cameraYaw);
            mc.player.setPitch(cameraPitch);
        }
    }

    private boolean rotateToTarget(final Entity entity) {
        if (mc.player == null || entity == null) {
            return false;
        }

        rotations = RotationUtil.getRotations(entity.getEyePos());
        return true;
    }

    private void findTarget() {
        if (mc.player == null || mc.world == null) {
            target = null;
            return;
        }

        Entity localTarget = null;
        if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            final Entity entity = ehr.getEntity();
            if (entity instanceof LivingEntity && entity != mc.player && !entity.isRemoved() && !isBot(entity)) {
                localTarget = entity;
            }
        }

        if (alinkTicks == -1) {
            renderTarget = localTarget;
        }

        if (localTarget != null) {
            target = localTarget;
            return;
        }

        Entity targetAround = null;
        Vec3d targetPos = null;
        double bestDistance = module.getAlinkTargetRange().getValue().doubleValue();
        for (final Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity == mc.player || entity.isRemoved() || isBot(entity)) {
                continue;
            }

            final double distance = mc.player.distanceTo(entity);
            if (distance > bestDistance) {
                continue;
            }

            if (renderTarget != null && entity.getId() == renderTarget.getId()) {
                continue;
            }

            bestDistance = distance;
            targetAround = entity;
            targetPos = getEntityPos(entity);
        }

        if (renderTarget != null && renderTargetPos != null && targetPos != null) {
            if (renderTargetPos.distanceTo(getPlayerPos()) <= targetPos.distanceTo(getPlayerPos())) {
                targetAround = renderTarget;
                targetPos = renderTargetPos;
            }
        }

        if (targetAround == null || targetPos == null) {
            target = null;
            return;
        }

        if (targetPos.distanceTo(getPlayerPos()) <= 3.0D) {
            target = targetAround;
        }
        // Reference behavior: a farther candidate does not clear an earlier target.

        if (alinkTicks == -1) {
            renderTarget = targetAround;
        }
    }

    private void updateRenderTargetPosition(final Packet<?> packet) {
        if (renderTarget == null || renderTargetPos == null) {
            return;
        }

        if (packet instanceof EntityS2CPacket entityPacket) {
            final Entity entity = entityPacket.getEntity(mc.world);
            if (entity != null && entity.getId() == renderTarget.getId()) {
                // Deliberately retain the reference's raw deltas (no /4096 conversion).
                renderTargetPos = renderTargetPos.add(entityPacket.getDeltaX(), entityPacket.getDeltaY(), entityPacket.getDeltaZ());
            }
            return;
        }

        if (packet instanceof EntityPositionS2CPacket positionPacket
                && positionPacket.entityId() == renderTarget.getId()) {
            renderTargetPos = positionPacket.change().position();
            return;
        }

        if (packet instanceof EntityPositionSyncS2CPacket syncPacket && syncPacket.id() == renderTarget.getId()) {
            renderTargetPos = syncPacket.values().position();
        }
    }

    private int getCurrentAttackCount() {
        return attackCount(velocityStrength, module.getAutoAttackCount().getValue(), module.getAttackCount().getValue().intValue());
    }

    static double velocityStrength(Vec3d velocity) {
        // The reference uses X/Y, not horizontal X/Z strength.
        double x = velocity.x * 8000.0D;
        double y = velocity.y * 8000.0D;
        return Math.sqrt(x * x + y * y);
    }

    static int attackCount(double velocityStrength, boolean automatic, int manualCount) {
        if (!automatic) return manualCount;

        if (velocityStrength < 1000.0D) {
            return 0;
        }
        if (velocityStrength < 2000.0D) {
            return 3;
        }
        if (velocityStrength < 10000.0D) {
            return 4;
        }
        return 5;
    }

    private void flushQueuedPackets() {
        if (packets.isEmpty()) return;
        final ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        final var world = mc.world;
        final var player = mc.player;
        final var queued = new java.util.ArrayList<Packet<?>>();
        Packet<?> packet;
        while ((packet = packets.poll()) != null) queued.add(packet);
        if (networkHandler == null) return;

        Runnable replay = () -> {
            // Never apply a previous world's buffered velocity to a new player/session.
            if (mc.getNetworkHandler() != networkHandler || mc.world != world || mc.player != player) return;
            for (Packet<?> queuedPacket : queued) {
                applyPacket(queuedPacket, networkHandler);
            }
        };
        if (mc.isOnThread()) replay.run();
        else mc.execute(replay);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyPacket(Packet<?> packet, ClientPlayNetworkHandler networkHandler) {
        try {
            ((Packet) packet).apply(networkHandler);
        } catch (Exception ignored) {
            // Match the reference: a failed replay must not strand the remaining queue.
        }
    }

    public synchronized void reset() {
        rotations = null;
        target = null;
        renderTarget = null;
        renderTargetPos = null;
        attackQueue = 0;
        receiveDamage = false;
        alinkTicks = -1;
        releaseReason = null;
        velocityStrength = 0.0D;
        attacking = false;
        hitSelectSkips = 0;
        packets.clear();
    }

    private Vec3d getPlayerPos() {
        return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private Vec3d getEntityPos(final Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    public synchronized boolean isAttacking() {
        return attacking;
    }

    public synchronized int getHitSelectSkips() {
        return hitSelectSkips;
    }

    public synchronized boolean consumeHitSelectSkip() {
        if (hitSelectSkips > 0) {
            hitSelectSkips--;
            return true;
        }
        return false;
    }

    public synchronized void disable() {
        flushQueuedPackets();
        reset();
    }

    public synchronized String getSuffix() {
        return alinkTicks >= 0 ? "Heypixel Reduce " + (module.getAlinkMaxDelay().getValue().intValue() - alinkTicks) + "Ticks" : "Heypixel Reduce";
    }

    public synchronized void onRotationRequest(RotationRequestEvent event) {
        if (rotations != null) {
            event.submit(RotationRequest.builder(module.getName(), rotations, 500)
                    .speed(0).movementCorrection(MovementCorrection.Strict).build());
        }
    }

    private boolean isBot(Entity entity) {
        AntiBot antiBot = module.getModule(AntiBot.class);
        return antiBot != null && antiBot.isEnabled() && entity instanceof LivingEntity living && antiBot.isBot(living);
    }

    private void debug(String message) {
        mc.execute(() -> Util.logToChat(message));
    }
}
