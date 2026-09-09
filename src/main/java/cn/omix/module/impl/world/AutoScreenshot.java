package cn.omix.module.impl.world;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.RenderFrameEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.Util;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;

public final class AutoScreenshot extends VictoryActionModule {
    private final BoolValue auto2ndPerspective = new BoolValue("Auto 2nd Perspective", false);
    private Perspective originalPerspective;

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
        restorePerspective();
        resetVictoryState();
    }

    @Override
    protected void disable() {
        restorePerspective();
        super.disable();
    }

    @Override
    protected void performVictoryAction() {
        if (originalPerspective != null) return;

        if (auto2ndPerspective.getValue()) {
            originalPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        } else {
            captureScreenshot();
        }
    }

    @EventTarget
    public void onRenderFrame(RenderFrameEvent event) {
        if (originalPerspective == null) return;

        try {
            // The world and HUD have now been rendered with the new perspective.
            captureScreenshot();
        } finally {
            // Pixel readback is queued before restoring the camera for the next frame.
            restorePerspective();
        }
    }

    private void restorePerspective() {
        if (originalPerspective != null) {
            mc.options.setPerspective(originalPerspective);
            originalPerspective = null;
        }
    }

    private void captureScreenshot() {
        ScreenshotRecorder.saveScreenshot(
                mc.runDirectory,
                mc.getFramebuffer(),
                message -> mc.execute(() -> mc.inGameHud.getChatHud().addMessage(message))
        );
        Util.log("&aAutoScreenshot captured a screenshot.");
    }
}
