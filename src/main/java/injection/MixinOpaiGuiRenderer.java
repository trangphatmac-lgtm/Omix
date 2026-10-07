package injection;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderer.class)
public abstract class MixinOpaiGuiRenderer {
 @Inject(method="render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",at=@At("HEAD"))
 private void omix$prepareBackdrop(CallbackInfo ci){cn.omix.util.opai.render.HudBackdrop.prepare();}
 @Inject(method="render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",at=@At("TAIL"))
 private void omix$renderNano(CallbackInfo ci){cn.omix.util.opai.OpaiRuntime.renderNano();}
 @Inject(method="close",at=@At("HEAD"))
 private void omix$closeNano(CallbackInfo ci){cn.omix.util.opai.render.NVGRenderer.close();}
}
