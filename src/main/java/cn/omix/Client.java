package cn.omix;

import cn.omix.command.CommandManager;
import cn.omix.config.ConfigManager;
import cn.omix.event.base.EventManager;
import cn.omix.fisproxy.FisProxyManager;
import cn.omix.management.FriendManager;
import cn.omix.management.PacketManager;
import cn.omix.management.RotationManager;
import cn.omix.management.TargetManager;
import cn.omix.module.ModuleManager;
import cn.omix.module.impl.exploits.disabler.MinibloxDisabler;
import cn.omix.ui.clickgui.ClickGuiScreen;
import cn.omix.ui.font.FontManager;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import cn.omix.util.render.Render3D;
import me.ksyz.accountmanager.AccountManager;
import im.webui.WebUiRuntime;
import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

@Getter
public class Client implements IMinecraft {
    public static Client instance;
    public static Logger logger;

    public static String name = "Omix";
    public static String version = "260922-SNAPSHOT";

    private cn.omix.script.ScriptManager scriptManager;
    private cn.omix.util.ai.MinecraftGameBridge gameBridge;
    private EventManager eventManager;
    private FisProxyManager fisProxyManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private RotationManager rotationManager;
    private TargetManager targetManager;
    private FriendManager friendManager;
    private FontManager fontManager;
    private PacketManager packetManager;
    private ClickGuiScreen clickGuiScreen;

    public void init() {

        // Why did you do that?
        PayloadTypeRegistry.playC2S().register(
                MinibloxDisabler.MovePayload.ID,
                MinibloxDisabler.MovePayload.CODEC
        );
        Render2D.init();
        Render3D.init();
        eventManager = new EventManager();
        fisProxyManager = new FisProxyManager(Path.of(name, "fisproxy.json"));
        moduleManager = new ModuleManager();
        commandManager = new CommandManager();
        configManager = new ConfigManager();
        rotationManager = new RotationManager();
        targetManager = new TargetManager();
        friendManager = new FriendManager();
        fontManager = new FontManager();
        packetManager = new PacketManager();
        clickGuiScreen = new ClickGuiScreen();
        AccountManager.init();
        try {
            scriptManager = new cn.omix.script.ScriptManager(mc.runDirectory.toPath().resolve("Omix/scripts"));
            scriptManager.start();
            gameBridge = new cn.omix.util.ai.MinecraftGameBridge();
        } catch (Exception error) { logger.error("Script/development service failed", error); }
        WebUiRuntime.getInstance().start();
    }

    private boolean shuttingDown;
    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        if (configManager != null) configManager.saveAll();
        if (gameBridge != null) gameBridge.close();
        if (scriptManager != null) scriptManager.close();
        WebUiRuntime.getInstance().stop();
        if (fisProxyManager != null) fisProxyManager.close();
        AccountManager.save();
    }
}
