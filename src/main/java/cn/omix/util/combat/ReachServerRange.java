package cn.omix.util.combat;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Attack limit measured from the pending server position, never from the client's displaced position. */
public final class ReachServerRange {
    private ReachServerRange() {}

    public static boolean contains(Vec3d serverPosition, double eyeHeight, Box targetBox, double configuredRange) {
        double range = Math.min(6.0, Math.max(0.0, configuredRange));
        Vec3d serverEye = serverPosition.add(0.0, eyeHeight, 0.0);
        return targetBox.squaredMagnitude(serverEye) <= range * range;
    }
}
