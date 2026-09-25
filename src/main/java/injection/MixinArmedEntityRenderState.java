package injection;

import cn.omix.util.combat.projectile.ProjectileAuraRendering;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ArmedEntityRenderState.class)
public abstract class MixinArmedEntityRenderState {
    @Redirect(method = "updateRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;getStackInArm(Lnet/minecraft/util/Arm;)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack omix$projectileArm(LivingEntity entity, Arm arm) {
        return ProjectileAuraRendering.stack(entity, arm == entity.getMainArm() ? Hand.MAIN_HAND : Hand.OFF_HAND,
                entity.getStackInArm(arm));
    }

    @Redirect(method = "updateRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private static ItemStack omix$projectileSwing(LivingEntity entity) {
        return ProjectileAuraRendering.stack(entity, Hand.MAIN_HAND, entity.getMainHandStack());
    }
}
