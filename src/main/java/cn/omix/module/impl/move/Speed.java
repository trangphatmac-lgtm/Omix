package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.StrafeEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.management.RotationManager;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.Util;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.player.MovementUtil;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class Speed extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Ground", "Ground", "Vulcan", "Prediction", "Prediction2", "Normal");
    private final BoolValue damageBoost = new BoolValue("Damage Boost", false,  () -> mode.is("Vulcan"));
    private final NumberValue vanillaSpeed = new NumberValue("Speed", 1, 1, 5, 0.1f, () -> mode.is("Ground"));
    private final NumberValue timerBoostMultiplier = new NumberValue("Timer Boost Multiplier", 0.75F, 0.1F, 1.0F, 0.05F, this::isPredictionMode);
    private final NumberValue lowTimerTicks = new NumberValue("Low Timer Ticks", 6, 1, 10, 1, this::isPredictionMode);
    private final BoolValue rotation = new BoolValue("Rotation", false, this::isPredictionMode);
    private final NumberValue multiplier = new NumberValue("Multiplier", 1.0F, 0.0F, 10.0F, 0.1F, () -> mode.is("Normal"));
    private final NumberValue friction = new NumberValue("Friction", 1.0F, 0.0F, 10.0F, 0.1F, () -> mode.is("Normal"));
    private final NumberValue strafe = new NumberValue("Strafe", 0, 0, 100, 1, () -> mode.is("Normal"));
    private final BoolValue lagBackCheck = new BoolValue("LagBack Check", true);

    private int ticks;
    private float yaw;
    private boolean finished;
    private boolean rotated;
    private float rotationYaw;
    private YawOffsetMode yawOffsetMode = YawOffsetMode.AIR;
    private final PredictionTimerBalance prediction2Timer = new PredictionTimerBalance();
    private String lastPredictionMode;

    public Speed() {
        super("Speed", Category.Move);
    }

    private enum YawOffsetMode {
        GROUND,
        AIR,
        CONSTANT
    }

    @Override
    public void onEnable() {
        ticks = 0;
        finished = false;
        rotated = false;
        prediction2Timer.reset();
        lastPredictionMode = null;
    }

    @Override
    public void onDisable() {
        resetPredictionTimer();
        rotated = false;
        lastPredictionMode = null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        String predictionMode = isPredictionMode() ? mode.getValue() : null;
        if (mc.player == null || predictionMode == null) {
            if (lastPredictionMode != null) {
                resetPredictionTimer();
                lastPredictionMode = null;
            }
            rotated = false;
            return;
        }

        if (!predictionMode.equals(lastPredictionMode)) {
            resetPredictionTimer();
            lastPredictionMode = predictionMode;
        }

        if (mode.is("Prediction2")) {
            handlePrediction2Timer();
        } else {
            handlePredictionTimer();
        }
        handlePredictionRotation();
    }

    private void handlePredictionTimer() {
        if (canBoost()) {
            if (!mc.player.isOnGround()) {
                if (ticks < lowTimerTicks.getValue().intValue()
                        && !finished
                        && mc.player.getVelocity().y < 0.0) {
                    ticks++;
                    TimerSpeedUtil.setTimerSpeed(timerBoostMultiplier.getValue());
                    if (ticks == lowTimerTicks.getValue().intValue()) {
                        finished = true;
                    }
                }

                if (finished && ticks > 0) {
                    ticks--;
                    TimerSpeedUtil.setTimerSpeed(2.0F);
                    if (ticks == 0) {
                        TimerSpeedUtil.reset();
                        finished = false;
                    }
                }
            } else {
                resetPredictionTimer();
            }
        } else {
            resetPredictionTimer();
        }
    }

    private void handlePrediction2Timer() {
        if (!canBoost() || mc.player.isOnGround()) {
            resetPredictionTimer();
            return;
        }

        float timerSpeed;
        if (mc.player.getVelocity().y > 0.0) {
            timerSpeed = prediction2Timer.boost(
                    timerBoostMultiplier.getValue(),
                    lowTimerTicks.getValue().intValue()
            );
        } else {
            timerSpeed = prediction2Timer.slow(
                    timerBoostMultiplier.getValue(),
                    lowTimerTicks.getValue().intValue()
            );
        }

        if (PredictionTimerBalance.isNormalSpeed(timerSpeed)) {
            TimerSpeedUtil.reset();
        } else {
            TimerSpeedUtil.setTimerSpeed(timerSpeed);
        }
    }

    private void handlePredictionRotation() {
        rotated = false;
        if (!rotation.getValue() || !canBoost() || isKillAuraEnabled() || isDiggingTargetBlock()) {
            return;
        }

        switch (yawOffsetMode) {
            case GROUND -> yaw = mc.player.isOnGround() ? getYawOffsetFromKeys() : 0.0F;
            case AIR -> yaw = !mc.player.isOnGround()
                    && mc.options.forwardKey.isPressed()
                    && !mc.options.leftKey.isPressed()
                    && !mc.options.rightKey.isPressed() ? -45.0F : 0.0F;
            case CONSTANT -> yaw = getYawOffsetFromKeys();
        }

        rotationYaw = mc.player.getYaw() - yaw;
        rotated = true;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (isPredictionMode()
                && rotation.getValue()
                && rotated
                && canBoost()
                && !isKillAuraEnabled()
                && MovementUtil.isForwardPressed()) {
            MovementUtil.fixMovement(event, RotationManager.getAppliedYaw(rotationYaw));
        }
    }

    @EventTarget
    @EventPriority(100)
    public void onStrafe(StrafeEvent event) {
        if (!mode.is("Normal") || !canBoost()) return;

        if (mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
            MovementUtil.setSpeed(MovementUtil.getJumpMotion() * multiplier.getValue(), MovementUtil.getMoveYaw());
            return;
        }

        if (friction.getValue() != 1.0F) {
            event.setFriction(event.getFriction() * friction.getValue());
        }

        if (strafe.getValue() > 0.0F) {
            double speed = MovementUtil.getSpeed();
            double strafeRatio = strafe.getValue() / 100.0F;
            MovementUtil.setSpeed(speed * (1.0F - strafeRatio), MovementUtil.getDirectionYaw());
            MovementUtil.addSpeed(speed * strafeRatio, MovementUtil.getMoveYaw());
            MovementUtil.setSpeed(speed, MovementUtil.getDirectionYaw());
        }
    }

    @EventTarget
    @EventPriority(100)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!mode.is("Normal") || !canBoost()) return;

        PlayerInput input = mc.player.input.playerInput;
        mc.player.input.playerInput = new PlayerInput(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                false,
                input.sneak(),
                input.sprint()
        );
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        setSuffix(mode.getValue());
        if (e.isPre()) {
            switch (mode.getValue()) {
                case "Ground" -> {
                    if (MovementUtil.isMoving() && mc.player.isOnGround()) {
                        MovementUtil.strafe(vanillaSpeed.getValue() / 4);
                    }
                }

                case "Vulcan" -> {
                    if (MovementUtil.isMoving() && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                        mc.player.jump();
                    }

                    if (damageBoost.getValue() && mc.player.hurtTime == 1) {
                        MovementUtil.strafe(MovementUtil.getSpeed() * 2);
                    }

                    MovementUtil.strafe(MovementUtil.getSpeed());
                }
            }
        }
    }


    @EventTarget
    public void onPacket(PacketEvent event) {
        if (lagBackCheck.getValue() && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            Util.log("Lag detected!");
            toggle();
        }
    }

    public boolean isPredictionRotationActive() {
        return isEnabled() && isPredictionMode() && rotated;
    }

    public float getPredictionRotationYaw() {
        return rotationYaw;
    }

    private float getYawOffsetFromKeys() {
        if (mc.options.forwardKey.isPressed() && mc.options.leftKey.isPressed()) return 45.0F;
        if (mc.options.forwardKey.isPressed() && mc.options.rightKey.isPressed()) return -45.0F;
        if (mc.options.backKey.isPressed() && mc.options.leftKey.isPressed()) return 135.0F;
        if (mc.options.backKey.isPressed() && mc.options.rightKey.isPressed()) return -135.0F;
        if (mc.options.backKey.isPressed()) return 180.0F;
        if (mc.options.leftKey.isPressed()) return 90.0F;
        if (mc.options.rightKey.isPressed()) return -90.0F;
        return 0.0F;
    }

    private boolean canBoost() {
        if (mc.player == null || mc.world == null) return false;

        ScaffoldX scaffoldX = getModule(ScaffoldX.class);
        Scaffold scaffold = getModule(Scaffold.class);
        return !scaffoldX.isEnabled()
                && !scaffold.isEnabled()
                && MovementUtil.isForwardPressed()
                && mc.player.getHungerManager().getFoodLevel() > 6
                && !mc.player.isSneaking()
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && !isInCobweb();
    }

    private boolean isPredictionMode() {
        return mode.is("Prediction") || mode.is("Prediction2");
    }

    private boolean isKillAuraEnabled() {
        return getModule(Aura.class).isEnabled();
    }

    private boolean isDiggingTargetBlock() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        return mc.options.attackKey.isPressed()
                || (mc.interactionManager != null && mc.interactionManager.isBreakingBlock());
    }

    private boolean isInCobweb() {
        Box box = mc.player.getBoundingBox();
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX + 1.0);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY + 1.0);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ + 1.0);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void resetPredictionTimer() {
        finished = false;
        TimerSpeedUtil.reset();
        ticks = 0;
        prediction2Timer.reset();
    }
}
