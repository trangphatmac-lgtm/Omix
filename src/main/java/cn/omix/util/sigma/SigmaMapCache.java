package cn.omix.util.sigma;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Shared Maps/MiniMap terrain cache. Main-thread sampling, worker-only file IO, bounded atlases. */
public final class SigmaMapCache implements IMinecraft {
    private static final SigmaMapCache INSTANCE = new SigmaMapCache();
    private final LinkedHashMap<Long, Region> regions = new LinkedHashMap<>(64, .75f, true);
    private final ExecutorService io = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(512), runnable -> { Thread thread = new Thread(runnable, "Omix Jello terrain IO"); thread.setDaemon(true); return thread; });
    private Path directory;
    private final List<ChunkPos> scan = new ArrayList<>();
    private ChunkPos origin;
    private int scanIndex, ticks, epoch, textureSequence, viewDistance;
    private boolean recording;

    private SigmaMapCache() {}
    public static SigmaMapCache get() { return INSTANCE; }

    @EventTarget public void onWorld(WorldEvent event) { reset(); }

    @EventTarget public void onTick(TickEvent event) {
        if (mc.world == null || mc.player == null) return;
        var hud = SigmaHud.active();
        if (!recording && !(mc.currentScreen instanceof cn.omix.ui.sigma.SigmaMapsScreen)
                && (hud == null || !hud.getSigmaMiniMap().getValue())) return;
        Path next = SigmaWaypoints.get().directory();
        if (!Objects.equals(next, directory)) { reset(); directory = next; }
        if (directory == null) return;
        recording = true;
        ChunkPos center = mc.player.getChunkPos();
        int radius = Math.min(32, mc.options.getClampedViewDistance());
        if (!center.equals(origin) || radius != viewDistance) {
            origin = center; viewDistance = radius; scan.clear(); scanIndex = 0;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) scan.add(new ChunkPos(center.x + x, center.z + z));
            scan.sort(Comparator.comparingLong(pos -> (long) (pos.x - center.x) * (pos.x - center.x) + (long) (pos.z - center.z) * (pos.z - center.z)));
        }
        long deadline = System.nanoTime() + 1_500_000;
        for (int sampled = 0; sampled < 4 && !scan.isEmpty(); sampled++) {
            ChunkPos pos = scan.get(scanIndex++ % scan.size());
            WorldChunk chunk = mc.world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk != null && !chunk.isEmpty()) sample(chunk);
            if (System.nanoTime() >= deadline) break;
        }
        if (++ticks % 100 == 0) for (Region region : List.copyOf(regions.values())) save(region);
        evict();
    }

    private void sample(WorldChunk chunk) {
        int[] pixels = new int[256];
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int ox = chunk.getPos().x * 16, oz = chunk.getPos().z * 16;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
            pos.set(ox + x, y, oz + z);
            var state = chunk.getBlockState(pos);
            if (state.isAir()) { pos.move(0, -1, 0); state = chunk.getBlockState(pos); }
            int color = state.getMapColor(mc.world, pos).color | 0xff000000;
            if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) color = net.minecraft.block.MapColor.WATER_BLUE.color | 0xff000000;
            if (state.getFluidState().isIn(FluidTags.LAVA)) color = net.minecraft.block.MapColor.BRIGHT_RED.color | 0xff000000;
            if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK)) color = 0xffffffff;
            if (mc.world.isChunkLoaded((pos.getX()) >> 4, (pos.getZ() - 1) >> 4)
                    && mc.world.isChunkLoaded(pos.getX() >> 4, (pos.getZ() + 1) >> 4)) {
                var north = mc.world.getBlockState(pos.north());
                var south = mc.world.getBlockState(pos.south());
                if (north.isAir() || north.isOf(Blocks.SNOW)) color = SigmaColors.mix(color, 0xff000000, .4f);
                else if (south.isAir() || south.isOf(Blocks.SNOW)) color = SigmaColors.mix(color, 0xffffffff, .4f);
            }
            pixels[z * 16 + x] = color == 0xff000000 ? SigmaMapRegion.UNKNOWN : color;
        }
        Region region = region(Math.floorDiv(chunk.getPos().x, 16), Math.floorDiv(chunk.getPos().z, 16));
        if (region != null) region.data.putChunk(Math.floorMod(chunk.getPos().x, 16), Math.floorMod(chunk.getPos().z, 16), pixels);
    }

    private Region region(int x, int z) {
        long key = ChunkPos.toLong(x, z);
        Region existing = regions.get(key);
        if (existing != null) return existing;
        if (regions.size() >= 128) return null; // Backpressure while old atlases are still referenced by a cached HUD frame.
        Region region = new Region(x, z, directory.resolve(x + "_" + z + ".jmap"));
        regions.put(key, region);
        int generation = epoch;
        try { io.execute(() -> {
            SigmaMapRegion loaded;
            try { loaded = SigmaMapRegion.read(region.file); }
            catch (java.io.IOException error) {
                Client.logger.warn("Cannot read Jello map region {}", region.file, error);
                mc.execute(() -> { region.loading = false; region.readable = false; }); return;
            }
            mc.execute(() -> {
                if (generation != epoch || regions.get(key) != region) return;
                region.data.mergeMissing(loaded); region.loading = false;
            });
        }); } catch (RejectedExecutionException error) { regions.remove(key); return null; }
        return region;
    }

    /** Coordinates are in Jello window pixels; the caller owns the current clip and transform. */
    public void draw(DrawContext context, float x, float y, float width, float height, double centerX, double centerZ, float pixelsPerBlock) {
        draw(context, x, y, width, height, centerX, centerZ, pixelsPerBlock, true);
    }

    public void draw(DrawContext context, float x, float y, float width, float height, double centerX, double centerZ, float pixelsPerBlock, boolean clip) {
        draw(context, x, y, width, height, centerX, centerZ, pixelsPerBlock, clip, null);
    }

    public void draw(DrawContext context, float x, float y, float width, float height, double centerX, double centerZ, float pixelsPerBlock, boolean clip, SigmaTexturePolygon.Clip rounded) {
        if (directory == null || pixelsPerBlock <= 0) return;
        if (rounded == null) Render2D.drawRect(context, x, y, width, height, SigmaMapRegion.UNKNOWN);
        else SigmaTexturePolygon.draw(context, net.minecraft.client.texture.TextureSetup.empty(), x, y, width, height, rounded, SigmaMapRegion.UNKNOWN);
        if (clip) Render2D.beginScissor(context, x, y, width, height);
        try {
            double minX = centerX - width / pixelsPerBlock / 2, minZ = centerZ - height / pixelsPerBlock / 2;
            int rx0 = Math.floorDiv((int) Math.floor(minX), 256), rz0 = Math.floorDiv((int) Math.floor(minZ), 256);
            int rx1 = Math.floorDiv((int) Math.ceil(minX + width / pixelsPerBlock), 256), rz1 = Math.floorDiv((int) Math.ceil(minZ + height / pixelsPerBlock), 256);
            int uploads = 2;
            for (int rz = rz0; rz <= rz1; rz++) for (int rx = rx0; rx <= rx1; rx++) {
                Region region = region(rx, rz);
                if (region == null) continue;
                region.lastDrawn = ticks;
                if (uploads > 0 && (region.texture == null || region.uploaded != region.data.revision()) && System.nanoTime() - region.lastUpload > 100_000_000L) {
                    region.upload(); uploads--;
                }
                if (region.texture == null) continue;
                float tx = x + (float) (rx * 256.0 - minX) * pixelsPerBlock, ty = y + (float) (rz * 256.0 - minZ) * pixelsPerBlock;
                if (rounded == null) Render2D.drawTexture(context, region.id, tx, ty, 256 * pixelsPerBlock, 256 * pixelsPerBlock);
                else SigmaTexturePolygon.draw(context, net.minecraft.client.texture.TextureSetup.of(region.texture.getGlTextureView(), region.texture.getSampler()),
                        tx, ty, 256 * pixelsPerBlock, 256 * pixelsPerBlock, rounded, -1);
            }
        } finally { if (clip) Render2D.endScissor(context); }
    }

    /** Reads only cached pixels; never loads a chunk or starts disk IO for a UI color sample. */
    public int colorAt(int x, int z) {
        Region region = regions.get(ChunkPos.toLong(Math.floorDiv(x, 256), Math.floorDiv(z, 256)));
        return region == null ? SigmaMapRegion.UNKNOWN : region.data.color(Math.floorMod(x, 256), Math.floorMod(z, 256));
    }

    private void save(Region region) {
        if (region.loading || !region.readable || region.saving || region.data.revision() == region.saved) return;
        SigmaMapRegion snapshot = region.data.snapshot();
        region.saving = true;
        try {
            io.execute(() -> {
                boolean success = false;
                try { snapshot.write(region.file); success = true; }
                catch (java.io.IOException error) { Client.logger.warn("Cannot save Jello map region {}", region.file, error); }
                boolean saved = success;
                mc.execute(() -> { region.saving = false; if (saved) region.saved = snapshot.revision(); });
            });
        } catch (RejectedExecutionException error) { region.saving = false; }
    }

    private void evict() {
        if (regions.size() <= 64) return;
        var iterator = regions.values().iterator();
        while (regions.size() > 64 && iterator.hasNext()) {
            Region region = iterator.next(); save(region);
            if (region.loading || region.saving || ticks - region.lastDrawn <= 20
                    || region.readable && region.data.revision() != region.saved) continue;
            region.close(); iterator.remove();
        }
    }

    private void reset() {
        for (Region region : regions.values()) {
            // A world switch may race the initial disk load or an older queued save. Merge on the
            // same IO worker before writing the latest snapshot; callbacks never touch a new world.
            if (region.readable && region.data.revision() != region.saved) {
                SigmaMapRegion snapshot = region.data.snapshot();
                try {
                    io.execute(() -> {
                        try { snapshot.mergeMissing(SigmaMapRegion.read(region.file)); snapshot.write(region.file); }
                        catch (java.io.IOException error) { Client.logger.warn("Cannot flush Jello map region {}", region.file, error); }
                    });
                } catch (RejectedExecutionException error) { Client.logger.error("Jello terrain IO queue full while changing worlds", error); }
            }
            region.close();
        }
        regions.clear(); directory = null; scan.clear(); origin = null; recording = false; epoch++;
    }

    public void close() {
        reset(); io.shutdown();
        try { io.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private final class Region {
        final Path file;
        final Identifier id;
        final SigmaMapRegion data = new SigmaMapRegion();
        NativeImageBackedTexture texture;
        NativeImage image;
        long uploaded = -1, saved, lastUpload;
        boolean loading = true, saving, readable = true;
        int lastDrawn = Integer.MIN_VALUE / 2;
        Region(int x, int z, Path file) {
            this.file = file; id = Identifier.of("omix", "sigma/map/" + epoch + "/" + textureSequence++);
        }
        void upload() {
            if (texture == null) {
                image = new NativeImage(256, 256, false);
                texture = new NativeImageBackedTexture(id::toString, image);
                mc.getTextureManager().registerTexture(id, texture);
            }
            for (int z = 0; z < 256; z++) for (int x = 0; x < 256; x++) image.setColorArgb(x, z, data.color(x, z));
            texture.upload(); uploaded = data.revision(); lastUpload = System.nanoTime();
        }
        void close() { if (texture != null) { mc.getTextureManager().destroyTexture(id); texture = null; image = null; } }
    }
}
