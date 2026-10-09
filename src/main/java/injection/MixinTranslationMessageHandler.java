package injection;

import cn.omix.util.translation.TranslationHooks;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MessageHandler.class)
public abstract class MixinTranslationMessageHandler {
    @org.spongepowered.asm.mixin.injection.Inject(method = "processChatMessageInternal", at = @At("HEAD"))
    private void omix$protectSender(net.minecraft.network.message.MessageType.Parameters params,
                                  net.minecraft.network.message.SignedMessage message, Text decorated,
                                  com.mojang.authlib.GameProfile sender, boolean secure, java.time.Instant receivedAt,
                                  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        TranslationHooks.protectPlayer(sender.name());
    }

    // Includes the delayed/profileless callbacks. Only network message delivery enters this class.
    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"), index = 0)
    private Text omix$receivedSystemTranslation(Text text) { return TranslationHooks.received(text); }

    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"), index = 0)
    private Text omix$receivedChatTranslation(Text text) { return TranslationHooks.received(text); }
}
