package cn.omix.module.impl.render;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.util.sound.WavSounds;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public final class ToggleSounds extends Module {
    private final ModeValue mode = new ModeValue("Mode", "XinXin", "XinXin", "Myau");

    public ToggleSounds() {
        super("ToggleSounds", Category.Render);
    }

    public void playToggle(boolean enabled) {
        if (mode.is("Myau")) {
            // Minecraft 1.21.11's equivalent of the legacy random.click event.
            mc.execute(() -> mc.getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F)));
        } else {
            WavSounds.play(WavSounds.Channel.TOGGLE,
                    "/assets/omix/sounds/xinxin/" + (enabled ? "enable" : "disable") + ".wav");
        }
    }
}
