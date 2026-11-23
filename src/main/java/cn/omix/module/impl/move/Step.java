package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.network.PacketUtil;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

public final class Step extends Module {
    private static final double[][] NCP_OFFSETS = {
            {0.42, 0.753},
            {0.42, 0.74998, 1.0008, 1.16, 1.23, 1.2},
            {0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43}
    };
    private static final float[] NCP_TIMER_SPEEDS = {0.7F, 0.35F, 0.25F};

    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "NCP");
    private final NumberValue height = new NumberValue("Height", 2.5F, 1.0F, 2.5F, 0.5F,
            () -> mode.is("NCP"));
    private long lastStepMillis;
    private int timerTicks;

    public Step() {
        super("Step", Category.Move);
        mode.onChange((previous, current) -> {
            resetNcp();
            if (isNativeBehaviorActive()) updateStepHeight();
        });
    }

    @Override
    public void onEnable() {
        resetNcp();
    }

    @Override
    public void onDisable() {
        resetNcp();
        setStepHeight(0.6);
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetNcp();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
        if (timerTicks > 0 && --timerTicks == 0) {
            TimerSpeedUtil.clearTemporaryOverride(this);
        }

        updateStepHeight();
    }

    @EventTarget
    public void onMotionEvent(MotionEvent event) {
        if (event.isPre()) updateStepHeight();
    }

    private void updateStepHeight() {
        if (mc.player == null || mc.world == null) return;
        boolean canStep = mc.player.isOnGround() && (!mode.is("NCP") || canNcpStep());
        setStepHeight(canStep ? mode.is("NCP") ? Math.min(height.getValue(), 2.0F) : 1.0 : 0.6);
    }

    /** Called after local collision movement, before the normal position packet. */
    public void onStepped(double startY, double actualHeight) {
        if (!isNativeBehaviorActive() || !mode.is("NCP") || mc.player == null || mc.world == null
                || !canNcpStep() || actualHeight > Math.min(height.getValue(), 2.0F) + 0.0001) return;

        int index = actualHeight <= 1.0001 ? 0 : actualHeight <= 1.5001 ? 1
                : actualHeight <= 2.0001 ? 2 : -1;
        if (index < 0) return;

        for (double offset : NCP_OFFSETS[index]) {
            PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), startY + offset, mc.player.getZ(), false,
                    mc.player.horizontalCollision));
        }
        TimerSpeedUtil.setTemporaryOverride(this, NCP_TIMER_SPEEDS[index]);
        timerTicks = 3;
        lastStepMillis = System.currentTimeMillis();
    }

    private boolean canNcpStep() {
        return mc.player.isOnGround() && !mc.player.isTouchingWater() && !mc.player.isInLava()
                && mc.world.getFluidState(BlockPos.ofFloored(mc.player.getX(),
                mc.player.getBoundingBox().minY - 0.01, mc.player.getZ())).isEmpty()
                && System.currentTimeMillis() - lastStepMillis >= 100;
    }

    private void setStepHeight(double value) {
        if (mc.player == null) return;
        EntityAttributeInstance step = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (step != null && step.getBaseValue() != value) step.setBaseValue(value);
    }

    private void resetNcp() {
        timerTicks = 0;
        lastStepMillis = 0;
        TimerSpeedUtil.clearTemporaryOverride(this);
    }
}
