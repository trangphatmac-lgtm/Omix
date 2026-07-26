package im.webui.interop;

import cn.remix.Client;
import cn.remix.config.Config;
import cn.remix.config.ConfigManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.Value;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.KeyValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.MultiBoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.module.value.impl.TextValue;
import cn.remix.util.misc.KeyUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.Color;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ClickGuiInteropBridge {
    private static final String STATE_PATH = "/api/v1/clickgui/state";
    private static final String MODULE_PATH = "/api/v1/clickgui/module";
    private static final String VALUE_PATH = "/api/v1/clickgui/value";
    private static final String CONFIG_PATH = "/api/v1/clickgui/config";

    public ClickGuiInteropBridge(InteropServer server) {
        registerRoutes(server.getRoutes());
    }

    private void registerRoutes(InteropRouteRegistry routes) {
        routes.get(STATE_PATH, ignored -> onClientThread(() ->
                InteropResponse.json(HttpResponseStatus.OK, state())));

        routes.put(MODULE_PATH, request -> onClientThread(() -> {
            JsonObject body = request.body();
            Module module = findModule(stringValue(body, "module"));
            if (module == null) {
                return InteropResponse.text(HttpResponseStatus.NOT_FOUND, "Unknown module");
            }

            if (body.has("enabled")) {
                module.setEnabled(body.get("enabled").getAsBoolean());
            }
            if (body.has("hidden")) {
                module.setHidden(body.get("hidden").getAsBoolean());
            }
            if (body.has("key")) {
                module.setKey(body.get("key").getAsInt());
            }
            return InteropResponse.json(HttpResponseStatus.OK, state());
        }));

        routes.put(VALUE_PATH, request -> onClientThread(() -> {
            JsonObject body = request.body();
            Module module = findModule(stringValue(body, "module"));
            if (module == null) {
                return InteropResponse.text(HttpResponseStatus.NOT_FOUND, "Unknown module");
            }

            Value value = findValue(module, stringValue(body, "setting"));
            if (value == null) {
                return InteropResponse.text(HttpResponseStatus.NOT_FOUND, "Unknown setting");
            }
            if (!body.has("value")) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, "Missing value");
            }

            try {
                updateValue(value, body);
                return InteropResponse.json(HttpResponseStatus.OK, state());
            } catch (IllegalArgumentException exception) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, exception.getMessage());
            }
        }));

        routes.post(CONFIG_PATH, request -> onClientThread(() -> {
            JsonObject body = request.body();
            String action = stringValue(body, "action").toLowerCase(Locale.ROOT);
            String name = stringValue(body, "name").trim();
            if (!validConfigName(name)) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, "Invalid config name");
            }

            ConfigManager manager = Client.instance.getConfigManager();
            switch (action) {
                case "create" -> {
                    if (manager.createConfig(name) == null) {
                        return InteropResponse.text(HttpResponseStatus.CONFLICT, "Config already exists");
                    }
                }
                case "load" -> {
                    if (manager.loadConfig(name) == null) {
                        return InteropResponse.text(HttpResponseStatus.NOT_FOUND, "Config not found");
                    }
                }
                case "save" -> manager.saveConfig(name);
                case "delete" -> {
                    if (!manager.deleteConfig(name)) {
                        return InteropResponse.text(
                                HttpResponseStatus.BAD_REQUEST,
                                name.equalsIgnoreCase("Default")
                                        ? "Default config cannot be deleted"
                                        : "Config could not be deleted"
                        );
                    }
                }
                default -> {
                    return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, "Unknown config action");
                }
            }
            return InteropResponse.json(HttpResponseStatus.OK, state());
        }));
    }

    private static JsonObject state() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        JsonObject response = new JsonObject();
        response.addProperty("name", Client.name);
        response.addProperty("version", Client.version);
        response.addProperty("fps", minecraft.getCurrentFps());
        response.addProperty("ping", currentPing(minecraft));

        JsonArray categories = new JsonArray();
        for (Category category : Category.values()) {
            JsonObject categoryObject = new JsonObject();
            categoryObject.addProperty("id", category.name());
            categoryObject.addProperty("name", category.getName());
            categories.add(categoryObject);
        }
        response.add("categories", categories);

        JsonArray modules = new JsonArray();
        Client.instance.getModuleManager().getModuleMap().values().stream()
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .map(ClickGuiInteropBridge::serializeModule)
                .forEach(modules::add);
        response.add("modules", modules);

        ConfigManager configManager = Client.instance.getConfigManager();
        JsonArray configs = new JsonArray();
        configManager.getAvailableConfigs().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(configs::add);
        response.add("configs", configs);
        Config current = configManager.getCurrentConfig();
        response.addProperty("currentConfig", current == null ? "" : current.getName());
        return response;
    }

    private static JsonObject serializeModule(Module module) {
        JsonObject result = new JsonObject();
        result.addProperty("name", module.getName());
        result.addProperty("category", module.getCategory().name());
        result.addProperty("enabled", module.isEnabled());
        result.addProperty("hidden", module.isHidden());
        result.addProperty("key", module.getKey());
        result.addProperty("keyName", KeyUtil.getKeyName(module.getKey()));

        JsonArray settings = new JsonArray();
        for (Value value : module.getValues()) {
            settings.add(serializeValue(value));
        }
        result.add("settings", settings);
        return result;
    }

    private static JsonObject serializeValue(Value value) {
        JsonObject result = new JsonObject();
        result.addProperty("name", value.getName());
        result.addProperty("visible", value.isVisible());

        switch (value) {
            case BoolValue bool -> {
                result.addProperty("type", "boolean");
                result.addProperty("value", bool.getValue());
            }
            case NumberValue number -> {
                result.addProperty("type", "number");
                result.addProperty("value", number.getValue());
                result.addProperty("min", number.getMin());
                result.addProperty("max", number.getMax());
                result.addProperty("step", number.getInc());
            }
            case ModeValue mode -> {
                result.addProperty("type", "mode");
                result.addProperty("value", mode.getValue());
                JsonArray options = new JsonArray();
                Arrays.stream(mode.getModes()).forEach(options::add);
                result.add("options", options);
            }
            case MultiBoolValue multi -> {
                result.addProperty("type", "multi");
                JsonArray options = new JsonArray();
                for (BoolValue child : multi.getValues()) {
                    JsonObject option = new JsonObject();
                    option.addProperty("name", child.getName());
                    option.addProperty("value", child.getValue());
                    options.add(option);
                }
                result.add("options", options);
            }
            case ColorValue color -> {
                result.addProperty("type", "color");
                Color current = color.getValue();
                result.addProperty(
                        "value",
                        "#%02x%02x%02x".formatted(
                                current.getRed(),
                                current.getGreen(),
                                current.getBlue()
                        )
                );
            }
            case TextValue text -> {
                result.addProperty("type", "text");
                result.addProperty("value", text.getValue());
            }
            case KeyValue key -> {
                result.addProperty("type", "key");
                result.addProperty("value", key.getValue());
                result.addProperty("keyName", KeyUtil.getKeyName(key.getValue()));
            }
            default -> result.addProperty("type", "unsupported");
        }
        return result;
    }

    private static void updateValue(Value value, JsonObject body) {
        JsonElement next = body.get("value");
        switch (value) {
            case BoolValue bool -> bool.setValue(next.getAsBoolean());
            case NumberValue number -> number.setValue(next.getAsFloat());
            case ModeValue mode -> mode.setValue(next.getAsString());
            case MultiBoolValue multi -> {
                String childName = stringValue(body, "child");
                if (multi.getValues().stream().noneMatch(child ->
                        child.getName().equalsIgnoreCase(childName))) {
                    throw new IllegalArgumentException("Unknown multi setting option");
                }
                multi.setValue(childName, next.getAsBoolean());
            }
            case ColorValue color -> color.setValue(parseColor(next.getAsString()));
            case TextValue text -> text.setValue(next.getAsString());
            case KeyValue key -> key.setValue(next.getAsInt());
            default -> throw new IllegalArgumentException("Unsupported setting type");
        }
    }

    private static Color parseColor(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid color");
        }
        return new Color(Integer.parseInt(normalized.substring(1), 16));
    }

    private static Module findModule(String name) {
        return Client.instance.getModuleManager().getModuleMap().values().stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static Value findValue(Module module, String name) {
        return module.getValues().stream()
                .filter(value -> value.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static boolean validConfigName(String name) {
        return name != null
                && !name.isBlank()
                && name.length() <= 48
                && !name.equals(".")
                && !name.equals("..")
                && name.matches("[\\p{L}\\p{N} _.-]+");
    }

    private static int currentPing(MinecraftClient minecraft) {
        try {
            if (minecraft.player == null || minecraft.getNetworkHandler() == null) {
                return -1;
            }
            PlayerListEntry entry = minecraft.getNetworkHandler()
                    .getPlayerListEntry(minecraft.player.getUuid());
            return entry == null ? -1 : entry.getLatency();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String stringValue(JsonObject object, String name) {
        return object != null && object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString()
                : "";
    }

    private static InteropResponse onClientThread(Callable<InteropResponse> operation) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        try {
            if (minecraft.isOnThread()) {
                return operation.call();
            }

            CompletableFuture<InteropResponse> future = new CompletableFuture<>();
            minecraft.execute(() -> {
                try {
                    future.complete(operation.call());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            Client.logger.error("ClickGUI bridge operation failed", exception);
            return InteropResponse.text(
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    exception.getMessage() == null ? "ClickGUI operation failed" : exception.getMessage()
            );
        }
    }
}
