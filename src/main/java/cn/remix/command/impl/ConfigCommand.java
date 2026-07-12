package cn.remix.command.impl;

import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.config.Config;
import cn.remix.config.impl.ModuleConfig;
import cn.remix.util.Util;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class ConfigCommand extends Command {

    public ConfigCommand() {
        super(".config <load/save/list> [name]", "config", "cfg", "c");
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
                Config targetConfig = Client.instance.getConfigManager().getConfig(configName);

                if (targetConfig == null) {
                    targetConfig = new ModuleConfig(configName);
                    Client.instance.getConfigManager().addConfigs(targetConfig);
                }

                targetConfig.save();
                Util.log(String.format("Saved configuration to " + Formatting.GREEN + "%s.json", configName));
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
        }
        return completions;
    }
}
