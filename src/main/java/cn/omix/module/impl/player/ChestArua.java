package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.RotationAppliedEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.management.movement.MovementCorrection;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.player.chest.ChestInteractionState;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.player.RayCastUtil;
import cn.omix.util.player.RotationUtil;
import lombok.Getter;
import injection.accessor.ClientPlayerEntityAccessor;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Opens nearby chests automatically or when the player presses use.
 */
@Getter
public final class ChestArua extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Auto", "Auto", "Manual");
    private final BoolValue allowSprint = new BoolValue("Allow Sprint", true);
    private final BoolValue sprintBypass = new BoolValue("Sprint Bypass", false);
    private final NumberValue range = new NumberValue("Range", 4.5F, 1.0F, 6.0F, 0.1F);
    private final NumberValue delay = new NumberValue("Delay", 250, 0, 1000, 25, () -> mode.is("Auto"));
    private final BoolValue rotate = new BoolValue("Rotate", true, () -> mode.is("Auto"));
    private final BoolValue movementFix = new BoolValue(
            "Movement Fix",
            false,
            () -> mode.is("Auto") && rotate.getValue()
    );
    private final BoolValue throughWalls = new BoolValue("Through Walls", false);
    private final BoolValue enderChests = new BoolValue("Ender Chests", true);
    private final BoolValue swing = new BoolValue("Swing", true);

    private final TimerUtil interactionTimer = new TimerUtil();
    private final Set<BlockPos> openedChests = new HashSet<>();

    private ChestTarget target;
    private float[] rotations;
    private boolean manualPending;
    private ClientPlayerEntity sprintPlayer;
    private boolean resumeSprint;
    private boolean chestScreenOpened;
    private boolean performingInteraction;
    private final TimerUtil openTimer = new TimerUtil();
    private final ChestInteractionState interactions = new ChestInteractionState();

    public ChestArua() {
        super("ChestArua", Category.Player);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        restoreSprint();
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (sprintPlayer != null) {
            if (sprintPlayer != mc.player || !sprintPlayer.isAlive()) {
                reset();
            } else if (!sprintBypass.getValue()) {
                restoreSprint();
            }
        }
        if (interactions.awaitingScreen() && !chestScreenOpened && openTimer.hasTimeElapsed(2000)) {
            finishInteraction();
        }
        setSuffix(mode.getValue() + " " + String.format(Locale.ROOT, "%.1f", range.getValue()));

        if (!canSearch()) {
            cancelManualInteraction();
            if (!interactions.awaitingScreen()) restoreSprint();
            return;
        }

        if (mode.is("Manual")) {
            if (!manualPending) {
                clearTarget();
                restoreSprint();
                return;
            }
            if (target == null || !isStillValid(target)) {
                cancelManualInteraction();
                restoreSprint();
                return;
            }

            rotations = RotationUtil.getRotations(target.hit().getPos());
            if (rotations == null) {
                cancelManualInteraction();
                restoreSprint();
            } else {
                suspendSprint();
            }
            return;
        }

        manualPending = false;
        target = findClosestChest();
        rotations = target == null || !rotate.getValue()
                ? null
                : RotationUtil.getRotations(target.hit().getPos());
        if (target == null) restoreSprint();
        else suspendSprint();
    }

    @EventTarget
    public void onRotationApplied(RotationAppliedEvent event) {
        if (!canInteract() || target == null || !isStillValid(target)) return;

        BlockHitResult hit = target.hit();
        if (shouldRotate() && !throughWalls.getValue()) {
            // Only use aim already sent by the preceding player tick.
            ClientPlayerEntityAccessor sent = (ClientPlayerEntityAccessor) mc.player;
            BlockHitResult raycast = RayCastUtil.raycastBlock(
                    sent.getLastYaw(),
                    sent.getLastPitch(),
                    range.getValue()
            );
            if (raycast == null || raycast.getType() != HitResult.Type.BLOCK
                    || !raycast.getBlockPos().equals(target.pos())) return;
            hit = raycast;
        }

        if (!interactions.beginUse()) return;
        openTimer.reset();
        performingInteraction = true;
        ActionResult result;
        try {
            result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        } catch (RuntimeException exception) {
            finishInteraction();
            throw exception;
        } finally {
            performingInteraction = false;
        }
        if (!result.isAccepted()) {
            finishInteraction();
            if (mode.is("Manual")) cancelManualInteraction();
            return;
        }

        markOpened(target.pos());
        if (swing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        interactionTimer.reset();
        manualPending = false;
        clearTarget();
    }

    /**
     * Handles one vanilla use action. The caller should cancel vanilla use only
     * when this method returns {@code true}.
     */
    public boolean handleManualUse() {
        if (isEnabled() && (interactions.suppressOtherUse() || isSprintSuppressed())) return true;
        if (!isEnabled() || !mode.is("Manual") || !canSearch()) return false;
        if (manualPending) return true;

        ChestTarget closest = findClosestChest();
        if (closest == null) return false;

        float[] targetRotations = RotationUtil.getRotations(closest.hit().getPos());
        if (targetRotations == null) return false;

        target = closest;
        rotations = targetRotations;
        manualPending = true;
        return true;
    }

    public boolean isRotationActive() {
        return isEnabled() && canSearch() && shouldRotate() && target != null && rotations != null;
    }

    public boolean isManualRotationActive() {
        return isRotationActive() && mode.is("Manual") && manualPending;
    }

    public MovementCorrection getMovementCorrection() {
        return mode.is("Manual") || sprintBypass.getValue() || movementFix.getValue()
                ? MovementCorrection.Silent
                : MovementCorrection.None;
    }

    private boolean isSprintAllowed() {
        return allowSprint.getValue() || mc.player != null && !mc.player.isSprinting()
                && !((ClientPlayerEntityAccessor) mc.player).omix$wasSprinting();
    }

    public boolean isSprintSuppressed() {
        return sprintPlayer != null && sprintPlayer == mc.player;
    }

    private void suspendSprint() {
        if (!sprintBypass.getValue() || sprintPlayer != null) return;
        if (mode.is("Auto") && !interactionTimer.hasTimeElapsed(delay.getValue())) return;

        sprintPlayer = mc.player;
        resumeSprint = sprintPlayer.isSprinting();
        // LivingUpdate runs before movement physics. Vanilla sends the state later
        // in this player tick; interaction waits until the following input phase.
        sprintPlayer.setSprinting(false);
    }

    /** Called after screen changes, including manual and ChestStealer closes. */
    public void onScreenChanged() {
        if (!interactions.awaitingScreen()) return;
        if (mc.currentScreen instanceof GenericContainerScreen) {
            chestScreenOpened = true;
        } else if (chestScreenOpened || mc.currentScreen != null) {
            finishInteraction();
        }
    }

    public boolean shouldBlockOtherInteraction() {
        return isEnabled() && !performingInteraction && interactions.suppressOtherUse();
    }

    @EventTarget
    @EventPriority(10000)
    public void onPacketSend(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Send || event.isCancelled()) return;
        if (event.getPacket() instanceof ClientTickEndC2SPacket) {
            interactions.tickEnded();
        } else if (event.getPacket() instanceof PlayerInteractBlockC2SPacket) {
            interactions.blockUsed();
        } else if (event.getPacket() instanceof ClientCommandC2SPacket command
                && (command.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING
                || command.getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING)) {
            interactions.sprintChanged();
        }
    }

    private void finishInteraction() {
        interactions.finishUse();
        chestScreenOpened = false;
        restoreSprint();
    }

    private void restoreSprint() {
        ClientPlayerEntity player = sprintPlayer;
        boolean resume = resumeSprint;
        clearSprintBypass();
        if (resume && player != null && player == mc.player && player.isAlive() && mc.world != null) {
            player.setSprinting(true);
        }
    }

    private void clearSprintBypass() {
        sprintPlayer = null;
        resumeSprint = false;
    }

    private boolean canSearch() {
        return mc.player != null
                && mc.world != null
                && mc.interactionManager != null
                && mc.player.isAlive()
                && !mc.player.isSneaking()
                && isSprintAllowed()
                && !interactions.awaitingScreen()
                && mc.currentScreen == null;
    }

    private boolean canInteract() {
        return canSearch()
                && interactions.canUse()
                && (!sprintBypass.getValue() || isSprintSuppressed()
                && !mc.player.isSprinting()
                && !((ClientPlayerEntityAccessor) mc.player).omix$wasSprinting())
                && (mode.is("Manual") ? manualPending : interactionTimer.hasTimeElapsed(delay.getValue()));
    }

    private boolean shouldRotate() {
        return mode.is("Manual") || rotate.getValue();
    }

    private ChestTarget findClosestChest() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eye = mc.player.getEyePos();
        double maxDistanceSq = MathHelper.square(range.getValue().doubleValue());
        int radius = MathHelper.ceil(range.getValue());
        BlockPos origin = BlockPos.ofFloored(eye);
        ChestTarget closest = null;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (openedChests.contains(pos)) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    if (!isSupportedChest(state.getBlock())) continue;
                    if (isBlockedChest(state, pos)) continue;

                    double distanceSq = squaredDistanceToBlock(eye, pos);
                    if (distanceSq > maxDistanceSq
                            || closest != null && distanceSq >= closest.distanceSq()) {
                        continue;
                    }

                    BlockHitResult hit = throughWalls.getValue()
                            ? createDirectHit(pos, eye)
                            : findVisibleHit(pos, eye, maxDistanceSq);
                    if (hit != null) {
                        closest = new ChestTarget(pos.toImmutable(), hit, distanceSq);
                    }
                }
            }
        }
        return closest;
    }

    private BlockHitResult findVisibleHit(BlockPos pos, Vec3d eye, double maxDistanceSq) {
        BlockHitResult closest = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Vec3d center = Vec3d.ofCenter(pos);

        for (Direction side : Direction.values()) {
            Vec3d hitPos = center.add(
                    side.getOffsetX() * 0.5,
                    side.getOffsetY() * 0.5,
                    side.getOffsetZ() * 0.5
            );
            double distanceSq = eye.squaredDistanceTo(hitPos);
            if (distanceSq > maxDistanceSq || distanceSq >= closestDistanceSq) continue;

            BlockHitResult raycast = mc.world.raycast(new RaycastContext(
                    eye,
                    hitPos,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));
            if (raycast.getBlockPos().equals(pos)) {
                closest = raycast;
                closestDistanceSq = distanceSq;
            }
        }
        return closest;
    }

    private BlockHitResult createDirectHit(BlockPos pos, Vec3d eye) {
        Vec3d center = Vec3d.ofCenter(pos);
        Direction side = Direction.getFacing(eye.subtract(center));
        Vec3d hitPos = center.add(
                side.getOffsetX() * 0.5,
                side.getOffsetY() * 0.5,
                side.getOffsetZ() * 0.5
        );
        return new BlockHitResult(hitPos, side, pos, false);
    }

    private boolean isStillValid(ChestTarget candidate) {
        if (openedChests.contains(candidate.pos())) return false;
        BlockState state = mc.world.getBlockState(candidate.pos());
        if (!isSupportedChest(state.getBlock())) return false;
        return squaredDistanceToBlock(mc.player.getEyePos(), candidate.pos())
                <= MathHelper.square(range.getValue().doubleValue());
    }

    private boolean isSupportedChest(Block block) {
        return block instanceof ChestBlock
                || enderChests.getValue() && block instanceof EnderChestBlock;
    }

    private boolean isBlockedChest(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ChestBlock)) return false;
        if (ChestBlock.isChestBlocked(mc.world, pos)) return true;
        if (state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) return false;
        return ChestBlock.isChestBlocked(mc.world, pos.offset(ChestBlock.getFacing(state)));
    }

    private double squaredDistanceToBlock(Vec3d point, BlockPos pos) {
        double nearestX = MathHelper.clamp(point.x, pos.getX(), pos.getX() + 1.0);
        double nearestY = MathHelper.clamp(point.y, pos.getY(), pos.getY() + 1.0);
        double nearestZ = MathHelper.clamp(point.z, pos.getZ(), pos.getZ() + 1.0);
        return point.squaredDistanceTo(nearestX, nearestY, nearestZ);
    }

    private void markOpened(BlockPos pos) {
        BlockPos immutablePos = pos.toImmutable();
        openedChests.add(immutablePos);

        BlockState state = mc.world.getBlockState(immutablePos);
        if (state.getBlock() instanceof ChestBlock
                && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            openedChests.add(immutablePos.offset(ChestBlock.getFacing(state)));
        }
    }

    private void clearTarget() {
        target = null;
        rotations = null;
    }

    private void cancelManualInteraction() {
        manualPending = false;
        clearTarget();
    }

    private void reset() {
        interactions.reset();
        chestScreenOpened = false;
        performingInteraction = false;
        clearSprintBypass();
        cancelManualInteraction();
        openedChests.clear();
        interactionTimer.reset();
    }

    private record ChestTarget(BlockPos pos, BlockHitResult hit, double distanceSq) {}
}
