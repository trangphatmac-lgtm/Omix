package cn.omix.util.sigma;

import cn.omix.util.IMinecraft;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Indexes loaded block entities at 4 Hz. Never asks the server to load a chunk. */
public final class SigmaChestCache implements IMinecraft {
    private final List<BlockPos> chests = new ArrayList<>();
    private ClientWorld world;
    private int nextScan;
    private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;

    public List<BlockPos> positions() { return chests; }

    public void update() {
        if (mc.world == null || mc.player == null) { clear(); return; }
        int x = mc.player.getChunkPos().x, z = mc.player.getChunkPos().z;
        if (world == mc.world && x == chunkX && z == chunkZ && mc.player.age < nextScan) return;
        world = mc.world;
        chunkX = x; chunkZ = z; nextScan = mc.player.age + 5;
        chests.clear();
        int radius = Math.min(32, mc.options.getClampedViewDistance() + 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = world.getChunkManager().getChunk(x + dx, z + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof ChestBlockEntity || entity instanceof EnderChestBlockEntity)
                        chests.add(entity.getPos().toImmutable());
                }
            }
        }
    }

    public void clear() {
        chests.clear(); world = null; nextScan = 0;
        chunkX = chunkZ = Integer.MIN_VALUE;
    }
}
