package cn.omix.util.combat;

/** Limits early mace attacks to the descending smash window. */
public final class MaceSmashTiming {
    private MaceSmashTiming() {}

    public static boolean canBypassCooldown(boolean mace, boolean vanillaSmash,
                                            boolean onGround, double velocityY) {
        return mace && vanillaSmash && !onGround && velocityY < 0;
    }
}
