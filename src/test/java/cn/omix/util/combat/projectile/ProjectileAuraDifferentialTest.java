package cn.omix.util.combat.projectile;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;

/** Runs the supplied original-instruction goldens against the actual production engine. */
class ProjectileAuraDifferentialTest {
    private String fixture(String name) throws Exception {
        return Path.of(getClass().getResource("/projectileaura/" + name + "-golden.tsv").toURI()).toString();
    }

    @Test void nativeStateAndEffects() throws Exception {
        NativeDifferentialTest.main(new String[]{fixture("native")});
    }

    @Test void bytecodeMathAndCooldown() throws Exception {
        MathDifferentialTest.main(new String[]{fixture("math")});
    }

    @Test void nativeSelectionAndVisibility() throws Exception {
        SelectionDifferentialTest.main(new String[]{fixture("selection")});
    }
}
