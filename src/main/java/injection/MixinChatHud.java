package injection;

import cn.omix.util.ai.AiChatCapture;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class MixinChatHud {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int omix$sigmaInfoHudChatOffset(int height) {
        var hud = cn.omix.util.sigma.SigmaHud.active();
        return hud != null && hud.getSigmaInfoHud().getValue() && hud.getSigmaInfoChat().getValue() ? height - 40 : height;
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void captureAiToolResponse(
            Text message,
            MessageSignatureData signature,
            MessageIndicator indicator,
            CallbackInfo ci
    ) {
        AiChatCapture.record(message);
    }
}
