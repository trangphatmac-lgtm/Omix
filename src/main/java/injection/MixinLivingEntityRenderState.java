package injection;

import cn.omix.util.render.LivingEntityRenderStateExtension;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class MixinLivingEntityRenderState implements LivingEntityRenderStateExtension {
    @Unique
    private LivingEntity omix$entity;

    @Override
    public LivingEntity omix$getEntity() {
        return omix$entity;
    }

    @Override
    public void omix$setEntity(LivingEntity entity) {
        omix$entity = entity;
    }
}
