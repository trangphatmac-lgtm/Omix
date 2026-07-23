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
        super(".ai <baseurl/apikey/model> [value]", "ai");
    }

    @Override
    public void execute(String[] arguments) {
        AiBackend backend = Client.instance.getAiBackend();
        if (arguments.length == 1) {
            showConfiguration(backend);
            return;
        }

        String setting = arguments[1].toLowerCase(Locale.ROOT);
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
                default -> Util.logToChat(getUsage());
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Util.logToChat("&c" + exception.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] arguments) {
        if (arguments.length == 2) {
            return List.of("baseurl", "apikey", "model");
        }
        if (arguments.length == 3) {
            return switch (arguments[1].toLowerCase(Locale.ROOT)) {
                case "baseurl" -> List.of("https://api.deepseek.com");
                case "model" -> Client.instance.getAiBackend().getModelSuggestions();
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
    }

    private void showSetting(AiBackend backend, String setting) {
        switch (setting) {
            case "baseurl" -> Util.logToChat("AI base URL: &b" + backend.getBaseUrl());
            case "apikey" -> Util.logToChat("AI API key: "
                    + (backend.hasApiKey() ? "&aconfigured" : "&cnot configured"));
            case "model" -> Util.logToChat("AI model: &b" + backend.getModel());
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
