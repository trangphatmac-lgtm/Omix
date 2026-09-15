package cn.omix.util;

/** Immutable angles shared by item use and its enclosing movement tick. */
public record LongJumpAim(float yaw, float pitch) {
    public static LongJumpAim behind(float takeoffYaw, float targetPitch) {
        return new LongJumpAim(takeoffYaw + 180.0F, targetPitch);
    }
}
