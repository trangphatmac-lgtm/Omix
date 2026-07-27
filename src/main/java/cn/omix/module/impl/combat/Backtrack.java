package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.render.Render3D;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;

public final class Backtrack extends Module {
    private static final int MAX_STORED_PACKETS = 512;
    private static final Color REAL_POSITION_COLOR = new Color(255, 0, 0, 80);

    private final NumberValue trackDelay = new NumberValue("Track Delay", 200, 0, 2000, 1);
    private final BoolValue showRealPosition = new BoolValue("ShowRealPosition", true);

    private final ArrayDeque<Packet<?>> storedPackets = new ArrayDeque<>();
    private final ArrayDeque<EntityPacketPosition> storedEntityMoves = new ArrayDeque<>();
    private final Object storageLock = new Object();
    private final TrackedPosition simulatedTrackedPosition = new TrackedPosition();
    private final TimerUtil timer = new TimerUtil();

    private Entity lastAttackedEntity;
    private int lastAttackedEntityId = -1;
    private Vec3d simulatedPosition;
    private Vec3d simulatedMovement = Vec3d.ZERO;
    private float simulatedYaw;
    private float simulatedPitch;
    private volatile boolean freezing;

    public Backtrack() {
        super("Backtrack", Category.Combat);
    }

    public boolean isBacktracking() {
        return isEnabled() && freezing;
    }

    @Override
    public void onEnable() {
        updateSuffix();
    }

    @Override
    public void onDisable() {
        releasePackets();
        clearTracking();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        synchronized (storageLock) {
            storedPackets.clear();
            storedEntityMoves.clear();
        }
        clearTracking();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        Entity target = event.getEntity();
        if (target != null && target != mc.player) {
            setTarget(target);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received
                || event.getPacket() == null
                || mc.player == null
                || mc.world == null) {
            return;
        }

        try {
            if (!validateTarget()) {
                return;
            }

            Packet<?> packet = event.getPacket();
            if (packet instanceof EntityS2CPacket entityPacket) {
                handleEntityMovePacket(event, entityPacket);
            } else if (packet instanceof EntityPositionS2CPacket positionPacket) {
                handleEntityPositionPacket(event, positionPacket);
            } else if (packet instanceof EntityPositionSyncS2CPacket syncPacket) {
                handleEntityPositionSyncPacket(event, syncPacket);
            }
        } catch (Exception ignored) {
            releasePackets();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost()) {
            return;
        }

        updateSuffix();
        if (mc.player == null || mc.world == null || !freezing) {
            return;
        }

        try {
            if (!validateTarget()) {
                return;
            }

            doSmoothRelease();
            if (!freezing) {
                return;
            }

            Entity target = lastAttackedEntity;
            Vec3d position = getTrackedPosition(target);
            if (target == null || position == null) {
                releasePackets();
                return;
            }

            if (isServerPositionInReleaseRange(position)) {
                releasePackets();
                return;
            }

            long delay = MathHelper.clamp(trackDelay.getValue().longValue(), 0L, 2000L);
            if (timer.getTime() < delay) {
                return;
            }

            Box targetBox = makeBoundingBox(target, position);
            double range = getLookingTargetRange(targetBox);
            if (range == Double.MAX_VALUE) {
                range = Math.sqrt(targetBox.squaredMagnitude(mc.player.getEyePos())) + 0.075D;
            }

            if (range <= 1.5D || timer.hasTimeElapsed(100L) && range >= 1.5D) {
                releasePackets();
            }
        } catch (Exception ignored) {
            releasePackets();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!showRealPosition.getValue()
                || !freezing
                || mc.player == null
                || mc.world == null) {
            return;
        }

        Entity target = lastAttackedEntity;
        if (target == null
                || target.isRemoved()
                || !target.isAlive()
                || mc.world.getEntityById(lastAttackedEntityId) != target) {
            return;
        }

        Vec3d realPosition = getTrackedPosition(target);
        if (realPosition == null) {
            return;
        }

        Vec3d offset = realPosition.subtract(target.getEntityPos());
        Box realPositionBox = target.getBoundingBox().offset(offset.x, offset.y, offset.z);
        Render3D.drawBox(event, realPositionBox, REAL_POSITION_COLOR, true, true);
    }

