package cn.omix.util.world.adjustdetect;

import net.minecraft.block.BedBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Packet-driven port of Adjust's HackerDetector check dispatcher for the current protocol. */
public final class AdjustHackerDetector {
    public interface Alert { void accept(PlayerEntity player, String check, int count, int threshold, boolean cheater); }

    // Names and thresholds are the eleven checks installed by Adjust's CheckDispatcher.
    private enum Check {
        VELOCITY("Velocity", 4), AUTO_BLOCK("Auto-Block", 3), SCAFFOLD("Scaffold", 3),
        SCAFFOLD_2("Scaffold 2", 10), SPRINT("Sprint", 5), FRICTION("Friction", 5),
        ROTATION("Rotation", 1), AUTO_BLOCK_2("Auto-Block 2", 4),
        AUTO_BLOCK_3("Auto-Block 3", 6), NUKER("Nuker", 4), INVENTORY("Inventory", 3);
        final String name;
        final int threshold;
        Check(String name, int threshold) { this.name = name; this.threshold = threshold; }
    }

    private record Observed(Packet<?> packet, boolean outgoing) {}
    private final ConcurrentLinkedQueue<Observed> packets = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final Map<Integer, Tracked> players = new HashMap<>();
    private final Map<UUID, Integer> lastScores = new HashMap<>();
    private Object world;
    private MinecraftClient mc;
    private Predicate<PlayerEntity> include;
    private Alert alert;

    public void enqueue(Packet<?> packet, boolean outgoing) {
        if (!relevant(packet, outgoing) || queued.incrementAndGet() > 4096) {
            if (queued.get() > 4096) queued.decrementAndGet();
            return;
        }
        packets.add(new Observed(packet, outgoing));
    }

    private static boolean relevant(Packet<?> packet, boolean outgoing) {
        if (outgoing) return false; // Adjust's outbound attack hook only informs velocity attribution.
        return packet instanceof EntityS2CPacket || packet instanceof EntityPositionS2CPacket
                || packet instanceof EntityPositionSyncS2CPacket || packet instanceof EntityAnimationS2CPacket
                || packet instanceof EntityTrackerUpdateS2CPacket || packet instanceof EntityEquipmentUpdateS2CPacket
                || packet instanceof EntityStatusS2CPacket || packet instanceof EntityDamageS2CPacket
                || packet instanceof BlockBreakingProgressS2CPacket
                || packet instanceof BlockUpdateS2CPacket || packet instanceof EntitySpawnS2CPacket;
    }

    public void clear() {
        players.clear();
        lastScores.clear();
        packets.clear();
        queued.set(0);
        world = null;
    }

    public int getSuspicionScore(UUID uuid) {
        for (Tracked tracked : players.values()) if (tracked.player.getUuid().equals(uuid)) {
            return score(tracked);
        }
        return lastScores.getOrDefault(uuid, 0);
    }

    /** Only players currently tracked and past an Adjust check threshold are marked. */
    public boolean isCheater(UUID uuid) {
        if (uuid == null) return false;
        for (Tracked tracked : players.values())
            if (tracked.player.getUuid().equals(uuid)) return tracked.cheater;
        return false;
    }

    private static int score(Tracked tracked) {
        if (tracked.cheater) return 100;
        int best = 0;
        for (Check check : Check.values())
            best = Math.max(best, Math.min(99, (int) (100 * tracked.vl[check.ordinal()] / check.threshold)));
        return best;
    }

