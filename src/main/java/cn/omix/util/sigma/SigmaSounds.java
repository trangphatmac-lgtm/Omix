package cn.omix.util.sigma;

import cn.omix.util.IMinecraft;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class SigmaSounds implements IMinecraft {
    private SigmaSounds() {}
    public static void toggled(boolean enabled) {
        var hud = SigmaHud.active();
        if (hud == null || !hud.getSigmaActiveMods().getValue() || !hud.getSigmaActiveModsSound().getValue()) return;
        play(enabled ? "activate" : "deactivate");
    }
    public static void play(String name) {
        mc.execute(() -> mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("omix", "sigma." + name)), 1, 1)));
    }
}
