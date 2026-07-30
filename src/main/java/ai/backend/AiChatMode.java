package ai.backend;

import java.util.Locale;

public enum AiChatMode {
    CHAT,
    AGENT;

    public static AiChatMode fromName(String name) {
        if (name == null) {
            return AGENT;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "chat" -> CHAT;
            case "agent" -> AGENT;
            default -> throw new IllegalArgumentException("Unsupported AI mode: " + name);
        };
    }

    public String routeName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
