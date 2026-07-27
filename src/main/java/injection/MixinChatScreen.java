package injection;

import cn.omix.event.impl.ChatScreenEvent;
import cn.omix.util.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen implements IMinecraft {

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void redactAiApiKeyFromHistory(String chatText, boolean addToHistory, CallbackInfo ci) {
        String normalized = ((ChatScreen) (Object) this).normalize(chatText);
        String[] arguments = normalized.trim().split("\\s+", 3);
        if (arguments.length < 3
                || !arguments[0].equalsIgnoreCase(".ai")
                || !arguments[1].equalsIgnoreCase("apikey")
                || mc.player == null) {
            return;
        }

        mc.player.networkHandler.sendChatMessage(normalized);
        ci.cancel();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V", shift = At.Shift.AFTER))
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        instance.getEventManager().call(new ChatScreenEvent(context, mouseX, mouseY));
    }
}
