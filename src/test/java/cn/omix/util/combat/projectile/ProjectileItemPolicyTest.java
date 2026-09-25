package cn.omix.util.combat.projectile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileItemPolicyTest {
    @Test void matchesDecodedEnglishAndChineseWindChargeNames() {
        assertTrue(ProjectileItemPolicy.isWindChargeName("[WIND CHARGE]"));
        assertTrue(ProjectileItemPolicy.isWindChargeName("[超级风弹 x16]"));
        assertFalse(ProjectileItemPolicy.isWindChargeName("[Custom Snowball]"));
        assertFalse(ProjectileItemPolicy.isWindChargeName("[Bridge Egg]"));
        assertFalse(ProjectileItemPolicy.isWindChargeName("[雪球]"));
    }
}
