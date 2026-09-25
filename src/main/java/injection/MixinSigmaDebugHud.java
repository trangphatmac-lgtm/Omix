package injection;

import cn.omix.util.sigma.SigmaHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugHud.class)
public abstract class MixinSigmaDebugHud {
    @Inject(method = "drawText", at = @At("HEAD"))
    private void omix$sigmaDebugHeight(DrawContext context, List<String> text, boolean left, CallbackInfo ci) {
        if (!left) SigmaHud.debugRightRows = text.size();
    }
}
