package injection;

import cn.omix.util.misc.TimerSpeedUtil;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public abstract class MixinRenderTickCounterDynamic {
    @Shadow private float dynamicDeltaTicks;
    @Shadow private float tickProgress;
    @Shadow private long lastTimeMillis;
    @Shadow @Final private float tickTime;

    @Inject(method = "beginRenderTick(JZ)I", at = @At("HEAD"), cancellable = true)
    private void omix$balanceTick(long timeMillis, boolean tick, CallbackInfoReturnable<Integer> cir) {
        float multiplier = TimerSpeedUtil.getBalanceTickMultiplier();
        if (multiplier == 1.0F) return;
        // Preserve the recovered division-then-multiplication order and float remainder.
        dynamicDeltaTicks = (float) (timeMillis - lastTimeMillis) / tickTime * multiplier;
        lastTimeMillis = timeMillis;
        tickProgress += dynamicDeltaTicks;
        int wholeTicks = (int) tickProgress;
        tickProgress -= wholeTicks;
        cir.setReturnValue(wholeTicks);
    }

    @Redirect(
            method = "beginRenderTick(J)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F",
                    remap = false
            )
    )
    private float applyTimerSpeed(FloatUnaryOperator operator, float value) {
        return operator.apply(value) / TimerSpeedUtil.getTimerSpeed();
    }
}
