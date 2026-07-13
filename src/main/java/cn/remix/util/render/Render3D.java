package cn.remix.util.render;

import cn.remix.event.impl.Render3DEvent;
import cn.remix.util.IMinecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

@UtilityClass
public final class Render3D implements IMinecraft {
    private final RenderPipeline SEE_THROUGH_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("remix", "pipeline/see_through_lines"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    private final RenderPipeline SEE_THROUGH_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("remix", "pipeline/see_through_quads"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    private final RenderLayer SEE_THROUGH_LINES = RenderLayer.of(
            "remix_see_through_lines",
            RenderSetup.builder(SEE_THROUGH_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .build()
    );
    private final RenderLayer SEE_THROUGH_QUADS = RenderLayer.of(
            "remix_see_through_quads",
            RenderSetup.builder(SEE_THROUGH_QUADS_PIPELINE)
                    .translucent()
                    .build()
    );

    public void drawBox(Render3DEvent event, Box box, Color color, boolean fill, boolean outline) {
        if ((!fill && !outline) || mc.gameRenderer == null) return;

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Box relative = box.offset(-camera.x, -camera.y, -camera.z);
        MatrixStack.Entry entry = event.getMatrixStack().peek();

        if (fill && color.getAlpha() > 0) {
            drawFilledBox(entry, event.getConsumers().getBuffer(SEE_THROUGH_QUADS), relative, color);
        }

        if (outline) {
            Color outlineColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
            drawBoxLines(entry, event.getConsumers().getBuffer(SEE_THROUGH_LINES), relative, outlineColor);
        }
    }

    public void drawLine(Render3DEvent event, Vec3d start, Vec3d end, Color color) {
        if (mc.gameRenderer == null) return;

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        drawLine(
                event.getMatrixStack().peek(),
                event.getConsumers().getBuffer(SEE_THROUGH_LINES),
                start.subtract(camera),
                end.subtract(camera),
                color
        );
    }

    private void drawFilledBox(MatrixStack.Entry entry, VertexConsumer buffer, Box box, Color color) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        quad(entry, buffer, color, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
        quad(entry, buffer, color, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        quad(entry, buffer, color, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ);
        quad(entry, buffer, color, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        quad(entry, buffer, color, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
        quad(entry, buffer, color, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ);
    }

    private void drawBoxLines(MatrixStack.Entry entry, VertexConsumer buffer, Box box, Color color) {
        Vec3d nnn = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d nnx = new Vec3d(box.minX, box.minY, box.maxZ);
        Vec3d nxn = new Vec3d(box.minX, box.maxY, box.minZ);
        Vec3d nxx = new Vec3d(box.minX, box.maxY, box.maxZ);
        Vec3d xnn = new Vec3d(box.maxX, box.minY, box.minZ);
        Vec3d xnx = new Vec3d(box.maxX, box.minY, box.maxZ);
        Vec3d xxn = new Vec3d(box.maxX, box.maxY, box.minZ);
        Vec3d xxx = new Vec3d(box.maxX, box.maxY, box.maxZ);

        drawLine(entry, buffer, nnn, nnx, color);
        drawLine(entry, buffer, nnx, xnx, color);
        drawLine(entry, buffer, xnx, xnn, color);
        drawLine(entry, buffer, xnn, nnn, color);
        drawLine(entry, buffer, nxn, nxx, color);
        drawLine(entry, buffer, nxx, xxx, color);
        drawLine(entry, buffer, xxx, xxn, color);
        drawLine(entry, buffer, xxn, nxn, color);
        drawLine(entry, buffer, nnn, nxn, color);
        drawLine(entry, buffer, nnx, nxx, color);
        drawLine(entry, buffer, xnx, xxx, color);
        drawLine(entry, buffer, xnn, xxn, color);
    }

    private void drawLine(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d start, Vec3d end, Color color) {
        Vec3d normal = end.subtract(start);
        if (normal.lengthSquared() < 1.0E-7) {
            normal = new Vec3d(0.0, 1.0, 0.0);
        } else {
            normal = normal.normalize();
        }

        buffer.vertex(entry, (float) start.x, (float) start.y, (float) start.z)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z)
                .lineWidth(2.0F);
        buffer.vertex(entry, (float) end.x, (float) end.y, (float) end.z)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z)
                .lineWidth(2.0F);
    }

    private void quad(MatrixStack.Entry entry, VertexConsumer buffer, Color color,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4) {
        buffer.vertex(entry, x1, y1, z1).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(entry, x2, y2, z2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(entry, x3, y3, z3).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(entry, x4, y4, z4).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
}
