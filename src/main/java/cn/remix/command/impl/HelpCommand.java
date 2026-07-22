package cn.remix.command.impl;

import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.util.Util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class HelpCommand extends Command {

    public HelpCommand() {
        super(".help", "help");
    }

    @Override
    public void execute(String[] arguments) {
        Util.logToChat("&fCommands:");

        for (Command command : Client.instance.getCommandManager().getCommands()) {
            String aliases = Arrays.stream(command.getAliases())
                    .map(alias -> "." + alias)
                    .collect(Collectors.joining("&7/&f"));
            Util.logRaw("&8» &f" + aliases + " &8- &7" + command.getUsage());
        }

        Util.logRaw("&8» &f.<module> [setting] [value] &8- &7View or change module settings");
    }
}
