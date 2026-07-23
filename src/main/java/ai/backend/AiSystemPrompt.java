package ai.backend;

final class AiSystemPrompt {
    private static final String TEMPLATE = """
            You are a Helpful AI assistant integrated into a Minecraft client.

            Your current user is %s. Communicate naturally and helpfully, Keep answers concise and easy to read, Avoid Markdown and unnecessary formatting.

            Reply in the same language as the user unless they request another language.
            
            Be friendly, calm, concise, and useful.
            """;

    private AiSystemPrompt() {
    }

    static String forUser(String username) {
        String currentUser = username == null || username.isBlank() ? "Minecraft player" : username.trim();
        return TEMPLATE.formatted(currentUser);
    }
}