    public void tick(MinecraftClient mc, Predicate<PlayerEntity> include, Alert alert) {
        if (world != mc.world) clear();
        world = mc.world;
        this.mc = mc;
        this.include = include;
        this.alert = alert;
        Set<Integer> present = new HashSet<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isRemoved() || !include.test(player)) continue;
            present.add(player.getId());
            Tracked tracked = players.get(player.getId());
            if (tracked == null || tracked.player != player) {
                Tracked next = new Tracked(player);
                if (tracked != null && tracked.player.getUuid().equals(player.getUuid())) {
                    System.arraycopy(tracked.vl, 0, next.vl, 0, tracked.vl.length);
                    next.cheater = tracked.cheater;
                }
                players.put(player.getId(), next);
            }
        }
        for (Tracked tracked : players.values()) if (!present.contains(tracked.player.getId()))
            lastScores.put(tracked.player.getUuid(), score(tracked));
        players.keySet().retainAll(present);
        Observed observed;
        while ((observed = packets.poll()) != null) {
            queued.decrementAndGet();
            if (!observed.outgoing) process(observed.packet);
        }
        for (Tracked tracked : players.values()) {
            if (tracked.damageAt != 0 && now() - tracked.damageAt >= 300) {
                if (tracked.damageSource && tracked.position.squaredDistanceTo(tracked.velocityStart) < 0.0025
                        && tracked.player.isOnGround()) flag(tracked, Check.VELOCITY);
                tracked.damageAt = 0;
            }
        }
    }

    private Tracked tracked(Entity entity) {
        return entity instanceof PlayerEntity player ? players.get(player.getId()) : null;
    }

    private void process(Packet<?> packet) {
        if (packet instanceof EntityS2CPacket p) {
            Tracked t = tracked(p.getEntity(mc.world));
            if (t != null) move(t, p.hasRotation(), false);
        } else if (packet instanceof EntityPositionS2CPacket p) {
            Tracked t = players.get(p.entityId());
            if (t != null) move(t, true, true);
        } else if (packet instanceof EntityPositionSyncS2CPacket p) {
            Tracked t = players.get(p.id());
            if (t != null) move(t, true, true);
        } else if (packet instanceof EntityAnimationS2CPacket p) {
            Tracked t = players.get(p.getEntityId());
            if (t != null && p.getAnimationId() == EntityAnimationS2CPacket.SWING_MAIN_HAND) swing(t);
        } else if (packet instanceof EntityTrackerUpdateS2CPacket p) {
            Tracked t = players.get(p.id());
            if (t != null) metadata(t, p.trackedValues());
        } else if (packet instanceof EntityEquipmentUpdateS2CPacket p) {
            Tracked t = players.get(p.getEntityId());
            if (t != null) for (var equipment : p.getEquipmentList()) if (equipment.getFirst() == EquipmentSlot.MAINHAND) {
                boolean before = t.held.isEmpty() || !(t.held.getItem() instanceof BlockItem);
                t.held = equipment.getSecond();
                if (before && !t.held.isEmpty() && t.held.getItem() instanceof BlockItem) t.swapAt = now();
            }
        } else if (packet instanceof EntityStatusS2CPacket p) {
            Tracked t = tracked(p.getEntity(mc.world));
            if (t != null && p.getStatus() == 2) {
                t.damageAt = now(); t.damageMove = t.moves; t.velocityStart = t.player.getEntityPos();
                t.damageSource = false;
            }
        } else if (packet instanceof EntityDamageS2CPacket p) {
            Tracked t = players.get(p.entityId());
            if (t != null && p.sourceCauseId() > 0 && p.sourceCauseId() != p.entityId()) {
                t.damageSource = true;
                if (t.damageAt == 0) {
                    t.damageAt = now(); t.damageMove = t.moves; t.velocityStart = t.player.getEntityPos();
                }
            }
        } else if (packet instanceof BlockBreakingProgressS2CPacket p) {
            Tracked t = players.get(p.getEntityId());
            if (t != null) breakProgress(t, p.getPos(), p.getProgress());
        } else if (packet instanceof BlockUpdateS2CPacket p) {
            if (!p.getState().isAir()) placement(p.getPos());
        } else if (packet instanceof EntitySpawnS2CPacket p) {
            if (p.getEntityType() == EntityType.ITEM) itemSpawn(p);
        }
    }

    private void flag(Tracked t, Check check) {
        int index = check.ordinal();
        float level = t.vl[index] + 1;
        t.vl[index] = level;
        boolean first = !t.cheater && level >= check.threshold;
        if (first) t.cheater = true;
        lastScores.put(t.player.getUuid(), score(t));
        alert.accept(t.player, check.name, (int) level, check.threshold, t.cheater);
    }

    private static long now() { return System.currentTimeMillis(); }
    private static float angle(float a, float b) { return Math.abs(MathHelper.wrapDegrees(a - b)); }
    private static double direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return angle < 0 ? angle + 360 : angle;
    }

    private void move(Tracked t, boolean rotation, boolean teleport) {
        PlayerEntity p = t.player;
        Vec3d pos = p.getEntityPos();
        float yaw = p.getYaw(), pitch = p.getPitch();
        t.deltaYaw = MathHelper.wrapDegrees(yaw - t.yaw);
        t.deltaPitch = pitch - t.pitch;
        t.yaw = yaw; t.pitch = pitch;
        if (rotation && Math.abs(pitch) > 90) flag(t, Check.ROTATION);
        if (teleport) t.teleportAt = now();
        Vec3d delta = pos.subtract(t.position);
        if (delta.lengthSquared() <= 1.0E-6) return;
        t.previousMotionY = t.motionY;
        t.previousSpeed = t.speed;
        t.motionY = delta.y;
        t.motionX = delta.x;
        t.motionZ = delta.z;
        t.speed = Math.hypot(delta.x, delta.z);
        t.position = pos;
        t.moves++;
        sprint(t, delta);
        friction(t, delta);
        scaffoldRotation(t);
        fastUse(t);
        velocity(t, delta);
        if (t.pendingItems > 0 && !teleport) {
            while (t.pendingItems-- > 0) flag(t, Check.INVENTORY);
            t.pendingItems = 0;
        }
        if (teleport) t.pendingItems = 0;
        t.vl[Check.AUTO_BLOCK.ordinal()] *= t.cheater ? 1 : 0.985F;
    }

    private void sprint(Tracked t, Vec3d delta) {
        if (!t.sprinting || t.player.hasVehicle()) { t.sprintTicks = 0; return; }
        if (t.sprintTicks++ < 4 || t.motionY != 0 || t.previousMotionY != 0
                || t.speed >= 0.75 || t.previousSpeed >= 0.75 || t.speed <= 0.1) return;
        double difference = angle(t.yaw, (float) direction(delta.x, delta.z));
        if (difference > 46) {
            t.sprintBuffer += (float) (difference / 180 * (0.300000011920929 * t.speed + 1));
            if (t.sprintBuffer > 3.95) { t.sprintBuffer /= 1.2F; flag(t, Check.SPRINT); }
        } else t.sprintBuffer *= 0.2F;
    }

    private void friction(Tracked t, Vec3d delta) {
        float movementDirection = (float) direction(delta.x, delta.z);
        if (t.frictionSampleTicks > 0) {
            if (t.speed >= 0.43 * t.frictionSampleSpeed && t.speed >= 0.1 && t.speed <= 1.39
                    && (t.frictionSampleTicks < 5 || angle(t.yaw, t.frictionSampleYaw) <= 10)
                    && angle(movementDirection, t.frictionSampleDirection) <= 18.5) {
                if (++t.frictionSampleTicks >= 4) flag(t, Check.FRICTION);
            } else t.frictionSampleTicks = 0;
        } else if (now() - t.teleportAt > 1000 && angle(t.yaw, t.previousYaw) > 105
                && t.speed < 1 && angle(movementDirection, t.lastDirection) < 17.35) {
            t.frictionSampleTicks = 1;
            t.frictionSampleDirection = movementDirection;
            t.frictionSampleSpeed = t.speed;
            t.frictionSampleYaw = t.yaw;
        }
        t.lastDirection = movementDirection;
        t.previousYaw = t.yaw;
    }

    private void metadata(Tracked t, List<DataTracker.SerializedEntry<?>> entries) {
        if (entries == null || entries.isEmpty()) return;
        boolean sneaking = t.player.isSneaking();
        boolean sprinting = t.player.isSprinting();
        for (var entry : entries) if (entry.id() == 0 && entry.value() instanceof Byte flags) {
            sneaking = (flags & 2) != 0;
            sprinting = (flags & 8) != 0;
        }
        boolean using = t.player.isUsingItem();
        if (using) {
            if (!t.usingItem) t.useAt = now();
            if (t.sneaking == sneaking && t.sprinting == sprinting) t.itemUseTicks++;
        } else t.itemUseTicks = 0;
        if (sneaking && !t.sneaking) t.sneakAt = now();
        t.usingItem = using; t.sneaking = sneaking; t.sprinting = sprinting;
        if (t.usingItem && t.itemUseTicks > 1 && t.sprinting && t.player.isBlocking()) flag(t, Check.AUTO_BLOCK_2);
        if (t.usingItem && t.itemUseTicks > (t.held.isOf(net.minecraft.item.Items.BOW) ? 4 : 1)) {
            if (t.autoBlockBuffer++ > 2) flag(t, Check.AUTO_BLOCK);
        } else t.autoBlockBuffer = Math.max(0, t.autoBlockBuffer - 0.1F);
        if (!t.usingItem) t.pendingSwings.clear();
        scaffoldSneak(t);
    }

    private void swing(Tracked t) {
        t.swingAt = now();
        if (t.usingItem && !t.held.isEmpty() && t.player.isUsingItem()) t.pendingSwings.add(now());
        else t.fastUseBuffer = Math.max(0, t.fastUseBuffer - 0.5F);
    }

    private void fastUse(Tracked t) {
        if (t.pendingSwings.isEmpty()) return;
        Iterator<Long> it = t.pendingSwings.iterator();
        while (it.hasNext()) {
            long start = it.next();
            if (now() - start < 140) continue;
            if (t.usingItem && t.useAt <= start && t.fastUseBuffer++ > 1) flag(t, Check.AUTO_BLOCK_3);
            it.remove();
        }
    }

    private void scaffoldRotation(Tracked t) {
        float delta = Math.abs(t.deltaYaw) + Math.abs(t.deltaPitch);
        if (delta > 100) { t.lastRotationDelta = delta; t.rotationAt = t.lastRotationAt = now(); }
        t.scaffoldBuffer = Math.max(0, t.scaffoldBuffer * 0.99825001F - 0.00052499998F);
    }

    private void placement(BlockPos pos) {
        for (Tracked t : players.values()) {
            if (t.position.y < pos.getY() || t.position.y - pos.getY() >= 2
                    || t.position.squaredDistanceTo(Vec3d.ofCenter(pos)) >= 2.25) continue;
            t.placements.addLast(pos);
            while (t.placements.size() > 6) t.placements.removeFirst();
            t.placementAt = now();
            if (t.speed < 0.115 && t.motionY == 0 && t.previousMotionY == 0) continue;
            long difference = Math.abs(t.swapAt - t.lastRotationAt);
            if (t.lastRotationDelta <= (difference < 132 ? 124.5F : 167.5F)) continue;
            long allowance = (long) (Math.min(180, t.lastRotationDelta) * 1.3) + (t.pitch > 80 ? 360 : 260)
                    + (difference < 132 ? difference < 75 ? 275 : 135 : 0)
                    + (t.previousMotionY != 0 ? 100 : 0);
            if (now() - t.rotationAt >= allowance) {
                t.scaffoldBuffer = Math.max(0, 0.96249998F * t.scaffoldBuffer - 0.01125F);
                continue;
            }
            double multiplier = difference < 132 ? 2 : 1;
            if (t.player.isOnGround()) {
                double distance = angle(t.yaw, (float) direction(t.motionX, t.motionZ));
                if (distance < 120) continue;
                multiplier *= distance <= 160 ? 1 : distance / 180 * 0.5 + 1;
            }
            multiplier *= t.position.y == (int) t.position.y ? 1.2 : 0.75;
            t.rotationAt = now();
            float scaled = (float) (multiplier * 15);
            t.scaffoldBuffer = (scaled * 0.064999998F + 1) * t.scaffoldBuffer + scaled * 0.029999999F;
            if (t.scaffoldBuffer > 3.3499999F) flag(t, Check.SCAFFOLD);
        }
    }

    private void scaffoldSneak(Tracked t) {
        if (!t.sneaking || !atEdge(t) || !diagonal(t.placements) || now() - t.placementAt >= 240
                || now() - t.sneakAt >= 420) return;
        if (t.scaffold2Buffer++ > 2) flag(t, Check.SCAFFOLD_2);
    }

    private boolean atEdge(Tracked t) {
        int x = MathHelper.floor(t.position.x), z = MathHelper.floor(t.position.z);
        BlockPos below = BlockPos.ofFloored(t.position.x, t.position.y - 0.2, t.position.z);
        if (mc.world.getBlockState(below).isAir()) return true;
        double dx = t.position.x - x, dz = t.position.z - z;
        return Math.min(Math.min(dx, 1 - dx), Math.min(dz, 1 - dz)) < 0.4;
    }

    private static boolean diagonal(Deque<BlockPos> positions) {
        if (positions.size() < 4) return false;
        BlockPos previous = null;
        int first = -1, second = -1, index = 0;
        for (BlockPos position : positions) {
            if (previous == null) { previous = position; continue; }
            if (position.getY() != previous.getY()) return false;
            int dx = position.getX() - previous.getX(), dz = position.getZ() - previous.getZ();
            int direction = dx == 1 && dz == 0 ? 0 : dx == -1 && dz == 0 ? 1
                    : dz == 1 && dx == 0 ? 2 : dz == -1 && dx == 0 ? 3 : -1;
            if (direction < 0) return false;
            if (first < 0) first = direction;
            else if (second < 0) { if (first / 2 == direction / 2) return false; second = direction; }
            else if (direction != (index++ % 2 == 0 ? first : second)) return false;
            previous = position;
        }
        return true;
    }

    private void breakProgress(Tracked t, BlockPos pos, int progress) {
        if (progress < 0) { t.vl[Check.NUKER.ordinal()] *= 0.2F; t.pendingSwings.clear(); return; }
        if (mc.world.getBlockState(pos.up()).getBlock() instanceof CarpetBlock) return;
        if (!hasSupport(pos, false)) flag(t, Check.NUKER);
        t.pendingSwings.clear();
    }

    private boolean hasSupport(BlockPos pos, boolean recursive) {
        for (Direction facing : Direction.values()) {
            BlockPos adjacent = pos.offset(facing);
            var state = mc.world.getBlockState(adjacent);
            if (state.isFullCube(mc.world, adjacent)) continue;
            if (!(state.getBlock() instanceof BedBlock)) return true;
            if (!recursive && hasSupport(adjacent, true)) return true;
        }
        return false;
    }

    private void itemSpawn(EntitySpawnS2CPacket p) {
        for (Tracked t : players.values()) {
            if (Math.abs(p.getX() - t.position.x) > 0.28175 || Math.abs(p.getZ() - t.position.z) > 0.28175
                    || Math.abs(p.getY() - (t.position.y + t.player.getEyeHeight(t.player.getPose()) - 0.3)) > 0.28175)
                continue;
            if (now() - t.lastItemAt < 20) t.pendingItems++;
            t.lastItemAt = now();
            break;
        }
    }

    private void velocity(Tracked t, Vec3d delta) {
        if (t.damageAt == 0 || now() - t.damageAt > 500) return;
        if (t.speed > 1.7 || Math.abs(t.motionY) > 1) { t.damageAt = 0; return; }
        if (t.moves - t.damageMove < 2) return;
        // The 1.8 check infers attacker direction from swing packets; modern damage packets
        // expose a direct source id, so this conservative branch only tests missing response.
        if (t.damageSource && t.position.squaredDistanceTo(t.velocityStart) < 0.0025 && t.player.isOnGround())
            flag(t, Check.VELOCITY);
        t.damageAt = 0;
    }

    private static final class Tracked {
        final PlayerEntity player;
        final float[] vl = new float[Check.values().length];
        final Deque<Long> pendingSwings = new ArrayDeque<>();
        final Deque<BlockPos> placements = new ArrayDeque<>();
        Vec3d position, velocityStart;
        ItemStack held;
        float yaw, pitch, previousYaw, deltaYaw, deltaPitch, sprintBuffer, autoBlockBuffer;
        float fastUseBuffer, scaffoldBuffer, scaffold2Buffer, lastRotationDelta;
        float lastDirection, frictionSampleDirection, frictionSampleYaw;
        double speed, previousSpeed, motionX, motionY, motionZ, previousMotionY, frictionSampleSpeed;
        int moves, sprintTicks, itemUseTicks, frictionSampleTicks, pendingItems, damageMove;
        long teleportAt, damageAt, useAt, swingAt, rotationAt, lastRotationAt, swapAt;
        long placementAt, sneakAt, lastItemAt;
        boolean sneaking, sprinting, usingItem, cheater, damageSource;
        Tracked(PlayerEntity player) {
            this.player = player;
            position = velocityStart = player.getEntityPos();
            held = player.getMainHandStack();
            yaw = previousYaw = player.getYaw(); pitch = player.getPitch();
            sneaking = player.isSneaking(); sprinting = player.isSprinting(); usingItem = player.isUsingItem();
            teleportAt = rotationAt = lastRotationAt = swapAt = now();
        }
    }
}
