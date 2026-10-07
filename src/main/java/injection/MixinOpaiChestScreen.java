package injection;

import cn.omix.util.opai.island.DynamicIslandManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinOpaiChestScreen {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void omix$renderChestIsland(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (DynamicIslandManager.extractChestItems((Screen) (Object) this, context)) ci.cancel();
    }
}
