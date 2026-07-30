package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.command.Command;
import cn.omix.module.Module;
import cn.omix.util.Util;
import cn.omix.util.misc.KeyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BindCommand extends Command {

    public BindCommand() {
        super(".bind <module> <key/none> | .bind list", "bind", "b");
    }

    @Override
    public void execute(final String[] arguments) {
        if (arguments.length == 2
                && (arguments[1].equalsIgnoreCase("list") || arguments[1].equalsIgnoreCase("l"))) {
            listBindings();
            return;
        }

        if (arguments.length < 3) {
            Util.log(this.getUsage());
            return;
        }

        final String moduleName = arguments[1].replace(" ", "");
        final String keyName = arguments[2].toUpperCase(Locale.ROOT);
        boolean found = false;

        for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
            if (module.getName().replace(" ", "").equalsIgnoreCase(moduleName)) {
                int keyCode = KeyUtil.getKeyCode(keyName);
                module.setKey(keyCode);
                Util.log(keyCode == 0 ? String.format("Removed bind for %s.", module.getName()) : String.format("Bound %s to %s.", module.getName(), KeyUtil.getKeyName(keyCode)));
                found = true;
                break;
            }
        }

        if (!found) {
            Util.log("Module not found.");
        }
    }

    private void listBindings() {
        boolean found = false;

        for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
            if (module.getKey() > 0) {
                Util.log(module.getName() + " -> " + KeyUtil.getKeyName(module.getKey()));
                found = true;
            }
        }

        if (!found) {
            Util.log("No modules are currently bound.");
        }
    }

    @Override
    public List<String> getCompletions(final String[] arguments) {
        List<String> completions = new ArrayList<>();

        if (arguments.length == 2) {
            completions.add("list");
            completions.add("l");
            for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
                completions.add(module.getName().replace(" ", ""));
            }
        } else if (arguments.length == 3
                && !arguments[1].equalsIgnoreCase("list")
                && !arguments[1].equalsIgnoreCase("l")) {
            completions.add("none");
        }

        return completions;
    }
}
