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
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class ChestESP extends Module {
    private static final int SCAN_RADIUS = 64;
    private static final int SCAN_PER_TICK = 4096;

    private final ColorValue chestColor = new ColorValue("Chest", new Color(255, 170, 0));
    private final ColorValue trappedChestColor = new ColorValue("Trapped Chest", new Color(255, 43, 0));
    private final ColorValue enderChestColor = new ColorValue("Ender Chest", new Color(26, 17, 170));
    private final BoolValue tracers = new BoolValue("Tracers", false);
    private final Set<BlockPos> chests = new CopyOnWriteArraySet<>();
    private int scanIndex;

    public ChestESP() {
        super("ChestESP", Category.Render);
    }

    @Override
    public void onEnable() {
        reset();
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
                chests.remove(pos);
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
        BlockPos origin = mc.player.getBlockPos();
        int width = SCAN_RADIUS * 2 + 1;
        int total = width * width * width;

        for (int scanned = 0; scanned < SCAN_PER_TICK; scanned++) {
            int index = scanIndex++;
            if (scanIndex >= total) scanIndex = 0;

            int dx = index % width - SCAN_RADIUS;
            int dy = index / width % width - SCAN_RADIUS;
            int dz = index / (width * width) - SCAN_RADIUS;
            BlockPos pos = origin.add(dx, dy, dz);
            if (mc.world.isInBuildLimit(pos) && isChest(mc.world.getBlockState(pos).getBlock())) {
                chests.add(pos.toImmutable());
            }
        }
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
        chests.clear();
        scanIndex = 0;
    }
}
