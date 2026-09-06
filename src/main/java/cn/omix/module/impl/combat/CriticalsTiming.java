package cn.omix.module.impl.combat;

import java.util.function.IntPredicate;

/** Attack timing shared by the reconstructed 1.9+ and Heypixel modes. */
final class CriticalsTiming {
    private float cachedDamage;

    boolean shouldDefer(boolean modern, int hurtTime, float damage, boolean cannotCrit,
                        double vy, float cooldown, float cooldownPeriod, float targetTicks,
                        IntPredicate predictsLanding) {
        if (modern && hurtTime > 0 && damage <= cachedDamage) return false;
        if (cannotCrit) return false;
        if (vy < -.08) {
            if (modern) cachedDamage = damage;
            return false;
        }
        float ticks = waitTicks(vy, cooldown, cooldownPeriod);
        if (ticks > targetTicks) return false;
        return !predictsLanding.test((int) (ticks * 1.3F));
    }

    static float waitTicks(double vy, float cooldown, float cooldownPeriod) {
        float cooldownTicks = Math.max(0F, (.95F - cooldown) * cooldownPeriod);
        float verticalTicks = (float) (vy / .08);
        return Math.max(cooldownTicks, verticalTicks);
    }

    static float estimateDamage(float base, float cooldown, boolean cannotCrit, double vy) {
        float damage = base * (.2F + cooldown * cooldown * .8F);
        if (!cannotCrit && vy < -.08) damage *= 1.5F;
        return damage;
    }

    static boolean skipStuckFall(boolean useFallDistance, double fallDistance, double vy) {
        return useFallDistance ? fallDistance <= 0.0 : vy > -.08;
    }
}
