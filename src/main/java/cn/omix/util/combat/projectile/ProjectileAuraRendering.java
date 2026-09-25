package cn.omix.util.combat.projectile;

import cn.omix.module.impl.combat.ProjectileAura;
import cn.omix.util.IMinecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/** Render-only lookup: never changes the gameplay inventory or remote players. */
public final class ProjectileAuraRendering implements IMinecraft {
    private ProjectileAuraRendering() {}

    public static ItemStack stack(LivingEntity entity, Hand hand, ItemStack original) {
        if (entity != mc.player || instance == null || instance.getModuleManager() == null) return original;
        ProjectileAura module = instance.getModuleManager().getModule(ProjectileAura.class);
        return module == null ? original : module.renderStack(hand, original);
    }
}
