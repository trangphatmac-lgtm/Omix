package ai.backend;

record AiMessage(String role, String content) {
    AiMessage {
        if (!role.equals("user") && !role.equals("assistant")) {
            throw new IllegalArgumentException("Unsupported AI message role: " + role);
        }
        if (content == null) {
            throw new IllegalArgumentException("AI message content cannot be null.");
        }
    }
}
