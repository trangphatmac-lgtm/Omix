package injection;

import im.webui.WebUiRuntime;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class MixinWindow {
    @Inject(method = "onFramebufferSizeChanged", at = @At("RETURN"))
    private void omix$resizeWebUi(long window, int width, int height, CallbackInfo ci) {
        WebUiRuntime.getInstance().resize(width, height);
    }
}
