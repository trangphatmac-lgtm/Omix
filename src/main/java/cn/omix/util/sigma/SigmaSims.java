package cn.omix.util.sigma;

import cn.omix.event.impl.Render3DEvent;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The original sixteen triangle faces, rotation and triangular bob from SimsESP. */
public final class SigmaSims {
    private static final int[] COLORS = {0xff88d948,0xff7cbd48,0xff67b54b,0xff88d948,0xff7cbd48,0xff67b54b,0xff88d948,0xff67b54b};
    private SigmaSims() {}

    public static void render(Render3DEvent event, Entity entity) {
        Vec3d origin = entity.getLerpedPos(event.getTickDelta()).add(0,
                entity.getHeight() + .7 + Math.abs(entity.age % 100 - 50) / 500.0, 0);
        for (int half = 0; half < 2; half++) {
            for (int face = 0; face < 8; face++) {
                Matrix4f transform = new Matrix4f().rotateY((float) Math.toRadians(-entity.age % 180 * 2 + half * 180 + face * 45));
                if (half != 0) transform.rotateX((float) Math.PI);
                transform.translate(0, 0, .25f).rotateX((float) Math.toRadians(-30));
                int color = half == 0 ? COLORS[face] : SigmaColors.mix(COLORS[face], 0xff000000, .2f);
                SigmaWorldRender.triangle(event, point(transform, origin, 0, .5f),
                        point(transform, origin, -.105f, 0), point(transform, origin, .105f, 0), color);
            }
        }
    }

    private static Vec3d point(Matrix4f matrix, Vec3d origin, float x, float y) {
        Vector3f point = matrix.transformPosition(new Vector3f(x, y, 0));
        return origin.add(point.x, point.y, point.z);
    }
}
