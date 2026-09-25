package injection;

import cn.omix.event.impl.KeyInputEvent;
import cn.omix.util.IMinecraft;
import im.webui.WebUiRuntime;
import im.webui.screen.WebUiScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard implements IMinecraft {

    @Inject(method = "onKey", at = @At(value = "HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        WebUiRuntime.getInstance().key(input.key(), input.scancode(), action, input.modifiers());
        if (mc.currentScreen instanceof WebUiScreen) {
            return;
        }
        if (action == 0 || action == 1) {
            boolean hadScreen = mc.currentScreen != null;
            KeyInputEvent event = new KeyInputEvent(input.key(), action);
            instance.getEventManager().call(event);
            if (!hadScreen && mc.currentScreen != null) {
                // The module already consumed this key to open a screen. Vanilla would otherwise
                // dispatch the same press to that new screen (and immediately close ClickGUI).
                if (mc.currentScreen instanceof cn.omix.ui.sigma.SigmaClickGuiScreen sigma) {
                    sigma.ignoreOpeningKeyUntilRelease(input.key());
                }
                ci.cancel();
            }
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"))
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        WebUiRuntime.getInstance().character(input.codepoint(), input.modifiers());
    }
}
