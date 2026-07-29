package im.webui.screen;

import im.webui.WebUiRuntime;
import im.webui.backend.BrowserPreparationProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class WebUiScreen extends Screen {
    private final Screen parent;
    private final WebScreenType type;
    private volatile boolean backgroundBlurEnabled = true;
    private boolean closing;

    public WebUiScreen(Screen parent, WebScreenType type) {
        super(Text.literal("Omix WebUI — " + type.routeName()));
        this.parent = parent;
        this.type = type;
    }

    public WebScreenType getType() {
        return type;
    }

    public void setBackgroundBlurEnabled(boolean backgroundBlurEnabled) {
        this.backgroundBlurEnabled = backgroundBlurEnabled;
    }

    @Override
    public void tick() {
        super.tick();
        if (type.equals(WebScreenType.AI)) {
            releaseMovementKeys();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        WebUiRuntime runtime = WebUiRuntime.getInstance();
        boolean music = type.equals(WebScreenType.MUSIC);
        MusicPanelLayout musicLayout = music ? MusicPanelLayout.current() : null;
        if (music) {
            renderMusicBackdrop(context, mouseX, mouseY, deltaTicks);
            renderMusicPanelBase(context, musicLayout);
        }
        if (runtime.isBrowserTextureReady()) {
            runtime.render(context);
            if (music) {
                renderMusicPanelChrome(context, musicLayout);
            }
            return;
        }

        if (!music) {
            context.fill(0, 0, width, height, 0xFF101218);
        }
        boolean failed = runtime.getState() == im.webui.WebUiState.FAILED
                || (music && runtime.getMusicRuntime().getState() == im.music.MusicServiceState.FAILED);
        String status = failed
                ? (music
                    ? "Music service failed — R retry, Shift+R re-download"
                    : "WebUI failed — press Esc")
                : (music ? "Loading Omix Music…" : "Loading WebUI…");
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(status),
                music ? panelCenterX(musicLayout) : width / 2,
                (music ? panelCenterY(musicLayout) : height / 2) - 16,
                failed ? 0xFFFF7777 : 0xFFFFFFFF
        );

        if (failed) {
            Throwable failure = music
                    ? runtime.getMusicRuntime().getFailure()
                    : runtime.getFailure();
            String detail = failure == null || failure.getMessage() == null
                    ? runtime.getState().name()
                    : failure.getMessage();
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(detail),
                    music ? panelCenterX(musicLayout) : width / 2,
                    (music ? panelCenterY(musicLayout) : height / 2) + 2,
                    0xFFFFAAAA
            );
            if (music) {
                renderMusicPanelChrome(context, musicLayout);
            }
            return;
        }

        BrowserPreparationProgress progress = music
                ? runtime.getMusicRuntime().getProgress()
                : runtime.getPreparationProgress();
        String detail = music ? progressDetail(progress) : preparationDetail(runtime, progress);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(detail),
                music ? panelCenterX(musicLayout) : width / 2,
                (music ? panelCenterY(musicLayout) : height / 2) + 2,
                0xFFB8C0D9
        );

        if (progress.progress() >= 0.0F) {
            int availableWidth = music ? panelGuiWidth(musicLayout) : width;
            int barWidth = Math.min(240, Math.max(120, availableWidth / 3));
            int barX = (music ? panelCenterX(musicLayout) : width / 2) - barWidth / 2;
            int barY = (music ? panelCenterY(musicLayout) : height / 2) + 20;
            context.fill(barX, barY, barX + barWidth, barY + 4, 0xFF303541);
            context.fill(
                    barX,
                    barY,
                    barX + Math.round(barWidth * progress.progress()),
                    barY + 4,
                    0xFF55A8FF
            );
        }
        if (music) {
            renderMusicPanelChrome(context, musicLayout);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (!type.equals(WebScreenType.AI) || backgroundBlurEnabled) {
            super.renderBackground(context, mouseX, mouseY, deltaTicks);
            return;
        }
        if (deferSubtitles()) {
            renderInGameBackground(context);
        } else {
            if (client.world == null) {
                renderPanoramaBackground(context, deltaTicks);
            }
            renderDarkening(context);
        }
        client.inGameHud.renderDeferredSubtitles();
    }

    @Override
    public void close() {
        WebUiRuntime runtime = WebUiRuntime.getInstance();
        if ((type.equals(WebScreenType.AI) || type.equals(WebScreenType.CLICK_GUI))
                && runtime.isBrowserTextureReady()) {
            if (closing) {
                return;
            }
            closing = true;
            runtime.beginScreenCloseAnimation();
            long closeDelay = type.equals(WebScreenType.CLICK_GUI) ? 300L : 420L;
            CompletableFuture.delayedExecutor(closeDelay, TimeUnit.MILLISECONDS)
                    .execute(() -> MinecraftClient.getInstance().execute(this::finishClose));
            return;
        }
        finishClose();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (type.equals(WebScreenType.MUSIC)
                && input.key() == GLFW.GLFW_KEY_R
                && WebUiRuntime.getInstance().getMusicRuntime().getState()
                    == im.music.MusicServiceState.FAILED) {
            WebUiRuntime runtime = WebUiRuntime.getInstance();
            boolean clearRuntime = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            var retry = clearRuntime
                    ? runtime.getMusicRuntime().clearRuntimeAndRetryAsync()
                    : runtime.getMusicRuntime().retryAsync();
            retry.whenComplete((ignored, failure) -> {
                if (failure == null) {
                    MinecraftClient.getInstance().execute(runtime::openMusicScreen);
                }
            });
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return closing || super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void finishClose() {
        WebUiRuntime.getInstance().closeScreen();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == this) {
            client.setScreen(parent);
        }
    }

    private static void releaseMovementKeys() {
        var options = MinecraftClient.getInstance().options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
    }

    private void renderMusicBackdrop(
            DrawContext context,
            int mouseX,
            int mouseY,
            float deltaTicks
    ) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.fill(0, 0, width, height, 0x52000000);
    }

    private void renderMusicPanelBase(DrawContext context, MusicPanelLayout layout) {
        double scale = client.getWindow().getScaleFactor();
        int panelX = (int) Math.round(layout.x() / scale);
        int panelY = (int) Math.round(layout.y() / scale);
        int panelWidth = panelGuiWidth(layout);
        int panelHeight = (int) Math.round(layout.height() / scale);
        int shadow = Math.max(2, (int) Math.round(4.0D));

        fillSoftRectangle(
                context,
                panelX - shadow,
                panelY - shadow,
                panelWidth + shadow * 2,
                panelHeight + shadow * 2,
                0x66000000
        );
        fillSoftRectangle(
                context,
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                0xFF202124
        );
    }

    private void renderMusicPanelChrome(DrawContext context, MusicPanelLayout layout) {
        double scale = client.getWindow().getScaleFactor();
        int panelX = (int) Math.round(layout.x() / scale);
        int panelY = (int) Math.round(layout.y() / scale);
        int panelWidth = panelGuiWidth(layout);
        int panelHeight = (int) Math.round(layout.height() / scale);
        int chromeHeight = Math.max(5, (int) Math.round(layout.chromeHeight() / scale));

        context.fill(
                panelX + 2,
                panelY + chromeHeight - 1,
                panelX + panelWidth - 2,
                panelY + chromeHeight,
                0xFF303238
        );
        drawTrafficLight(context, panelX + 12, panelY + chromeHeight / 2, 0xFFE06C5F);
        drawTrafficLight(context, panelX + 22, panelY + chromeHeight / 2, 0xFFE8BE55);
        drawTrafficLight(context, panelX + 32, panelY + chromeHeight / 2, 0xFF70BC62);

        int outline = 0xFF4A4D55;
        context.fill(panelX + 3, panelY, panelX + panelWidth - 3, panelY + 1, outline);
        context.fill(
                panelX + 3,
                panelY + panelHeight - 1,
                panelX + panelWidth - 3,
                panelY + panelHeight,
                outline
        );
        context.fill(panelX, panelY + 3, panelX + 1, panelY + panelHeight - 3, outline);
        context.fill(
                panelX + panelWidth - 1,
                panelY + 3,
                panelX + panelWidth,
                panelY + panelHeight - 3,
                outline
        );
    }

    private static void fillSoftRectangle(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (width <= 4 || height <= 4) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x + 2, y, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    private static void drawTrafficLight(
            DrawContext context,
            int centerX,
            int centerY,
            int color
    ) {
        context.fill(centerX - 2, centerY - 3, centerX + 2, centerY + 4, color);
        context.fill(centerX - 3, centerY - 2, centerX + 3, centerY + 3, color);
    }

    private int panelCenterX(MusicPanelLayout layout) {
        double scale = client.getWindow().getScaleFactor();
        return (int) Math.round((layout.x() + layout.width() / 2.0D) / scale);
    }

    private int panelCenterY(MusicPanelLayout layout) {
        double scale = client.getWindow().getScaleFactor();
        return (int) Math.round((layout.y() + layout.height() / 2.0D) / scale);
    }

    private int panelGuiWidth(MusicPanelLayout layout) {
        return (int) Math.round(layout.width() / client.getWindow().getScaleFactor());
    }

    private static String preparationDetail(
            WebUiRuntime runtime,
            BrowserPreparationProgress progress
    ) {
        if (runtime.getState() != im.webui.WebUiState.CEF_PREPARING) {
            return runtime.getState().name();
        }
        if (progress.totalBytes() > 0L) {
            double downloaded = progress.bytesRead() / 1024.0D / 1024.0D;
            double total = progress.totalBytes() / 1024.0D / 1024.0D;
            return "%s — %.1f / %.1f MB".formatted(progress.task(), downloaded, total);
        }
        if (progress.progress() >= 0.0F) {
            return "%s — %d%%".formatted(progress.task(), Math.round(progress.progress() * 100.0F));
        }
        return progress.task();
    }

    private static String progressDetail(BrowserPreparationProgress progress) {
        if (progress.totalBytes() > 0L) {
            double downloaded = progress.bytesRead() / 1024.0D / 1024.0D;
            double total = progress.totalBytes() / 1024.0D / 1024.0D;
            return "%s — %.1f / %.1f MB".formatted(progress.task(), downloaded, total);
        }
        if (progress.progress() >= 0.0F) {
            return "%s — %d%%".formatted(progress.task(), Math.round(progress.progress() * 100.0F));
        }
        return progress.task();
    }
}
