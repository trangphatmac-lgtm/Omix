package cn.omix.command.impl;

import cn.omix.command.Command;
import cn.omix.util.Util;
import me.ksyz.accountmanager.auth.SessionService;

public final class UsernameCommand extends Command {

    public UsernameCommand() {
        super(".username/.name/.ign", "username", "name", "ign");
    }

    @Override
    public void execute(String[] arguments) {
        Util.logToChat("Username: &b" + SessionService.current().getUsername());
    }
}
