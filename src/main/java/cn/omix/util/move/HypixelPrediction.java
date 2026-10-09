package cn.omix.util.move;

/** Fixed timer cycle and key-based yaw from AmunixNext's HypixelSpeed. */
public final class HypixelPrediction {
    private static final float[] TIMERS = {
            1.00F, 0.85F, 0.70F, 0.63F, 0.55F,
            1.70F, 1.52F, 1.50F, 1.47F, 1.44F
    };

    private int tickCounter;

    public float tick(boolean active) {
        if (!active) {
            reset();
            return 1.0F;
        }
        float speed = TIMERS[tickCounter];
        tickCounter = (tickCounter + 1) % TIMERS.length;
        return speed;
    }

    public void reset() {
        tickCounter = 0;
    }

    public static float movementYaw(float cameraYaw, int forward, int right, boolean onGround) {
        float yaw = cameraYaw + (float) Math.toDegrees(Math.atan2(right, forward))
                + (onGround ? 0.0F : -45.0F);
        // Normalize without depending on Minecraft, so the port can be tested on the JVM.
        return ((yaw % 360.0F) + 540.0F) % 360.0F - 180.0F;
    }
}
