package cn.omix.module.impl.world;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.util.Util;
import net.minecraft.client.util.ScreenshotRecorder;

public final class AutoScreenshot extends VictoryActionModule {
    public AutoScreenshot() {
        super("AutoScreenshot");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        handlePacket(event);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        handleUpdate();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetVictoryState();
    }

    @Override
    protected void performVictoryAction() {
        ScreenshotRecorder.saveScreenshot(
                mc.runDirectory,
                mc.getFramebuffer(),
                message -> mc.execute(() -> mc.inGameHud.getChatHud().addMessage(message))
        );
        Util.log("&aAutoScreenshot captured a screenshot.");
    }
}
