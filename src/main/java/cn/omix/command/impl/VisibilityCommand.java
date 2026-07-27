package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.command.Command;
import cn.omix.module.Module;
import cn.omix.util.Util;

import java.util.List;

public final class VisibilityCommand extends Command {
    private final boolean hidden;

    public VisibilityCommand(boolean hidden, String usage, String... aliases) {
        super(usage, aliases);
        this.hidden = hidden;
    }

    @Override
    public void execute(String[] arguments) {
        if (arguments.length < 2) {
            Util.logToChat(getUsage());
            return;
        }

        Module module = findModule(arguments[1]);
        if (module == null) {
            Util.logToChat("&cModule not found.");
            return;
        }

        if (module.isHidden() == hidden) {
            Util.logToChat(module.getName() + " is already " + (hidden ? "&chidden" : "&ashown") + " &fin the HUD.");
            return;
        }

        module.setHidden(hidden);
        Util.logToChat(module.getName() + " is now " + (hidden ? "&chidden" : "&ashown") + " &fin the HUD.");
    }

    @Override
    public List<String> getCompletions(String[] arguments) {
        if (arguments.length != 2) {
            return List.of();
        }

        return Client.instance.getModuleManager().getModuleMap().values().stream()
                .filter(module -> module.isHidden() != hidden)
                .map(module -> module.getName().replaceAll("\\s+", "-"))
                .toList();
    }

    private Module findModule(String name) {
        String normalized = normalize(name);
        return Client.instance.getModuleManager().getModuleMap().values().stream()
                .filter(module -> normalize(module.getName()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private static String normalize(String name) {
        StringBuilder normalized = new StringBuilder();
        name.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
