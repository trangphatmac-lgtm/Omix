package cn.omix.util.sigma;

import cn.omix.event.impl.Render3DEvent;
import cn.omix.util.IMinecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.pipeline.BlendFunction;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;

/** Batched, depth-independent equivalents of Jello's world primitives. */
public final class SigmaWorldRender implements IMinecraft {
    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("omix", "pipeline/sigma_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("omix", "pipeline/sigma_fill"))
            .withBlend(BlendFunction.TRANSLUCENT).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderLayer LINES = RenderLayer.of("sigma_lines", RenderSetup.builder(LINE_PIPELINE).translucent().build());
    private static final RenderLayer FILLS = RenderLayer.of("sigma_fill", RenderSetup.builder(FILL_PIPELINE).translucent().build());
    private static final RenderPipeline SPRITE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(Identifier.of("omix", "pipeline/sigma_sprite")).withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderLayer SHADOWS = RenderLayer.of("sigma_shadow", RenderSetup.builder(SPRITE_PIPELINE)
            .texture("Sampler0", SigmaResources.texture("alt/shadow.png")).translucent().build());

    private SigmaWorldRender() {}
    public static void init() { SigmaMaskEffect.init(); SigmaBlur.init(); }

    public static void line(Render3DEvent event, Vec3d from, Vec3d to, int startColor, int endColor, float width) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d normal = to.subtract(from).normalize();
        if (normal.lengthSquared() < 1e-8) return;
        MatrixStack.Entry entry = event.getMatrixStack().peek();
        VertexConsumer buffer = event.getConsumers().getBuffer(LINES);
        lineVertex(buffer, entry, from.subtract(camera), normal, startColor, width);
        lineVertex(buffer, entry, to.subtract(camera), normal, endColor, width);
    }

