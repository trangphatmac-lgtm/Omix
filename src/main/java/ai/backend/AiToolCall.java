package ai.backend;

import com.google.gson.JsonObject;

record AiToolCall(String id, String name, String arguments) {
    AiToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tool call ID cannot be empty.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool call name cannot be empty.");
        }
        arguments = arguments == null || arguments.isBlank() ? "{}" : arguments;
    }

    JsonObject toJson() {
        JsonObject call = new JsonObject();
        call.addProperty("id", id);
        call.addProperty("type", "function");
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("arguments", arguments);
        call.add("function", function);
        return call;
    }

    static AiToolCall fromJson(JsonObject call) {
        if (!call.has("function") || !call.get("function").isJsonObject()) {
            throw new IllegalArgumentException("Tool call is missing its function.");
        }
        JsonObject function = call.getAsJsonObject("function");
        return new AiToolCall(
                requiredString(call, "id"),
                requiredString(function, "name"),
                requiredString(function, "arguments")
        );
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Tool call is missing " + name + ".");
        }
        return object.get(name).getAsString();
    }
}
