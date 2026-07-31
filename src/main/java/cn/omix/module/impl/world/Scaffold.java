package cn.omix.module.impl.world;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.management.RotationManager;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.move.Speed;
import cn.omix.module.impl.player.Stuck;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.Util;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.*;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Getter
public class Scaffold extends Module {
    private static final double PLACEMENT_REACH = 4.5;
    private static final double CLUTCH_REACH = 4.5;
    private static final double ESSENTIAL_CLUTCH_TARGET_RANGE = 5.0;

    public static NumberValue delay = new NumberValue("Delay", 0, 0, 200, 10);
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Telly Bridge");
    private final NumberValue tellyTick = new NumberValue("Telly Tick", 1, 0, 5, 1, () -> !mode.is("Normal"));
    private final ModeValue rotationMode = new ModeValue("Rotation Mode", "Normal", "Normal", "Facing", "Hit Vec", "Nearest", "Hypixel");
    private final NumberValue shrink = new NumberValue("Shrink", .1f, 0, .45f, .01f, () -> rotationMode.is("Nearest") || rotationMode.is("Hypixel"));
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 180, 0, 180, 5);
    private final ModeValue towerMode = new ModeValue(
            "Tower Mode",
            "None",
            "None",
            "Vanilla",
            "NCP",
            "Hypixel"
    );
    public static BoolValue downwards = new BoolValue("Downwards", false);
    private final BoolValue autoJump = new BoolValue("Auto Jump", false);
    private final BoolValue sprint = new BoolValue("Sprint", false);
    private final BoolValue rayCast = new BoolValue("Ray Cast", false);
    private final BoolValue maxStack = new BoolValue("Max Stack", false);
    private final BoolValue itemSpoof = new BoolValue("Item Spoof", false);
    private final BoolValue noSwing = new BoolValue("No Swing", false);
    private final BoolValue movementFix = new BoolValue("Movement Fix", false);
    private final BoolValue clutch = new BoolValue("Clutch", false);
    private final BoolValue onlyStuckInEssential = new BoolValue(
            "Only stuck in essential",
            false,
            clutch::getValue
    );
    private final NumberValue clutchEyeTick = new NumberValue("Clutch Eye Tick", 2, 1, 20, 1, clutch::getValue);
    private final NumberValue clutchHeightTick = new NumberValue("Clutch Height Tick", 3, 1, 20, 1, clutch::getValue);
    private final NumberValue clutchGroundDistance = new NumberValue("Clutch Ground Distance", 3, 1, 20, 1, clutch::getValue);
    private final NumberValue clutchStuckTime = new NumberValue("Clutch Stuck Time (s)", 5.0F, .5F, 30.0F, .5F, clutch::getValue);
    private final TimerUtil delayTimer = new TimerUtil();
    private boolean canRotation;
    private boolean canPlace;
    private boolean clutchActive;
    private boolean clutchTimedOut;
    private boolean hypixelTowerActive;
    private boolean hypixelTowerGrounded;
    private int oldSlot;
    private float savedDelay;
    private float savedTellyTick;
    private String savedRotationMode;
    private long clutchStartedAt;
    private double ncpTowerGround = Double.NaN;
    private double keepYCoord;
    private float[] rotations;
    private PlaceInfo data;

    public Scaffold() {
        super("Scaffold", Category.World);
    }

    @Override
    public void onEnable() {
        ScaffoldMutex.activate(getModule(ScaffoldX.class));
        if (mc.player == null || mc.world == null) return;

        oldSlot = mc.player.getInventory().getSelectedSlot();
        canPlace = false;
        data = null;
        resetClutchState();
        resetTowerState();
    }

    @Override
    public void onDisable() {
        stopClutch(true);
        clutchTimedOut = false;
        resetTowerState();

        if (mc.player == null || mc.world == null) return;

        if (itemSpoof.getValue()) {
            ItemSpoofUtil.stopSpoof();
        }

        mc.player.getInventory().setSelectedSlot(oldSlot);
        canPlace = false;
        data = null;
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        stopClutch(false);
        clutchTimedOut = false;
        resetTowerState();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (autoJump.getValue() && MovementUtil.isMoving() && mc.player.isOnGround()) {
            event.setJumping(true);
        }

        if (isDownwards()) {
            event.setSneaking(false);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(mode.getValue());
        if (BlockUtil.getBlockSlot(maxStack.getValue()) == -1) {
            toggle();
            return;
        }

        boolean skipTowerPlacement = updateTower();

        if (mc.player.isOnGround()) {
            keepYCoord = Math.floor(mc.player.getY() - 1.0);
        }

        BlockPos targetBlock = BlockPos.ofFloored(mc.player.getX(), getYLevel() - (Scaffold.isDownwards() ? 1 : 0), mc.player.getZ());
        data = getBlockData(targetBlock);
        updateClutch();

        if (itemSpoof.getValue()) {
            ItemSpoofUtil.startSpoof(oldSlot);
        }

        mc.player.getInventory().setSelectedSlot(BlockUtil.getBlockSlot(maxStack.getValue()));

        switch (mode.getValue()) {
            case "Normal" -> canRotation = canPlace = true;
            case "Telly Bridge" -> canRotation = canPlace = Util.offGroundTicks >= tellyTick.getValue().intValue() || !MovementUtil.isMoving();
        }

        if (!skipTowerPlacement && canPlace && data != null) {
            boolean rayCast = true;
            if (this.rayCast.getValue()) {
                rayCast = RayCastUtil.overBlock(data.blockPos(), data.facing(), false);
            }

            if (rayCast) {
                if (delayTimer.hasTimeElapsed(delay.getValue())) {
                    place(data.blockPos(), data.facing(), getVec(data.blockPos(), data.facing()));
                    delayTimer.reset();
                }
            }
        }
    }

    private boolean updateTower() {
        if (!mc.options.jumpKey.isPressed() || towerMode.is("None")) {
            resetTowerState();
            return false;
        }

        return switch (towerMode.getValue()) {
            case "Vanilla" -> {
                resetTowerState();
                setVelocityY(0.42);
                yield false;
            }
            case "NCP" -> {
                hypixelTowerActive = false;
                hypixelTowerGrounded = false;
                if (!hasAroundBlock()) {
                    yield false;
                }

                if (mc.player.isOnGround() || Double.isNaN(ncpTowerGround)) {
                    ncpTowerGround = mc.player.getY();
                    setVelocityY(0.42);
                }
                if (mc.player.getY() > ncpTowerGround + 0.79F) {
                    mc.player.setPosition(
                            mc.player.getX(),
                            MathHelper.floor(mc.player.getY()),
                            mc.player.getZ()
                    );
                    setVelocityY(0.42);
                    ncpTowerGround = mc.player.getY();
                }
                yield false;
            }
            case "Hypixel" -> updateHypixelTower();
            default -> false;
        };
    }

    private boolean updateHypixelTower() {
        ncpTowerGround = Double.NaN;
        if (!canUseHypixelTower()) {
            hypixelTowerActive = false;
            hypixelTowerGrounded = false;
            return false;
        }

        if (!hypixelTowerActive && mc.player.isOnGround()) {
            hypixelTowerActive = true;
        }
        if (!hypixelTowerActive || !hasAroundBlock() || !MovementUtil.isMoving()) {
            return false;
        }

        if (mc.player.isOnGround()) {
            hypixelTowerGrounded = true;
        }
        if (!hypixelTowerGrounded) {
            return false;
        }
        if (Util.offGroundTicks >= 18) {
            hypixelTowerActive = false;
            return true;
        }

        switch (Util.offGroundTicks % 3) {
            case 0 -> {
                StatusEffectInstance speed = mc.player.getStatusEffect(StatusEffects.SPEED);
                double strafeSpeed = 0.22;
                if (speed != null) {
                    int level = speed.getAmplifier() + 1;
                    strafeSpeed += level * (speed.getAmplifier() == 0 ? 0.036 : 0.042);
                }
                MovementUtil.strafe(strafeSpeed);
                setVelocityY(0.42);
            }
            case 1 -> setVelocityY(0.33);
            case 2 -> setVelocityY(MathHelper.floor(mc.player.getY()) + 1.0 - mc.player.getY());
        }
        return false;
    }

    private boolean canUseHypixelTower() {
        boolean hasSpeed = mc.player.hasStatusEffect(StatusEffects.SPEED);
        boolean hasJumpBoost = mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST);
        Speed speed = getModule(Speed.class);
        return hasSpeed || !hasJumpBoost && (speed == null || !speed.isEnabled());
    }

    private boolean hasAroundBlock() {
        BlockPos base = mc.player.getBlockPos();
        for (int y = 0; y >= -2; y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockState state = mc.world.getBlockState(base.add(x, y, z));
                    if (!state.isReplaceable() && state.getFluidState().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void setVelocityY(double velocityY) {
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, velocityY, velocity.z);
    }

    private void resetTowerState() {
        hypixelTowerActive = false;
        hypixelTowerGrounded = false;
        ncpTowerGround = Double.NaN;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent e) {
        if (mc.player == null || mc.world == null || data == null) return;

        switch (rotationMode.getValue()) {
            case "Normal" -> rotations = RotationUtil.getRotations(data.blockPos());
            case "Hit Vec" -> rotations = RotationUtil.getRotations(getVec(data.blockPos(), data.facing()));
            case "Nearest", "Hypixel" -> rotations = new float[]{RotationUtil.getNearestRotation(data.blockPos(), data.facing(), RotationManager.currentRotations, shrink.getValue())[0], RotationUtil.getRotations(data.blockPos())[1]};
            case "Facing" -> rotations = RotationUtil.getRotations(data.blockPos(), data.facing());
        }
    }

    private void place(BlockPos pos, Direction facing, Vec3d hitVec) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, facing, pos, false)) == ActionResult.SUCCESS) {
            if (noSwing.getValue()) {
                PacketUtil.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            } else {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    public double getYLevel() {
        if (mc.player == null) return 0.0;

        double posY = mc.player.getY();
        if (!autoJump.getValue()) return posY - 1.0;
        return posY - 1.0 >= keepYCoord && Math.max(posY, keepYCoord) - Math.min(posY, keepYCoord) <= 3.0 && !mc.options.jumpKey.isPressed() ? keepYCoord : posY - 1.0;
    }

    public PlaceInfo getBlockData(BlockPos belowBlockPos) {
        if (mc.player == null || mc.world == null) return null;
        if (!mc.world.getBlockState(belowBlockPos).isAir()) return null;

        final double reachSq = PLACEMENT_REACH * PLACEMENT_REACH;
        final Vec3d eye = mc.player.getEyePos();

        SupportCandidate best = null;

        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                for (int sx = (x == 0 ? 1 : -1); sx <= 1; sx += 2) {
                    for (int sz = (z == 0 ? 1 : -1); sz <= 1; sz += 2) {
                        BlockPos blockPos = belowBlockPos.add(x * sx, 0, z * sz);
                        SupportCandidate candidate = findSupport(blockPos, eye, reachSq);
                        if (candidate != null && (best == null || candidate.distanceSq() < best.distanceSq())) {
                            best = candidate;
                        }
                    }
                }
            }
        }

        if (best != null) return best.placeInfo();

        // During a fast ascent playerY - 1 can skip above the topmost support,
        // leaving an air layer between the requested placement and every block.
        // Fall back to the highest reachable placement in the player's column so
        // the scaffold can catch up one block at a time instead of giving up.
        int minimumY = Math.max(
                mc.world.getBottomY(),
                belowBlockPos.getY() - MathHelper.ceil(PLACEMENT_REACH)
        );
        for (int y = belowBlockPos.getY() - 1; y >= minimumY; y--) {
            SupportCandidate candidate = findSupport(
                    new BlockPos(belowBlockPos.getX(), y, belowBlockPos.getZ()),
                    eye,
                    reachSq
            );
            if (candidate != null) return candidate.placeInfo();
        }

        return null;
    }

    private SupportCandidate findSupport(BlockPos placePos, Vec3d eye, double reachSq) {
        if (!mc.world.getBlockState(placePos).isAir()) return null;

        SupportCandidate best = null;
        for (Direction direction : Direction.values()) {
            if (!isDownwards() && direction == Direction.UP) continue;

            BlockPos neighborPos = placePos.offset(direction);
            BlockState neighborState = mc.world.getBlockState(neighborPos);
            if (neighborState.isReplaceable() || !neighborState.getFluidState().isEmpty()) continue;

            Direction facing = direction.getOpposite();
            Vec3d hitVec = new Vec3d(
                    neighborPos.getX() + 0.5 + facing.getOffsetX() * 0.5,
                    neighborPos.getY() + 0.5 + facing.getOffsetY() * 0.5,
                    neighborPos.getZ() + 0.5 + facing.getOffsetZ() * 0.5
            );
            double distanceSq = eye.squaredDistanceTo(hitVec);
            if (distanceSq > reachSq) continue;

            if (best == null || distanceSq < best.distanceSq()) {
                best = new SupportCandidate(new PlaceInfo(neighborPos, facing), distanceSq);
            }
        }
        return best;
    }

    public Vec3d getVec(BlockPos pos, Direction facing) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        switch (facing) {
            case NORTH -> z -= 0.5;
            case SOUTH -> z += 0.5;
            case WEST  -> x -= 0.5;
            case EAST  -> x += 0.5;
            case UP    -> y += 0.5;
        }
        return new Vec3d(x, y, z);
    }

    public int getRotationSpeed() {
        if (mc.player == null || mc.world == null) return 0;

        int speed = rotationSpeed.getValue().intValue();
        if (rotationMode.is("Hypixel")) {
            if (mc.options.jumpKey.isPressed() && !MovementUtil.movementInput()) {
                speed = rotationSpeed.getValue().intValue();
            } else if (mc.player.getMovement().y <= 0) {
                speed = rotationSpeed.getValue().intValue();
            } else if (!canPlace) {
                speed = rotationSpeed.getValue().intValue();
            } else if (Util.offGroundTicks == tellyTick.getValue().intValue()) {
                speed = 120;
            } else {
                speed = 35;
            }
        }
        return speed;
    }

    private void updateClutch() {
        if (!clutch.getValue()) {
            stopClutch(true);
            clutchTimedOut = false;
            return;
        }

        boolean unsupported = isGroundBeyondClutchDistance();
        if (!unsupported) {
            stopClutch(true);
            clutchTimedOut = false;
            return;
        }

        if (clutchActive) {
            long maxStuckNanos = (long) (clutchStuckTime.getValue().doubleValue() * 1_000_000_000.0);
            if (System.nanoTime() - clutchStartedAt >= maxStuckNanos) {
                stopClutch(true);
                clutchTimedOut = true;
                return;
            }

            rotationMode.setValue("Nearest");
            Stuck stuck = getModule(Stuck.class);
            if (stuck != null) {
                if (shouldUseClutchStuck()) {
                    stuck.beginClutchFreeze();
                } else {
                    stuck.endClutchFreeze(true);
                }
            }
            return;
        }

        if (!clutchTimedOut && isPredictedClutchDanger()) {
            startClutch();
        }
    }

    private boolean isPredictedClutchDanger() {
        FallingPlayer eyePrediction = new FallingPlayer(mc.player);
        eyePrediction.calculate(clutchEyeTick.getValue().intValue());
        Vec3d predictedEyePos = eyePrediction.getEyePos();

        FallingPlayer heightPrediction = new FallingPlayer(mc.player);
        heightPrediction.calculate(clutchHeightTick.getValue().intValue());
        Vec3d predictedHeightPos = heightPrediction.getPos();

        if (data == null) return true;

        Vec3d supportCenter = new Vec3d(
                data.blockPos().getX() + 0.5,
                data.blockPos().getY() + 0.5,
                data.blockPos().getZ() + 0.5
        );
        boolean tooFarFromSupport = predictedEyePos.distanceTo(supportCenter) >= CLUTCH_REACH;
        boolean fallingBelowSupport = predictedHeightPos.y < data.blockPos().getY();
        return tooFarFromSupport || fallingBelowSupport;
    }

    private boolean isGroundBeyondClutchDistance() {
        Box box = mc.player.getBoundingBox();
        int minX = MathHelper.floor(box.minX + 1.0E-6);
        int maxX = MathHelper.floor(box.maxX - 1.0E-6);
        int minZ = MathHelper.floor(box.minZ + 1.0E-6);
        int maxZ = MathHelper.floor(box.maxZ - 1.0E-6);
        int startY = MathHelper.floor(box.minY - 1.0E-6);

        for (int y = startY;
             y >= mc.world.getBottomY() && box.minY - (y + 1.0) <= clutchGroundDistance.getValue();
             y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = mc.world.getBlockState(new BlockPos(x, y, z));
                    if (!state.isReplaceable() && state.getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void startClutch() {
        savedDelay = delay.getValue();
        savedTellyTick = tellyTick.getValue();
        savedRotationMode = rotationMode.getValue();
        delay.setValue(0);
        tellyTick.setValue(0);
        rotationMode.setValue("Nearest");
        clutchStartedAt = System.nanoTime();
        clutchActive = true;

        Stuck stuck = getModule(Stuck.class);
        if (stuck != null && shouldUseClutchStuck()) {
            stuck.beginClutchFreeze();
        }
    }

    private boolean shouldUseClutchStuck() {
        if (!onlyStuckInEssential.getValue()) {
            return true;
        }
        if (mc.player == null || data == null) {
            return false;
        }

        Vec3d hitVec = getVec(data.blockPos(), data.facing());
        return mc.player.getEyePos().squaredDistanceTo(hitVec)
                <= ESSENTIAL_CLUTCH_TARGET_RANGE * ESSENTIAL_CLUTCH_TARGET_RANGE;
    }

    private void stopClutch(boolean restoreStuckState) {
        if (!clutchActive) return;

        clutchActive = false;
        delay.setValue(savedDelay);
        tellyTick.setValue(savedTellyTick);
        rotationMode.setValue(savedRotationMode);
        savedRotationMode = null;

        Stuck stuck = getModule(Stuck.class);
        if (stuck != null) {
            stuck.endClutchFreeze(restoreStuckState);
        }
    }

    private void resetClutchState() {
        clutchActive = false;
        clutchTimedOut = false;
        clutchStartedAt = 0L;
    }

    public static boolean isDownwards() {
        return downwards.getValue() && mc.options.sneakKey.isPressed();
    }

    private record SupportCandidate(PlaceInfo placeInfo, double distanceSq) {}

    public record PlaceInfo(BlockPos blockPos, Direction facing) {}
}
