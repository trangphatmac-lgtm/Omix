package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ColorValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.util.render.Render3D;
import cn.omix.util.sigma.SigmaChestCache;
import cn.omix.util.sigma.SigmaColors;
import cn.omix.util.sigma.SigmaWorldRender;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChestESP extends Module {
    private static final int CLASSIC_SCAN_CHUNK_RADIUS = 4;

    private final ModeValue implementation = new ModeValue("Implementation", "Classic", "Classic", "Omix", "Sigma");
    private final ModeValue sigmaMode = new ModeValue("Sigma Mode", "Outline", () -> implementation.is("Sigma"), "Outline", "Box");
    private final BoolValue sigmaRegular = new BoolValue("Show Regular Chests", true, () -> implementation.is("Sigma"));
    private final ColorValue sigmaRegularColor = new ColorValue("Regular Color", new Color(SigmaColors.WHITE), () -> implementation.is("Sigma"));
    private final BoolValue sigmaTrapped = new BoolValue("Show Trapped Chests", true, () -> implementation.is("Sigma"));
    private final ColorValue sigmaTrappedColor = new ColorValue("Trapped Color", new Color(-13108), () -> implementation.is("Sigma"));
    private final BoolValue sigmaEnder = new BoolValue("Show Ender Chests", true, () -> implementation.is("Sigma"));
    private final ColorValue sigmaEnderColor = new ColorValue("Ender Color", new Color(-1848065), () -> implementation.is("Sigma"));
    private final SigmaChestCache sigmaChests = new SigmaChestCache();

    private final ColorValue chestColor = new ColorValue(
            "Chest",
            new Color(255, 170, 0),
            () -> implementation.is("Classic")
    );
    private final ColorValue trappedChestColor = new ColorValue(
            "Trapped Chest",
            new Color(255, 43, 0),
            () -> implementation.is("Classic")
    );
    private final ColorValue enderChestColor = new ColorValue(
            "Ender Chest",
            new Color(26, 17, 170),
            () -> implementation.is("Classic")
    );
    private final BoolValue tracers = new BoolValue("Tracers", false, () -> implementation.is("Classic"));

    private Set<BlockPos> classicChests = Set.of();
    private final List<BlockPos> omixOpenedChests = Collections.synchronizedList(new ArrayList<>());
    private final List<BlockEntity> omixChests = new ArrayList<>();

    public ChestESP() {
        super("ChestESP", Category.Render);
    }

    @Override
    public void onEnable() {
        reset();
        if (implementation.is("Sigma")) {
            sigmaChests.update();
        } else if (implementation.is("Classic")) {
            scanClassicChests();
        }
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
    public void onPacket(PacketEvent event) {
        if (!implementation.is("Omix") || mc.player == null || mc.world == null) return;

        if (event.getType() == PacketEvent.Type.Received) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof BlockEventS2CPacket blockEvent
                    && blockEvent.getType() == 1
                    && blockEvent.getData() > 0) {
                BlockPos pos = blockEvent.getPos();
                if (!omixOpenedChests.contains(pos)) {
                    omixOpenedChests.add(pos);
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(implementation.getValue());
        if (implementation.is("Sigma")) {
            sigmaChests.update();
        } else if (implementation.is("Classic")) {
            scanClassicChests();
        } else {
            scanOmixChests();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        if (implementation.is("Sigma")) {
            renderSigma(event);
        } else if (implementation.is("Omix")) {
            renderOmix(event);
        } else {
            renderClassic(event);
        }
    }

    private void renderClassic(Render3DEvent event) {
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) return;

        Vec3d lineStart = cameraEntity.getLerpedPos(event.getTickDelta())
                .add(0.0, cameraEntity.getEyeHeight(cameraEntity.getPose()), 0.0);
        if (mc.options.getPerspective().isFirstPerson()) {
            lineStart = lineStart.add(cameraEntity.getRotationVec(event.getTickDelta()).multiply(0.25));
        }

        for (BlockPos pos : classicChests) {
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            if (!isChest(block)) continue;

            Color color = getClassicChestColor(block);
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

    private void renderOmix(Render3DEvent event) {
        if (mc.player == null || omixChests.isEmpty()) return;

        for (BlockEntity chest : omixChests) {
            BlockPos pos = chest.getPos();
            Color color;

            if (chest instanceof EnderChestBlockEntity) {
                color = new Color(255, 0, 255, 60);
            } else if (omixOpenedChests.contains(pos)) {
                color = new Color(255, 0, 0, 60);
            } else {
                color = new Color(0, 255, 0, 60);
            }

            Render3D.drawBox(event.getMatrixStack(), pos, color.getRGB());
        }
    }

    private void scanClassicChests() {
        if (mc.world == null || mc.player == null) return;
        Set<BlockPos> found = new HashSet<>();
        ChunkPos origin = mc.player.getChunkPos();
        for (int chunkX = origin.x - CLASSIC_SCAN_CHUNK_RADIUS;
             chunkX <= origin.x + CLASSIC_SCAN_CHUNK_RADIUS;
             chunkX++) {
            for (int chunkZ = origin.z - CLASSIC_SCAN_CHUNK_RADIUS;
                 chunkZ <= origin.z + CLASSIC_SCAN_CHUNK_RADIUS;
                 chunkZ++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (isChest(blockEntity.getCachedState().getBlock())) {
                        found.add(blockEntity.getPos().toImmutable());
                    }
                }
            }
        }
        classicChests = Set.copyOf(found);
    }

    private void scanOmixChests() {
        omixChests.clear();
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(playerChunkX + x, playerChunkZ + z);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ChestBlockEntity
                            || blockEntity instanceof EnderChestBlockEntity) {
                        omixChests.add(blockEntity);
                    }
                }
            }
        }
    }

    private boolean isChest(Block block) {
        return block instanceof ChestBlock || block instanceof EnderChestBlock;
    }

    private Color getClassicChestColor(Block block) {
        if (block instanceof TrappedChestBlock) {
            return trappedChestColor.getValue();
        }
        if (block instanceof EnderChestBlock) {
            return enderChestColor.getValue();
        }
        return chestColor.getValue();
    }

    private void reset() {
        sigmaChests.clear();
        classicChests = Set.of();
        omixOpenedChests.clear();
        omixChests.clear();
    }

    private void renderSigma(Render3DEvent event) {
        boolean outline = sigmaMode.is("Outline");
        var boxes = new java.util.ArrayList<cn.omix.util.sigma.SigmaMaskEffect.ColoredBox>();
        for (BlockPos pos : sigmaChests.positions()) {
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            int color;
            if (block instanceof EnderChestBlock) {
                if (!sigmaEnder.getValue()) continue;
                color = sigmaEnderColor.getValue().getRGB();
            } else if (block instanceof TrappedChestBlock) {
                if (!sigmaTrapped.getValue()) continue;
                color = sigmaTrappedColor.getValue().getRGB();
            } else if (block instanceof ChestBlock) {
                if (!sigmaRegular.getValue()) continue;
                color = sigmaRegularColor.getValue().getRGB();
            } else continue;
            var shape = state.getOutlineShape(mc.world, pos);
            if (shape.isEmpty()) continue;
            Box box = shape.getBoundingBox().offset(pos);
            if (outline) boxes.add(new cn.omix.util.sigma.SigmaMaskEffect.ColoredBox(box, SigmaColors.alpha(color, .7f)));
            else SigmaWorldRender.box(event, box, SigmaColors.alpha(color, .14f), true, 2);
        }
        if (outline) cn.omix.util.sigma.SigmaMaskEffect.boxes(event, boxes, java.util.List.of());
    }
}
