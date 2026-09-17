package cn.omix.command.impl;

import cn.omix.command.Command;
import cn.omix.util.Util;
import cn.omix.util.ai.HarnessRuntime;
import im.webui.WebUiRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public final class AiCommand extends Command {
    public AiCommand() { super(".ai [open/status/restart]", "ai"); }

    @Override public void execute(String[] arguments) {
        if (arguments.length > 2) { Util.logToChat(getUsage()); return; }
        var web = WebUiRuntime.getInstance();
        String action = arguments.length == 1 ? "open" : arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "open" -> sendHarnessLink(web);
            case "restart" -> {
                web.restartAi();
                Util.logToChat("Restarting DeepSeek Harness…");
            }
            case "status" -> {
                var runtime = web.getAiRuntime();
                Util.logToChat("DeepSeek Harness: " + runtime.getState() + " — " + runtime.getProgress().task());
                if (web.getAiFailure() != null) Util.logToChat("&c" + web.getAiFailure().getMessage());
            }
            default -> Util.logToChat(getUsage() + " — Configure models in the Harness Web UI.");
        }
    }

    private void sendHarnessLink(WebUiRuntime web) {
        try {
            var runtime = web.getAiRuntime();
            if (runtime.getState() != HarnessRuntime.State.READY) {
                Util.logToChat("Starting DeepSeek Harness… The browser link will appear when ready.");
            }
            runtime.startAsync().whenComplete((url, error) -> MinecraftClient.getInstance().execute(() -> {
                if (error != null) {
                    Util.logToChat("&cHarness is unavailable: " + error.getMessage());
                    return;
                }
                // Keep the authentication token in the click action, out of visible chat/log text.
                Text link = Text.literal(url.resolve("/").toString()).styled(style -> style
                        .withColor(Formatting.AQUA)
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent.OpenUrl(url)));
                Util.logToChat(Text.empty()
                        .append(Text.literal("Harness: ").formatted(Formatting.WHITE))
                        .append(link));
            }));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Util.logToChat("&cHarness is unavailable: " + exception.getMessage());
        }
    }

    @Override public List<String> getCompletions(String[] arguments) {
        return arguments.length == 2 ? List.of("open", "status", "restart") : List.of();
    }
}
