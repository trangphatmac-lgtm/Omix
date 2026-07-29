package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionService;
import cn.omix.Client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.session.Session;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class AccountManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int LIST_TOP = 100;
    private static final Pattern OFFLINE_USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final Screen previous;
    private volatile String status = "";
    private ExecutorService executor;
    private CompletableFuture<Void> task;
    private int selected = -1;
    private int scroll;
    private long lastClickTime;
    private int lastClicked = -1;

    public AccountManagerScreen(Screen previous) {
        super(Text.literal("Accounts"));
        this.previous = previous;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0xAA000000);
        drawString(context, Client.name + " Accounts (" + AccountManager.accounts.size() + ")", width / 2 - 82, 18, 0xFFFFFFFF, true);
        renderButton(context, 20, 38, 130, 58, "Microsoft Login", !isBusy());
        renderButton(context, 138, 38, 230, 58, "Email Login", !isBusy());
        renderButton(context, 238, 38, 334, 58, "Access Token", !isBusy());
        renderButton(context, 342, 38, 422, 58, "Offline", !isBusy());
        renderButton(context, 430, 38, 536, 58, "Use Selected", canUseSelection());
        renderButton(context, 20, 62, 84, 82, "Delete", canUseSelection());
        renderButton(context, 92, 62, 148, 82, "Up", canMoveUp());
        renderButton(context, 156, 62, 222, 82, "Down", canMoveDown());
        renderButton(context, width - 164, 62, width - 92, 82, "Reload", !isBusy());
        renderButton(context, width - 84, 62, width - 20, 82, "Back", true);

        int visibleRows = visibleRows();
        int y = LIST_TOP;
        int end = Math.min(AccountManager.accounts.size(), scroll + visibleRows);
        for (int i = scroll; i < end; i++) {
            Account account = AccountManager.accounts.get(i);
            int color = selected == i ? 0xCC315A7A : i % 2 == 0 ? 0xBB111820 : 0xBB172230;
            context.fill(20, y, width - 20, y + ROW_HEIGHT, color);
            String username = displayUsername(account);
            int usernameColor = isCurrentAccount(account) ? 0xFF71E58B : 0xFFFFFFFF;
            drawString(context, username, 26, y + 6, usernameColor, true);
            drawString(context, banText(account), width - 160, y + 6, banColor(account), true);
            y += ROW_HEIGHT + ROW_GAP;
        }
        if (AccountManager.accounts.isEmpty()) {
            drawString(context, "No saved accounts yet.", 24, 80, 0xFFAAAAAA, false);
        }
        if (scroll > 0) {
            drawString(context, "^", width - 14, LIST_TOP, 0xFFAAAAAA, true);
        }
        if (end < AccountManager.accounts.size()) {
            drawString(context, "v", width - 14, height - 48, 0xFFAAAAAA, true);
        }
        if (!status.isBlank()) {
            drawString(context, status, 24, height - 24, status.startsWith("Error") ? 0xFFFF8080 : 0xFFFFD36B, true);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (inside(x, y, 20, 38, 130, 58) && !isBusy()) {
            startMicrosoftLogin();
            return true;
        }
        if (inside(x, y, 138, 38, 230, 58) && !isBusy()) {
            MinecraftClient.getInstance().setScreen(new EmailLoginScreen(this));
            return true;
        }
        if (inside(x, y, 238, 38, 334, 58) && !isBusy()) {
            MinecraftClient.getInstance().setScreen(new TokenInputScreen(this));
            return true;
        }
        if (inside(x, y, 342, 38, 422, 58) && !isBusy()) {
            MinecraftClient.getInstance().setScreen(new OfflineAccountScreen(this));
            return true;
        }
        if (inside(x, y, 430, 38, 536, 58) && canUseSelection()) {
            useSelected();
            return true;
        }
        if (inside(x, y, 20, 62, 84, 82) && canUseSelection()) {
            deleteSelected();
            return true;
        }
        if (inside(x, y, 92, 62, 148, 82) && canMoveUp()) {
            moveSelected(-1);
            return true;
        }
        if (inside(x, y, 156, 62, 222, 82) && canMoveDown()) {
            moveSelected(1);
            return true;
        }
        if (inside(x, y, width - 164, 62, width - 92, 82) && !isBusy()) {
            AccountManager.load();
            selected = Math.min(selected, AccountManager.accounts.size() - 1);
            clampScroll();
            setStatus("Reloaded account file.");
            return true;
        }
        if (inside(x, y, width - 84, 62, width - 20, 82)) {
            close();
            return true;
        }
        int row = rowAt(y);
        if (row >= 0 && row < AccountManager.accounts.size()) {
            long now = Util.getMeasuringTimeMs();
            boolean doubleClick = row == lastClicked && now - lastClickTime <= 250L;
            selected = row;
            lastClicked = row;
            lastClickTime = now;
            if (doubleClick && canUseSelection()) {
                useSelected();
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0.0D) {
            scroll--;
        } else if (verticalAmount < 0.0D) {
            scroll++;
        }
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (canUseSelection()) {
                useSelected();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (canUseSelection()) {
                deleteSelected();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (selected > 0) {
                if (hasControlDown(input)) {
                    moveSelected(-1);
                } else {
                    selected--;
                    keepSelectionVisible();
                }
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (selected < AccountManager.accounts.size() - 1) {
                if (hasControlDown(input)) {
                    moveSelected(1);
                } else {
                    selected++;
                    keepSelectionVisible();
                }
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C && hasControlDown(input) && canUseSelection()) {
            MinecraftClient.getInstance().keyboard.setClipboard(AccountManager.accounts.get(selected).getUsername());
            setStatus("Copied username.");
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        cancelTask();
        MinecraftClient.getInstance().setScreen(previous);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void startMicrosoftLogin() {
        if (isBusy()) {
            return;
        }
        ExecutorService worker = worker();
        MicrosoftAuth.CLIENT_ID = MicrosoftAuth.DEFAULT_CLIENT_ID;
        MicrosoftAuth.SCOPE = MicrosoftAuth.DEFAULT_SCOPE;
        String state = MicrosoftAuth.newState();
        URI uri = MicrosoftAuth.getMSAuthLink(state);
        MinecraftClient client = MinecraftClient.getInstance();
        client.keyboard.setClipboard(uri.toString());
        Util.getOperatingSystem().open(uri);
        setStatus("Login link copied and opened. Waiting for Microsoft callback...");

        AtomicReference<String> refreshToken = new AtomicReference<>("");
        AtomicReference<String> accessToken = new AtomicReference<>("");
        task = MicrosoftAuth.acquireMSAuthCode(state, worker)
                .thenComposeAsync(code -> {
                    setStatus("Acquiring Microsoft access tokens...");
                    return MicrosoftAuth.acquireMSAccessTokens(code, worker);
                }, worker)
                .thenComposeAsync(tokens -> {
                    refreshToken.set(tokens.get("refresh_token"));
                    setStatus("Acquiring Xbox access token...");
                    return MicrosoftAuth.acquireXboxAccessToken(tokens.get("access_token"), worker);
                }, worker)
                .thenComposeAsync(xboxToken -> {
                    setStatus("Acquiring Xbox XSTS token...");
                    return MicrosoftAuth.acquireXboxXstsToken(xboxToken, worker);
                }, worker)
                .thenComposeAsync(xsts -> {
                    setStatus("Acquiring Minecraft access token...");
                    return MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), worker);
                }, worker)
                .thenComposeAsync(mcToken -> {
                    accessToken.set(mcToken);
                    setStatus("Fetching Minecraft profile...");
                    return MicrosoftAuth.login(mcToken, worker);
                }, worker)
                .thenAccept(session -> client.execute(() -> addAccountAndSwitch(
                        refreshToken.get(),
                        accessToken.get(),
                        session,
                        MicrosoftAuth.DEFAULT_CLIENT_ID,
                        MicrosoftAuth.DEFAULT_SCOPE
                )))
                .exceptionally(error -> {
                    setStatus("Error: " + errorMessage(error));
                    return null;
                });
    }

    void addAccessToken(String token) {
        if (isBusy() || token == null || token.isBlank()) {
            return;
        }
        ExecutorService worker = worker();
        MinecraftClient client = MinecraftClient.getInstance();
        MicrosoftAuth.CLIENT_ID = MicrosoftAuth.TOKEN_CLIENT_ID;
        MicrosoftAuth.SCOPE = MicrosoftAuth.TOKEN_SCOPE;
        setStatus("Trying Minecraft access token...");

        AtomicBoolean refreshed = new AtomicBoolean(false);
        AtomicReference<String> refreshToken = new AtomicReference<>(token);
        AtomicReference<String> accessToken = new AtomicReference<>(token);
        task = MicrosoftAuth.login(token, worker)
                .handle((session, error) -> {
                    if (session != null) {
                        return CompletableFuture.completedFuture(session);
                    }
                    setStatus("Refreshing Microsoft access tokens...");
                    return MicrosoftAuth.refreshMSAccessTokens(token, worker)
                            .thenComposeAsync(tokens -> {
                                refreshed.set(true);
                                refreshToken.set(tokens.get("refresh_token"));
                                setStatus("Acquiring Xbox access token...");
                                return MicrosoftAuth.acquireXboxAccessToken(tokens.get("access_token"), worker);
                            }, worker)
                            .thenComposeAsync(xboxToken -> {
                                setStatus("Acquiring Xbox XSTS token...");
                                return MicrosoftAuth.acquireXboxXstsToken(xboxToken, worker);
                            }, worker)
                            .thenComposeAsync(xsts -> {
                                setStatus("Acquiring Minecraft access token...");
                                return MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), worker);
                            }, worker)
                            .thenComposeAsync(mcToken -> {
                                accessToken.set(mcToken);
                                setStatus("Fetching Minecraft profile...");
                                return MicrosoftAuth.login(mcToken, worker);
                            }, worker);
                })
                .thenCompose(future -> future)
                .thenAccept(session -> client.execute(() -> addAccountAndSwitch(
                        refreshed.get() ? refreshToken.get() : "",
                        accessToken.get(),
                        session,
                        MicrosoftAuth.TOKEN_CLIENT_ID,
                        MicrosoftAuth.TOKEN_SCOPE
                )))
                .exceptionally(error -> {
                    setStatus("Error: " + errorMessage(error));
                    return null;
                });
    }

    void addEmailPassword(String email, String password) {
        if (isBusy() || email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        ExecutorService worker = worker();
        MinecraftClient client = MinecraftClient.getInstance();
        MicrosoftAuth.CLIENT_ID = MicrosoftAuth.DESKTOP_CLIENT_ID;
        MicrosoftAuth.SCOPE = MicrosoftAuth.TOKEN_SCOPE;
        setStatus("Logging in with email and password...");

        AtomicReference<String> refreshToken = new AtomicReference<>("");
        AtomicReference<String> accessToken = new AtomicReference<>("");
        task = MicrosoftAuth.acquireRefreshTokenWithCredentials(email, password, worker)
                .thenComposeAsync(token -> {
                    refreshToken.set(token);
                    setStatus("Refreshing Microsoft access tokens...");
                    return MicrosoftAuth.refreshMSAccessTokens(token, worker);
                }, worker)
                .thenComposeAsync(tokens -> {
                    refreshToken.set(tokens.get("refresh_token"));
                    setStatus("Acquiring Xbox access token...");
                    return MicrosoftAuth.acquireXboxAccessToken(tokens.get("access_token"), worker);
                }, worker)
                .thenComposeAsync(xboxToken -> {
                    setStatus("Acquiring Xbox XSTS token...");
                    return MicrosoftAuth.acquireXboxXstsToken(xboxToken, worker);
                }, worker)
                .thenComposeAsync(xsts -> {
                    setStatus("Acquiring Minecraft access token...");
                    return MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), worker);
                }, worker)
                .thenComposeAsync(mcToken -> {
                    accessToken.set(mcToken);
                    setStatus("Fetching Minecraft profile...");
                    return MicrosoftAuth.login(mcToken, worker);
                }, worker)
                .thenAccept(session -> client.execute(() -> addAccountAndSwitch(
                        refreshToken.get(),
                        accessToken.get(),
                        session,
                        MicrosoftAuth.DESKTOP_CLIENT_ID,
                        MicrosoftAuth.TOKEN_SCOPE
                )))
                .exceptionally(error -> {
                    setStatus("Error: " + errorMessage(error));
                    return null;
                });
    }

    void addOfflineAccount(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!OFFLINE_USERNAME_PATTERN.matcher(username).matches()) {
            setStatus("Error: Offline username must be 3-16 letters, numbers, or underscores.");
            return;
        }

        Account account = new Account(
                "",
                "",
                username,
                preservedUnban(username),
                "",
                "",
                true
        );
        AccountManager.accounts.add(account);
        selected = AccountManager.accounts.size() - 1;
        keepSelectionVisible();
        AccountManager.save();
        SessionService.SwitchResult result = SessionService.switchToOffline(username);
        setStatus(result.message());
    }

    private void useSelected() {
        if (!canUseSelection() || isBusy()) {
            return;
        }
        Account account = AccountManager.accounts.get(selected);
        if (account.isOffline()) {
            SessionService.SwitchResult result = SessionService.switchToOffline(account.getUsername());
            setStatus(result.message());
            return;
        }
        ExecutorService worker = worker();
        MicrosoftAuth.CLIENT_ID = blankToDefault(account.getClientId(), MicrosoftAuth.DEFAULT_CLIENT_ID);
        MicrosoftAuth.SCOPE = blankToDefault(account.getScope(), MicrosoftAuth.DEFAULT_SCOPE);
        setStatus("Fetching Minecraft profile... (" + displayUsername(account) + ")");

        AtomicBoolean refreshed = new AtomicBoolean(false);
        AtomicReference<String> refreshToken = new AtomicReference<>(account.getRefreshToken());
        AtomicReference<String> accessToken = new AtomicReference<>(account.getAccessToken());
        task = MicrosoftAuth.login(account.getAccessToken(), worker)
                .handle((session, error) -> {
                    if (session != null) {
                        return CompletableFuture.completedFuture(session);
                    }
                    setStatus("Refreshing Microsoft access tokens... (" + displayUsername(account) + ")");
                    return MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), worker)
                            .thenComposeAsync(tokens -> {
                                refreshed.set(true);
                                refreshToken.set(tokens.get("refresh_token"));
                                setStatus("Acquiring Xbox access token... (" + displayUsername(account) + ")");
                                return MicrosoftAuth.acquireXboxAccessToken(tokens.get("access_token"), worker);
                            }, worker)
                            .thenComposeAsync(xboxToken -> {
                                setStatus("Acquiring Xbox XSTS token... (" + displayUsername(account) + ")");
                                return MicrosoftAuth.acquireXboxXstsToken(xboxToken, worker);
                            }, worker)
                            .thenComposeAsync(xsts -> {
                                setStatus("Acquiring Minecraft access token... (" + displayUsername(account) + ")");
                                return MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), worker);
                            }, worker)
                            .thenComposeAsync(mcToken -> {
                                accessToken.set(mcToken);
                                setStatus("Fetching Minecraft profile... (" + displayUsername(account) + ")");
                                return MicrosoftAuth.login(mcToken, worker);
                            }, worker);
                })
                .thenCompose(future -> future)
                .thenAccept(session -> MinecraftClient.getInstance().execute(() -> {
                    if (refreshed.get()) {
                        account.setRefreshToken(refreshToken.get());
                        account.setAccessToken(accessToken.get());
                    }
                    account.setUsername(session.getUsername());
                    AccountManager.save();
                    SessionService.SwitchResult result = SessionService.switchTo(session);
                    setStatus(result.message());
                }))
                .exceptionally(error -> {
                    setStatus("Error: " + errorMessage(error));
                    return null;
                });
    }

    private void addAccountAndSwitch(String refreshToken, String accessToken, Session session, String clientId, String scope) {
        Account account = new Account(refreshToken, accessToken, session.getUsername(), preservedUnban(session.getUsername()), clientId, scope, false);
        AccountManager.accounts.add(account);
        selected = AccountManager.accounts.size() - 1;
        keepSelectionVisible();
        AccountManager.save();
        SessionService.SwitchResult result = SessionService.switchTo(session);
        setStatus(result.message());
    }

    private long preservedUnban(String username) {
        for (Account account : AccountManager.accounts) {
            if (username.equals(account.getUsername())) {
                return account.getUnban();
            }
        }
        return 0L;
    }

    private void deleteSelected() {
        if (!canUseSelection()) {
            return;
        }
        AccountManager.accounts.remove(selected);
        selected = Math.min(selected, AccountManager.accounts.size() - 1);
        AccountManager.save();
        clampScroll();
        setStatus("Deleted account.");
    }

    private void moveSelected(int direction) {
        int target = selected + direction;
        if (selected < 0 || target < 0 || target >= AccountManager.accounts.size()) {
            return;
        }
        Collections.swap(AccountManager.accounts, selected, target);
        selected = target;
        keepSelectionVisible();
        AccountManager.save();
        setStatus("Moved account.");
    }

    private ExecutorService worker() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Omix Account Manager");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    private void cancelTask() {
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private boolean isBusy() {
        return task != null && !task.isDone();
    }

    private boolean canUseSelection() {
        return !isBusy() && selected >= 0 && selected < AccountManager.accounts.size();
    }

    private boolean canMoveUp() {
        return canUseSelection() && selected > 0;
    }

    private boolean canMoveDown() {
        return canUseSelection() && selected < AccountManager.accounts.size() - 1;
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 42) / (ROW_HEIGHT + ROW_GAP));
    }

    private int rowAt(int y) {
        int local = y - LIST_TOP;
        if (local < 0) {
            return -1;
        }
        int slot = local / (ROW_HEIGHT + ROW_GAP);
        if (local % (ROW_HEIGHT + ROW_GAP) >= ROW_HEIGHT) {
            return -1;
        }
        return scroll + slot;
    }

    private void keepSelectionVisible() {
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + visibleRows()) {
            scroll = selected - visibleRows() + 1;
        }
        clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, AccountManager.accounts.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private String displayUsername(Account account) {
        String username = account.getUsername();
        String display = username == null || username.isBlank() ? "(unnamed account)" : username;
        return account.isOffline() ? "[Offline] " + display : display;
    }

    private boolean isCurrentAccount(Account account) {
        Session current = SessionService.current();
        if (account.isOffline()) {
            return current.getAccessToken().isEmpty()
                    && account.getUsername().equals(current.getUsername());
        }
        return !account.getAccessToken().isEmpty()
                && account.getAccessToken().equals(current.getAccessToken());
    }

    private String banText(Account account) {
        long unban = account.getUnban();
        if (unban < 0L) {
            return "permanent ban";
        }
        long now = System.currentTimeMillis();
        if (unban <= now) {
            return "available";
        }
        long diff = unban - now;
        long seconds = (diff / 1000L) % 60L;
        long minutes = (diff / 60000L) % 60L;
        long hours = (diff / 3600000L) % 24L;
        long days = diff / 86400000L;
        return (days > 0L ? days + "d " : "")
                + (hours > 0L ? hours + "h " : "")
                + (minutes > 0L ? minutes + "m " : "")
                + (seconds > 0L ? seconds + "s" : "");
    }

    private int banColor(Account account) {
        if (account.getUnban() < 0L) {
            return 0xFFFF5555;
        }
        return account.getUnban() > System.currentTimeMillis() ? 0xFFFFD36B : 0xFF71E58B;
    }

    private void renderButton(DrawContext context, int x1, int y1, int x2, int y2, String text, boolean enabled) {
        context.fill(x1, y1, x2, y2, enabled ? 0xCC1D2B38 : 0x88404A52);
        drawString(context, text, x1 + 6, y1 + 6, enabled ? 0xFFFFFFFF : 0xFF9A9A9A, true);
    }

    private boolean inside(int x, int y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    private static void drawString(DrawContext context, String text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(text), x, y, color);
        } else {
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(text), x, y, color, false);
        }
    }

    private static boolean hasControlDown(KeyInput input) {
        int modifiers = input.modifiers();
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
    }

    private void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    private String errorMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static class TokenInputScreen extends Screen {
        private final AccountManagerScreen parent;
        private TextFieldWidget tokenField;

        TokenInputScreen(AccountManagerScreen parent) {
            super(Text.literal("Access Token"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            tokenField = new TextFieldWidget(textRenderer, width / 2 - 150, height / 2 - 10, 300, 20, Text.literal("Access token"));
            tokenField.setMaxLength(32767);
            tokenField.setFocused(true);
            addDrawableChild(tokenField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> submit())
                    .dimensions(width / 2 - 102, height / 2 + 22, 100, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                    .dimensions(width / 2 + 2, height / 2 + 22, 100, 20)
                    .build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            drawString(context, "Access Token Login", width / 2 - 70, height / 2 - 40, 0xFFFFFFFF, true);
        }

        @Override
        public boolean keyPressed(KeyInput input) {
            int keyCode = input.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submit();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        private void submit() {
            String token = tokenField.getText();
            MinecraftClient.getInstance().setScreen(parent);
            parent.addAccessToken(token);
        }
    }

    private static class OfflineAccountScreen extends Screen {
        private final AccountManagerScreen parent;
        private TextFieldWidget usernameField;

        OfflineAccountScreen(AccountManagerScreen parent) {
            super(Text.literal("Offline Account"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            usernameField = new TextFieldWidget(
                    textRenderer,
                    width / 2 - 100,
                    height / 2 - 10,
                    200,
                    20,
                    Text.literal("Username")
            );
            usernameField.setMaxLength(16);
            usernameField.setPlaceholder(Text.literal("Offline username"));
            usernameField.setFocused(true);
            addDrawableChild(usernameField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> submit())
                    .dimensions(width / 2 - 102, height / 2 + 22, 100, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                    .dimensions(width / 2 + 2, height / 2 + 22, 100, 20)
                    .build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            drawString(context, "Add Offline Account", width / 2 - 72, height / 2 - 40, 0xFFFFFFFF, true);
        }

        @Override
        public boolean keyPressed(KeyInput input) {
            int keyCode = input.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submit();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        private void submit() {
            String username = usernameField.getText();
            MinecraftClient.getInstance().setScreen(parent);
            parent.addOfflineAccount(username);
        }
    }

    private static class EmailLoginScreen extends Screen {
        private final AccountManagerScreen parent;
        private TextFieldWidget emailField;
        private TextFieldWidget passwordField;

        EmailLoginScreen(AccountManagerScreen parent) {
            super(Text.literal("Email Login"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            emailField = new TextFieldWidget(textRenderer, width / 2 - 150, height / 2 - 34, 300, 20, Text.literal("Email"));
            emailField.setMaxLength(320);
            emailField.setPlaceholder(Text.literal("email@example.com"));
            passwordField = new TextFieldWidget(textRenderer, width / 2 - 150, height / 2 - 8, 300, 20, Text.literal("Password"));
            passwordField.setMaxLength(1024);
            passwordField.setPlaceholder(Text.literal("password"));
            passwordField.addFormatter((text, offset) -> OrderedText.styledForwardsVisitedString("*".repeat(text.length()), net.minecraft.text.Style.EMPTY));
            emailField.setFocused(true);
            addDrawableChild(emailField);
            addDrawableChild(passwordField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Login"), button -> submit())
                    .dimensions(width / 2 - 102, height / 2 + 28, 100, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                    .dimensions(width / 2 + 2, height / 2 + 28, 100, 20)
                    .build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            drawString(context, "Email Password Login", width / 2 - 78, height / 2 - 64, 0xFFFFFFFF, true);
        }

        @Override
        public boolean keyPressed(KeyInput input) {
            int keyCode = input.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submit();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        private void submit() {
            String email = emailField.getText();
            String password = passwordField.getText();
            MinecraftClient.getInstance().setScreen(parent);
            parent.addEmailPassword(email, password);
        }
    }
}
