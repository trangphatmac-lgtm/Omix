package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MouseScrollEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import net.minecraft.util.math.MathHelper;

public final class Zoom extends Module {
    private static final float DEFAULT_ZOOM_FOV = 20.0F;
    private static final float MIN_FOV = 3.0F;
    private static final float MAX_FOV = 160.0F;
    private static final float SCROLL_STEP = 5.0F;
    private static final float ANIMATION_SPEED = 8.0F;

    private float targetFov = DEFAULT_ZOOM_FOV;
    private Float currentFov;
    private Float baseFov;
    private long lastFrameTime = System.nanoTime();
    private boolean zoomed;
    private boolean previousSmoothCamera;

    public Zoom() {
        super("Zoom", Category.Render);
    }

    @Override
    public boolean isHoldToUse() {
        return true;
    }

    @Override
    public void onEnable() {
        if (!zoomed) {
            previousSmoothCamera = mc.options.smoothCameraEnabled;
        }
        targetFov = DEFAULT_ZOOM_FOV;
        lastFrameTime = System.nanoTime();
        zoomed = true;
        setSuffix(String.valueOf(Math.round(targetFov)));
        mc.options.smoothCameraEnabled = true;
    }

    @Override
    public void onDisable() {
        targetFov = DEFAULT_ZOOM_FOV;
        lastFrameTime = System.nanoTime();

        if (currentFov == null) {
            finishZoomOut();
        }
    }

    @EventTarget
    public void onMouseScroll(MouseScrollEvent event) {
        if (mc.player == null || currentFov == null) return;

        targetFov = MathHelper.clamp(targetFov - (float) event.getVertical() * SCROLL_STEP, MIN_FOV, MAX_FOV);
        setSuffix(String.valueOf(Math.round(targetFov)));
        event.setCancelled();
    }

    public float applyFov(float original) {
        baseFov = original;

        if (isEnabled() && currentFov == null) {
            currentFov = original;
            lastFrameTime = System.nanoTime();
        }

        if (!isEnabled() && !zoomed) {
            return original;
        }

        if (currentFov == null) {
            return original;
        }

        long now = System.nanoTime();
        float deltaTime = Math.min((now - lastFrameTime) / 1_000_000_000.0F, 0.1F);
        lastFrameTime = now;

        float destination = isEnabled() ? targetFov : baseFov;
        float progress = MathHelper.clamp(ANIMATION_SPEED * deltaTime, 0.0F, 1.0F);
        currentFov = MathHelper.lerp(progress, currentFov, destination);

        if (!isEnabled() && Math.abs(currentFov - baseFov) <= 0.1F) {
            finishZoomOut();
            return original;
        }

        return currentFov;
    }

    public boolean shouldHideHand() {
        return isEnabled();
    }

    private void finishZoomOut() {
        currentFov = null;
        baseFov = null;
        zoomed = false;
        mc.options.smoothCameraEnabled = previousSmoothCamera;
    }

    public static Zoom getInstance() {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return null;
        return client.getModuleManager().getModule(Zoom.class);
    }
}
