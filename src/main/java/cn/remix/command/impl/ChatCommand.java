package cn.remix.command.impl;

import ai.backend.AiBackend;
import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.util.Util;
import injection.accessor.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class ChatCommand extends Command {

    public ChatCommand() {
        super(".chat <message>", "chat");
    }

    @Override
    public void execute(String[] arguments) {
        if (arguments.length < 2) {
            Util.logToChat(getUsage());
            return;
        }

        AiBackend backend = Client.instance.getAiBackend();
        if (backend.isChatActive()) {
            Util.logToChat("&cAnother AI response is still streaming.");
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length)).trim();
        StreamingChatMessage output = new StreamingChatMessage(MinecraftClient.getInstance());
        backend.streamChat(message, output::append)
                .whenComplete((response, error) -> output.finish(response, error));
    }

    private static final class StreamingChatMessage {
        private final MinecraftClient client;
        private final ChatHud chatHud;
        private final MutableText text;
        private final StringBuilder pending = new StringBuilder();

        private boolean updateScheduled;
        private volatile boolean receivedContent;

        private StreamingChatMessage(MinecraftClient client) {
            this.client = client;
            this.chatHud = client.inGameHud.getChatHud();
            this.text = Text.empty()
                    .append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("AI").formatted(Formatting.AQUA))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
            this.chatHud.addMessage(text);
        }

        private synchronized void append(String content) {
            if (content == null || content.isEmpty()) {
                return;
            }
            pending.append(content);
            receivedContent = true;
            if (!updateScheduled) {
                updateScheduled = true;
                client.execute(this::flush);
            }
        }

        private void finish(String response, Throwable error) {
            client.execute(() -> {
                flush();
                if (error != null) {
                    text.append(Text.literal("\nError: " + errorMessage(error)).formatted(Formatting.RED));
                } else if (!receivedContent && (response == null || response.isEmpty())) {
                    text.append(Text.literal("(empty response)").formatted(Formatting.GRAY));
                }
                refresh();
            });
        }

        private void flush() {
            final String content;
            synchronized (this) {
                content = pending.toString();
                pending.setLength(0);
                updateScheduled = false;
            }
            if (!content.isEmpty()) {
                text.append(Text.literal(content).formatted(Formatting.WHITE));
                refresh();
            }
        }

        private void refresh() {
            ((ChatHudAccessor) chatHud).remix$refresh();
        }

        private static String errorMessage(Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null
                    && (cause instanceof CompletionException || cause instanceof ExecutionException)) {
                cause = cause.getCause();
            }
            return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        }
    }
}
