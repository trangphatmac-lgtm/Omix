package cn.remix.config;

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

    public ConfigManager() {
        instance.getEventManager().register(this);

        addConfigs(
                new ModuleConfig()
        );

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

    private Config findConfig(final String name) {
        return configs.stream()
                .filter(config -> config.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
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
