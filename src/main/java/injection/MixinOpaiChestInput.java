package injection;

import cn.omix.util.opai.island.DynamicIslandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent invisible vanilla slots from accepting input behind the island. */
@Mixin(HandledScreen.class)
public abstract class MixinOpaiChestInput {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void omix$click(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> ci) {
        if (DynamicIslandManager.replacesContainer((Screen) (Object) this)) ci.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void omix$drag(Click click, double dx, double dy, CallbackInfoReturnable<Boolean> ci) {
        if (DynamicIslandManager.replacesContainer((Screen) (Object) this)) ci.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void omix$release(Click click, CallbackInfoReturnable<Boolean> ci) {
        if (DynamicIslandManager.replacesContainer((Screen) (Object) this)) ci.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void omix$key(KeyInput key, CallbackInfoReturnable<Boolean> ci) {
        Screen screen = (Screen) (Object) this;
        if (!DynamicIslandManager.replacesContainer(screen)) return;
        if (key.key() == 256 || MinecraftClient.getInstance().options.inventoryKey.matchesKey(key)) screen.close();
        ci.setReturnValue(true);
    }
}
