package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.command.Command;
import cn.omix.module.Module;
import cn.omix.util.Util;
import cn.omix.util.misc.KeyUtil;

public final class ModulesCommand extends Command {

    public ModulesCommand() {
        super(".l/.list/.modules/." + Client.name, "l", "list", "modules", Client.name);
    }

    @Override
    public void execute(String[] arguments) {
        Util.logToChat("&fModules:");

        for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
            String key = module.getKey() > 0
                    ? "&7[&f" + KeyUtil.getKeyName(module.getKey()) + "&7] "
                    : "";
            String state = module.isEnabled() ? "&aON" : "&cOFF";
            Util.logRaw("&8» " + key + "&f" + module.getName() + " &7(" + state + "&7)");
        }
    }
}
