package cn.remix.module.impl.player;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Freecam extends Module {
    private final NumberValue horizontalSpeed = new NumberValue("Horizontal Speed", 2, 0.1, 4, 0.1);
    private final NumberValue verticalSpeed = new NumberValue("Vertical Speed", 1, 0.1, 4, 0.1);

    private static boolean canFly;
    private static Vec3d cameraPosition = Vec3d.ZERO;
    private static Vec3d previousCameraPosition = Vec3d.ZERO;
    private static float cameraYaw;
    private static float cameraPitch;

    private ClientPlayerEntity ownerPlayer;
    private ClientWorld ownerWorld;

    public Freecam() {
        super("Freecam", Category.Player);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            setEnabled(false);
            return;
        }

        ownerPlayer = mc.player;
        ownerWorld = mc.world;
        cameraPosition = mc.player.getEyePos();
        previousCameraPosition = cameraPosition;
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
        canFly = true;
        reloadChunks();
    }

    @Override
    public void onDisable() {
        if (canFly) {
            reloadChunks();
        }
        canFly = false;
        ownerPlayer = null;
        ownerWorld = null;
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        setEnabled(false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.player != ownerPlayer || mc.world != ownerWorld) {
            setEnabled(false);
            return;
        }

        if (!canFly || mc.currentScreen != null) {
            previousCameraPosition = cameraPosition;
            return;
        }

        double left = 0.0;
        double forward = 0.0;

        if (isPressed(mc.options.leftKey)) left += 1.0;
        if (isPressed(mc.options.rightKey)) left -= 1.0;
        if (isPressed(mc.options.forwardKey)) forward += 1.0;
        if (isPressed(mc.options.backKey)) forward -= 1.0;

        double length = Math.sqrt(left * left + forward * forward);
        if (length > 1.0) {
            left /= length;
            forward /= length;
        }

        double yawRadians = Math.toRadians(cameraYaw);
        double sinYaw = Math.sin(yawRadians);
        double cosYaw = Math.cos(yawRadians);
        double offsetX = left * cosYaw - forward * sinYaw;
        double offsetZ = left * sinYaw + forward * cosYaw;

        double offsetY = 0.0;
        if (isPressed(mc.options.jumpKey)) offsetY += verticalSpeed.getValue();
        if (isPressed(mc.options.sneakKey)) offsetY -= verticalSpeed.getValue();

        previousCameraPosition = cameraPosition;
        cameraPosition = cameraPosition.add(
                offsetX * horizontalSpeed.getValue(),
                offsetY,
                offsetZ * horizontalSpeed.getValue()
        );
    }

    private boolean isPressed(KeyBinding keyBinding) {
        int keyCode = InputUtil.fromTranslationKey(keyBinding.getBoundKeyTranslationKey()).getCode();
        return InputUtil.isKeyPressed(mc.getWindow(), keyCode);
    }

    private void reloadChunks() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public static boolean isActive() {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return false;

        Freecam freecam = client.getModuleManager().getModule(Freecam.class);
        return freecam != null && freecam.isEnabled() && canFly;
    }

    public static Vec3d getCameraPosition(float tickDelta) {
        return previousCameraPosition.lerp(cameraPosition, MathHelper.clamp(tickDelta, 0.0F, 1.0F));
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        cameraYaw += (float) (deltaYaw * 0.15);
        cameraPitch = MathHelper.clamp(cameraPitch + (float) (deltaPitch * 0.15), -90.0F, 90.0F);
    }

    public static Vec3d getCameraDirection(double scale) {
        return Vec3d.fromPolar(cameraPitch, cameraYaw).multiply(scale);
    }
}