    private static void lineVertex(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d pos, Vec3d normal, int color, float width) {
        buffer.vertex(entry, (float) pos.x, (float) pos.y, (float) pos.z).color(color)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z).lineWidth(width);
    }

    public static void triangle(Render3DEvent event, Vec3d a, Vec3d b, Vec3d c, int color) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumer buffer = event.getConsumers().getBuffer(FILLS);
        MatrixStack.Entry entry = event.getMatrixStack().peek();
        vertex(buffer, entry, a.subtract(camera), color);
        vertex(buffer, entry, b.subtract(camera), color);
        vertex(buffer, entry, c.subtract(camera), color);
        vertex(buffer, entry, c.subtract(camera), color);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d p, int color) {
        buffer.vertex(entry, (float) p.x, (float) p.y, (float) p.z).color(color);
    }

    /** Three contiguous batches: changing Immediate layers inside the 360-segment loop would flush every segment. */
    public static void waypoint(Render3DEvent event, Vec3d origin, int color, float scale, int age) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        float x = (float) (origin.x - camera.x), y = (float) (origin.y - camera.y), z = (float) (origin.z - camera.z);
        var entry = event.getMatrixStack().peek();
        var fill = event.getConsumers().getBuffer(FILLS);
        for (int i = 0; i < 360; i++) {
            var a = SigmaGeometry.circleVertex(i); var b = SigmaGeometry.circleVertex(i + 1);
            fill.vertex(entry, x, y, z).color(0x1d000000);
            fill.vertex(entry, x + (float) a.x() * .5f, y, z + (float) a.y() * .5f).color(0x1d000000);
            fill.vertex(entry, x + (float) b.x() * .5f, y, z + (float) b.y() * .5f).color(0x1d000000);
            fill.vertex(entry, x + (float) b.x() * .5f, y, z + (float) b.y() * .5f).color(0x1d000000);
        }
        double rotation = Math.toRadians(age % 90 * 4), cos = Math.cos(rotation), sin = Math.sin(rotation);
        var lines = event.getConsumers().getBuffer(LINES);
        float width = 1.4f + 1.4f / scale;
        for (int i = 0; i < 360; i++) {
            var a = SigmaGeometry.circleVertex(i); var b = SigmaGeometry.circleVertex(i + 1);
            double nx = (b.x() - a.x()) * cos, ny = b.y() - a.y(), nz = (b.x() - a.x()) * sin;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            lines.vertex(entry, x + (float) (a.x() * cos * .6), y + .7f + (float) a.y() * .6f, z + (float) (a.x() * sin * .6)).color(color)
                    .normal(entry, (float) (nx / length), (float) (ny / length), (float) (nz / length)).lineWidth(width);
            lines.vertex(entry, x + (float) (b.x() * cos * .6), y + .7f + (float) b.y() * .6f, z + (float) (b.x() * sin * .6)).color(color)
                    .normal(entry, (float) (nx / length), (float) (ny / length), (float) (nz / length)).lineWidth(width);
        }
        fill = event.getConsumers().getBuffer(FILLS);
        for (int half = 0; half < 2; half++) for (int face = 0; face < 4; face++) {
            int shade = SigmaColors.mix(color, SigmaColors.BLACK, face * .04f);
            for (int vertex = 0; vertex < 4; vertex++) {
                var p = SigmaGeometry.waypointVertex(half, face, Math.min(vertex, 2));
                fill.vertex(entry, x + (float) (p.x() * cos + p.z() * sin), y + .7f + (float) p.y(), z + (float) (-p.x() * sin + p.z() * cos)).color(shade);
            }
        }
    }

    public static void box(Render3DEvent event, Box box, int color, boolean filled, float lineWidth) {
        Vec3d[] vertices = corners(box);
        if (filled) {
            for (int[] face : FACES) {
                triangle(event, vertices[face[0]], vertices[face[1]], vertices[face[2]], color);
                triangle(event, vertices[face[0]], vertices[face[2]], vertices[face[3]], color);
            }
        }
        if (lineWidth > 0) for (int[] edge : EDGES) line(event, vertices[edge[0]], vertices[edge[1]], color, color, lineWidth);
    }

    public static void shadowSprite(Render3DEvent event, Entity entity) {
        Vec3d relative = entity.getLerpedPos(event.getTickDelta()).subtract(mc.gameRenderer.getCamera().getCameraPos());
        MatrixStack matrices = event.getMatrixStack();
        matrices.push();
        matrices.translate(relative.x, relative.y + entity.getHeight() + .1, relative.z);
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(mc.gameRenderer.getCamera().getYaw()));
        matrices.scale(-.11f, -.11f, -.11f);
        var matrix = matrices.peek().getPositionMatrix();
        float x = -entity.getWidth() * 22, y = -entity.getHeight() * 5.5f, w = entity.getWidth() * 44, h = entity.getHeight() * 21;
        int color = SigmaColors.alpha(SigmaColors.WHITE, .8f);
        VertexConsumer buffer = event.getConsumers().getBuffer(SHADOWS);
        buffer.vertex(matrix, x, y, 0).texture(0, 0).color(color);
        buffer.vertex(matrix, x, y + h, 0).texture(0, 1).color(color);
        buffer.vertex(matrix, x + w, y + h, 0).texture(1, 1).color(color);
        buffer.vertex(matrix, x + w, y, 0).texture(1, 0).color(color);
        matrices.pop();
    }

    public static Vec3d[] corners(Box box) {
        Vec3d[] vertices = new Vec3d[8];
        for (int i = 0; i < 8; i++) vertices[i] = new Vec3d((i & 1) == 0 ? box.minX : box.maxX,
                (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
        return vertices;
    }

    /** Only the outside contour is visible in Jello's stencil-masked outline. */
    public static void silhouette(Render3DEvent event, Box box, int color, float width) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d[] vertices = corners(box);
        for (int[] edge : SigmaGeometry.silhouetteEdges(box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ, camera.x, camera.y, camera.z)) {
            line(event, vertices[edge[0]], vertices[edge[1]], color, color, width);
        }
    }

    private static final int[][] EDGES = {{0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7}};
    private static final int[][] FACES = {{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
}
