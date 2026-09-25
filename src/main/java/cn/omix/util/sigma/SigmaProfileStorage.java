package cn.omix.util.sigma;

import cn.omix.Client;
import cn.omix.config.Config;
import cn.omix.config.ConfigManager;
import cn.omix.config.impl.ModuleConfig;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Jello profile actions backed by Omix files; copies preserve encrypted and unknown fields. */
public final class SigmaProfileStorage {
    private static JsonObject defaults;
    private SigmaProfileStorage() {}

    /** Called once after module registration, before loading user configuration. */
    public static void captureDefaults() { defaults = new ModuleConfig().snapshot(); }
    private static ConfigManager manager() { return Client.instance.getConfigManager(); }
    public static void saveCurrent() {
        Config current = manager().getCurrentConfig();
        if (current != null) current.save();
    }
    public static void activate(Config config) {
        if (config == manager().getCurrentConfig()) return;
        saveCurrent();
        manager().loadConfig(config.getName());
        SigmaSounds.play("switch");
    }
    public static Config blank() throws IOException {
        if (defaults == null) throw new IOException("Default settings are not available");
        String name = unique("New Profile ");
        Files.writeString(target(name), defaults.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        manager().addConfigs(new ModuleConfig(name));
        return manager().getConfig(name);
    }
    public static Config duplicate() throws IOException {
        Config current = manager().getCurrentConfig();
        if (current == null) throw new IOException("No active profile");
        saveCurrent();
        String name = unique(current.getName() + " Copy ");
        Files.copy(current.getFile().toPath(), target(name));
        manager().addConfigs(new ModuleConfig(name));
        return manager().getConfig(name);
    }
    public static Config rename(Config config, String requestedName) throws IOException {
        String name = requestedName.strip();
        if (config.getName().equals(name)) return config;
        if (config.getName().equalsIgnoreCase("Default")) throw new IOException("Default cannot be renamed");
        Path destination = target(name);
        if (manager().getConfig(name) != null) throw new IOException("Profile already exists");
        boolean active = manager().getCurrentConfig() == config;
        if (active) saveCurrent();
        Files.copy(config.getFile().toPath(), destination);
        manager().addConfigs(new ModuleConfig(name));
        Config renamed = manager().getConfig(name);
        // Avoid deleteConfig's Default fallback when renaming the active profile.
        if (active) manager().loadConfig(name);
        if (!manager().deleteConfig(config.getName())) {
            if (active) manager().loadConfig(config.getName());
            manager().deleteConfig(name);
            throw new IOException("Could not rename profile");
        }
        return renamed;
    }
    private static String unique(String prefix) {
        int index = 1;
        while (manager().getConfig(prefix + index) != null) index++;
        return prefix + index;
    }
    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= 128 && name.equals(name.strip())
                && !name.endsWith(".") && !name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                && !name.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");
    }
    private static Path target(String name) throws IOException {
        if (!validName(name)) throw new IOException("Choose a valid profile name");
        Path root = Config.getDirectory().toPath().toAbsolutePath().normalize();
        Path path = root.resolve(name.toLowerCase(Locale.ROOT) + ".json").normalize();
        if (!root.equals(path.getParent())) throw new IOException("Choose a valid profile name");
        return path;
    }
}
