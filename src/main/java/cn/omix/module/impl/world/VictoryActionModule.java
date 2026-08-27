package cn.omix.module.impl.world;

import cn.omix.event.impl.PacketEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayDeque;
import java.util.Deque;

abstract class VictoryActionModule extends Module {
    private static final long NO_PENDING_ACTION = -1L;
    private static final long FIREWORK_WINDOW_MS = 3_000L;
    private static final double FIREWORK_RADIUS_SQUARED = 32.0D * 32.0D;
    private static final int REQUIRED_FIREWORKS = 3;

    private final BoolValue title = new BoolValue("Title", true);
    private final BoolValue chat = new BoolValue("Chat", true);
    private final BoolValue fireworks = new BoolValue("Fireworks", true);
    private final NumberValue cooldown = new NumberValue("Cooldown", 10_000, 1_000, 60_000, 500);
    private final NumberValue delay = new NumberValue("Delay", 1_000, 0, 10_000, 100);

    private final Object stateLock = new Object();
    private final Deque<Long> nearbyFireworks = new ArrayDeque<>();
    private long pendingActionAt = NO_PENDING_ACTION;
    private long cooldownUntil;

    protected VictoryActionModule(String name) {
        super(name, Category.World);
    }

    @Override
    public final void onEnable() {
        resetVictoryState();
    }

    @Override
    public final void onDisable() {
        resetVictoryState();
    }

    protected final void handlePacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received || mc.player == null) return;

        Packet<?> packet = event.getPacket();
        if (title.getValue() && matchesTitlePacket(packet)) {
            queueVictoryAction(now());
            return;
        }

        if (chat.getValue()
                && packet instanceof GameMessageS2CPacket message
                && !message.overlay()
                && VictorySignalMatcher.matchesChat(message.content().getString())) {
            queueVictoryAction(now());
            return;
        }

        if (fireworks.getValue()) {
            countNearbyFireworks(packet);
        } else {
            clearFireworks();
        }
    }

    protected final void handleUpdate() {
        boolean shouldPerformAction = false;
        long currentTime = now();

        synchronized (stateLock) {
            if (pendingActionAt != NO_PENDING_ACTION && currentTime >= pendingActionAt) {
                pendingActionAt = NO_PENDING_ACTION;
                shouldPerformAction = true;
            }
        }

        if (shouldPerformAction && mc.player != null && mc.world != null) {
            performVictoryAction();
        }
    }

    protected final void resetVictoryState() {
        synchronized (stateLock) {
            pendingActionAt = NO_PENDING_ACTION;
            cooldownUntil = 0L;
            nearbyFireworks.clear();
        }
    }

    protected abstract void performVictoryAction();

    private boolean matchesTitlePacket(Packet<?> packet) {
        if (packet instanceof TitleS2CPacket titlePacket) {
            return VictorySignalMatcher.matchesTitle(titlePacket.text().getString());
        }
        if (packet instanceof SubtitleS2CPacket subtitlePacket) {
            return VictorySignalMatcher.matchesTitle(subtitlePacket.text().getString());
        }
        return false;
    }

    private void countNearbyFireworks(Packet<?> packet) {
        int count;
        double x;
        double y;
        double z;

        if (packet instanceof EntitySpawnS2CPacket spawn
                && spawn.getEntityType() == EntityType.FIREWORK_ROCKET) {
            count = 1;
            x = spawn.getX();
            y = spawn.getY();
            z = spawn.getZ();
        } else if (packet instanceof ParticleS2CPacket particles
                && particles.getParameters().getType() == ParticleTypes.FIREWORK) {
            count = Math.max(1, particles.getCount());
            x = particles.getX();
            y = particles.getY();
            z = particles.getZ();
        } else {
            return;
        }

        double deltaX = x - mc.player.getX();
        double deltaY = y - mc.player.getY();
        double deltaZ = z - mc.player.getZ();
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > FIREWORK_RADIUS_SQUARED) return;

        long currentTime = now();
        synchronized (stateLock) {
            pruneOldFireworks(currentTime);
            for (int i = 0; i < Math.min(count, REQUIRED_FIREWORKS); i++) {
                nearbyFireworks.addLast(currentTime);
            }

            if (nearbyFireworks.size() >= REQUIRED_FIREWORKS) {
                nearbyFireworks.clear();
                queueVictoryActionLocked(currentTime);
            }
        }
    }

    private void clearFireworks() {
        synchronized (stateLock) {
            nearbyFireworks.clear();
        }
    }

    private void pruneOldFireworks(long currentTime) {
        while (!nearbyFireworks.isEmpty()
                && currentTime - nearbyFireworks.peekFirst() > FIREWORK_WINDOW_MS) {
            nearbyFireworks.removeFirst();
        }
    }

    private void queueVictoryAction(long currentTime) {
        synchronized (stateLock) {
            queueVictoryActionLocked(currentTime);
        }
    }

    private void queueVictoryActionLocked(long currentTime) {
        if (pendingActionAt != NO_PENDING_ACTION || currentTime < cooldownUntil) return;

        pendingActionAt = currentTime + delay.getValue().longValue();
        cooldownUntil = pendingActionAt + cooldown.getValue().longValue();
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}
