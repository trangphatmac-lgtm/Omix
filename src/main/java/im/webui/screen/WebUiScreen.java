package im.webui.screen;

import im.webui.WebUiRuntime;
import im.webui.backend.BrowserPreparationProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class WebUiScreen extends Screen {
    private final Screen parent;
    private final WebScreenType type;

    public WebUiScreen(Screen parent, WebScreenType type) {
        super(Text.literal("Remix WebUI — " + type.routeName()));
        this.parent = parent;
        this.type = type;
    }

    public WebScreenType getType() {
        return type;
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
    public void close() {
        WebUiRuntime.getInstance().closeTestScreen();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
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
