package injection;

import cn.remix.util.render.LivingEntityRenderStateExtension;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class MixinLivingEntityRenderState implements LivingEntityRenderStateExtension {
    @Unique
    private LivingEntity remix$entity;

    @Override
    public LivingEntity remix$getEntity() {
        return remix$entity;
    }

    @Override
    public void remix$setEntity(LivingEntity entity) {
        remix$entity = entity;
    }
}
