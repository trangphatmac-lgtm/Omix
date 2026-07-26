package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.TextValue;
import cn.remix.util.Util;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class AutoPlay extends Module {
    private final ModeValue mode = new ModeValue(
            "Mode",
            "Hypixel Limbo",
            "Hypixel Limbo",
            "Cubecraft",
            "Purple Prison",
            "Auth Me"
    );
    private final TextValue password = new TextValue(
            "Password",
            "aaaaaaaa",
            () -> mode.is("Auth Me")
    );

    private final AtomicReference<PendingCommand> pendingCommand = new AtomicReference<>();

    public AutoPlay() {
        super("Auto Bypass", Category.World);
    }

    @Override
    public void onEnable() {
        pendingCommand.set(null);
    }

    @Override
    public void onDisable() {
        pendingCommand.set(null);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
        if (mc.player == null) return;

        PendingCommand pending = pendingCommand.getAndSet(null);
        if (pending == null) return;

        mc.player.networkHandler.sendChatCommand(pending.command());
        Util.log(pending.notification());
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received
                || pendingCommand.get() != null
                || !(event.getPacket() instanceof GameMessageS2CPacket packet)
                || packet.overlay()) {
            return;
        }

        String message = packet.content().getString();
        String normalizedMessage = message.toLowerCase(Locale.ROOT);

        switch (mode.getValue().toLowerCase(Locale.ROOT)) {
            case "hypixel limbo" -> {
                if (message.contains("You were spawned in Limbo.")) {
                    queueCommand("lobby", "Trying to bypass limbo...");
                }
            }
            case "cubecraft" -> {
                if (message.contains("Thank you for playing")) {
                    queueCommand("playagain now", "Joining the next game...");
                }
            }
            case "purple prison" -> {
                if (message.contains("ALERT! Your inventory is full (Use /sell)")) {
                    queueCommand("sell", "Sold all items.");
                }
            }
            case "auth me" -> {
                if (normalizedMessage.contains("login")) {
                    queueCommand("login " + password.getValue(), "Logging in...");
                } else if (normalizedMessage.contains("register")) {
                    queueCommand(
                            "register " + password.getValue() + " " + password.getValue(),
                            "Registering..."
                    );
                }
            }
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        pendingCommand.set(null);
    }

    private void queueCommand(String command, String notification) {
        pendingCommand.compareAndSet(null, new PendingCommand(command, notification));
    }

    private record PendingCommand(String command, String notification) {}
}
