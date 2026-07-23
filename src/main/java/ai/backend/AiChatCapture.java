package ai.backend;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class AiChatCapture {
    private static final int MAX_MESSAGES = 100;
    private static final int MAX_CHARACTERS = 16_000;

    private static Capture active;

    private AiChatCapture() {
    }

    static synchronized Capture begin() {
        if (active != null) {
            throw new IllegalStateException("Another command response capture is already active.");
        }
        active = new Capture();
        return active;
    }

    public static synchronized void record(Text text) {
        if (active == null || text == null) {
            return;
        }
        active.add(text.getString());
    }

    static synchronized String finish(Capture capture) {
        if (active != capture) {
            return "Command response capture ended unexpectedly.";
        }
        active = null;
        return capture.result();
    }

    static final class Capture {
        private final List<String> messages = new ArrayList<>();
        private int characters;
        private boolean truncated;

        private void add(String message) {
            if (message == null || message.isEmpty()) {
                return;
            }
            if (messages.size() >= MAX_MESSAGES || characters + message.length() > MAX_CHARACTERS) {
                truncated = true;
                return;
            }
            messages.add(message);
            characters += message.length();
        }

        private String result() {
            if (messages.isEmpty()) {
                return "(No new chat messages appeared within 0.5 seconds.)";
            }
            String result = String.join("\n", messages);
            return truncated ? result + "\n(Chat output was truncated.)" : result;
        }
    }
}
