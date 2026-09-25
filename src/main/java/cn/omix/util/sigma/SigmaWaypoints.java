package cn.omix.util.sigma;

import cn.omix.Client;
import cn.omix.util.IMinecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Client-thread ownership; IO is serialized separately from rendering. */
public final class SigmaWaypoints implements IMinecraft {
    private static final SigmaWaypoints INSTANCE = new SigmaWaypoints();
    private final List<SigmaWaypoint> points = new ArrayList<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Omix Jello map storage"); thread.setDaemon(true); return thread;
    });
    private final ConcurrentMap<Path, List<SigmaWaypoint>> pending = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean writing = new java.util.concurrent.atomic.AtomicBoolean();
    private ClientWorld world;
    private Path directory;
    private String label = "";
    private boolean readable = true;
    private SigmaWaypoints() {}
    public static SigmaWaypoints get() { return INSTANCE; }

    public void updateWorld() {
        if (world == mc.world) return;
        world = mc.world; points.clear(); directory = null; readable = true;
        if (world == null) return;
        String server;
        if (mc.getServer() != null) {
            server = "local/" + mc.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            label = "local - " + mc.getServer().getSaveProperties().getLevelName();
        } else {
            server = "server/" + (mc.getCurrentServerEntry() == null ? "unknown" : mc.getCurrentServerEntry().address);
            label = server.replace("/", " - ");
        }
        directory = mc.runDirectory.toPath().resolve("Omix/sigma/maps").resolve(SigmaWaypointStore.key(server, world.getRegistryKey().getValue().toString()));
        Path file = directory.resolve("waypoints.json");
        try {
            var snapshot = pending.get(file);
            points.addAll(snapshot == null ? SigmaWaypointStore.read(file) : snapshot);
        }
        catch (java.io.IOException error) {
            readable = false; Client.logger.warn("Cannot read Jello waypoints; preserving the original file", error);
        }
    }

    public Path directory() { updateWorld(); return directory; }
    public String label() { updateWorld(); return label; }
    public List<SigmaWaypoint> points() { updateWorld(); return Collections.unmodifiableList(points); }

    public void put(SigmaWaypoint point) {
        updateWorld();
        if (directory == null || !readable) throw new IllegalStateException("Waypoint storage unavailable");
        if (points.stream().noneMatch(existing -> existing.id().equals(point.id())) && points.size() >= 4096)
            throw new IllegalStateException("Waypoint limit reached");
        for (int i = 0; i < points.size(); i++) if (points.get(i).id().equals(point.id())) { points.set(i, point); save(); return; }
        points.add(point); save();
    }

    public void remove(UUID id) { updateWorld(); if (points.removeIf(point -> point.id().equals(id))) save(); }

    public void move(UUID id, int index) {
        updateWorld();
        for (int i = 0; i < points.size(); i++) if (points.get(i).id().equals(id)) {
            int target = Math.clamp(index, 0, points.size() - 1);
            if (target != i) { var point = points.remove(i); points.add(target, point); save(); }
            return;
        }
    }

    private void save() {
        if (directory == null || !readable) return;
        Path file = directory.resolve("waypoints.json");
        List<SigmaWaypoint> snapshot = List.copyOf(points);
        pending.put(file, snapshot);
        // Dragging many rows produces one latest snapshot per world, not an unbounded queue of files/copies.
        if (writing.compareAndSet(false, true)) writer.execute(this::flush);
    }

    private void flush() {
        for (;;) {
            var entry = pending.entrySet().stream().findFirst().orElse(null);
            if (entry == null) {
                writing.set(false);
                if (pending.isEmpty() || !writing.compareAndSet(false, true)) return;
                continue;
            }
            try { SigmaWaypointStore.write(entry.getKey(), entry.getValue()); }
            catch (java.io.IOException error) { Client.logger.error("Cannot save Jello waypoints", error); }
            // Keep it visible to updateWorld while the atomic file replacement is in flight.
            pending.remove(entry.getKey(), entry.getValue());
        }
    }

    public void close() {
        writer.shutdown();
        try { if (!writer.awaitTermination(3, TimeUnit.SECONDS)) Client.logger.warn("Jello waypoint writes still pending at shutdown"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
