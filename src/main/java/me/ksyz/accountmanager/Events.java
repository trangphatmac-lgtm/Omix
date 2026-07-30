package me.ksyz.accountmanager;

import cn.omix.module.impl.render.NickHider;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.SessionService;
import me.ksyz.accountmanager.gui.AccountManagerScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Events {
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);
    private static boolean registered;
    private static Field disconnectionInfoField;

    private Events() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (hasAccountEntry(screen)) {
                addAccountButton(client, screen, scaledWidth);
                ScreenEvents.afterRender(screen).register(Events::renderSession);
            }
            if (screen instanceof DisconnectedScreen) {
                trackDisconnectBan(screen);
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo serverInfo = client.getCurrentServerEntry();
            if (serverInfo == null || !isHypixel(serverInfo.address)) {
                return;
            }
            updateCurrentAccountUnban(0L);
        });
    }

    private static boolean hasAccountEntry(Screen screen) {
        return screen instanceof TitleScreen
                || screen instanceof SelectWorldScreen
                || screen instanceof MultiplayerScreen;
    }

    private static void addAccountButton(MinecraftClient client, Screen screen, int scaledWidth) {
        Screens.getButtons(screen).add(ButtonWidget.builder(
                        Text.literal("Accounts"),
                        button -> client.setScreen(new AccountManagerScreen(screen))
                )
                .dimensions(scaledWidth - 106, 6, 100, 20)
                .build());
    }

    private static void renderSession(Screen screen, DrawContext context, int mouseX, int mouseY, float tickDelta) {
        if (!hasAccountEntry(screen)) {
            return;
        }
        String text = "Username: " + SessionService.current().getUsername();
        NickHider.withoutHiding(() ->
                context.drawTextWithShadow(
                        Screens.getTextRenderer(screen),
                        Text.literal(text).withColor(0xAAAAAA),
                        3,
                        3,
                        0xFFFFFFFF
                )
        );
    }

    private static void trackDisconnectBan(Screen screen) {
        String text = disconnectText(screen).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return;
        }
        if (text.contains("permanently banned") || text.contains("account has been blocked")) {
            updateCurrentAccountUnban(-1L);
            return;
        }
        if (!text.contains("temporarily banned") && !text.contains("temporarily blocked")) {
            return;
        }

        Matcher matcher = DURATION_PATTERN.matcher(text);
        long duration = 0L;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "d" -> duration += value * 86400000L;
                case "h" -> duration += value * 3600000L;
                case "m" -> duration += value * 60000L;
                case "s" -> duration += value * 1000L;
                default -> {
                }
            }
        }
        if (duration > 0L) {
            updateCurrentAccountUnban(System.currentTimeMillis() + duration);
        }
    }

    private static String disconnectText(Screen screen) {
        try {
            Field field = disconnectionInfoField();
            if (field == null) {
                return "";
            }
            DisconnectionInfo info = (DisconnectionInfo) field.get(screen);
            return info == null ? "" : info.reason().getString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Field disconnectionInfoField() {
        if (disconnectionInfoField != null) {
            return disconnectionInfoField;
        }
        for (Field field : DisconnectedScreen.class.getDeclaredFields()) {
            if (field.getType().isAssignableFrom(DisconnectionInfo.class)) {
                field.setAccessible(true);
                disconnectionInfoField = field;
                return field;
            }
        }
        return null;
    }

    private static void updateCurrentAccountUnban(long unban) {
        String username = SessionService.current().getUsername();
        AccountManager.load();
        boolean changed = false;
        for (Account account : AccountManager.accounts) {
            if (username.equals(account.getUsername())) {
                account.setUnban(unban);
                changed = true;
            }
        }
        if (changed) {
            AccountManager.save();
        }
    }

    private static boolean isHypixel(String address) {
        if (address == null) {
            return false;
        }
        String host = address.toLowerCase(Locale.ROOT);
        int port = host.indexOf(':');
        if (port >= 0) {
            host = host.substring(0, port);
        }
        return host.endsWith("hypixel.net") || host.endsWith("hypixel.io");
    }
}
