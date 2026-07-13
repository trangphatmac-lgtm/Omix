package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.util.render.Render3D;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

public final class ChestESP extends Module {
    private static final int SCAN_CHUNK_RADIUS = 4;
    private final ColorValue chestColor = new ColorValue("Chest", new Color(255, 170, 0));
    private final ColorValue trappedChestColor = new ColorValue("Trapped Chest", new Color(255, 43, 0));
    private final ColorValue enderChestColor = new ColorValue("Ender Chest", new Color(26, 17, 170));
    private final BoolValue tracers = new BoolValue("Tracers", false);
    private Set<BlockPos> chests = Set.of();

    public ChestESP() {
        super("ChestESP", Category.Render);
    }

    @Override
    public void onEnable() {
        reset();
        scanForChests();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        scanForChests();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) return;

        Vec3d lineStart = cameraEntity.getLerpedPos(event.getTickDelta())
                .add(0.0, cameraEntity.getEyeHeight(cameraEntity.getPose()), 0.0);
        if (mc.options.getPerspective().isFirstPerson()) {
            lineStart = lineStart.add(cameraEntity.getRotationVec(event.getTickDelta()).multiply(0.25));
        }

        for (BlockPos pos : chests) {
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            if (!isChest(block)) {
                continue;
            }

            Color color = getChestColor(block);
            Render3D.drawBox(event, new Box(
                    pos.getX() + 0.0625,
                    pos.getY(),
                    pos.getZ() + 0.0625,
                    pos.getX() + 0.9375,
                    pos.getY() + 0.875,
                    pos.getZ() + 0.9375
            ), color, false, true);

            if (tracers.getValue()) {
                Render3D.drawLine(event, lineStart, Vec3d.ofCenter(pos), color);
            }
        }
    }

    private void scanForChests() {
        if (mc.world == null || mc.player == null) return;

        Set<BlockPos> found = new HashSet<>();
        ChunkPos origin = mc.player.getChunkPos();
        for (int chunkX = origin.x - SCAN_CHUNK_RADIUS; chunkX <= origin.x + SCAN_CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = origin.z - SCAN_CHUNK_RADIUS; chunkZ <= origin.z + SCAN_CHUNK_RADIUS; chunkZ++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockState state = blockEntity.getCachedState();
                    if (isChest(state.getBlock())) {
                        found.add(blockEntity.getPos().toImmutable());
                    }
                }
            }
        }
        chests = Set.copyOf(found);
    }

    private boolean isChest(Block block) {
        return block instanceof ChestBlock || block instanceof EnderChestBlock;
    }

    private Color getChestColor(Block block) {
        if (block instanceof TrappedChestBlock) {
            return trappedChestColor.getValue();
        }
        if (block instanceof EnderChestBlock) {
            return enderChestColor.getValue();
        }
        return chestColor.getValue();
    }

    private void reset() {
        chests = Set.of();
    }
}
