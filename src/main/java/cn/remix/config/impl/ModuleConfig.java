package cn.remix.config.impl;

import cn.remix.Client;
import cn.remix.config.Config;
import cn.remix.config.ConfigStorageMode;
import cn.remix.module.Module;
import cn.remix.module.value.DynamicBoolValueProvider;
import cn.remix.module.value.Value;
import cn.remix.module.value.impl.*;
import cn.remix.security.SafeStorage;
import cn.remix.ui.hud.Drag;
import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public final class ModuleConfig extends Config {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ConfigStorageMode storageMode = ConfigStorageMode.NONE;
    private boolean storageModeKnown;

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
        try {
            final JsonObject jsonObject = new JsonObject();

            for (Module module : instance.getModuleManager().getModuleMap().values()) {
                final JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("enabled", !module.isHoldToUse() && module.isEnabled());
                moduleObject.addProperty("key", module.getKey());
                moduleObject.addProperty("hidden", module.isHidden());

                if (module instanceof Drag drag) {
                    moduleObject.addProperty("percentX", drag.percentX);
                    moduleObject.addProperty("percentY", drag.percentY);
                }

                JsonObject valuesObject = this.serializeValues(module);
                if (!valuesObject.isEmpty()) {
                    moduleObject.add("values", valuesObject);
                }
                jsonObject.add(module.getName(), moduleObject);
            }

            String serialized = this.gson.toJson(jsonObject);
            Files.writeString(
                    this.getFile().toPath(),
                    storageMode.encode(serialized),
                    StandardCharsets.UTF_8
            );
        } catch (final Exception exception) {
            Client.logger.debug("Failed to save config: {}. Error: {}", this.getName(), exception.getMessage());
        }
    }

    @Override
    public void load() {
        if (!this.getFile().exists()) {
            return;
        }

        try {
            String storedValue = Files.readString(this.getFile().toPath(), StandardCharsets.UTF_8);
            storageMode = ConfigStorageMode.detect(storedValue);
            storageModeKnown = true;

            final JsonElement jsonElement = JsonParser.parseString(storageMode.decode(storedValue));
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                return;
            }
            final JsonObject jsonObject = jsonElement.getAsJsonObject();

            for (final Module module : instance.getModuleManager().getModuleMap().values()) {
                if (!jsonObject.has(module.getName())) {
                    continue;
                }

                final JsonObject moduleObject = jsonObject.getAsJsonObject(module.getName());
                this.deserializeModule(module, moduleObject);
            }
        } catch (final Exception exception) {
            Client.logger.debug("Failed to load config: {}. Error: {}", this.getName(), exception.getMessage());
        }
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

    private JsonObject serializeValues(final Module module) {
        final JsonObject valuesObject = new JsonObject();
        for (final Value value : module.getValues()) {
            switch (value) {
                case BoolValue bool -> valuesObject.addProperty(bool.getName(), bool.getValue());
                case NumberValue num -> valuesObject.addProperty(num.getName(), num.getValue());
                case ModeValue mode -> valuesObject.addProperty(mode.getName(), mode.getValue());
                case TextValue text -> valuesObject.addProperty(
                        text.getName(),
                        text.isSensitive()
                                ? SafeStorage.encrypt(text.getValue())
                                : text.getValue()
                );
                case KeyValue key -> valuesObject.addProperty(key.getName(), key.getValue());
                case ColorValue color -> valuesObject.addProperty(color.getName(), color.getValue().getRGB());
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
        if (moduleObject.has("enabled")) {
            final boolean shouldEnable = !module.isHoldToUse() && moduleObject.get("enabled").getAsBoolean();
            if (shouldEnable != module.isEnabled()) {
                module.toggle();
            }
        }

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
