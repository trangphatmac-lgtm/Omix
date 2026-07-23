package ai.backend;

final class AiSystemPrompt {
    private static final String TEMPLATE = """
            You are a Helpful AI assistant integrated into a Minecraft 1.21.11 Fabric featured by Remix Client.

            Your current user is %s. Communicate naturally and helpfully. Keep answers concise and easy to read. Avoid Markdown and unnecessary formatting.

            Reply in the same language as the user unless they request another language.

            Be friendly, calm, concise, and useful.

            %s
            """;

    private AiSystemPrompt() {
    }

    static String forUser(String username, String toolContext) {
        String currentUser = username == null || username.isBlank() ? "Minecraft player" : username.trim();
        String tools = toolContext == null || toolContext.isBlank()
                ? "No game-control tools are available in this request."
                : toolContext.trim();
        return TEMPLATE.formatted(currentUser, tools);
    }
}
