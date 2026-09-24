package cn.omix.module.impl.world;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.module.value.impl.TextValue;
import cn.omix.util.Util;
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
    private final NumberValue hypixelLimboDelay = new NumberValue(
            "Hypixel Limbo Delay (s)", 0, 0, 10, 0.1F, () -> mode.is("Hypixel Limbo")
    );
    private final NumberValue cubecraftDelay = new NumberValue(
            "Cubecraft Delay (s)", 0, 0, 10, 0.1F, () -> mode.is("Cubecraft")
    );
    private final NumberValue purplePrisonDelay = new NumberValue(
            "Purple Prison Delay (s)", 0, 0, 10, 0.1F, () -> mode.is("Purple Prison")
    );
    private final NumberValue authMeDelay = new NumberValue(
            "Auth Me Delay (s)", 0, 0, 10, 0.1F, () -> mode.is("Auth Me")
    );
    private final TextValue password = new TextValue(
            "Password",
            "aaaaaaaa",
            () -> mode.is("Auth Me"),
            true
    );

    private final AtomicReference<PendingCommand> pendingCommand = new AtomicReference<>();

    public AutoPlay() {
        super("Auto Bypass", Category.World);
        mode.onChange((previous, current) -> pendingCommand.set(null));
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

        PendingCommand pending = pendingCommand.get();
        if (pending == null || System.nanoTime() - pending.executeAtNanos() < 0) return;
        if (!pendingCommand.compareAndSet(pending, null)) return;

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
                    queueCommand("lobby", "Trying to bypass limbo...", hypixelLimboDelay);
                }
            }
            case "cubecraft" -> {
                if (message.contains("Thank you for playing")) {
                    queueCommand("playagain now", "Joining the next game...", cubecraftDelay);
                }
            }
            case "purple prison" -> {
                if (message.contains("ALERT! Your inventory is full (Use /sell)")) {
                    queueCommand("sell", "Sold all items.", purplePrisonDelay);
                }
            }
            case "auth me" -> {
                if (normalizedMessage.contains("login")) {
                    queueCommand("login " + password.getValue(), "Logging in...", authMeDelay);
                } else if (normalizedMessage.contains("register")) {
                    queueCommand(
                            "register " + password.getValue() + " " + password.getValue(),
                            "Registering...",
                            authMeDelay
                    );
                }
            }
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        pendingCommand.set(null);
    }

    private void queueCommand(String command, String notification, NumberValue delay) {
        long executeAtNanos = System.nanoTime() + Math.round(delay.getValue().doubleValue() * 1_000_000_000L);
        pendingCommand.compareAndSet(null, new PendingCommand(command, notification, executeAtNanos));
    }

    private record PendingCommand(String command, String notification, long executeAtNanos) {}
}
