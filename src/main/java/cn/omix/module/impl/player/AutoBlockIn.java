package cn.omix.module.impl.player;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.event.impl.RotationAppliedEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.management.RotationManager;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.move.Derp;
import cn.omix.module.impl.player.blockin.BlockInPlanner;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.KeyValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.module.value.impl.TextValue;
import cn.omix.util.player.RayCastUtil;
import cn.omix.util.misc.KeyUtil;
import cn.omix.util.render.Render2D;
import injection.accessor.BlockItemAccessor;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Hold Select Keybind to build the same nine-cell enclosure as Raven's Block In. */
public final class AutoBlockIn extends Module {
    private static final double REACH = 4.5;
    private final NumberValue speed = new NumberValue("Speed", 10, 1, 30, 1);
    private final NumberValue randomization = new NumberValue("Randomization", 10, 0, 100, 1);
    private final NumberValue rotationTolerance = new NumberValue("Rotation Tolerance", 25, 20, 100, 1);
    private final KeyValue selectKeybind = new KeyValue("Select Keybind", 0, true);
    private final BoolValue ignoreBlocks = new BoolValue("Ignore Blocks", false);
    private final TextValue ignoredItems = new TextValue("Ignored Items", "", ignoreBlocks::getValue);

    private final Set<Identifier> ignoredIds = new HashSet<>();
    private String parsedItems;
    private List<Slot> slots = List.of();
    private BlockInPlanner.Aim target;
    private float[] rotations;
    private float[] baseRotations;
    private boolean placing;
    private ClientPlayerEntity slotOwner;
    private int previousSlot = -1;
    private boolean swapped;
    private int fillCount;
    private float progressFrom;
    private float progressTarget;
    private long progressStart;

    private record Slot(int index, float strength) {}

    public AutoBlockIn() {
        super("AutoBlockIn", Category.Player);
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
        if (!canRun()) {
            reset();
            return;
        }
        updateProgress();
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!canRun()) {
            reset();
            return;
        }
        target = null;
        rotations = null;
        refreshSlots();
        if (slots.isEmpty()) {
            stopPlacing();
            return;
        }

