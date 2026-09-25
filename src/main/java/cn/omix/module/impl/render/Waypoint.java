package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.render.Render2D;
import cn.omix.util.sigma.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;

public final class Waypoint extends Module {
    private final BoolValue unspawnPositions = new BoolValue("Unspawn Positions", false);
    private final Map<UUID, SigmaWaypoint> lastPlayers = new HashMap<>(), unspawned = new HashMap<>();
    private final Map<UUID, Double> heights = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();

    public Waypoint() { super("Waypoint", Category.Render); }
    @Override
    public void onDisable() { clear(); }
    @EventTarget
    public void onWorld(WorldEvent event) { clear(); }
    private void clear() { lastPlayers.clear(); unspawned.clear(); heights.clear(); labels.clear(); }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.world == null || mc.player == null) return;
        SigmaWaypoints.get().updateWorld();
        if (!unspawnPositions.getValue()) { lastPlayers.clear(); unspawned.clear(); return; }
        Set<UUID> visible = new HashSet<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            UUID id = player.getUuid(); visible.add(id); unspawned.remove(id);
            if (player.isAlive()) lastPlayers.put(id, new SigmaWaypoint(id, player.getName().getString() + " Unspawn",
                    player.getBlockX(), player.getY(), player.getBlockZ(), SigmaColors.UNSPAWN, false));
            else lastPlayers.remove(id);
        }
        var iterator = lastPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!visible.contains(entry.getKey())) { unspawned.put(entry.getKey(), entry.getValue()); iterator.remove(); }
        }
        // Bound transient data on very long multiplayer sessions.
        if (unspawned.size() > 4096) unspawned.clear();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        labels.clear();
        if (mc.world == null || mc.player == null) return;
        List<SigmaWaypoint> points = new ArrayList<>(SigmaWaypoints.get().points());
        if (unspawnPositions.getValue()) points.addAll(unspawned.values());
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        points.sort(Comparator.comparingDouble((SigmaWaypoint point) -> camera.squaredDistanceTo(point.x(), point.y(), point.z())).reversed());
        Set<UUID> ids = new HashSet<>();
        for (SigmaWaypoint point : points) ids.add(point.id());
        heights.keySet().retainAll(ids);
        var frustum = new org.joml.FrustumIntersection(new org.joml.Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()));
        for (SigmaWaypoint point : points) {
            double y = heights.getOrDefault(point.id(), point.y());
            if (camera.squaredDistanceTo(point.x(), y, point.z()) > 300 * 300) continue;
            if (point.surface()) {
                var chunk = mc.world.getChunkManager().getChunk(point.x() >> 4, point.z() >> 4, ChunkStatus.FULL, false);
                if (chunk != null) {
                    // Chunk.sampleHeightmap returns the top solid block; 1.16's Heightmap.getHeight returned the first air block.
                    double target = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, point.x() & 15, point.z() & 15) + 1;
                    y += (target - y) * .1;
                    heights.put(point.id(), y);
                }
            }
            Vec3d origin = new Vec3d(point.x() + .5, y, point.z() + .5);
            float scale = SigmaGeometry.magnification(camera.squaredDistanceTo(origin), 1);
            float rx = (float) (origin.x - camera.x), ry = (float) (origin.y - camera.y), rz = (float) (origin.z - camera.z);
            if (frustum.testAab(rx - .7f, ry - .01f, rz - .7f, rx + .7f, ry + 1.4f, rz + .7f))
                SigmaWorldRender.waypoint(event, origin, point.color(), scale, mc.player.age);
            var projection = SigmaProjection.label(event, origin.add(0, 1.9 + .18 * scale * Math.sqrt(Math.sqrt(scale)), 0), .009f * scale);
            if (projection != null) labels.add(new Label(point.name(), projection));
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        var context = event.getContext();
        for (Label label : labels) {
            label.projection.begin(context);
            try {
                var font = SigmaResources.light(25);
                float width = font.getStringWidth(label.name);
                Render2D.drawRect(context, -width / 2 - 14, -5, width + 28, font.getHeight() + 12,
                        SigmaColors.alpha(SigmaColors.sourceBlend(SigmaColors.WHITE, SigmaColors.BLACK, 75), .5f));
                SigmaDraw.shadow(context, -width / 2 - 14, -5, width + 28, font.getHeight() + 12, 20, .5f);
                font.drawString(context, label.name, -width / 2, 0, SigmaColors.alpha(SigmaColors.WHITE, .8f));
            } finally { label.projection.end(context); }
        }
    }

    private record Label(String name, SigmaProjection.Label projection) {}
}
