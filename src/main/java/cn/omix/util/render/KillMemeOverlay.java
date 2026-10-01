package cn.omix.util.render;

import cn.omix.event.impl.Render3DEvent;
import cn.omix.util.sigma.SigmaProjection;
import cn.omix.util.sigma.SigmaRearView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Short-lived, camera-facing red circle and curved arrow anchored to a death position. */
public final class KillMemeOverlay {
    private static final int MAX_MARKERS = 16;
    private static final float[] MESH = mesh();
    private final ArrayDeque<Marker> markers = new ArrayDeque<>();
    private final List<Projected> projected = new ArrayList<>();

    public void spawn(double x, double y, double z, long now, float seconds) {
        active(now);
        while (markers.size() >= MAX_MARKERS) markers.removeFirst();
        markers.addLast(new Marker(x, y, z, now, (long) (seconds * 1_000_000_000L)));
    }

    public void clear() {
        markers.clear();
        projected.clear();
    }

    List<Marker> active(long now) {
        markers.removeIf(marker -> marker.expired(now));
        return List.copyOf(markers);
    }

    public void project(Render3DEvent event, long now) {
        // A rear-view pass must never replace the main camera's projected positions.
        if (SigmaRearView.isRendering()) return;
        projected.clear();
        for (Marker marker : active(now)) {
            var label = SigmaProjection.label(event, new Vec3d(marker.x(), marker.y(), marker.z()), .011F);
            if (label != null) projected.add(new Projected(marker, label));
        }
    }

    public void draw(DrawContext context, long now) {
        projected.removeIf(item -> item.marker().expired(now));
        if (projected.isEmpty()) return;
        context.createNewRootLayer();
        for (Projected item : projected) {
            int alpha = item.marker().alpha(now);
            if (alpha == 0) continue;
            item.label().begin(context);
            try {
                var pose = new Matrix3x2f(context.getMatrices());
                var clip = context.scissorStack.peekLast();
                // Includes the full ring and arrow, with margin for fractional coordinates.
                ScreenRect bounds = new ScreenRect(-94, -180, 266, 274).transformEachVertex(pose);
                if (clip != null) bounds = clip.intersection(bounds);
                context.state.addSimpleElement(new MeshState(pose, (alpha << 24) | 0xFF1919, clip, bounds));
            } finally {
                item.label().end(context);
            }
        }
    }

    record Marker(double x, double y, double z, long born, long lifetime) {
        boolean expired(long now) { return now - born >= lifetime; }

        int alpha(long now) {
            long age = now - born;
            if (age < 0 || expired(now)) return 0;
            // Hold for half a second, then flash twice per second until expiry.
            return age < 500_000_000L || ((age - 500_000_000L) / 250_000_000L) % 2 == 0 ? 255 : 0;
        }
    }

    private record Projected(Marker marker, SigmaProjection.Label label) {}

    private record MeshState(Matrix3x2fc pose, int color, @Nullable ScreenRect scissorArea,
                             @Nullable ScreenRect bounds) implements SimpleGuiElementRenderState {
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.empty(); }

        @Override public void setupVertices(VertexConsumer buffer) {
            for (int i = 0; i < MESH.length; i += 2) {
                buffer.vertex(pose, MESH[i], MESH[i + 1]).color(color);
            }
        }
    }

    /** Vector geometry keeps the meme crisp at any GUI scale; no raster asset is required. */
    static float[] mesh() {
        int circleSegments = 96, curveSegments = 32;
        float[] vertices = new float[(circleSegments + curveSegments + 1) * 8];
        int index = 0;
        for (int i = 0; i < circleSegments; i++) {
            double a = Math.PI * 2 * i / circleSegments;
            double b = Math.PI * 2 * (i + 1) / circleSegments;
            for (float[] point : new float[][]{
                    {(float) Math.cos(a) * 92, (float) Math.sin(a) * 92},
                    {(float) Math.cos(a) * 89, (float) Math.sin(a) * 89},
                    {(float) Math.cos(b) * 89, (float) Math.sin(b) * 89},
                    {(float) Math.cos(b) * 92, (float) Math.sin(b) * 92}}) {
                vertices[index++] = point[0]; vertices[index++] = point[1];
            }
        }
        for (int i = 0; i < curveSegments; i++) {
            float a = (float) i / curveSegments, b = (float) (i + 1) / curveSegments;
            for (float[] point : new float[][]{edge(a, false), edge(b, false), edge(b, true), edge(a, true)}) {
                vertices[index++] = point[0]; vertices[index++] = point[1];
            }
        }
        // Arrowhead points down-left into the ring. Repeat one vertex for the GUI quad batch.
        for (float coordinate : new float[]{-20, -78, -6, -8, 67, -24, -20, -78}) vertices[index++] = coordinate;
        return vertices;
    }

    private static float[] edge(float t, boolean inner) {
        return new float[]{
                cubic(t, 170, inner ? 104 : 60, inner ? 65 : 34, inner ? 36 : 9),
                cubic(t, -120, inner ? -128 : -178, inner ? -112 : -120, inner ? -46 : -45)};
    }

    private static float cubic(float t, float start, float control1, float control2, float end) {
        float s = 1 - t;
        return s * s * s * start + 3 * s * s * t * control1 + 3 * s * t * t * control2 + t * t * t * end;
    }
}
