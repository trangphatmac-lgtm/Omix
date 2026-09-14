package cn.omix.module.impl.combat;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReachServerRangeTest {
    @Test
    void usesServerOriginEvenWhenClientCanStandNextToTarget() {
        Box target = new Box(10, 0, -0.3, 10.6, 1.8, 0.3);
        assertFalse(ReachServerRange.contains(Vec3d.ZERO, 1.62, target, 6));
        assertTrue(ReachServerRange.contains(new Vec3d(8, 0, 0), 1.62, target, 6));
    }

    @Test
    void usesNearestHitboxPointAndNeverExceedsSixBlocks() {
        Box boundary = new Box(6, 0, -0.3, 6.6, 1.8, 0.3);
        Box outside = boundary.offset(0.0001, 0, 0);
        assertTrue(ReachServerRange.contains(Vec3d.ZERO, 1.62, boundary, 6));
        assertFalse(ReachServerRange.contains(Vec3d.ZERO, 1.62, outside, 6));
        assertFalse(ReachServerRange.contains(Vec3d.ZERO, 1.62, outside, 100));
    }

    @Test
    void respectsConfiguredRangeAndVerticalDistance() {
        Box target = new Box(4, 0, 0, 4.6, 1.8, 0.6);
        assertFalse(ReachServerRange.contains(Vec3d.ZERO, 1.62, target, 3));
        assertTrue(ReachServerRange.contains(Vec3d.ZERO, 1.62, target, 4));
        Box above = new Box(0, 8, 0, 0.6, 9.8, 0.6);
        assertFalse(ReachServerRange.contains(Vec3d.ZERO, 1.62, above, 6));
        assertTrue(ReachServerRange.contains(new Vec3d(0, 1, 0), 1.62, above, 6));
    }
}
