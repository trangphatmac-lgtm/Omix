package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.module.Category;
import cn.remix.module.Module;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

public final class AntiDebuff extends Module {

    public AntiDebuff() {
        super("AntiDebuff", Category.Render);
    }

    public static boolean suppresses(RegistryEntry<StatusEffect> effect) {
        return isActive() && (effect == StatusEffects.BLINDNESS
                || effect == StatusEffects.DARKNESS
                || effect == StatusEffects.NAUSEA);
    }

    public static boolean isActive() {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return false;

        AntiDebuff module = client.getModuleManager().getModule(AntiDebuff.class);
        return module != null && module.isEnabled();
    }
}
