package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Brightness extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Effects", "Effects", "Gamma");

    public Brightness() {
        super("Brightness", Category.Render);
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;

        if (mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }

        if (mode.is("Gamma")) {
            mc.options.getGamma().setValue(0.5);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        setSuffix(mode.getValue());
        switch (mode.getValue()) {
            case "Effects" -> {
                mc.options.getGamma().setValue(0.5);
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000000000, 0, false, false, false));
            }

            case "Gamma" -> {
                if (mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                    mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                }

                if (mc.options.getGamma().getValue() < 1.0) {
                    mc.options.getGamma().setValue(1.0);
                }
            }
        }
    }
}