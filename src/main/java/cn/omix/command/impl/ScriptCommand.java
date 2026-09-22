package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.command.Command;
import cn.omix.util.Util;
import cn.omix.util.script.*;
import java.util.*;

public final class ScriptCommand extends Command {
    public ScriptCommand() { super(".script <open|list|create|check|load|reload|unload|status|logs|mcp> [id] [template]", "script", "scripts"); }
    @Override public void execute(String[] args) {
        try {
            var manager = Client.instance.getScriptManager();
            if (manager == null) throw new IllegalStateException("Script service unavailable");
            String action = args.length < 2 ? "open" : args[1].toLowerCase(Locale.ROOT);
            String id = args.length > 2 ? args[2] : "";
            switch (action) {
                // ChatScreen closes itself after sending the command. send() always queues,
                // whereas execute() runs inline on the client thread and gets overwritten.
                case "open" -> net.minecraft.client.MinecraftClient.getInstance().send(() ->
                        im.webui.WebUiRuntime.getInstance().openScreen(im.webui.screen.WebScreenType.SCRIPTS));
                case "list", "status" -> Util.log(manager.status().toString());
                case "create" -> {
                    String template = args.length > 3 ? args[3] : "Sprint";
                    if (!ScriptReference.templates().contains(template)) throw new IllegalArgumentException("Unknown template: " + template);
                    manager.files().write(id, ScriptReference.read("examples/" + template + ".java"), "");
                    Util.log("Created " + id + ".java; use .script load " + id);
                }
                case "check", "load", "reload" -> {
                    var job = manager.submit(id, action);
                    Util.log("Script job " + job.id + " queued. Open Scripts for diagnostics.");
                }
                case "unload" -> { manager.unload(id); Util.log("Unloaded " + id); }
                case "logs" -> Util.log(manager.log().read(id, 0, 10).toString());
                case "mcp" -> ScriptDistribution.prepareNode(manager.files().root());
                default -> Util.log(getUsage());
            }
        } catch (Exception error) { Util.log("&cScript: " + error.getMessage()); }
    }
    @Override public List<String> getCompletions(String[] arguments) {
        if (arguments.length <= 2) return List.of("open", "list", "create", "check", "load", "reload", "unload", "status", "logs", "mcp");
        try { return Client.instance.getScriptManager().files().list(); } catch (Exception ignored) { return List.of(); }
    }
}
