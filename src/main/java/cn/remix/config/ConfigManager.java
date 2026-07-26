package cn.remix.config;

import cn.remix.Client;
import cn.remix.config.impl.ModuleConfig;
import cn.remix.util.IMinecraft;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ConfigManager implements IMinecraft {
    private final List<Config> configs = new ArrayList<>();
    private Config currentConfig;

    public ConfigManager() {
        instance.getEventManager().register(this);

        addConfigs(
                new ModuleConfig()
        );

        currentConfig = findConfig("Default");
        loadAll();
        discoverConfigs();
    }

    public Config getConfig(final String name) {
        discoverConfigs();
        return findConfig(name);
    }

    public List<Config> getConfigs() {
        discoverConfigs();
        return List.copyOf(configs);
    }

    public Config getCurrentConfig() {
        return currentConfig;
    }

    public Config loadConfig(final String name) {
        Config config = getConfig(name);
        if (config == null) return null;

        config.load();
        currentConfig = config;
        return config;
    }

    public Config createConfig(final String name) {
        discoverConfigs();
        Config existing = findConfig(name);
        if (existing != null && existing.getFile().exists()) {
            return null;
        }

        Config config = existing == null ? new ModuleConfig(name) : existing;
        if (existing == null) {
            configs.add(config);
        }
        config.save();
        return config;
    }

    public Config saveConfig(final String name) {
        discoverConfigs();
        Config config = findConfig(name);
        if (config == null) {
            config = new ModuleConfig(name);
            configs.add(config);
        }
        config.save();
        return config;
    }

    public boolean deleteConfig(final String name) {
        if (name == null || name.equalsIgnoreCase("Default")) {
            return false;
        }

        discoverConfigs();
        Config config = findConfig(name);
        if (config == null || !config.getFile().exists() || !config.getFile().delete()) {
            return false;
        }

        configs.remove(config);
        if (currentConfig == config) {
            currentConfig = findConfig("Default");
            if (currentConfig != null) {
                currentConfig.load();
            }
        }
        return true;
    }

    private Config findConfig(final String name) {
        return configs.stream()
                .filter(config -> config.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<String> getAvailableConfigs() {
        List<String> configNames = new ArrayList<>();
        File directory = new File(Client.name, "configs");
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    configNames.add(name.substring(0, name.length() - 5));
                }
            }
        }

        if (configNames.isEmpty()) configNames.add("Default");
        return configNames;
    }

    public void addConfigs(final Config... configsArray) {
        Arrays.stream(configsArray)
                .filter(config -> findConfig(config.getName()) == null)
                .forEach(configs::add);
    }

    public void discoverConfigs() {
        File[] files = Config.getDirectory().listFiles((directory, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".json"));

        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            if (!file.isFile()) continue;

            String fileName = file.getName();
            String configName = fileName.substring(0, fileName.length() - ".json".length());
            if (findConfig(configName) == null) {
                configs.add(new ModuleConfig(configName));
            }
        }
    }

    public void saveAll() {
        Config defaultConfig = findConfig("Default");
        if (defaultConfig != null) {
            defaultConfig.save();
        }
    }

    public void loadAll() {
        Config defaultConfig = findConfig("Default");
        if (defaultConfig != null) {
            defaultConfig.load();
        }
    }
}
