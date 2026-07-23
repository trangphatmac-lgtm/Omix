package cn.remix.command.impl;

import ai.backend.AiBackend;
import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.util.Util;
import injection.accessor.ChatHudAccessor;
import me.ksyz.accountmanager.auth.SessionService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
        MinecraftClient client = MinecraftClient.getInstance();
        addUserMessage(client, SessionService.current().getUsername(), message);
        StreamingChatMessage output = new StreamingChatMessage(
                client,
                backend.getModel(),
                backend.isThinkingEnabled()
        );
        backend.streamChat(message, output::append)
                .whenComplete((response, error) -> output.finish(response, error));
    }

    private static void addUserMessage(MinecraftClient client, String username, String message) {
        MutableText text = Text.empty()
                .append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(username).formatted(Formatting.AQUA))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(message).formatted(Formatting.WHITE));
        client.inGameHud.getChatHud().addMessage(text);
    }

    private static final class StreamingChatMessage {
        private final MinecraftClient client;
        private final ChatHud chatHud;
        private final MutableText text;
        private final String model;
        private final boolean thinkingEnabled;
        private final long thinkingStartedAt;
        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder response = new StringBuilder();

        private boolean updateScheduled;
        private boolean answerStarted;
        private boolean finished;

        private StreamingChatMessage(MinecraftClient client, String model, boolean thinkingEnabled) {
            this.client = client;
            this.chatHud = client.inGameHud.getChatHud();
            this.model = model;
            this.thinkingEnabled = thinkingEnabled;
            this.thinkingStartedAt = System.nanoTime();
            this.text = Text.empty();
            setDisplayedContent(thinkingEnabled ? "Think...(0s)" : "", Formatting.GRAY);
            this.chatHud.addMessage(text);
            if (thinkingEnabled) {
                scheduleThinkingUpdate();
            }
        }

        private synchronized void append(String content) {
            if (content == null || content.isEmpty()) {
                return;
            }
            pending.append(content);
            answerStarted = true;
            if (!updateScheduled) {
                updateScheduled = true;
                client.execute(this::flush);
            }
        }

        private void finish(String response, Throwable error) {
            client.execute(() -> {
                final String answer;
                synchronized (this) {
                    this.response.append(pending);
                    pending.setLength(0);
                    updateScheduled = false;
                    finished = true;
                    answer = this.response.toString();
                }
                if (error != null) {
                    setDisplayedContent("Error: " + errorMessage(error), Formatting.RED);
                } else if (answer.isEmpty() && (response == null || response.isEmpty())) {
                    setDisplayedContent("(empty response)", Formatting.GRAY);
                } else {
                    setDisplayedContent(answer, Formatting.WHITE);
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
                response.append(content);
            }
            if (!content.isEmpty()) {
                setDisplayedContent(response.toString(), Formatting.WHITE);
                refresh();
            }
        }

        private void scheduleThinkingUpdate() {
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
                synchronized (this) {
                    if (finished || answerStarted) {
                        return;
                    }
                }
                client.execute(this::renderThinking);
                scheduleThinkingUpdate();
            });
        }

        private void renderThinking() {
            synchronized (this) {
                if (finished || answerStarted || !thinkingEnabled) {
                    return;
                }
            }
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - thinkingStartedAt);
            setDisplayedContent("Think...(" + seconds + "s)", Formatting.GRAY);
            refresh();
        }

        private void setDisplayedContent(String content, Formatting formatting) {
            text.getSiblings().clear();
            text.append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(model).formatted(Formatting.AQUA))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
            if (!content.isEmpty()) {
                text.append(Text.literal(content).formatted(formatting));
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
