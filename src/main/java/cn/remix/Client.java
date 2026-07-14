package cn.remix;

import cn.remix.command.CommandManager;
import cn.remix.config.ConfigManager;
import cn.remix.event.base.EventManager;
import cn.remix.management.FriendManager;
import cn.remix.management.PacketManager;
import cn.remix.management.RotationManager;
import cn.remix.management.TargetManager;
import cn.remix.module.ModuleManager;
import cn.remix.ui.font.FontManager;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.Render2D;
import cn.remix.util.render.Render3D;
import me.ksyz.accountmanager.AccountManager;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

@Getter
public class Client implements IMinecraft {
    public static Client instance;
    public static Logger logger;

    public static String name = "Remix";
    public static String version = "v1.7.0";

    private EventManager eventManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private RotationManager rotationManager;
    private TargetManager targetManager;
    private FriendManager friendManager;
    private FontManager fontManager;
    private PacketManager packetManager;

    public void init() {

        // Why did you do that?
        Render2D.init();
        Render3D.init();
        eventManager = new EventManager();
        moduleManager = new ModuleManager();
        commandManager = new CommandManager();
        configManager = new ConfigManager();
        rotationManager = new RotationManager();
        targetManager = new TargetManager();
        friendManager = new FriendManager();
        fontManager = new FontManager();
        packetManager = new PacketManager();
        AccountManager.init();
    }

    public void shutdown() {
        configManager.saveAll();
        AccountManager.save();
    }
}
