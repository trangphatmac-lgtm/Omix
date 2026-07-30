package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import lombok.Getter;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spoofs the player's server-side rotation without changing the camera.
 */
@Getter
public final class Derp extends Module {
    private final ModeValue yawMode = new ModeValue(
            "Yaw", "Random", "Static", "Offset", "Random", "Jitter", "Spin"
    );
    private final NumberValue yaw = new NumberValue(
            "Yaw Value", 0.0F, -180.0F, 180.0F, 1.0F, () -> yawMode.is("Static")
    );
    private final NumberValue yawOffset = new NumberValue(
            "Yaw Offset", 0.0F, -180.0F, 180.0F, 1.0F, () -> yawMode.is("Offset")
    );
    private final NumberValue forwardTicks = new NumberValue(
            "Forward Ticks", 2.0F, 0.0F, 100.0F, 1.0F, () -> yawMode.is("Jitter")
    );
    private final NumberValue backwardTicks = new NumberValue(
            "Backward Ticks", 2.0F, 0.0F, 100.0F, 1.0F, () -> yawMode.is("Jitter")
    );
    private final NumberValue spinSpeed = new NumberValue(
            "Spin Speed", 50.0F, -70.0F, 70.0F, 1.0F, () -> yawMode.is("Spin")
    );

    private final ModeValue pitchMode = new ModeValue(
            "Pitch", "Random", "Static", "Offset", "Random"
    );
    private final NumberValue pitch = new NumberValue(
            "Pitch Value", -90.0F, -180.0F, 180.0F, 1.0F, () -> pitchMode.is("Static")
    );
    private final NumberValue pitchOffset = new NumberValue(
            "Pitch Offset", 0.0F, -180.0F, 180.0F, 1.0F, () -> pitchMode.is("Offset")
    );

    private final BoolValue safePitch = new BoolValue("Safe Pitch", true);
    private final BoolValue notDuringSprint = new BoolValue("Not During Sprint", true);

    private float[] rotations;
    private float jitterYaw;
    private float spinYaw;
    private int jitterTick;

    public Derp() {
        super("Derp", Category.Move);
    }

    @Override
    public void onDisable() {
        rotations = null;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) {
            rotations = null;
            return;
        }

        updateActiveYawMode();
        setSuffix(yawMode.getValue() + " / " + pitchMode.getValue());

        if (notDuringSprint.getValue()
                && (mc.options.sprintKey.isPressed() || mc.player.isSprinting())) {
            rotations = null;
            return;
        }

        float spoofedYaw = switch (yawMode.getValue()) {
            case "Static" -> yaw.getValue();
            case "Offset" -> mc.player.getYaw() + yawOffset.getValue();
            case "Jitter" -> jitterYaw;
            case "Spin" -> spinYaw;
            default -> randomRotation(-180.0F, 180.0F);
        };

        float spoofedPitch = switch (pitchMode.getValue()) {
            case "Static" -> pitch.getValue();
            case "Offset" -> mc.player.getPitch() + pitchOffset.getValue();
            default -> safePitch.getValue()
                    ? randomRotation(-90.0F, 90.0F)
                    : randomRotation(-180.0F, 180.0F);
        };

        if (safePitch.getValue()) {
            spoofedPitch = MathHelper.clamp(spoofedPitch, -90.0F, 90.0F);
        }

        rotations = new float[]{spoofedYaw, spoofedPitch};
    }

    private void updateActiveYawMode() {
        if (yawMode.is("Spin")) {
            spinYaw += spinSpeed.getValue();
            return;
        }

        if (!yawMode.is("Jitter")) {
            return;
        }

        int forward = forwardTicks.getValue().intValue();
        int backward = backwardTicks.getValue().intValue();
        int cycleLength = forward + backward;

        if (cycleLength == 0) {
            jitterTick = 0;
            jitterYaw = mc.player.getYaw();
            return;
        }

        if (jitterTick >= cycleLength) {
            jitterTick = 0;
        }

        jitterYaw = jitterTick < forward
                ? mc.player.getYaw()
                : mc.player.getYaw() + 180.0F;
        jitterTick = (jitterTick + 1) % cycleLength;
    }

    private float randomRotation(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}
