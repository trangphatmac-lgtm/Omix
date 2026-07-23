package cn.remix.command.impl;

import ai.backend.AiBackend;
import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.util.Util;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AiCommand extends Command {

    public AiCommand() {
        super(".ai <baseurl/apikey/model/think/clear> [value]", "ai");
    }

    @Override
    public void execute(String[] arguments) {
        AiBackend backend = Client.instance.getAiBackend();
        if (arguments.length == 1) {
            showConfiguration(backend);
            return;
        }

        String setting = arguments[1].toLowerCase(Locale.ROOT);
        if (setting.equals("clear")) {
            if (arguments.length != 2) {
                Util.logToChat(getUsage());
                return;
            }
            try {
                int cleared = backend.clearConversation();
                Util.logToChat("Cleared &b" + cleared + " &fmessage(s) from the AI context.");
            } catch (IllegalStateException exception) {
                Util.logToChat("&c" + exception.getMessage());
            }
            return;
        }
        if (arguments.length == 2) {
            showSetting(backend, setting);
            return;
        }

        String value = String.join(" ", Arrays.copyOfRange(arguments, 2, arguments.length)).trim();
        try {
            switch (setting) {
                case "baseurl" -> {
                    backend.setBaseUrl(value);
                    Util.logToChat("AI base URL has been set to &b" + backend.getBaseUrl());
                    if (backend.hasApiKey()) {
                        refreshModels(backend);
                    }
                }
                case "apikey" -> {
                    boolean clear = value.equalsIgnoreCase("clear");
                    backend.setApiKey(clear ? "" : value);
                    Util.logToChat(clear ? "AI API key has been cleared." : "AI API key has been updated.");
                    if (!clear) {
                        refreshModels(backend);
                    }
                }
                case "model" -> {
                    backend.setModel(value);
                    Util.logToChat("AI model has been set to &b" + backend.getModel());
                }
                case "think" -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException("Think must be True or False.");
                    }
                    backend.setThinking(Boolean.parseBoolean(value));
                    Util.logToChat("AI thinking mode has been set to "
                            + (backend.isThinkingEnabled() ? "&atrue" : "&cfalse") + ".");
                }
                default -> Util.logToChat(getUsage());
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Util.logToChat("&c" + exception.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] arguments) {
        if (arguments.length == 2) {
            return List.of("baseurl", "apikey", "model", "think", "clear");
        }
        if (arguments.length == 3) {
            return switch (arguments[1].toLowerCase(Locale.ROOT)) {
                case "baseurl" -> List.of("https://api.deepseek.com");
                case "model" -> Client.instance.getAiBackend().getModelSuggestions();
                case "think" -> List.of("True", "False");
                default -> List.of();
            };
        }
        return List.of();
    }

    private void showConfiguration(AiBackend backend) {
        Util.logToChat("&fAI configuration:");
        Util.logRaw("&8» &7baseurl: &b" + backend.getBaseUrl());
        Util.logRaw("&8» &7apikey: " + (backend.hasApiKey() ? "&aconfigured" : "&cnot configured"));
        Util.logRaw("&8» &7model: &b" + backend.getModel());
        Util.logRaw("&8» &7think: " + (backend.isThinkingEnabled() ? "&atrue" : "&cfalse"));
        Util.logRaw("&8» &7context: &b" + backend.getConversationMessageCount() + " &7message(s)");
    }

    private void showSetting(AiBackend backend, String setting) {
        switch (setting) {
            case "baseurl" -> Util.logToChat("AI base URL: &b" + backend.getBaseUrl());
            case "apikey" -> Util.logToChat("AI API key: "
                    + (backend.hasApiKey() ? "&aconfigured" : "&cnot configured"));
            case "model" -> Util.logToChat("AI model: &b" + backend.getModel());
            case "think" -> Util.logToChat("AI thinking mode: "
                    + (backend.isThinkingEnabled() ? "&atrue" : "&cfalse"));
            default -> Util.logToChat(getUsage());
        }
    }

    private void refreshModels(AiBackend backend) {
        backend.refreshModels().whenComplete((models, error) ->
                MinecraftClient.getInstance().execute(() -> {
                    if (error == null) {
                        Util.logToChat("Loaded &b" + models.size() + " &fAI model(s).");
                    } else {
                        Util.logToChat("&cFailed to load AI models: " + errorMessage(error));
                    }
                }));
    }

    private static String errorMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null
                && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
