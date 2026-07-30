package cn.omix.module.impl.render;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.render.Render3D;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Xray extends Module {
    private static final float LINE_START_NDC_Z = 0.6F;

    private static final Set<Block> DIAMOND_BLOCKS = Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
    private static final Set<Block> GOLD_BLOCKS = Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
    private static final Set<Block> IRON_BLOCKS = Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
    private static final Set<Block> COAL_BLOCKS = Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
    private static final Set<Block> REDSTONE_BLOCKS = Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
    private static final Set<Block> LAPIS_BLOCKS = Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
    private static final Set<Block> EMERALD_BLOCKS = Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
    private static final Set<Block> XRAY_BLOCKS = Set.of(
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COAL_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.SPAWNER,
            Blocks.SUGAR_CANE,
            Blocks.NETHER_WART
    );
    private static final List<BlockPos> CAVE_OFFSETS_SMALL = List.of(
            new BlockPos(0, -1, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, -1),
            new BlockPos(0, 0, 1),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0)
    );
    private static final List<BlockPos> CAVE_OFFSETS_LARGE = List.of(
            new BlockPos(0, -2, 0),
            new BlockPos(1, -1, 0),
            new BlockPos(0, -1, -1),
            new BlockPos(0, -1, 0),
            new BlockPos(0, -1, 1),
            new BlockPos(-1, -1, 0),
            new BlockPos(2, 0, 0),
            new BlockPos(0, 0, 2),
            new BlockPos(0, 0, -2),
            new BlockPos(-2, 0, 0),
            new BlockPos(1, 0, -1),
            new BlockPos(1, 0, 0),
            new BlockPos(1, 0, 1),
            new BlockPos(0, 0, -1),
            new BlockPos(0, 0, 1),
            new BlockPos(-1, 0, -1),
            new BlockPos(-1, 0, 0),
            new BlockPos(-1, 0, 1),
            new BlockPos(1, 1, 0),
            new BlockPos(0, 1, -1),
            new BlockPos(0, 1, 0),
            new BlockPos(0, 1, 1),
            new BlockPos(-1, 1, 0),
            new BlockPos(0, 2, 0)
    );

    private final CopyOnWriteArraySet<BlockPos> trackedBlocks = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<BlockPos> pendingBlocks = new CopyOnWriteArraySet<>();

    private final ModeValue mode = new ModeValue("Mode", "Soft", "Soft", "Full");
    private final NumberValue opacity = new NumberValue("Opacity", 50, 0, 100, 1);
    private final NumberValue range = new NumberValue("Range", 64, 16, 512, 1);
    private final BoolValue cavesOnly = new BoolValue("Caves Only", true);
    private final NumberValue caveRadius = new NumberValue("Caves Radius", 2, 1, 2, 1);
    private final BoolValue diamonds = new BoolValue("Diamonds", true);
    private final BoolValue diamondTracers = new BoolValue("Diamonds Tracers", true);
    private final BoolValue gold = new BoolValue("Gold", true);
    private final BoolValue goldTracers = new BoolValue("Gold Tracers", true);
    private final BoolValue iron = new BoolValue("Iron", false);
    private final BoolValue ironTracers = new BoolValue("Iron Tracers", false);
    private final BoolValue coal = new BoolValue("Coal", false);
    private final BoolValue coalTracers = new BoolValue("Coal Tracers", false);
    private final BoolValue redstone = new BoolValue("Redstone", false);
    private final BoolValue redstoneTracers = new BoolValue("Redstone Tracers", false);
    private final BoolValue lapis = new BoolValue("Lapis", false);
    private final BoolValue lapisTracers = new BoolValue("Lapis Tracers", false);
    private final BoolValue emeralds = new BoolValue("Emeralds", false);
    private final BoolValue emeraldsTracers = new BoolValue("Emeralds Tracers", false);
    private final BoolValue spawners = new BoolValue("Spawners", false);
    private final BoolValue spawnerTracers = new BoolValue("Spawners Tracers", false);
    private final BoolValue canes = new BoolValue("Canes", false);
    private final BoolValue canesTracers = new BoolValue("Canes Tracers", false);
    private final BoolValue warts = new BoolValue("Warts", false);
    private final BoolValue wartsTracers = new BoolValue("Warts Tracers", false);

    private int lastSettingsHash;

    public Xray() {
        super("Xray", Category.Render);
    }

    public static Xray getActive() {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return null;

        Xray xray = client.getModuleManager().getModule(Xray.class);
        return xray != null && xray.isEnabled() ? xray : null;
    }

    @Override
    public void onEnable() {
        reset();
        lastSettingsHash = getSettingsHash();
        reloadChunks();
    }

    @Override
    public void onDisable() {
        reset();
        reloadChunks();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        setSuffix(mode.getValue());

        int settingsHash = getSettingsHash();
        if (settingsHash != lastSettingsHash) {
            lastSettingsHash = settingsHash;
            reset();
            reloadChunks();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received) return;

        if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::queuePendingBlock);
        } else if (event.getPacket() instanceof BlockUpdateS2CPacket packet) {
            queuePendingBlock(packet.getPos(), packet.getState());
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d lineStart = getLineStart(event, camera.getCameraPos());
        for (BlockPos pos : trackedBlocks) {
            if (pendingBlocks.contains(pos)) {
                trackedBlocks.remove(pos);
                continue;
            }

            Block block = mc.world.getBlockState(pos).getBlock();
            if (isXrayBlock(block)) {
                renderBlock(event, pos, block, lineStart);
            } else {
                trackedBlocks.remove(pos);
            }
        }

        for (BlockPos pos : pendingBlocks) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (isXrayBlock(block)) {
                renderBlock(event, pos, block, lineStart);
            } else {
                pendingBlocks.remove(pos);
            }
        }
    }

    public boolean shouldRenderSide(Block block) {
        return XRAY_BLOCKS.contains(block);
    }

    public boolean isFullMode() {
        return mode.is("Full");
    }

    public boolean shouldMakeTranslucent(Block block) {
        return !shouldRenderSide(block) || mode.is("Soft") && !isXrayBlock(block);
    }

    public boolean isXrayBlock(Block block) {
        if (DIAMOND_BLOCKS.contains(block)) return diamonds.getValue();
        if (GOLD_BLOCKS.contains(block)) return gold.getValue();
        if (IRON_BLOCKS.contains(block)) return iron.getValue();
        if (COAL_BLOCKS.contains(block)) return coal.getValue();
        if (REDSTONE_BLOCKS.contains(block)) return redstone.getValue();
        if (LAPIS_BLOCKS.contains(block)) return lapis.getValue();
        if (EMERALD_BLOCKS.contains(block)) return emeralds.getValue();
        if (block == Blocks.SPAWNER) return spawners.getValue();
        if (block == Blocks.SUGAR_CANE) return canes.getValue();
        if (block == Blocks.NETHER_WART) return warts.getValue();
        return false;
    }

    public boolean checkBlock(BlockRenderView world, BlockPos pos) {
        if (!cavesOnly.getValue()) return true;

        List<BlockPos> offsets = caveRadius.getValue() >= 2.0F ? CAVE_OFFSETS_LARGE : CAVE_OFFSETS_SMALL;
        for (BlockPos offset : offsets) {
            BlockPos nearbyPos = pos.add(offset);
            BlockState nearbyState = world.getBlockState(nearbyPos);
            if (isValidCaveBlock(world, nearbyPos, nearbyState)) {
                return true;
            }
        }
        return false;
    }

    public void trackBlock(BlockRenderView world, BlockPos pos, BlockState state) {
        if (!isXrayBlock(state.getBlock())) return;

        BlockPos immutablePos = pos.toImmutable();
        if (checkBlock(world, immutablePos)) {
            trackedBlocks.add(immutablePos);
        } else {
            trackedBlocks.remove(immutablePos);
        }
    }

    public float getTerrainOpacity() {
        return opacity.getValue() / 100.0F;
    }

    private boolean isValidCaveBlock(BlockRenderView world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SpawnerBlock
                || !state.isFullCube(world, pos)
                || !state.isOpaque()
                || state.emitsRedstonePower();
    }

    private void queuePendingBlock(BlockPos pos, BlockState state) {
        if (isXrayBlock(state.getBlock())) {
            pendingBlocks.add(pos.toImmutable());
        }
    }

    private void renderBlock(Render3DEvent event, BlockPos pos, Block block, Vec3d lineStart) {
        double maxDistance = range.getValue();
        if (mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > maxDistance * maxDistance) return;

        Color color = getBlockColor(block);
        Render3D.drawBox(event, new Box(pos), color, false, true, 1.5F);
        if (shouldDrawTracer(block)) {
            Render3D.drawLine(event, lineStart, Vec3d.ofCenter(pos), color, 1.5F);
        }
    }

    private Vec3d getLineStart(Render3DEvent event, Vec3d cameraPos) {
        Matrix4f inverseViewProjection = new Matrix4f(event.getProjectionMatrix())
                .mul(event.getModelViewMatrix())
                .invert();
        Vector4f relative = new Vector4f(0.0F, 0.0F, LINE_START_NDC_Z, 1.0F)
                .mul(inverseViewProjection);
        if (Math.abs(relative.w) > 1.0E-6F) {
            relative.div(relative.w);
        }
        return cameraPos.add(relative.x, relative.y, relative.z);
    }

    private Color getBlockColor(Block block) {
        if (GOLD_BLOCKS.contains(block)) return new Color(0xFFFF55);
        if (IRON_BLOCKS.contains(block)) return new Color(0xFFFFFF);
        if (COAL_BLOCKS.contains(block)) return new Color(0x000000);
        if (LAPIS_BLOCKS.contains(block)) return new Color(0x5555FF);
        if (block == Blocks.SPAWNER) return new Color(0xFF55FF);
        if (DIAMOND_BLOCKS.contains(block)) return new Color(0x55FFFF);
        if (REDSTONE_BLOCKS.contains(block)) return new Color(0xFF5555);
        if (block == Blocks.SUGAR_CANE) return new Color(0xAAFFAA);
        if (block == Blocks.NETHER_WART) return new Color(0xAA0000);
        if (EMERALD_BLOCKS.contains(block)) return new Color(0x55FF55);
        return Color.WHITE;
    }

    private boolean shouldDrawTracer(Block block) {
        if (GOLD_BLOCKS.contains(block)) return goldTracers.getValue();
        if (IRON_BLOCKS.contains(block)) return ironTracers.getValue();
        if (COAL_BLOCKS.contains(block)) return coalTracers.getValue();
        if (LAPIS_BLOCKS.contains(block)) return lapisTracers.getValue();
        if (block == Blocks.SPAWNER) return spawnerTracers.getValue();
        if (DIAMOND_BLOCKS.contains(block)) return diamondTracers.getValue();
        if (REDSTONE_BLOCKS.contains(block)) return redstoneTracers.getValue();
        if (block == Blocks.SUGAR_CANE) return canesTracers.getValue();
        if (block == Blocks.NETHER_WART) return wartsTracers.getValue();
        if (EMERALD_BLOCKS.contains(block)) return emeraldsTracers.getValue();
        return false;
    }

    private int getSettingsHash() {
        return Objects.hash(
                mode.getValue(),
                opacity.getValue(),
                range.getValue(),
                cavesOnly.getValue(),
                caveRadius.getValue(),
                diamonds.getValue(),
                diamondTracers.getValue(),
                gold.getValue(),
                goldTracers.getValue(),
                iron.getValue(),
                ironTracers.getValue(),
                coal.getValue(),
                coalTracers.getValue(),
                redstone.getValue(),
                redstoneTracers.getValue(),
                lapis.getValue(),
                lapisTracers.getValue(),
                emeralds.getValue(),
                emeraldsTracers.getValue(),
                spawners.getValue(),
                spawnerTracers.getValue(),
                canes.getValue(),
                canesTracers.getValue(),
                warts.getValue(),
                wartsTracers.getValue()
        );
    }

    private void reloadChunks() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    private void reset() {
        trackedBlocks.clear();
        pendingBlocks.clear();
    }
}
