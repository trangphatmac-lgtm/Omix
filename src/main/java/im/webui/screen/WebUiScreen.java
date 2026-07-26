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
        super(Text.literal("Remix WebUI — " + type.routeName()));
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
        if (runtime.isBrowserTextureReady()) {
            runtime.render(context);
            return;
        }

        context.fill(0, 0, width, height, 0xFF101218);
        boolean failed = runtime.getState() == im.webui.WebUiState.FAILED;
        String status = failed ? "WebUI failed — press Esc" : "Loading WebUI…";
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(status),
                width / 2,
                height / 2 - 16,
                failed ? 0xFFFF7777 : 0xFFFFFFFF
        );

        if (failed) {
            Throwable failure = runtime.getFailure();
            String detail = failure == null || failure.getMessage() == null
                    ? runtime.getState().name()
                    : failure.getMessage();
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(detail),
                    width / 2,
                    height / 2 + 2,
                    0xFFFFAAAA
            );
            return;
        }

        BrowserPreparationProgress progress = runtime.getPreparationProgress();
        String detail = preparationDetail(runtime, progress);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(detail),
                width / 2,
                height / 2 + 2,
                0xFFB8C0D9
        );

        if (progress.progress() >= 0.0F) {
            int barWidth = Math.min(240, Math.max(120, width / 3));
            int barX = (width - barWidth) / 2;
            int barY = height / 2 + 20;
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
}
