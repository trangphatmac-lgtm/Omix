package injection;

import cn.omix.event.impl.MouseScrollEvent;
import cn.omix.module.impl.player.AutoBlockIn;
import cn.omix.util.IMinecraft;
import im.webui.WebUiRuntime;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MixinMouse implements IMinecraft {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        WebUiRuntime.getInstance().mouseButton(input.button(), action);
        // Let releases clear vanilla's mouse state even when a press was suppressed.
        if (action == GLFW.GLFW_PRESS && window == mc.getWindow().getHandle()
                && (AutoBlockIn.blocksMouseInput() || AutoBlockIn.isActivationMouseButton(input.button()))) {
            ci.cancel();
        }
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        WebUiRuntime.getInstance().mouseMoved(x, y);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        WebUiRuntime.getInstance().mouseScrolled(vertical);
        MouseScrollEvent event = new MouseScrollEvent(horizontal, vertical);
        instance.getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
