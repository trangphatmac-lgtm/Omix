package cn.omix.util.sigma;

import cn.omix.module.impl.player.AntiBot;
import cn.omix.util.IMinecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class SigmaEntityFilter implements IMinecraft {
    private SigmaEntityFilter() {}

    public static boolean bot(Entity entity) {
        if (!(entity instanceof LivingEntity living) || instance.getModuleManager() == null) return false;
        AntiBot antiBot = instance.getModuleManager().getModule(AntiBot.class);
        return antiBot != null && antiBot.isEnabled() && antiBot.isBot(living);
    }

    public static boolean matches(Entity entity, boolean players, boolean mobs, boolean passives, boolean invisible) {
        if (entity == mc.player || !(entity instanceof LivingEntity) || (!invisible && entity.isInvisible()) || bot(entity)) return false;
        // Preserve PlayerUtil.getEntityCategory: its MONSTER group includes all MobEntity subclasses.
        return entity instanceof PlayerEntity ? players : entity instanceof MobEntity ? mobs : passives;
    }
}