    private void handleEntityMovePacket(PacketEvent event, EntityS2CPacket packet) {
        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity) || !isTarget(entity)) {
            return;
        }

        ensureSimulationInitialized(entity);

        Box beforeBox = entity.getBoundingBox();
        Vec3d afterPosition = simulateMovePacket(entity, packet);
        Box afterBox = makeBoundingBox(entity, afterPosition);
        double beforeRange = Math.sqrt(beforeBox.squaredMagnitude(mc.player.getEyePos()));
        double afterRange = Math.sqrt(afterBox.squaredMagnitude(mc.player.getEyePos()));

        if (!freezing
                && packet.isPositionChanged()
                && beforeRange <= 8.0D
                && afterRange >= 1.5D
                && afterRange <= 7.5D
                && afterRange > beforeRange + 0.02D
                && (!(entity instanceof PlayerEntity player) || player.hurtTime <= getCalculatedMaxHurtTime())) {
            startFreeze();
            storePacket(event, packet, entity, afterPosition);
            return;
        }

        if (freezing && beforeRange > 8.0D && afterRange <= beforeRange) {
            releasePackets();
            return;
        }

        if (freezing) {
            storePacket(event, packet, entity, afterPosition);
        }
    }

    private void handleEntityPositionPacket(PacketEvent event, EntityPositionS2CPacket packet) {
        Entity entity = mc.world.getEntityById(packet.entityId());
        if (!(entity instanceof PlayerEntity) || !isTarget(entity)) {
            return;
        }

        ensureSimulationInitialized(entity);
        EntityPosition current = new EntityPosition(
                simulatedPosition,
                simulatedMovement,
                simulatedYaw,
                simulatedPitch
        );
        EntityPosition next = EntityPosition.apply(current, packet.change(), packet.relatives());
        updateSimulation(next.position(), next.deltaMovement(), next.yaw(), next.pitch());

        if (freezing) {
            storePacket(event, packet, entity, next.position());
        }
    }

    private void handleEntityPositionSyncPacket(PacketEvent event, EntityPositionSyncS2CPacket packet) {
        Entity entity = mc.world.getEntityById(packet.id());
        if (!(entity instanceof PlayerEntity) || !isTarget(entity)) {
            return;
        }

        EntityPosition values = packet.values();
        updateSimulation(values.position(), values.deltaMovement(), values.yaw(), values.pitch());

        if (freezing) {
            storePacket(event, packet, entity, values.position());
        }
    }

    private Vec3d simulateMovePacket(Entity entity, EntityS2CPacket packet) {
        Vec3d position = simulatedPosition != null ? simulatedPosition : entity.getEntityPos();

        if (packet.isPositionChanged()) {
            position = simulatedTrackedPosition.withDelta(
                    packet.getDeltaX(),
                    packet.getDeltaY(),
                    packet.getDeltaZ()
            );
            simulatedTrackedPosition.setPos(position);
            simulatedPosition = position;
        }

        if (packet.hasRotation()) {
            simulatedYaw = packet.getYaw();
            simulatedPitch = packet.getPitch();
        }

        return position;
    }

    private void storePacket(PacketEvent event, Packet<?> packet, Entity entity, Vec3d position) {
        boolean overflow;
        synchronized (storageLock) {
            if (!freezing) {
                return;
            }

            overflow = storedPackets.size() >= MAX_STORED_PACKETS;
            if (!overflow) {
                event.setCancelled(true);
                storedPackets.addLast(packet);
                storedEntityMoves.addLast(new EntityPacketPosition(entity, position));
            }
        }

        if (overflow) {
            releasePackets();
            return;
        }

        if (freezing && isServerPositionInReleaseRange(position)) {
            releasePackets();
        }
    }

    private void startFreeze() {
        timer.reset();
        freezing = true;
    }

    private void releasePackets() {
        ArrayDeque<Packet<?>> pendingPackets;
        synchronized (storageLock) {
            pendingPackets = new ArrayDeque<>(storedPackets);
            storedPackets.clear();
            storedEntityMoves.clear();
            freezing = false;
        }

        if (pendingPackets.isEmpty()) {
            return;
        }

        mc.execute(() -> {
            while (!pendingPackets.isEmpty()) {
                PacketUtil.receivePacket(pendingPackets.removeFirst());
            }
        });
    }

    private void clearTracking() {
        synchronized (storageLock) {
            storedEntityMoves.clear();
        }
        resetTarget();
        simulatedPosition = null;
        simulatedMovement = Vec3d.ZERO;
        simulatedYaw = 0.0F;
        simulatedPitch = 0.0F;
        freezing = false;
    }

    private void setTarget(Entity target) {
        if (lastAttackedEntityId != -1 && lastAttackedEntityId != target.getId()) {
            releasePackets();
        }

        lastAttackedEntity = target;
        lastAttackedEntityId = target.getId();
        initializeSimulation(target);
    }

    private void resetTarget() {
        lastAttackedEntity = null;
        lastAttackedEntityId = -1;
    }

    private boolean isTarget(Entity entity) {
        return entity != null
                && entity.getId() == lastAttackedEntityId
                && entity == lastAttackedEntity;
    }

    private boolean validateTarget() {
        if (lastAttackedEntity == null || mc.player == null || mc.world == null) {
            if (freezing) {
                releasePackets();
            }
            resetTarget();
            return false;
        }

        Entity worldEntity = mc.world.getEntityById(lastAttackedEntityId);
        if (worldEntity != lastAttackedEntity
                || lastAttackedEntity.isRemoved()
                || !lastAttackedEntity.isAlive()
                || mc.player.distanceTo(lastAttackedEntity) > 10.0F) {
            if (freezing) {
                releasePackets();
            }
            resetTarget();
            return false;
        }

        return true;
    }

    private void doSmoothRelease() {
        Entity target = lastAttackedEntity;
        if (target == null) {
            releasePackets();
            return;
        }

        boolean found = false;
        synchronized (storageLock) {
            for (EntityPacketPosition move : storedEntityMoves) {
                if (target == move.entity) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            releasePackets();
        }
    }

    private void ensureSimulationInitialized(Entity entity) {
        if (simulatedPosition == null) {
            initializeSimulation(entity);
        }
    }

    private void initializeSimulation(Entity entity) {
        Vec3d base = entity.getTrackedPosition().getPos();
        if (base == null) {
            base = entity.getEntityPos();
        }

        simulatedTrackedPosition.setPos(base);
        simulatedPosition = entity.getEntityPos();
        simulatedMovement = entity.getMovement();
        simulatedYaw = entity.getYaw();
        simulatedPitch = entity.getPitch();
    }

    private void updateSimulation(Vec3d position, Vec3d movement, float yaw, float pitch) {
        simulatedPosition = position;
        simulatedMovement = movement;
        simulatedYaw = yaw;
        simulatedPitch = pitch;
        simulatedTrackedPosition.setPos(position);
    }

    private Vec3d getTrackedPosition(Entity entity) {
        if (entity == null) {
            return null;
        }

        synchronized (storageLock) {
            Iterator<EntityPacketPosition> iterator = storedEntityMoves.descendingIterator();
            while (iterator.hasNext()) {
                EntityPacketPosition move = iterator.next();
                if (move.entity == entity) {
                    return move.position;
                }
            }
        }

        return simulatedPosition;
    }

    private boolean isServerPositionInReleaseRange(Vec3d position) {
        double releaseRange = 3.5D;
        return mc.player != null
                && position != null
                && position.squaredDistanceTo(mc.player.getEntityPos()) <= releaseRange * releaseRange;
    }

    private Box makeBoundingBox(Entity entity, Vec3d position) {
        double halfWidth = entity.getWidth() / 2.0D;
        double minY = position.y - 0.1D;
        double maxY = position.y + entity.getHeight() + 0.1D;
        return new Box(
                position.x - halfWidth,
                minY,
                position.z - halfWidth,
                position.x + halfWidth,
                maxY,
                position.z + halfWidth
        );
    }

    private double getLookingTargetRange(Box box) {
        Vec3d eyePosition = mc.player.getEyePos();
        Vec3d reachPosition = eyePosition.add(mc.player.getRotationVec(1.0F).multiply(8.0D));
        Optional<Vec3d> hit = box.raycast(eyePosition, reachPosition);
        return hit.map(eyePosition::distanceTo).orElse(Double.MAX_VALUE);
    }

    private int getCalculatedMaxHurtTime() {
        return 6;
    }

    private void updateSuffix() {
        setSuffix(trackDelay.getValue().intValue() + "ms");
    }

    private record EntityPacketPosition(Entity entity, Vec3d position) {
    }
}
