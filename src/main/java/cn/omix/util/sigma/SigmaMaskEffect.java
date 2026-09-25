package cn.omix.util.sigma;

import cn.omix.event.impl.Render3DEvent;
import cn.omix.util.IMinecraft;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.OptionalInt;

/** Alpha-mask replacement for Jello's stencil union. Both passes are batched at framebuffer resolution. */
public final class SigmaMaskEffect implements IMinecraft {
    private static final RenderPipeline COMPOSITE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.of("omix", "pipeline/sigma_mask"))
            .withVertexShader(Identifier.of("omix", "core/sigma_mask"))
            .withFragmentShader(Identifier.of("omix", "core/sigma_mask"))
            .withSampler("MaskTexture").withSampler("OverlayTexture").withUniform("MaskSettings", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA).withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static SimpleFramebuffer mask, overlay;
    private static GpuBuffer settings;
    private static long lastUse;

    private SigmaMaskEffect() {}
    public static void init() {}
    public record ColoredBox(Box box, int color) {}

    public static void boxes(Render3DEvent event, List<ColoredBox> boxes, List<Entity> shadows) {
        if (boxes.isEmpty()) return;
        render(event, () -> {
            for (ColoredBox box : boxes) SigmaWorldRender.box(event, box.box, -1, true, 0);
        }, () -> {
            for (Entity entity : shadows) if (entity instanceof PlayerEntity) SigmaWorldRender.shadowSprite(event, entity);
            for (ColoredBox box : boxes) SigmaWorldRender.box(event, box.box, box.color, false, 3);
        }, 0, 0);
    }

    public static void entities(Render3DEvent event, List<Entity> entities, int color) {
        if (entities.isEmpty()) return;
        render(event, () -> {
            var dispatcher = mc.getEntityRenderDispatcher();
            var camera = mc.gameRenderer.getCamera().getCameraPos();
            var queue = mc.gameRenderer.getEntityRenderCommandQueue();
            var cameraState = mc.gameRenderer.getEntityRenderStates().cameraRenderState;
            for (Entity entity : entities) {
                // A fresh render state carries the exact animated mesh, armor and held items, without mutating the entity.
                var state = dispatcher.getAndUpdateRenderState(entity, event.getTickDelta());
                state.displayName = null; state.shadowPieces.clear(); state.onFire = false; state.outlineColor = 0;
                state.leashDatas = null;
                dispatcher.render(state, cameraState, state.x - camera.x, state.y - camera.y, state.z - camera.z,
                        event.getMatrixStack(), queue);
            }
            mc.gameRenderer.getEntityRenderDispatcher().render();
            mc.getBufferBuilders().getEffectVertexConsumers().draw();
        }, () -> entities.forEach(entity -> SigmaWorldRender.shadowSprite(event, entity)), color, 1);
    }

    private static void render(Render3DEvent event, Runnable maskPass, Runnable overlayPass, int color, float edgeWidth) {
        if (!(event.getConsumers() instanceof VertexConsumerProvider.Immediate consumers)) return;
        consumers.draw();
        ensureBuffers(); lastUse = System.nanoTime();
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try {
            encoder.clearColorAndDepthTextures(mask.getColorAttachment(), 0, mask.getDepthAttachment(), 1);
            encoder.clearColorAndDepthTextures(overlay.getColorAttachment(), 0, overlay.getDepthAttachment(), 1);
            RenderSystem.outputColorTextureOverride = mask.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = mask.getDepthAttachmentView();
            maskPass.run(); consumers.draw();
            RenderSystem.outputColorTextureOverride = overlay.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = overlay.getDepthAttachmentView();
            overlayPass.run(); consumers.draw();
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = stack.malloc(32);
            data.putFloat((color >> 16 & 255) / 255f).putFloat((color >> 8 & 255) / 255f).putFloat((color & 255) / 255f).putFloat((color >>> 24) / 255f);
            data.putFloat(edgeWidth).putFloat(0).putFloat(0).putFloat(0).flip();
            encoder.writeToBuffer(settings.slice(), data);
        }
        try (var pass = encoder.createRenderPass(() -> "Jello outer contour", previousColor != null ? previousColor : mc.getFramebuffer().getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(COMPOSITE);
            var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            pass.bindTexture("MaskTexture", mask.getColorAttachmentView(), sampler);
            pass.bindTexture("OverlayTexture", overlay.getColorAttachmentView(), sampler);
            pass.setUniform("MaskSettings", settings);
            pass.draw(0, 3);
        }
    }
    private static void ensureBuffers() {
        int width = mc.getFramebuffer().textureWidth, height = mc.getFramebuffer().textureHeight;
        if (mask == null) {
            mask = new SimpleFramebuffer("Jello silhouette mask", width, height, true);
            overlay = new SimpleFramebuffer("Jello silhouette overlay", width, height, true);
            settings = RenderSystem.getDevice().createBuffer(() -> "Jello mask settings", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
        } else if (mask.textureWidth != width || mask.textureHeight != height) { mask.resize(width, height); overlay.resize(width, height); }
    }
    public static void releaseUnused() { if (mask != null && (mc.world == null || System.nanoTime() - lastUse > 2_000_000_000L)) close(); }
    public static void close() {
        if (mask != null) { mask.delete(); overlay.delete(); settings.close(); mask = overlay = null; settings = null; }
    }
}
