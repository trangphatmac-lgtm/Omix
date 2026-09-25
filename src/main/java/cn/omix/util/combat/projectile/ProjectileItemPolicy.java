package cn.omix.util.combat.projectile;

import java.util.Locale;

public final class ProjectileItemPolicy {
    private ProjectileItemPolicy() {}

    public static boolean isWindChargeName(String hoverText) {
        String name = hoverText.toLowerCase(Locale.ROOT);
        return name.contains("wind charge") || name.contains("风弹");
    }
}
