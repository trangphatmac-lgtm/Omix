package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.command.Command;
import cn.omix.config.Config;
import cn.omix.config.ConfigStorageMode;
import cn.omix.config.impl.ModuleConfig;
import cn.omix.util.Util;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class ConfigCommand extends Command {

    public ConfigCommand() {
        super(".cfg <load/save/list> [name] [--crypto 0|1|2]", "config", "cfg", "c");
    }

    @Override
    public void execute(final String[] arguments) {
        if (arguments.length < 2) {
            Util.log(this.getUsage());
            return;
        }

        final String inputAction = arguments[1].toLowerCase();
        final String action = switch (inputAction) {
            case "l" -> arguments.length >= 3 ? "load" : "list";
            case "s" -> "save";
            default -> inputAction;
        };

        switch (action) {
            case "list" -> {
                Util.log(Formatting.BLUE + "Available configs:");
                for (final Config config : Client.instance.getConfigManager().getConfigs()) {
                    Util.log("- " + config.getName());
                }
            }

            case "save" -> {
                if (arguments.length < 3) {
                    final Config currentConfig = Client.instance.getConfigManager().getCurrentConfig();
                    if (currentConfig != null) {
                        currentConfig.save();
                        Util.log("Saved current config: " + Formatting.GREEN + currentConfig.getName());
                    }
                    return;
                }

                final String configName = arguments[2];
                final ConfigStorageMode requestedMode;
                try {
                    requestedMode = parseStorageMode(arguments);
                } catch (IllegalArgumentException exception) {
                    Util.log(Formatting.RED + exception.getMessage());
                    Util.log("Usage: .cfg save <name> [--crypto 0|1|2]");
                    return;
                }

                final Config targetConfig;
                if (requestedMode == null) {
                    targetConfig = Client.instance.getConfigManager().saveConfig(configName);
                } else {
                    targetConfig = Client.instance.getConfigManager().saveConfig(
                            configName,
                            requestedMode
                    );
                }
                ConfigStorageMode actualMode = targetConfig instanceof ModuleConfig moduleConfig
                        ? moduleConfig.getStorageMode()
                        : ConfigStorageMode.NONE;
                Util.log(String.format(
                        "Saved configuration to " + Formatting.GREEN + "%s.json"
                                + Formatting.RESET + " (" + actualMode.displayName() + ")",
                        configName
                ));
            }

            case "load" -> {
                if (arguments.length < 3) {
                    Util.log("Usage: .cfg load <name>");
                    return;
                }

                final String configName = arguments[2];
                final Config targetConfig = Client.instance.getConfigManager().loadConfig(configName);

                if (targetConfig != null) {
                    Util.log("Loaded config: " + Formatting.AQUA + targetConfig.getName());
                } else {
                    Util.log(Formatting.RED + "Config not found: " + configName);
                }
            }

            default -> Util.log(this.getUsage());
        }
    }

    @Override
    public List<String> getCompletions(final String[] arguments) {
        final List<String> completions = new ArrayList<>();
        if (arguments.length == 2) {
            completions.add("l");
            completions.add("s");
            completions.add("load");
            completions.add("save");
            completions.add("list");
        } else if (arguments.length == 3) {
            for (final Config config : Client.instance.getConfigManager().getConfigs()) {
                completions.add(config.getName());
            }
        } else if (arguments.length == 4 && isSaveAction(arguments[1])) {
            completions.add("--crypto");
        } else if (arguments.length == 5
                && isSaveAction(arguments[1])
                && isEncryptionFlag(arguments[3])) {
            completions.add("0");
            completions.add("1");
            completions.add("2");
        }
        return completions;
    }

    private static ConfigStorageMode parseStorageMode(String[] arguments) {
        if (arguments.length == 3) {
            return null;
        }
        if (arguments.length != 5 || !isEncryptionFlag(arguments[3])) {
            throw new IllegalArgumentException("Invalid config encryption option.");
        }
        try {
            return ConfigStorageMode.fromCode(Integer.parseInt(arguments[4]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Config encryption mode must be 0, 1, or 2.");
        }
    }

    private static boolean isSaveAction(String action) {
        return action.equalsIgnoreCase("save") || action.equalsIgnoreCase("s");
    }

    private static boolean isEncryptionFlag(String value) {
        return value.equalsIgnoreCase("--crypto");
    }
}
