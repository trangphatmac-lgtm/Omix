package cn.omix.util.sigma;

import cn.omix.util.IMinecraft;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;

import java.util.OptionalInt;

/** Original separable box blur (35px HUD / animated 20px GUI), with exactly equivalent bilinear tap pairs. */
public final class SigmaBlur implements IMinecraft {
    public static final RenderPipeline OPAQUE_TEXTURE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(Identifier.of("omix", "pipeline/sigma_opaque_texture"))
            .withFragmentShader(Identifier.of("omix", "core/sigma_opaque_texture")).withCull(false).build());
    private static final RenderPipeline BLUR = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.of("omix", "pipeline/sigma_blur"))
            .withVertexShader(Identifier.of("omix", "core/sigma_mask"))
            .withFragmentShader(Identifier.of("omix", "core/sigma_blur"))
            .withSampler("Source").withUniform("BlurSettings", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES).withoutBlend().withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static SimpleFramebuffer horizontal, result;
    private static GpuBuffer settings;
    private static long updated;
    private static int generation;
    private static boolean fullScreen;

    private SigmaBlur() {}
    public static void init() {}
    public static int generation() { return generation; }

    public static void releaseUnused() {
        var hud = SigmaHud.active();
        boolean screen = mc.currentScreen instanceof cn.omix.ui.sigma.SigmaClickGuiScreen || mc.currentScreen instanceof cn.omix.ui.sigma.SigmaMapsScreen;
        boolean tab = mc.world != null && hud != null && hud.getSigmaTabGui().getValue() && !mc.options.hudHidden && !mc.getDebugHud().shouldShowDebugHud();
        if (result != null && !screen && !tab && System.nanoTime() - updated > 2_000_000_000L) close();
    }

    public static void capture() {
        var hud = SigmaHud.active();
        boolean screen = mc.currentScreen instanceof cn.omix.ui.sigma.SigmaClickGuiScreen || mc.currentScreen instanceof cn.omix.ui.sigma.SigmaMapsScreen;
        boolean tab = hud != null && hud.getSigmaTabGui().getValue() && !mc.options.hudHidden && !mc.getDebugHud().shouldShowDebugHud();
        if (!screen && !tab) { if (result != null && System.nanoTime() - updated > 2_000_000_000L) close(); return; }
        var source = mc.getFramebuffer();
        int width = source.textureWidth, height = source.textureHeight;
        if (result == null) {
            horizontal = new SimpleFramebuffer("Jello horizontal blur", width, height, false);
            result = new SimpleFramebuffer("Jello blur", width, height, false);
            settings = RenderSystem.getDevice().createBuffer(() -> "Jello blur direction", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
            generation++;
        } else if (result.textureWidth != width || result.textureHeight != height) {
            horizontal.resize(width, height); result.resize(width, height); generation++; updated = 0;
        }
        long now = System.nanoTime();
        int fps = hud == null ? 60 : Math.clamp(hud.getHudFps().getValue().intValue(), 5, 60);
        if (screen == fullScreen && now - updated < 1_000_000_000L / fps) return;
        fullScreen = screen;
        updated = now;
        // HUD blur is confined to the two possible TabGUI columns plus its filter border.
        int right = screen ? width : Math.min(width, (int) Math.ceil(375.0 * width / SigmaDraw.width()));
        int radius = mc.currentScreen instanceof cn.omix.ui.sigma.SigmaClickGuiScreen gui ? gui.blurRadius() : screen ? 20 : 35;
        pass(source.getColorAttachmentView(), horizontal, 1f / width, 0, right, radius);
        pass(horizontal.getColorAttachmentView(), result, 0, 1f / height, right, radius);
    }

    private static void pass(GpuTextureView source, SimpleFramebuffer target, float dx, float dy, int right, int radius) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = stack.malloc(16).putFloat(dx).putFloat(dy).putFloat(radius).putFloat(0).flip();
            encoder.writeToBuffer(settings.slice(), data);
        }
        try (var pass = encoder.createRenderPass(() -> "Jello blur", target.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(BLUR); pass.setUniform("BlurSettings", settings);
            pass.bindTexture("Source", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.enableScissor(0, 0, right, target.textureHeight);
            pass.draw(0, 3);
        }
    }

    /** Coordinates and UVs follow the window-pixel UI, including the framebuffer's vertical flip. */
    public static void draw(DrawContext context, float x, float y, float width, float height, float opacity) {
        if (result == null) return;
        context.state.addSimpleElement(new TexturedQuadGuiElementRenderState(OPAQUE_TEXTURE,
                TextureSetup.of(result.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                new Matrix3x2f(context.getMatrices()), (int) x, (int) y, (int) Math.ceil(x + width), (int) Math.ceil(y + height),
                x / SigmaDraw.width(), (x + width) / SigmaDraw.width(), 1 - y / SigmaDraw.height(), 1 - (y + height) / SigmaDraw.height(),
                SigmaColors.alpha(-1, opacity), context.scissorStack.peekLast()));
    }

    public static void close() {
        if (result != null) { horizontal.delete(); result.delete(); settings.close(); horizontal = result = null; settings = null; generation++; }
        updated = 0;
    }
}
