package cn.remix.command.impl;

import cn.remix.command.Command;
import cn.remix.util.Util;
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
