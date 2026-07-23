package ai.backend;

import com.google.gson.JsonArray;

record AiToolSnapshot(JsonArray definitions, String promptContext) {
    AiToolSnapshot {
        definitions = definitions == null ? new JsonArray() : definitions.deepCopy();
        promptContext = promptContext == null ? "" : promptContext;
    }
}
