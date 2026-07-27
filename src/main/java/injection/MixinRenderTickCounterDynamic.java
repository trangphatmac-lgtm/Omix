package injection;

import cn.omix.util.misc.TimerSpeedUtil;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderTickCounter.Dynamic.class)
public abstract class MixinRenderTickCounterDynamic {
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
