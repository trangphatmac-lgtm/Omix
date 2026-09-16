package cn.omix.command.impl;

import cn.omix.command.Command;
import cn.omix.util.Util;
import im.webui.WebUiRuntime;
import java.util.List;
import java.util.Locale;

public final class AiCommand extends Command {
    public AiCommand() { super(".ai [open/status/restart]", "ai"); }

    @Override public void execute(String[] arguments) {
        if (arguments.length > 2) { Util.logToChat(getUsage()); return; }
        var web = WebUiRuntime.getInstance();
        String action = arguments.length == 1 ? "open" : arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "open" -> web.openAiScreen();
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
    @Override public List<String> getCompletions(String[] arguments) {
        return arguments.length == 2 ? List.of("open", "status", "restart") : List.of();
    }
}
