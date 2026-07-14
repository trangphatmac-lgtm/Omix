package injection;

import cn.remix.module.impl.render.AntiDebuff;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StatusEffectFogModifier.class)
public abstract class MixinStatusEffectFogModifier {

    @Shadow
    public abstract RegistryEntry<StatusEffect> getStatusEffect();

    @Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
    private void shouldApply(CameraSubmersionType submersionType, Entity entity,
                             CallbackInfoReturnable<Boolean> cir) {
        if (AntiDebuff.suppresses(getStatusEffect())) {
            cir.setReturnValue(false);
        }
    }
}
