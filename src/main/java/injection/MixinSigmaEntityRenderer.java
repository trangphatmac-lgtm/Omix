package injection;

import cn.omix.Client;
import cn.omix.module.impl.render.ESP;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinSigmaEntityRenderer {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void omix$sigmaOutline(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return;
        ESP esp = Client.instance.getModuleManager().getModule(ESP.class);
        if (esp != null && esp.sigmaOutline(entity)) state.outlineColor = esp.sigmaOutlineColor(entity);
        var tags = Client.instance.getModuleManager().getModule(cn.omix.module.impl.render.NameTags.class);
        if (tags != null && tags.hidesVanillaLabel(entity)) state.displayName = null;
    }
}
