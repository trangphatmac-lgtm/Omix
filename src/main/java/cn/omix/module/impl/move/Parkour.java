package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.player.MovementUtil;
import net.minecraft.util.math.BlockPos;

public final class Parkour extends Module {
    private final NumberValue jumpDelay = new NumberValue("Jump Delay", 30, 0, 300, 1);
    private final BoolValue onlyForward = new BoolValue("Only Forward", false);

    private final TimerUtil jumpTimer = new TimerUtil();

    public Parkour() {
        super("Parkour", Category.Move);
    }

    @Override
    public void onEnable() {
        jumpTimer.reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null
                || mc.world == null
                || !mc.player.isOnGround()
                || mc.player.isSneaking()
                || mc.player.getAbilities().flying
                || mc.player.isTouchingWater()
                || mc.player.isInLava()) {
            return;
        }

        boolean moving = onlyForward.getValue()
                ? mc.player.input != null && mc.player.input.getMovementInput().y > 0.0F
                : MovementUtil.isMoving();
        if (!moving) return;

        BlockPos blockBelow = BlockPos.ofFloored(
                mc.player.getX(),
                mc.player.getY() - 1.0,
                mc.player.getZ()
        );
        if (!mc.world.getBlockState(blockBelow).isAir()) return;
        if (!jumpTimer.hasTimeElapsed(jumpDelay.getValue())) return;

        mc.player.jump();
        jumpTimer.reset();
    }
}
