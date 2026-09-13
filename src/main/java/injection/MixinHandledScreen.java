package injection;

import cn.omix.module.impl.player.chest.ChestScreenGuard;
import cn.omix.util.IMinecraft;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen implements IMinecraft {
    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void omix$deferChestScreenClose(CallbackInfo ci) {
        // Cancelling only player.closeHandledScreen is insufficient: HandledScreen
        // subsequently calls Screen.close(), which would still remove the GUI.
        if ((Object) this == mc.currentScreen && ChestScreenGuard.deferClose()) ci.cancel();
    }
}
