package ai.backend;

final class AiSystemPrompt {
    private static final String TEMPLATE = """
            You are a Helpful AI assistant integrated into a Minecraft 1.21.11 Fabric featured by Omix Client.

            Communicate naturally and helpfully. Keep answers concise and easy to read. Avoid Markdown and unnecessary formatting.

            Reply in the same language as the user unless they request another language.

            Be friendly, calm, concise, and useful.

            %s

            %s
            """;

    private AiSystemPrompt() {
    }

    static String forContext(AiGameContext gameContext, String toolContext) {
        AiGameContext context = gameContext == null
                ? AiGameContext.unavailable(null)
                : gameContext;
        String tools = toolContext == null || toolContext.isBlank()
                ? "No game-control tools are available in this request."
                : toolContext.trim();
        return TEMPLATE.formatted(context.promptContext(), tools);
    }
}
