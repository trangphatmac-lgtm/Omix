package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.Render3D;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class BedESP extends Module {
    private static final int SCAN_RADIUS = 64;
    private static final int SCAN_PER_TICK = 4096;
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
    private final Set<BlockPos> beds = new CopyOnWriteArraySet<>();
    private int scanIndex;

    public BedESP() {
        super("BedESP", Category.Render);
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
        scanForBeds();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        for (BlockPos head : beds) {
            BlockState headState = mc.world.getBlockState(head);
            if (!(headState.getBlock() instanceof BedBlock) || headState.get(BedBlock.PART) != BedPart.HEAD) {
                beds.remove(head);
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
            if (!mc.world.isInBuildLimit(pos)) continue;

            BlockState state = mc.world.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD) {
                beds.add(pos.toImmutable());
            }
        }
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
        beds.clear();
        scanIndex = 0;
    }
}
