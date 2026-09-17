package im.webui.screen;

import im.webui.WebUiRuntime;
import cn.omix.util.webui.WebPanelLayout;
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
    private boolean closing;

    public WebUiScreen(Screen parent, WebScreenType type) {
        super(Text.literal("Omix WebUI — " + type.routeName()));
        this.parent = parent;
        this.type = type;
    }

    public WebScreenType getType() {
        return type;
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
        boolean ai = type.equals(WebScreenType.AI);
        boolean panel = music || ai;
        WebPanelLayout panelLayout = panel ? WebPanelLayout.current() : null;
        if (panel) {
            renderPanelBackdrop(context);
            renderPanelBase(context, panelLayout);
        }
        if (runtime.isBrowserTextureReady()) {
            runtime.render(context);
            return;
        }

        if (!panel) {
            context.fill(0, 0, width, height, 0xFF101218);
        }
        boolean failed = runtime.getState() == im.webui.WebUiState.FAILED
                || (music && runtime.getMusicRuntime().getState() == im.music.MusicServiceState.FAILED)
                || (ai && runtime.getAiFailure() != null);
        String status = failed
                ? (ai ? "Harness failed — R retry, Esc close" : music
                    ? "Music service failed — R retry, Shift+R re-download"
                    : "WebUI failed — press Esc")
                : (music ? "Loading Omix Music…" : ai ? "Loading DeepSeek Harness…" : "Loading WebUI…");
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(status),
                panel ? panelCenterX(panelLayout) : width / 2,
                (panel ? panelCenterY(panelLayout) : height / 2) - 16,
                failed ? 0xFFFF7777 : 0xFFFFFFFF
        );

        if (failed) {
            Throwable failure = ai && runtime.getAiFailure() != null ? runtime.getAiFailure() : music
                    ? runtime.getMusicRuntime().getFailure()
                    : runtime.getFailure();
            String detail = failure == null || failure.getMessage() == null
                    ? runtime.getState().name()
                    : failure.getMessage();
            int detailWidth = Math.max(1, (panel ? panelGuiWidth(panelLayout) : width) - 32);
            int centerX = panel ? panelCenterX(panelLayout) : width / 2;
            int detailY = (panel ? panelCenterY(panelLayout) : height / 2) + 2;
            int bottom = panel
                    ? (int) Math.round((panelLayout.y() + panelLayout.height()) / client.getWindow().getScaleFactor()) - 12
                    : height - 12;
            var lines = textRenderer.wrapLines(Text.literal(detail), detailWidth);
            int limit = Math.max(1, Math.min(8, (bottom - detailY) / (textRenderer.fontHeight + 2)));
            for (int i = 0; i < Math.min(lines.size(), limit); i++) {
                if (i == limit - 1 && lines.size() > limit) {
                    context.drawCenteredTextWithShadow(textRenderer, Text.literal("…"), centerX, detailY, 0xFFFFAAAA);
                } else {
                    context.drawCenteredTextWithShadow(textRenderer, lines.get(i), centerX, detailY, 0xFFFFAAAA);
                }
                detailY += textRenderer.fontHeight + 2;
            }
            return;
        }

        BrowserPreparationProgress progress = music
                ? runtime.getMusicRuntime().getProgress()
                : ai && runtime.getState() == im.webui.WebUiState.READY
                    ? runtime.getAiRuntime().getProgress() : runtime.getPreparationProgress();
        String detail = music || (ai && runtime.getState() == im.webui.WebUiState.READY)
                ? progressDetail(progress) : preparationDetail(runtime, progress);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(detail),
                panel ? panelCenterX(panelLayout) : width / 2,
                (panel ? panelCenterY(panelLayout) : height / 2) + 2,
                0xFFB8C0D9
        );

        if (progress.progress() >= 0.0F) {
            int availableWidth = panel ? panelGuiWidth(panelLayout) : width;
            int barWidth = Math.min(240, Math.max(120, availableWidth / 3));
            int barX = (panel ? panelCenterX(panelLayout) : width / 2) - barWidth / 2;
            int barY = (panel ? panelCenterY(panelLayout) : height / 2) + 20;
            context.fill(barX, barY, barX + barWidth, barY + 4, 0xFF303541);
            context.fill(
                    barX,
                    barY,
                    barX + Math.round(barWidth * progress.progress()),
                    barY + 4,
                    0xFF55A8FF
            );
        }
    }

    @Override
    public void close() {
        WebUiRuntime runtime = WebUiRuntime.getInstance();
        if (type.equals(WebScreenType.CLICK_GUI) && runtime.isBrowserTextureReady()) {
            if (closing) {
                return;
            }
            closing = true;
            runtime.beginScreenCloseAnimation();
            long closeDelay = 300L;
            CompletableFuture.delayedExecutor(closeDelay, TimeUnit.MILLISECONDS)
                    .execute(() -> MinecraftClient.getInstance().execute(this::finishClose));
            return;
        }
        finishClose();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (type.equals(WebScreenType.AI) && input.key() == GLFW.GLFW_KEY_R
                && WebUiRuntime.getInstance().getAiFailure() != null) {
            WebUiRuntime.getInstance().restartAi();
            return true;
        }
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

    private void renderPanelBackdrop(DrawContext context) {
        // Minecraft invokes renderBackground before Screen.render. Calling it here
        // again crashes 1.21.11 with "Can only blur once per frame".
        context.fill(0, 0, width, height, 0x52000000);
    }

    private void renderPanelBase(DrawContext context, WebPanelLayout layout) {
        double scale = client.getWindow().getScaleFactor();
        int panelX = (int) Math.round(layout.x() / scale);
        int panelY = (int) Math.round(layout.y() / scale);
        int panelWidth = panelGuiWidth(layout);
        int panelHeight = (int) Math.round(layout.height() / scale);
        int panelRadius = (int) Math.round(layout.cornerRadius() / scale);
        fillRoundedRect(
                context,
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                panelRadius,
                0xFF202124
        );
    }

    private static void fillRoundedRect(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int color
    ) {
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (safeRadius == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }

        context.fill(x, y + safeRadius, x + width, y + height - safeRadius, color);
        for (int row = 0; row < safeRadius; row++) {
            double distanceFromCenter = safeRadius - row - 0.5D;
            int inset = (int) Math.ceil(
                    safeRadius - Math.sqrt(
                            safeRadius * (double) safeRadius
                                    - distanceFromCenter * distanceFromCenter
                    )
            );
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            context.fill(
                    x + inset,
                    y + height - row - 1,
                    x + width - inset,
                    y + height - row,
                    color
            );
        }
    }

    private int panelCenterX(WebPanelLayout layout) {
        double scale = client.getWindow().getScaleFactor();
        return (int) Math.round((layout.x() + layout.width() / 2.0D) / scale);
    }

    private int panelCenterY(WebPanelLayout layout) {
        double scale = client.getWindow().getScaleFactor();
        return (int) Math.round((layout.y() + layout.height() / 2.0D) / scale);
    }

    private int panelGuiWidth(WebPanelLayout layout) {
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
