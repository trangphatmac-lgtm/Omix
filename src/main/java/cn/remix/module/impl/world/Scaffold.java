package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.management.RotationManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.Util;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.BlockUtil;
import cn.remix.util.player.FallingPlayer;
import cn.remix.util.player.ItemSpoofUtil;
import cn.remix.util.player.MovementUtil;
import cn.remix.util.player.RayCastUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render2D;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

import java.awt.Color;

/**
 * Scaffold and clutch implementation adapted to the Remix event/rotation stack.
 * Core behaviour is based on OpenSSNGScaffoldAndClutch (MIT, Copyright 2026 Un4nown).
 */
@Getter
public final class Scaffold extends Module {
    private static final double REACH = 4.5;
    private static final double FACE_SHRINK = 0.08;

    private final ModeValue mode = new ModeValue("Mode", "Telly", "Telly", "Snap", "Normal");
    private final BoolValue alwaysUpdateRotation = new BoolValue("Always Update Rotation", false);
    private final NumberValue placeTick = new NumberValue("Place Tick", 1, 1, 5, 1, () -> mode.is("Telly"));
    private final NumberValue rotationTick = new NumberValue("Rotation Tick", 1, 1, 5, 1, () -> mode.is("Telly"));
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 180, 5, 180, 5);
    private final BoolValue movementFix = new BoolValue("Movement Fix", true);
    private final BoolValue spoofItem = new BoolValue("Spoof Item", true);
    private final BoolValue noSwing = new BoolValue("No Swing", false);
    private final BoolValue strictRayCast = new BoolValue("Strict Ray Cast", true);
    private final BoolValue smoothTelly = new BoolValue("Smooth Telly", true, () -> mode.is("Telly"));
    private final BoolValue safeMode = new BoolValue("Safe Mode", true, () -> mode.is("Telly"));
    private final BoolValue noUpTelly = new BoolValue("No Up Telly", true, () -> mode.is("Telly"));
    private final BoolValue eagle = new BoolValue("Eagle", false);
    private final NumberValue eagleTick = new NumberValue("Eagle Tick", 1, 1, 5, 1, eagle::getValue);
    private final NumberValue keepEagleTick = new NumberValue("Keep Eagle Tick", 1, 1, 5, 1, eagle::getValue);
    private final ModeValue jumpMode = new ModeValue("Jump Mode", "Normal", () -> mode.is("Telly"), "Normal", "Parkour", "None");
    private final ModeValue blockSlotMode = new ModeValue("Block Slot Mode", "Farthest", "Farthest", "Most Blocks");
    private final NumberValue safeDistance = new NumberValue("Clutch Safe Distance", 4.5F, 1.0F, 5.0F, .25F,
            () -> mode.is("Telly") && safeMode.getValue());
    private final BoolValue mark = new BoolValue("Mark", true);
    private final BoolValue blockCount = new BoolValue("Block Count", true);

    private SlotData blockSlot;
    private BlockData blockData;
    private BlockData lastBlockData;
    private BlockPos lastPlacePosition;
    private Vector4f markBounds;
    private float[] rotations;
    private float[] lastRotation;
    private int oldSlot;
    private int startHotbarCount;
    private int placeCount;
    private int eagleTicks;
    private double sameY;
    private boolean canRotation;
    private boolean canPlace;
    private boolean eaglePending;

    public Scaffold() {
        super("Scaffold", Category.World);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;

        ScaffoldOld old = getModule(ScaffoldOld.class);
        if (old != null && old.isEnabled()) {
            old.setEnabled(false);
        }

        oldSlot = mc.player.getInventory().getSelectedSlot();
        startHotbarCount = Math.max(1, getBlockCountHotbar());
        sameY = Math.floor(mc.player.getY() - 1.0);
        lastRotation = new float[]{mc.player.getYaw(), mc.player.getPitch()};
        blockSlot = null;
        blockData = null;
        lastBlockData = null;
        lastPlacePosition = null;
        markBounds = null;
        rotations = null;
        placeCount = 0;
        eagleTicks = 0;
        canRotation = false;
        canPlace = false;
        eaglePending = false;
    }

    @Override
    public void onDisable() {
        if (spoofItem.getValue()) {
            ItemSpoofUtil.stopSpoof();
        }

        if (mc.player != null) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }

        blockSlot = null;
        blockData = null;
        rotations = null;
        markBounds = null;
        canRotation = false;
        canPlace = false;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(mode.getValue());
        if (mode.is("Telly") && MovementUtil.isMoving() && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
            switch (jumpMode.getValue()) {
                case "Normal" -> event.setJumping(true);
                case "Parkour" -> {
                    if (isApproachingEdge()) event.setJumping(true);
                }
            }

            if (eagle.getValue()) {
                eaglePending = true;
                eagleTicks = 0;
            }
        }

        if (eagle.getValue() && eaglePending) {
            int start = eagleTick.getValue().intValue();
            int end = start + keepEagleTick.getValue().intValue();
            event.setSneaking(eagleTicks >= start && eagleTicks < end);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        blockSlot = findBlockSlot();
        if (blockSlot == null || !blockSlot.isValid()) {
            blockData = null;
            rotations = null;
            canRotation = false;
            canPlace = false;
            return;
        }

        if (mc.player.isOnGround()) {
            sameY = Math.floor(mc.player.getY() - 1.0);
        }
        if (mc.options.jumpKey.isPressed() && noUpTelly.getValue()) {
            sameY = mc.player.getBlockY() - 1;
        }

        BlockPos target = BlockPos.ofFloored(mc.player.getX(), sameY, mc.player.getZ());
        BlockData possible = isReplaceable(target) ? findBlockData(target) : null;
        if (possible != null) {
            blockData = possible;
        } else if (blockData == null || !isReplaceable(blockData.placePos())) {
            blockData = null;
        }
        lastBlockData = possible;

        boolean forcedClutch = applyClutchPrediction();
        canPlace = switch (mode.getValue()) {
            case "Normal" -> true;
            case "Snap" -> isReplaceable(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1.0, mc.player.getZ()));
            default -> Util.offGroundTicks >= placeTick.getValue().intValue();
        };
        if (forcedClutch) canPlace = true;

        if (eaglePending) {
            eagleTicks++;
            int end = eagleTick.getValue().intValue() + keepEagleTick.getValue().intValue();
            if (eagleTicks >= end) {
                eaglePending = false;
                eagleTicks = 0;
            }
        }

        if (blockData == null) {
            rotations = null;
            canRotation = false;
            return;
        }

        rotations = calculateRotations(forcedClutch);
        if (rotations != null && strictRayCast.getValue() && lastRotation != null && !alwaysUpdateRotation.getValue()
                && RayCastUtil.overBlock(blockData.pos(), blockData.facing(), true, lastRotation[0], lastRotation[1], REACH)) {
            rotations = lastRotation.clone();
        }

        if (rotations != null) {
            rotations[1] = MathHelper.clamp(rotations[1], -90.0F, 90.0F);
            lastRotation = rotations.clone();
            canRotation = true;
        } else {
            canRotation = false;
        }

        if (blockSlot.hand() == Hand.MAIN_HAND) {
            if (spoofItem.getValue()) ItemSpoofUtil.startSpoof(oldSlot);
            mc.player.getInventory().setSelectedSlot(blockSlot.slot());
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost()) {
            place();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!mark.getValue() || lastPlacePosition == null) {
            markBounds = null;
            return;
        }

        Box box = new Box(lastPlacePosition);
        Vec3d[] corners = {
                new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ), new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.maxZ), new Vec3d(box.maxX, box.maxY, box.maxZ)
        };

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean visible = false;
        for (Vec3d corner : corners) {
            Vec3d projected = ProjectUtil.worldSpaceToScreenSpace(corner, event.getProjectionMatrix(), event.getModelViewMatrix());
            if (projected.z > 0.0 && projected.z < 1.0) {
                minX = Math.min(minX, (float) projected.x);
                minY = Math.min(minY, (float) projected.y);
                maxX = Math.max(maxX, (float) projected.x);
                maxY = Math.max(maxY, (float) projected.y);
                visible = true;
            }
        }
        markBounds = visible ? new Vector4f(minX, minY, maxX, maxY) : null;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mark.getValue() && markBounds != null) {
            Render2D.drawOutline(event.getContext(), markBounds.x, markBounds.y,
                    markBounds.z - markBounds.x, markBounds.w - markBounds.y,
                    1.0F, new Color(255, 255, 255, 170).getRGB());
        }

        if (!blockCount.getValue() || mc.player == null) return;
        int count = getBlockCountHotbar();
        startHotbarCount = Math.max(startHotbarCount, Math.max(1, count));
        float ratio = MathHelper.clamp((float) count / startHotbarCount, 0.0F, 1.0F);
        String text = count + " Blocks";
        TrueTypeFont font = instance.getFontManager().getFont(18);
        float width = Math.max(76.0F, font.getStringWidth(text) + 28.0F);
        float x = (mc.getWindow().getScaledWidth() - width) / 2.0F;
        float y = mc.getWindow().getScaledHeight() / 2.0F + 18.0F;

        Render2D.drawRect(event.getContext(), x, y, width, 22.0F, new Color(0, 0, 0, 110).getRGB());
        Render2D.drawRect(event.getContext(), x, y + 20.0F, width * ratio, 2.0F, blockCountColor(count));
        ItemStack displayStack = blockSlot == null ? ItemStack.EMPTY : blockSlot.stack();
        Render2D.drawItem(event.getContext(), displayStack, x + 3.0F, y + 3.0F);
        font.drawStringWithShadow(event.getContext(), text, x + 23.0F, y + 5.0F, blockCountColor(count));
    }

    private void place() {
        if (!canPlace || !canRotation || blockData == null || blockSlot == null || !blockSlot.isValid()
                || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        float[] applied = RotationManager.currentRotations != null ? RotationManager.currentRotations : rotations;
        if (applied == null) return;
        if (strictRayCast.getValue()
                && !RayCastUtil.overBlock(blockData.pos(), blockData.facing(), true, applied[0], applied[1], REACH)) {
            return;
        }

        if (blockSlot.hand() == Hand.MAIN_HAND) {
            mc.player.getInventory().setSelectedSlot(blockSlot.slot());
        }

        Vec3d hitVec = faceCenter(blockData.pos(), blockData.facing());
        BlockHitResult hit = new BlockHitResult(hitVec, blockData.facing(), blockData.pos(), false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, blockSlot.hand(), hit);
        if (result == ActionResult.SUCCESS) {
            lastPlacePosition = blockData.placePos();
            placeCount++;
            if (noSwing.getValue()) {
                PacketUtil.sendPacket(new HandSwingC2SPacket(blockSlot.hand()));
            } else {
                mc.player.swingHand(blockSlot.hand());
            }
            blockData = null;
            canPlace = false;
        }
    }

    private float[] calculateRotations(boolean forceRotation) {
        if (blockData == null || mc.player == null) return null;

        float[] reference = RotationManager.currentRotations != null
                ? RotationManager.currentRotations
                : new float[]{mc.player.getYaw(), mc.player.getPitch()};
        float[] target = RotationUtil.getNearestRotation(
                blockData.pos(), blockData.facing(), reference, FACE_SHRINK
        );
        if (target == null) return null;

        if (mode.is("Telly") && smoothTelly.getValue() && !forceRotation) {
            int airTicks = Util.offGroundTicks;
            if (airTicks < rotationTick.getValue().intValue()) {
                if (mc.player.isOnGround()) {
                    return new float[]{mc.player.getYaw(), 75.5F};
                }
                float step = airTicks == 1 ? 80.0F : 50.0F;
                float diff = MathHelper.wrapDegrees(target[0] - reference[0]);
                target[0] = reference[0] + MathHelper.clamp(diff, -step, step);
            }
        }

        if (mode.is("Snap") && !canPlace) {
            return new float[]{mc.player.getYaw(), 85.0F};
        }
        return target;
    }

    private boolean applyClutchPrediction() {
        if (!safeMode.getValue() || mc.player == null || mc.world == null) return false;

        FallingPlayer prediction = new FallingPlayer(mc.player);
        prediction.calculate(2);
        Vec3d predictedPos = prediction.getPos();
        BlockPos predictedTarget = BlockPos.ofFloored(predictedPos.x, predictedPos.y - 1.0, predictedPos.z);
        BlockData predictedData = isReplaceable(predictedTarget) ? findBlockData(predictedTarget) : null;
        if (predictedData == null) return false;

        boolean fallingPastSupport = blockData != null && predictedPos.y < blockData.pos().getY();
        boolean tooFar = blockData == null || prediction.getEyePos().distanceTo(faceCenter(blockData.pos(), blockData.facing())) >= safeDistance.getValue();
        if ((mc.player.getVelocity().y < -0.08 && fallingPastSupport) || tooFar) {
            blockData = predictedData;
            return true;
        }
        return false;
    }

    private BlockData findBlockData(BlockPos target) {
        if (mc.player == null || mc.world == null || !isReplaceable(target)) return null;

        BlockData direct = directSupport(target);
        if (direct != null) return direct;

        BlockData best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3d eyes = mc.player.getEyePos();
        for (int radius = 1; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    BlockPos candidate = target.add(x, 0, z);
                    if (!isReplaceable(candidate)) continue;
                    BlockData data = directSupport(candidate);
                    if (data == null) continue;
                    double distance = eyes.squaredDistanceTo(faceCenter(data.pos(), data.facing()));
                    if (distance <= REACH * REACH && distance < bestDistance) {
                        bestDistance = distance;
                        best = data;
                    }
                }
            }
            if (best != null) break;
        }
        return best;
    }

    private BlockData directSupport(BlockPos placePos) {
        BlockData best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3d eyes = mc.player.getEyePos();
        for (Direction offset : Direction.values()) {
            if (offset == Direction.UP) continue;
            BlockPos support = placePos.offset(offset);
            if (!isSolidSupport(support)) continue;
            Direction face = offset.getOpposite();
            Vec3d hit = faceCenter(support, face);
            double distance = eyes.squaredDistanceTo(hit);
            if (distance <= REACH * REACH && distance < bestDistance) {
                bestDistance = distance;
                best = new BlockData(support, face);
            }
        }
        return best;
    }

    private SlotData findBlockSlot() {
        if (mc.player == null) return null;
        ItemStack offhand = mc.player.getOffHandStack();
        if (isValidBlock(offhand)) return new SlotData(-1, Hand.OFF_HAND);

        int selected = mc.player.getInventory().getSelectedSlot();
        if (!blockSlotMode.is("Most Blocks") && isValidBlock(mc.player.getInventory().getStack(selected))) {
            return new SlotData(selected, Hand.MAIN_HAND);
        }

        int result = -1;
        int mostBlocks = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!isValidBlock(stack)) continue;
            if (blockSlotMode.is("Farthest")) {
                result = slot;
            } else if (stack.getCount() > mostBlocks) {
                result = slot;
                mostBlocks = stack.getCount();
            }
        }
        return result == -1 ? null : new SlotData(result, Hand.MAIN_HAND);
    }

    private boolean isApproachingEdge() {
        if (mc.player == null || mc.world == null) return false;
        double yaw = Math.toRadians(mc.player.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        for (int distance = 1; distance <= 2; distance++) {
            BlockPos front = BlockPos.ofFloored(
                    mc.player.getX() + forwardX * distance,
                    mc.player.getY() - 0.1,
                    mc.player.getZ() + forwardZ * distance
            );
            if (isReplaceable(front)) return true;
        }
        return false;
    }

    private boolean isSolidSupport(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return !state.isReplaceable() && state.getFluidState().isEmpty() && !state.isAir();
    }

    private boolean isReplaceable(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isValidBlock(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && BlockUtil.isPlaceable(blockItem.getBlock());
    }

    private Vec3d faceCenter(BlockPos pos, Direction face) {
        return pos.toCenterPos().add(
                face.getOffsetX() * 0.5,
                face.getOffsetY() * 0.5,
                face.getOffsetZ() * 0.5
        );
    }

    private int getBlockCountHotbar() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isValidBlock(stack)) count += stack.getCount();
        }
        if (isValidBlock(mc.player.getOffHandStack())) {
            count += mc.player.getOffHandStack().getCount();
        }
        return count;
    }

    private int blockCountColor(int count) {
        if (count < 16) return new Color(255, 80, 80).getRGB();
        if (count < 32) return new Color(255, 220, 80).getRGB();
        return Color.WHITE.getRGB();
    }

    private record BlockData(BlockPos pos, Direction facing) {
        private BlockPos placePos() {
            return pos.offset(facing);
        }
    }

    private record SlotData(int slot, Hand hand) {
        private ItemStack stack() {
            if (mc.player == null) return ItemStack.EMPTY;
            return hand == Hand.OFF_HAND
                    ? mc.player.getOffHandStack()
                    : mc.player.getInventory().getStack(slot);
        }

        private boolean isValid() {
            ItemStack stack = stack();
            return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                    && BlockUtil.isPlaceable(blockItem.getBlock());
        }
    }
}
