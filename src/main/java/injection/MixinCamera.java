package injection;

import cn.remix.Client;
import cn.remix.module.impl.render.ViewClip;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class MixinCamera {

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void clipToSpace(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) {
            return;
        }

        ViewClip viewClip = client.getModuleManager().getModule(ViewClip.class);
        if (viewClip != null && viewClip.isEnabled()) {
            cir.setReturnValue(desiredCameraDistance);
        }
    }
}
