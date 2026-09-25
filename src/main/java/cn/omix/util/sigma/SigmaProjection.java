package cn.omix.util.sigma;

import cn.omix.event.impl.Render3DEvent;
import cn.omix.util.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

/** Projects Jello's camera-facing world labels into the modern GUI command buffer. */
public final class SigmaProjection implements IMinecraft {
    private SigmaProjection() {}

    public static Label label(Render3DEvent event, Vec3d position, float worldScale) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Vector4f clip = new Vector4f((float) (position.x - camera.x), (float) (position.y - camera.y),
                (float) (position.z - camera.z), 1).mul(event.getModelViewMatrix()).mul(event.getProjectionMatrix());
        if (clip.w <= .01 || clip.z < -clip.w || clip.z > clip.w) return null;
        var rotation = mc.gameRenderer.getCamera().getRotation();
        var right = rotation.transform(new org.joml.Vector3f(worldScale, 0, 0));
        var down = rotation.transform(new org.joml.Vector3f(0, -worldScale, 0));
        Vector4f dx = new Vector4f(right, 0).mul(event.getModelViewMatrix()).mul(event.getProjectionMatrix());
        Vector4f dy = new Vector4f(down, 0).mul(event.getModelViewMatrix()).mul(event.getProjectionMatrix());
        float x = (clip.x / clip.w * .5f + .5f) * SigmaDraw.width(), y = (.5f - clip.y / clip.w * .5f) * SigmaDraw.height();
        float rx = (dx.x * clip.w - clip.x * dx.w) / (clip.w * clip.w) * SigmaDraw.width() * .5f;
        float ry = -(dx.y * clip.w - clip.y * dx.w) / (clip.w * clip.w) * SigmaDraw.height() * .5f;
        float bx = (dy.x * clip.w - clip.x * dy.w) / (clip.w * clip.w) * SigmaDraw.width() * .5f;
        float by = -(dy.y * clip.w - clip.y * dy.w) / (clip.w * clip.w) * SigmaDraw.height() * .5f;
        if (!Float.isFinite(rx + ry + bx + by)) return null;
        return new Label(x, y, rx, ry, bx, by);
    }

    public record Label(float x, float y, float rightX, float rightY, float downX, float downY) {
        public void begin(DrawContext context) {
            SigmaDraw.begin(context);
            context.getMatrices().mul(new org.joml.Matrix3x2f(rightX, rightY, downX, downY, x, y));
        }
        public void end(DrawContext context) { SigmaDraw.end(context); }
    }
}