        baseRotations = RotationManager.isRotating()
                ? RotationManager.currentRotations.clone()
                : new float[]{mc.player.getYaw(), mc.player.getPitch()};
        BlockInPlanner planner = new BlockInPlanner(new BlockInPlanner.Environment() {
            public boolean replaceable(BlockPos pos) { return isReplaceable(pos); }
            public boolean support(BlockPos pos, boolean roof) { return isSupport(pos, roof); }
            public boolean canPlace(BlockHitResult hit) { return findSlot(hit) != -1; }
            public BlockHitResult raycast(float yaw, float pitch) { return cast(yaw, pitch); }
        }, mc.player.getBlockPos(), mc.player.getEyePos(), baseRotations[0], baseRotations[1], reach(), Math::random);
        target = planner.find(closestPlayer());
        if (target == null) {
            stopPlacing();
            return;
        }
        int slot = findSlot(target.hit());
        if (slot == -1) {
            stopPlacing();
            return;
        }
        if (!placing) {
            placing = true;
            slotOwner = mc.player;
            previousSlot = mc.player.getInventory().getSelectedSlot();
        }
        equip(slot);
        rotations = BlockInPlanner.smooth(baseRotations[0], baseRotations[1], target.yaw(), target.pitch(),
                speed.getValue(), randomization.getValue(), Math.random());
    }

    @EventTarget
    public void onRotationApplied(RotationAppliedEvent event) {
        if (!canRun()) {
            reset();
            return;
        }
        if (!placing || target == null || rotations == null) return;
        suppress(mc.options.attackKey);
        suppress(mc.options.useKey);
        mc.interactionManager.cancelBlockBreaking();

        // Use the manager's actual applied aim, never a queued hit from an earlier target.
        float[] applied = RotationManager.currentRotations;
        if (!RotationManager.isRotating() || applied == null
                || Math.abs(MathHelper.wrapDegrees(applied[0] - rotations[0])) > 0.001
                || Math.abs(applied[1] - rotations[1]) > 0.001) return;
        float tolerance = rotationTolerance.getValue();
        if (Math.abs(MathHelper.wrapDegrees(applied[0] - baseRotations[0])) > tolerance
                || Math.abs(applied[1] - baseRotations[1]) > tolerance) return;
        BlockHitResult hit = cast(applied[0], applied[1]);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(target.hit().getBlockPos())
                || hit.getSide() != target.hit().getSide()) return;

        refreshSlots();
        int slot = findSlot(hit);
        if (slot == -1) {
            stopPlacing();
            return;
        }
        equip(slot);
        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        // At most one attempt per selected target; replan after movement/world changes.
        target = null;
        rotations = null;
        updateProgress();
    }

    public float[] getRotations() {
        return rotations;
    }

    public boolean isPlacing() {
        return isEnabled() && placing && canRun();
    }

    public static boolean blocksMouseInput() {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return false;
        AutoBlockIn module = client.getModuleManager().getModule(AutoBlockIn.class);
        return module != null && module.isPlacing();
    }

    private boolean canRun() {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.interactionManager == null
                || !mc.player.isAlive() || mc.player.isSpectator() || mc.currentScreen != null
                || !mc.isWindowFocused() || !selectPressed() || Freecam.isActive()) return false;
        // These modules also own the hotbar or have higher-priority rotation requests.
        return !getModule(Scaffold.class).isEnabled() && !getModule(ScaffoldX.class).isEnabled()
                && !getModule(Derp.class).isEnabled()
                && !getModule(ChestArua.class).isManualRotationActive()
                && !(getModule(AntiLava.class).isEnabled() && getModule(AntiLava.class).getRotations() != null);
    }

    private boolean selectPressed() {
        return KeyUtil.isPressed(selectKeybind.getValue(),
                key -> InputUtil.isKeyPressed(mc.getWindow(), key),
                button -> GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS);
    }

    public static boolean isActivationMouseButton(int button) {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return false;
        AutoBlockIn module = client.getModuleManager().getModule(AutoBlockIn.class);
        return module != null && KeyUtil.mouseButton(module.selectKeybind.getValue()) == button && module.canRun();
    }

    private double reach() {
        return Math.min(REACH, mc.player.getBlockInteractionRange());
    }

    private BlockHitResult cast(float yaw, float pitch) {
        return RayCastUtil.raycastBlock(yaw, pitch, reach());
    }

    private boolean isReplaceable(BlockPos pos) {
        return !mc.world.isOutOfHeightLimit(pos) && mc.world.getWorldBorder().contains(pos)
                && mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isSupport(BlockPos pos, boolean roof) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (state.isReplaceable() || state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (roof && (block instanceof FenceBlock || block instanceof WallBlock)) return false;
        // Clicking these without sneaking would open/toggle them instead of placing.
        return !(block instanceof BlockEntityProvider || block instanceof DoorBlock
                || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock
                || block instanceof NoteBlock || block instanceof BedBlock)
                && state.createScreenHandlerFactory(mc.world, pos) == null;
    }

    private void refreshSlots() {
        String value = ignoredItems.getValue();
        if (!value.equals(parsedItems)) {
            parsedItems = value;
            ignoredIds.clear();
            for (String entry : value.toLowerCase(Locale.ROOT).split("[,;\\s]+")) {
                Identifier id = Identifier.tryParse(entry);
                if (id != null) ignoredIds.add(id);
            }
        }
        List<Slot> available = new ArrayList<>();
        for (int index = 8; index >= 0; index--) {
            ItemStack stack = mc.player.getInventory().getStack(index);
            if (!usable(stack)) continue;
            BlockState state = ((BlockItem) stack.getItem()).getBlock().getDefaultState();
            float hardness = state.getHardness(mc.world, mc.player.getBlockPos());
            float strength = hardness < 0 ? Float.MAX_VALUE : hardness * (state.isToolRequired() ? 100 : 30);
            available.add(new Slot(index, strength));
        }
        available.sort(Comparator.comparingDouble(Slot::strength).reversed());
        slots = available;
    }

    private boolean usable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem
                && (!ignoreBlocks.getValue() || !ignoredIds.contains(Registries.ITEM.getId(stack.getItem())));
    }

    private int findSlot(BlockHitResult hit) {
        BlockPos goal = hit.getBlockPos().offset(hit.getSide());
        if (!isSupport(hit.getBlockPos(), false) || !isReplaceable(goal)) return -1;
        boolean strong = BlockInPlanner.isDirect(mc.player.getBlockPos(), goal);
        // Reverse strength ordering for expendable scaffolding, preserving high-slot tie breaking.
        Slot best = null;
        for (Slot slot : slots) {
            ItemStack stack = mc.player.getInventory().getStack(slot.index);
            if (!usable(stack) || !canPlace(stack, hit, goal)) continue;
            if (best == null || (strong ? slot.strength > best.strength : slot.strength < best.strength)
                    || slot.strength == best.strength && slot.index > best.index) best = slot;
        }
        return best == null ? -1 : best.index;
    }

    private boolean canPlace(ItemStack stack, BlockHitResult hit, BlockPos goal) {
        BlockItem item = (BlockItem) stack.getItem();
        if (!item.getBlock().isEnabled(mc.world.getEnabledFeatures())) return false;
        ItemPlacementContext context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, hit);
        if (!context.canPlace()) return false;
        context = item.getPlacementContext(context);
        if (context == null || !context.getBlockPos().equals(goal)) return false;
        // Ask the item's real placement implementation, including entity collision and special items.
        return ((BlockItemAccessor) item).omix$getPlacementState(context) != null;
    }

    private Vec3d closestPlayer() {
        Vec3d nearest = null;
        double best = 100; // The legacy helper takes squared distance: ten blocks.
        for (var player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()
                    || mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null) continue;
            double distance = player.squaredDistanceTo(mc.player);
            if (distance < best) {
                best = distance;
                nearest = new Vec3d(player.getX(), player.getY(), player.getZ());
            }
        }
        return nearest;
    }

    private void equip(int slot) {
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            swapped = true;
        }
    }

    private void stopPlacing() {
        if (swapped && slotOwner == mc.player && previousSlot >= 0) {
            slotOwner.getInventory().setSelectedSlot(previousSlot);
        }
        if (placing) {
            restore(mc.options.attackKey);
            restore(mc.options.useKey);
        }
        placing = false;
        swapped = false;
        slotOwner = null;
        previousSlot = -1;
        target = null;
        rotations = null;
        baseRotations = null;
    }

    private static void suppress(KeyBinding binding) {
        binding.setPressed(false);
        while (binding.wasPressed()) { /* Discard queued vanilla clicks as well as held input. */ }
    }

    private void restore(KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        boolean pressed = false;
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (key.getCategory() == InputUtil.Type.MOUSE) {
                pressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
            } else if (key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() > 0) {
                pressed = InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
            }
        }
        binding.setPressed(pressed);
    }

    private void reset() {
        stopPlacing();
        slots = List.of();
        fillCount = 0;
        progressFrom = progressTarget = 0;
        setSuffix("");
    }

    private void updateProgress() {
        int count = 0;
        for (BlockPos pos : BlockInPlanner.enclosure(mc.player.getBlockPos())) {
            if (!isReplaceable(pos)) count++;
        }
        if (count != fillCount) {
            progressFrom = progress();
            progressTarget = count / 9.0F;
            progressStart = System.currentTimeMillis();
            fillCount = count;
        }
        setSuffix(count + "/9");
    }

    private float progress() {
        float t = MathHelper.clamp((System.currentTimeMillis() - progressStart) / 50.0F, 0, 1);
        float eased = t < 0.5F ? 2 * t * t : -1 + (4 - 2 * t) * t;
        return MathHelper.lerp(eased, progressFrom, progressTarget);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!canRun() || mc.options.hudHidden || fillCount <= 0) return;
        float ratio = progress();
        int color = 0xFF000000 | Math.round((1 - ratio) * 255) << 16 | Math.round(ratio * 255) << 8;
        var matrices = event.getContext().getMatrices();
        matrices.pushMatrix();
        matrices.translate(mc.getWindow().getScaledWidth() / 2.0F - 1, mc.getWindow().getScaledHeight() / 2.0F);
        // Local tangent quads form a 3px-thick ring using the current GUI render pipeline.
        for (int segment = 0; segment < 100; segment++) {
            matrices.pushMatrix();
            matrices.rotate((float) (Math.PI / 2 + segment * Math.PI * 2 / 100));
            Render2D.drawRect(event.getContext(), 8.5F, -0.36F, 3, 0.72F,
                    segment / 100.0F < ratio ? color : 0x80000000);
            matrices.popMatrix();
        }
        matrices.popMatrix();
    }
}
