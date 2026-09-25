package cn.omix.util.sigma;

import cn.omix.Client;
import cn.omix.module.impl.render.HUD;
import cn.omix.util.IMinecraft;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.memory.ObjectPool;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** A rear-facing world pass before the main pass. Never rotates the player or recursively renders the HUD. */
public final class SigmaRearView implements IMinecraft {
    private static final SigmaRearView INSTANCE = new SigmaRearView();
    private final RearCamera camera = new RearCamera();
    private final SigmaAnimation animation = new SigmaAnimation();
    private SimpleFramebuffer framebuffer;
    private RawProjectionMatrix projection;
    private FogRenderer fog;
    private GlobalSettings globals;
    private ObjectPool pool;
    private ClientWorld world;
    private boolean rendering, visible, failed;
    private int visibilityTimer, lastTick = -1;
    private long lastRender;
    private float progress;

    private SigmaRearView() {}
    public static SigmaRearView get() { return INSTANCE; }
    public static boolean isRendering() { return INSTANCE.rendering; }
    public static Framebuffer target() { return INSTANCE.rendering ? INSTANCE.framebuffer : null; }
    public static Camera renderingCamera() { return INSTANCE.rendering ? INSTANCE.camera : null; }

    public void prepareFrame() {
        HUD hud = SigmaHud.active();
        if (mc.world != world) { release(); world = mc.world; failed = false; lastTick = -1; visibilityTimer = 0; }
        if (hud == null || !hud.getSigmaRearView().getValue() || mc.world == null || mc.player == null) { release(); return; }
        if (failed) { release(); return; }
        if (hud.getSigmaRearViewSmart().getValue() && mc.player.age != lastTick) {
            lastTick = mc.player.age;
            boolean behind = mc.world.getPlayers().stream().anyMatch(player -> player != mc.player && player.isAlive()
                    && !SigmaEntityFilter.bot(player) && player.squaredDistanceTo(mc.player) < 144
                    && Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(player.getZ() - mc.player.getZ(), player.getX() - mc.player.getX())) - 90 - mc.player.getYaw())) > 90);
            visibilityTimer = behind ? 5 : Math.max(0, visibilityTimer - 1);
        }
        visible = !mc.options.hudHidden && (mc.currentScreen == null || hud.getSigmaRearViewGui().getValue())
                && (!hud.getSigmaRearViewSmart().getValue() || visibilityTimer > 0);
        progress = animation.update(visible, System.nanoTime(), visible ? 230 : 200);
        if (progress == 0 && framebuffer != null && System.nanoTime() - lastRender > 2_000_000_000L) release();
    }

    public void renderWorld(RenderTickCounter tickCounter) {
        HUD hud = SigmaHud.active();
        if (failed || hud == null || !hud.getSigmaRearView().getValue() || mc.world == null || mc.player == null || progress <= 0 || rendering) return;
        long now = System.nanoTime();
        int fps = Math.clamp(hud.getHudFps().getValue().intValue(), 5, 60);
        if (framebuffer != null && now - lastRender < 1_000_000_000L / fps) return;
        lastRender = now;
        int width = Math.max(1, Math.round(hud.getSigmaRearViewSize().getValue() * mc.getWindow().getFramebufferWidth() / SigmaDraw.width()));
        int height = Math.max(1, width * mc.getWindow().getFramebufferHeight() / mc.getWindow().getFramebufferWidth());
        if (framebuffer == null) framebuffer = new SimpleFramebuffer("Jello rear view", width, height, true);
        else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) framebuffer.resize(width, height);
        if (projection == null) { projection = new RawProjectionMatrix("Jello rear view"); fog = new FogRenderer(); globals = new GlobalSettings(); pool = new ObjectPool(3); }
        Camera mainCamera = mc.gameRenderer.getCamera();
        float tickDelta = tickCounter.getTickProgress(true);
        camera.copyFrom(mainCamera, tickDelta);
        CameraRenderState state = mc.gameRenderer.getEntityRenderStates().cameraRenderState;
        CameraRenderState previousCameraState = state;
        state = new CameraRenderState();
        state.initialized = camera.isReady(); state.pos = camera.getCameraPos(); state.blockPos = camera.getBlockPos();
        state.entityPos = camera.getFocusedEntity().getLerpedPos(tickDelta); state.orientation = new Quaternionf(camera.getRotation());
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousProjectionType = RenderSystem.getProjectionType();
        var previousFog = RenderSystem.getShaderFog();
        var previousLights = RenderSystem.getShaderLights();
        var previousGlobals = RenderSystem.getGlobalSettingsUniform();
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        try {
            rendering = true;
            mc.gameRenderer.getEntityRenderStates().cameraRenderState = state;
            globals.set(width, height, mc.options.getGlintStrength().getValue(), mc.world.getTime(), tickCounter,
                    mc.options.getMenuBackgroundBlurrinessValue(), camera, mc.options.getTextureFiltering().getValue() == TextureFilteringMode.RGSS);
            Matrix4f matrix = new Matrix4f().perspective((float) Math.toRadians(114), (float) width / height, .05f, mc.gameRenderer.getFarPlaneDistance());
            Matrix4f view = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));
            RenderSystem.setProjectionMatrix(projection.set(matrix), ProjectionType.PERSPECTIVE);
            var fogColor = fog.applyFog(camera, mc.options.getClampedViewDistance(), tickCounter, mc.gameRenderer.getSkyDarkness(tickDelta), mc.world);
            mc.worldRenderer.render(pool, tickCounter, false, camera, view, matrix, matrix, fog.getFogBuffer(FogRenderer.FogType.WORLD), fogColor,
                    !mc.inGameHud.getBossBarHud().shouldThickenFog());
            pool.decrementLifespan(); fog.rotate();
        } catch (RuntimeException error) {
            failed = true;
            Client.logger.error("Jello RearView pass failed; disabled until the world changes", error);
        } finally {
            rendering = false;
            mc.gameRenderer.getEntityRenderStates().cameraRenderState = previousCameraState;
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            RenderSystem.setShaderFog(previousFog); RenderSystem.setShaderLights(previousLights); RenderSystem.setGlobalSettingsUniform(previousGlobals);
            RenderSystem.outputColorTextureOverride = previousColor; RenderSystem.outputDepthTextureOverride = previousDepth;
            mc.getEntityRenderDispatcher().configure(mainCamera, mc.targetedEntity);
            mc.getBlockEntityRenderDispatcher().configure(mainCamera);
        }
    }

    /** Submitted outside Omix's cached HUD so a framebuffer resize never leaves a stale texture view. */
    public void draw(DrawContext context) {
        HUD hud = SigmaHud.active();
        if (failed || hud == null || !hud.getSigmaRearView().getValue() || framebuffer == null || progress <= 0 || mc.options.hudHidden) return;
        SigmaDraw.begin(context);
        try {
            int width = hud.getSigmaRearViewSize().getValue().intValue();
            int height = width * SigmaDraw.height() / SigmaDraw.width();
            float p = visible ? SigmaAnimation.bezier(progress, .3, .88, .47, 1) : SigmaAnimation.bezier(progress, .49, .59, .16, 1.04);
            int x = SigmaDraw.width() - 10 - width, y = SigmaDraw.height() - (int) ((height + 10) * p);
            SigmaDraw.shadow(context, x, y, width, height - 1, 14, progress);
            var texture = TextureSetup.of(framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            context.state.addSimpleElement(new TexturedQuadGuiElementRenderState(SigmaBlur.OPAQUE_TEXTURE, texture, new Matrix3x2f(context.getMatrices()),
                    x, y, x + width, y + height, 0, 1, 1, 0, -1, context.scissorStack.peekLast()));
        } finally { SigmaDraw.end(context); }
    }
    public void release() {
        if (framebuffer != null) { framebuffer.delete(); framebuffer = null; }
        if (projection != null) { projection.close(); projection = null; fog.close(); fog = null; globals.close(); globals = null; pool.close(); pool = null; }
        progress = 0; lastRender = 0; animation.reset();
    }
    private static final class RearCamera extends Camera {
        private net.minecraft.world.attribute.EnvironmentAttributeInterpolator environment;
        void copyFrom(Camera main, float tickDelta) {
            update(mc.world, main.getFocusedEntity(), false, false, tickDelta);
            setPos(main.getCameraPos()); setRotation(main.getYaw() + 180, main.getPitch());
            // 1.21.11 moved sky, clouds and fog into the camera's environment interpolator.
            // This camera is not ticked by GameRenderer; the main camera has the same position.
            environment = main.getEnvironmentAttributeInterpolator();
        }
        @Override public net.minecraft.world.attribute.EnvironmentAttributeInterpolator getEnvironmentAttributeInterpolator() {
            return environment == null ? super.getEnvironmentAttributeInterpolator() : environment;
        }
    }
}
