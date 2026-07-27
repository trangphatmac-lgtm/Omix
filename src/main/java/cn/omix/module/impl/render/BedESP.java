package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ColorValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.render.Render3D;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class BedESP extends Module {
    private static final int SCAN_CHUNK_RADIUS = 4;
    private static final List<Direction> OBSIDIAN_DIRECTIONS = List.of(
            Direction.UP,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    );

    private final ModeValue mode = new ModeValue("Mode", "Default", "Default", "Full");
    private final ModeValue colorMode = new ModeValue("Color", "Custom", "Custom", "HUD");
    private final ColorValue customColor = new ColorValue("Custom Color", new Color(255, 85, 255), () -> colorMode.is("Custom"));
    private final NumberValue opacity = new NumberValue("Opacity", 25, 0, 100, 1);
    private final BoolValue outline = new BoolValue("Outline", false);
    private final BoolValue obsidian = new BoolValue("Obsidian", true);
    private Set<BlockPos> beds = Set.of();

    public BedESP() {
        super("BedESP", Category.Render);
    }

    @Override
    public void onEnable() {
        reset();
        scanForBeds();
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
        scanForBeds();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        for (BlockPos head : beds) {
            BlockState headState = mc.world.getBlockState(head);
            if (!(headState.getBlock() instanceof BedBlock) || headState.get(BedBlock.PART) != BedPart.HEAD) {
                continue;
            }

            Direction facing = headState.get(BedBlock.FACING);
            BlockPos foot = head.offset(facing.getOpposite());
            BlockState footState = mc.world.getBlockState(foot);
            if (!(footState.getBlock() instanceof BedBlock) || footState.get(BedBlock.PART) != BedPart.FOOT) {
                continue;
            }

            if (obsidian.getValue()) {
                drawObsidian(event, head, foot);
            }

            Color baseColor = getColor();
            Color drawColor = new Color(
                    baseColor.getRed(),
                    baseColor.getGreen(),
                    baseColor.getBlue(),
                    Math.round(opacity.getValue() / 100.0F * 255.0F)
            );
            Box bedBox = new Box(
                    Math.min(head.getX(), foot.getX()),
                    head.getY(),
                    Math.min(head.getZ(), foot.getZ()),
                    Math.max(head.getX(), foot.getX()) + 1.0,
                    head.getY() + (mode.is("Full") ? 1.0 : 0.5625),
                    Math.max(head.getZ(), foot.getZ()) + 1.0
            );
            Render3D.drawBox(event, bedBox, drawColor, opacity.getValue() > 0, outline.getValue());
        }
    }

    private void scanForBeds() {
        if (mc.world == null || mc.player == null) return;

        Set<BlockPos> found = new HashSet<>();
        ChunkPos origin = mc.player.getChunkPos();
        for (int chunkX = origin.x - SCAN_CHUNK_RADIUS; chunkX <= origin.x + SCAN_CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = origin.z - SCAN_CHUNK_RADIUS; chunkZ <= origin.z + SCAN_CHUNK_RADIUS; chunkZ++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getPos();
                    BlockState state = blockEntity.getCachedState();
                    if (state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD) {
                        found.add(pos.toImmutable());
                    }
                }
            }
        }
        beds = Set.copyOf(found);
    }

    private void drawObsidian(Render3DEvent event, BlockPos head, BlockPos foot) {
        Color color = new Color(170, 0, 170, 110);

        for (Direction direction : OBSIDIAN_DIRECTIONS) {
            BlockPos headOffset = head.offset(direction);
            BlockPos footOffset = foot.offset(direction);
            boolean headObsidian = mc.world.getBlockState(headOffset).isOf(Blocks.OBSIDIAN);
            boolean footObsidian = mc.world.getBlockState(footOffset).isOf(Blocks.OBSIDIAN);

            if (headObsidian && footObsidian) {
                Render3D.drawBox(event, new Box(
                        Math.min(headOffset.getX(), footOffset.getX()),
                        headOffset.getY(),
                        Math.min(headOffset.getZ(), footOffset.getZ()),
                        Math.max(headOffset.getX(), footOffset.getX()) + 1.0,
                        headOffset.getY() + 1.0,
                        Math.max(headOffset.getZ(), footOffset.getZ()) + 1.0
                ), color, true, outline.getValue());
            } else if (headObsidian) {
                Render3D.drawBox(event, new Box(headOffset), color, true, outline.getValue());
            } else if (footObsidian) {
                Render3D.drawBox(event, new Box(footOffset), color, true, outline.getValue());
            }
        }
    }

    private Color getColor() {
        if (colorMode.is("HUD")) {
            return new Color(getModule(HUD.class).getColor(), true);
        }
        return customColor.getValue();
    }

    private void reset() {
        beds = Set.of();
    }
}
