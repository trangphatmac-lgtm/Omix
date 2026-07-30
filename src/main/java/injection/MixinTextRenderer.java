package injection;

import cn.omix.module.impl.render.NickHider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
public class MixinTextRenderer {

    @ModifyVariable(
            method = {
                    "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
                    "getWidth(Ljava/lang/String;)I",
                    "trimToWidth(Ljava/lang/String;IZ)Ljava/lang/String;",
                    "trimToWidth(Ljava/lang/String;I)Ljava/lang/String;"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private String omix$hideAccountName(String text) {
        return NickHider.replace(text);
    }

    @ModifyVariable(
            method = {
                    "getWidth(Lnet/minecraft/text/StringVisitable;)I",
                    "trimToWidth(Lnet/minecraft/text/StringVisitable;I)Lnet/minecraft/text/StringVisitable;",
                    "getWrappedLinesHeight(Lnet/minecraft/text/StringVisitable;I)I",
                    "wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;",
                    "wrapLinesWithoutLanguage(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private StringVisitable omix$hideAccountName(StringVisitable text) {
        return NickHider.replace(text);
    }

    @ModifyVariable(
            method = {
                    "prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
                    "getWidth(Lnet/minecraft/text/OrderedText;)I",
                    "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private OrderedText omix$hideAccountName(OrderedText text) {
        return NickHider.replace(text);
    }
}
