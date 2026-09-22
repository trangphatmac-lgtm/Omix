import com.google.gson.*;

void onLoad() {
    // Works in menus too: a reusable tool that reads actual module state.
    tools.register("module_summary", "Read loaded Omix modules whose name contains query; returns at most 20 matches.", """
        {"type":"object","properties":{"query":{"type":"string","maxLength":80}},"required":["query"],"additionalProperties":false}
        """, args -> {
        String query = args.get("query").getAsString().toLowerCase(Locale.ROOT);
        JsonArray result = new JsonArray();
        for (var module : modules.list()) {
            if (!module.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("id", module.getId());
            item.addProperty("name", module.getName());
            item.addProperty("enabled", module.isEnabled());
            result.add(item);
            if (result.size() == 20) break;
        }
        return result;
    }).requiresWorld(false);

    // Defaults to requiresWorld(true); runs on the client thread in the current world.
    tools.register("nearby_entities", "Read at most 20 nearby entities sorted by distance, within radius blocks.", """
        {"type":"object","properties":{"radius":{"type":"number","minimum":1,"maximum":32}},"required":["radius"]}
        """, args -> {
        JsonArray result = new JsonArray();
        for (var entity : game.entities(args.get("radius").getAsDouble())) {
            JsonObject item = new JsonObject();
            item.addProperty("name", entity.getName().getString());
            item.addProperty("distance", mc.player.distanceTo(entity));
            result.add(item);
            if (result.size() == 20) break;
        }
        return result;
    });
}
