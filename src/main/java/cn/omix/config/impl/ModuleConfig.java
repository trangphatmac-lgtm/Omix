package cn.omix.config.impl;

import cn.omix.Client;
import cn.omix.config.Config;
import cn.omix.config.ConfigStorageMode;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.DynamicBoolValueProvider;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.security.SafeStorage;
import cn.omix.ui.hud.Drag;
import cn.omix.util.misc.KeyUtil;
import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public final class ModuleConfig extends Config {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ConfigStorageMode storageMode = ConfigStorageMode.NONE;
    private boolean storageModeKnown;
    private JsonObject retained = new JsonObject();

    public ModuleConfig() {
        this("Default");
    }

    public ModuleConfig(final String name) {
        super(name);
    }

    @Override
    public void save() {
        if (!storageModeKnown) {
            storageMode = detectStoredMode();
            storageModeKnown = true;
        }
        saveWithCurrentMode();
    }

    public void save(ConfigStorageMode mode) {
        storageMode = mode;
        storageModeKnown = true;
        saveWithCurrentMode();
    }

    public ConfigStorageMode getStorageMode() {
        if (!storageModeKnown) {
            storageMode = detectStoredMode();
            storageModeKnown = true;
        }
        return storageMode;
    }

    private void saveWithCurrentMode() {
        try { writeChecked(serializeCurrentState(false)); }
        catch (Exception exception) { Client.logger.debug("Failed to save config: {}. Error: {}", getName(), exception.getMessage()); }
    }

    public void saveChecked() throws java.io.IOException {
        if (!storageModeKnown) { storageMode = detectStoredMode(); storageModeKnown = true; }
        writeChecked(serializeCurrentState(false));
    }

    /** Writes a native snapshot atomically, retaining this profile's encryption mode. */
    public void writeChecked(JsonObject snapshot) throws java.io.IOException {
        if (!storageModeKnown) { storageMode = detectStoredMode(); storageModeKnown = true; }
        var target = getFile().toPath().toAbsolutePath();
        if (Files.isSymbolicLink(target)) throw new java.io.IOException("Linked configuration files are not supported");
        cn.omix.util.script.ScriptFiles.atomicWrite(target, storageMode.encode(gson.toJson(snapshot)));
    }

    @Override public void load() {
        if (!getFile().exists()) return;
        try { loadChecked(); }
        catch (Exception exception) { Client.logger.debug("Failed to load config: {}. Error: {}", getName(), exception.getMessage()); }
    }

    public void loadChecked() throws java.io.IOException {
        if (!getFile().isFile() || Files.isSymbolicLink(getFile().toPath()))
            throw new java.io.IOException("Configuration no longer exists or is linked");
        try {
            String storedValue = Files.readString(getFile().toPath(), StandardCharsets.UTF_8);
            ConfigStorageMode nextMode = ConfigStorageMode.detect(storedValue);
            JsonElement parsed = JsonParser.parseString(nextMode.decode(storedValue));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Expected a configuration object");
            JsonObject jsonObject = parsed.getAsJsonObject();
            // Geometry is validated before any live state is changed.
            if (jsonObject.has("_opaiHud")) new cn.omix.util.opai.layout.HudLayouts().load(jsonObject.getAsJsonObject("_opaiHud"));
            if (jsonObject.has("_opaiClickGui")) cn.omix.util.opai.layout.ClickGuiLayouts.validate(jsonObject.getAsJsonObject("_opaiClickGui"));
            for (Module module : instance.getModuleManager().getModuleMap().values()) {
                var state = jsonObject.get(configKey(module));
                if (state != null && !state.isJsonObject()) throw new IllegalArgumentException("Invalid module: " + module.getName());
            }
            storageMode = nextMode; storageModeKnown = true; retained = jsonObject.deepCopy();
            if (jsonObject.has("_opaiHud")) cn.omix.util.opai.layout.HudLayouts.INSTANCE.load(jsonObject.getAsJsonObject("_opaiHud"));
            if (jsonObject.has("_opaiClickGui")) cn.omix.util.opai.layout.ClickGuiLayouts.load(jsonObject.getAsJsonObject("_opaiClickGui"));
            for (Module module : instance.getModuleManager().getModuleMap().values())
                if (jsonObject.has(configKey(module))) deserializeModule(module, jsonObject.getAsJsonObject(configKey(module)));
        } catch (RuntimeException error) { throw new java.io.IOException("Invalid configuration: " + error.getMessage(), error); }
    }

    private ConfigStorageMode detectStoredMode() {
        if (!this.getFile().isFile()) {
            return ConfigStorageMode.NONE;
        }
        try {
            String storedValue = Files.readString(this.getFile().toPath(), StandardCharsets.UTF_8);
            return ConfigStorageMode.detect(storedValue);
        } catch (Exception exception) {
            Client.logger.debug(
                    "Failed to detect config storage mode: {}. Error: {}",
                    this.getName(),
                    exception.getMessage()
            );
            return ConfigStorageMode.NONE;
        }
    }

    public JsonObject snapshotForAi() {
        JsonObject categories = new JsonObject();
        for (Category category : Category.values()) {
            categories.add(category.getName(), new JsonObject());
        }

        for (Module module : instance.getModuleManager().getModuleMap().values()) {
            JsonObject moduleObject = new JsonObject();
            moduleObject.addProperty("enabled", module.isEnabled());
            moduleObject.addProperty("key", KeyUtil.getKeyName(module.getKey()));

            JsonObject settings = serializeValues(module, true, true);
            if (!settings.isEmpty()) {
                moduleObject.add("settings", settings);
            }
            categories.getAsJsonObject(module.getCategory().getName()).add(module.getName(), moduleObject);
        }
        return categories;
    }

    /** Detached native configuration for local profile creation, including hidden values. */
    public JsonObject snapshot() { return serializeCurrentState(false); }

    private JsonObject serializeCurrentState(boolean redactSensitive) {
        final JsonObject jsonObject = retained.deepCopy();
        jsonObject.add("_opaiHud", cn.omix.util.opai.layout.HudLayouts.INSTANCE.snapshot());
        jsonObject.add("_opaiClickGui", cn.omix.util.opai.layout.ClickGuiLayouts.snapshot());
        for (Module module : instance.getModuleManager().getModuleMap().values()) {
            final JsonObject moduleObject = new JsonObject();
            moduleObject.addProperty("enabled", !module.isHoldToUse() && module.isEnabled());
            moduleObject.addProperty("key", module.getKey());
            moduleObject.addProperty("hidden", module.isHidden());

            if (module instanceof Drag drag) {
                moduleObject.addProperty("percentX", drag.percentX);
                moduleObject.addProperty("percentY", drag.percentY);
            }

            JsonObject valuesObject = serializeValues(module, redactSensitive, false);
            if (!valuesObject.isEmpty()) {
                moduleObject.add("values", valuesObject);
            }
            JsonObject previous = jsonObject.has(configKey(module)) ? jsonObject.getAsJsonObject(configKey(module)) : null;
            if (previous != null && previous.has("values")) {
                JsonObject merged = previous.getAsJsonObject("values").deepCopy();
                valuesObject.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                moduleObject.add("values", merged);
            }
            jsonObject.add(configKey(module), moduleObject);
        }
        return jsonObject;
    }

    /** Preserve dynamic modules and mode settings before their registrations disappear. */
    public void retainCurrentState() { retained = serializeCurrentState(false); }

    private JsonObject serializeValues(
            final Module module,
            boolean redactSensitive,
            boolean onlyClickGuiVisible
    ) {
        final JsonObject valuesObject = new JsonObject();
        for (final Value value : module.getValues()) {
            if (onlyClickGuiVisible && !value.isVisible()) {
                continue;
            }
            switch (value) {
                case BoolValue bool -> valuesObject.addProperty(bool.getName(), bool.getValue());
                case NumberValue num -> valuesObject.addProperty(num.getName(), num.getValue());
                case ModeValue mode -> valuesObject.addProperty(mode.getName(), mode.getValue());
                case TextValue text -> {
                    String serializedText = text.isSensitive()
                            ? (redactSensitive ? "<redacted>" : SafeStorage.encrypt(text.getValue()))
                            : text.getValue();
                    valuesObject.addProperty(text.getName(), serializedText);
                }
                case KeyValue key -> {
                    if (onlyClickGuiVisible) {
                        valuesObject.addProperty(key.getName(), KeyUtil.getKeyName(key.getValue()));
                    } else {
                        valuesObject.addProperty(key.getName(), key.getValue());
                    }
                }
                case ColorValue color -> {
                    if (onlyClickGuiVisible) {
                        valuesObject.addProperty(
                                color.getName(),
                                "#%02x%02x%02x".formatted(
                                        color.getValue().getRed(),
                                        color.getValue().getGreen(),
                                        color.getValue().getBlue()
                                )
                        );
                    } else {
                        valuesObject.addProperty(color.getName(), color.getValue().getRGB());
                    }
                }
                case MultiBoolValue multi -> {
                    final JsonObject multiObject = new JsonObject();
                    for (final BoolValue child : multi.getValues()) {
                        multiObject.addProperty(child.getName(), child.getValue());
                    }
                    valuesObject.add(multi.getName(), multiObject);
                }
                default -> {}
            }
        }
        return valuesObject;
    }

    private void deserializeModule(final Module module, final JsonObject moduleObject) {
        if (moduleObject.has("key")) {
            module.setKey(moduleObject.get("key").getAsInt());
        }

        if (moduleObject.has("hidden")) {
            module.setHidden(moduleObject.get("hidden").getAsBoolean());
        }

        if (module instanceof Drag drag) {
            if (moduleObject.has("percentX")) {
                drag.percentX = moduleObject.get("percentX").getAsFloat();
            }
            if (moduleObject.has("percentY")) {
                drag.percentY = moduleObject.get("percentY").getAsFloat();
            }
        }

        if (moduleObject.has("values")) {
            final JsonObject valuesObject = moduleObject.getAsJsonObject("values");
            for (Map.Entry<String, JsonElement> entry : valuesObject.entrySet()) {
                Value value = module.getValues().stream()
                        .filter(candidate -> candidate.getName().equals(entry.getKey()))
                        .findFirst()
                        .orElse(null);

                if (value == null
                        && module instanceof DynamicBoolValueProvider provider
                        && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    value = provider.getOrCreateBoolValue(entry.getKey(), entry.getValue().getAsBoolean());
                }

                if (value == null) continue;

                try {
                    deserializeValue(value, entry.getValue());
                } catch (final Exception exception) {
                    Client.logger.debug("Failed to load value {}: {}", value.getName(), exception.getMessage());
                }
            }
        }
        if (moduleObject.has("enabled")) {
            final boolean shouldEnable = !module.isHoldToUse() && moduleObject.get("enabled").getAsBoolean();
            if (shouldEnable != module.isEnabled()) {
                module.toggle();
            }
        }

    }

    private static String configKey(Module module) { return module.getId().startsWith("script:") ? module.getId() : module.getName(); }

    public void applyRetained(Module module) {
        JsonElement state = retained.get(configKey(module));
        if (state != null && state.isJsonObject()) deserializeModule(module, state.getAsJsonObject());
    }

    private void deserializeValue(Value value, JsonElement element) {
        switch (value) {
            case BoolValue bool -> bool.setValue(element.getAsBoolean());
            case NumberValue num -> num.setValue(element.getAsFloat());
            case ModeValue mode -> mode.setValue(element.getAsString());
            case TextValue text -> text.setValue(
                    text.isSensitive()
                            ? SafeStorage.decrypt(element.getAsString())
                            : element.getAsString()
            );
            case KeyValue key -> key.setValue(element.getAsInt());
            case ColorValue color -> color.setValue(new java.awt.Color(element.getAsInt()));
            case MultiBoolValue multi when element.isJsonObject() -> {
                final JsonObject multiObject = element.getAsJsonObject();
                for (final BoolValue child : multi.getValues()) {
                    if (multiObject.has(child.getName())) {
                        child.setValue(multiObject.get(child.getName()).getAsBoolean());
                    }
                }
            }
            default -> {}
        }
    }
}
