package ai.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

record AiMessage(
        String role,
        String content,
        String reasoningContent,
        List<AiToolCall> toolCalls,
        String toolCallId
) {
    AiMessage {
        if (!role.equals("user") && !role.equals("assistant") && !role.equals("tool")) {
            throw new IllegalArgumentException("Unsupported AI message role: " + role);
        }
        content = content == null ? "" : content;
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolCallId = toolCallId == null ? "" : toolCallId;

        if (role.equals("tool") && toolCallId.isBlank()) {
            throw new IllegalArgumentException("Tool messages require a tool call ID.");
        }
        if (!role.equals("assistant") && (!reasoningContent.isEmpty() || !toolCalls.isEmpty())) {
            throw new IllegalArgumentException("Only assistant messages can contain reasoning or tool calls.");
        }
    }

    static AiMessage user(String content) {
        return new AiMessage("user", content, "", List.of(), "");
    }

    static AiMessage assistant(String content, String reasoningContent, List<AiToolCall> toolCalls) {
        return new AiMessage("assistant", content, reasoningContent, toolCalls, "");
    }

    static AiMessage tool(String toolCallId, String content) {
        return new AiMessage("tool", content, "", List.of(), toolCallId);
    }

    JsonObject toJson() {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        if (!reasoningContent.isEmpty() || (!toolCalls.isEmpty() && role.equals("assistant"))) {
            message.addProperty("reasoning_content", reasoningContent);
        }
        if (!toolCalls.isEmpty()) {
            JsonArray calls = new JsonArray();
            for (AiToolCall toolCall : toolCalls) {
                calls.add(toolCall.toJson());
            }
            message.add("tool_calls", calls);
        }
        if (!toolCallId.isEmpty()) {
            message.addProperty("tool_call_id", toolCallId);
        }
        return message;
    }

    static AiMessage fromJson(JsonObject message) {
        if (!message.has("role") || message.get("role").isJsonNull()) {
            throw new IllegalArgumentException("AI message is missing a role.");
        }

        String role = message.get("role").getAsString();
        String content = stringValue(message, "content");
        String reasoningContent = stringValue(message, "reasoning_content");
        String toolCallId = stringValue(message, "tool_call_id");
        List<AiToolCall> toolCalls = List.of();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            JsonArray array = message.getAsJsonArray("tool_calls");
            java.util.ArrayList<AiToolCall> parsed = new java.util.ArrayList<>();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    parsed.add(AiToolCall.fromJson(element.getAsJsonObject()));
                }
            }
            toolCalls = List.copyOf(parsed);
        }
        return new AiMessage(role, content, reasoningContent, toolCalls, toolCallId);
    }

    private static String stringValue(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString()
                : "";
    }
}
