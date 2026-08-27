package cn.omix.ui.screen.impl.proxy;

import cn.omix.fisproxy.FisProxyConnector;
import cn.omix.fisproxy.FisProxyFormatter;
import cn.omix.fisproxy.FisProxyManager;
import cn.omix.ui.font.TrueTypeFont;
import cn.omix.ui.screen.AbstractScreen;
import cn.omix.ui.screen.util.AdaptiveButton;
import cn.omix.ui.screen.util.AdaptiveTextBox;
import cn.omix.util.render.Render2D;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.fisproxy.ChangeIpOptions;
import org.fisproxy.FisProxyException;
import org.fisproxy.ListOperationsOptions;
import org.fisproxy.Operation;
import org.fisproxy.RequestOptions;
import org.fisproxy.SessionStatus;
import org.fisproxy.StartOptions;
import org.fisproxy.WaitOptions;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProxyScreen extends AbstractScreen {
    private static final Gson GSON = new Gson();
    private static final int COLOR_IDLE = new Color(160, 160, 160).getRGB();
    private static final int COLOR_OK = new Color(85, 255, 85).getRGB();
    private static final int COLOR_ERROR = new Color(255, 85, 85).getRGB();
    private static final int COLOR_BUSY = new Color(85, 190, 255).getRGB();

    private final Screen parent;
    private final List<FieldLabel> fieldLabels = new ArrayList<>();

    private ProxyTab tab = ProxyTab.NORMAL;
    private FisPage fisPage = FisPage.SESSION;
    private float panelLeft;
    private float panelTop;
    private float panelWidth;
    private float contentLeft;
    private float contentTop;
    private float contentWidth;
    private float outputY;

    private AdaptiveTextBox ipBox;
    private AdaptiveTextBox portBox;
    private AdaptiveTextBox usernameBox;
    private AdaptiveTextBox passwordBox;

    private AdaptiveTextBox apiKeyBox;
    private AdaptiveTextBox baseUrlBox;
    private AdaptiveTextBox clientIdBox;
    private AdaptiveTextBox httpTimeoutBox;
    private AdaptiveTextBox serviceFilterBox;

    private AdaptiveTextBox serviceIdBox;
    private AdaptiveTextBox targetBox;
    private AdaptiveTextBox autoNfaBox;
    private AdaptiveTextBox tryPreviousNfaBox;
    private AdaptiveTextBox waitBox;
    private AdaptiveTextBox waitRouteAckBox;
    private AdaptiveTextBox nfaItemIdBox;
    private AdaptiveTextBox reuseNfaItemIdBox;
    private AdaptiveTextBox nfaSourceBox;
    private AdaptiveTextBox nfaSkuBox;
    private AdaptiveTextBox idempotencyKeyBox;
    private AdaptiveTextBox operationTimeoutBox;
    private AdaptiveTextBox intervalBox;

    private AdaptiveTextBox operationIdBox;
    private AdaptiveTextBox operationStatusBox;
    private AdaptiveTextBox operationKindBox;
    private AdaptiveTextBox operationLimitBox;
    private AdaptiveTextBox waitTimeoutBox;
    private AdaptiveTextBox waitIntervalBox;

    private AdaptiveTextBox rawMethodBox;
    private AdaptiveTextBox rawPathBox;
    private AdaptiveTextBox rawQueryBox;
    private AdaptiveTextBox rawBodyBox;
    private AdaptiveTextBox rawIdempotencyBox;
    private AdaptiveTextBox rawSignBox;

    private volatile boolean busy;
    private String statusMessage = "No Proxy Set";
    private int statusColor = COLOR_IDLE;
    private List<String> detailLines = List.of();
    private String lastFisAddress;

    @Getter
    private static Proxy proxy;

    public ProxyScreen(Screen parent) {
        super("Proxy Screen");
        this.parent = parent;
    }

    @Override
    protected void initScreen() {
        fieldLabels.clear();
        panelWidth = Math.min(Math.max(300f, width - 24f), 820f);
        panelLeft = (width - panelWidth) / 2f;
        panelTop = 18f;
        float sidebarWidth = Math.min(112f, panelWidth * 0.25f);
        contentLeft = panelLeft + sidebarWidth + 12f;
        contentWidth = panelWidth - sidebarWidth - 12f;
        contentTop = panelTop + 58f;

        addButton(tab == ProxyTab.NORMAL ? "• SOCKS5 Proxy" : "SOCKS5 Proxy",
                panelLeft, panelTop + 40f, sidebarWidth, 22f, () -> selectTab(ProxyTab.NORMAL));
        addButton(tab == ProxyTab.FISPROXY ? "• FisProxy" : "FisProxy",
                panelLeft, panelTop + 68f, sidebarWidth, 22f, () -> selectTab(ProxyTab.FISPROXY));
        addButton("Back", panelLeft, Math.max(panelTop + 102f, height - 36f), sidebarWidth, 22f,
                () -> mc.setScreen(parent));

        if (tab == ProxyTab.NORMAL) {
            buildNormalProxy();
        } else {
            buildFisProxy();
        }
    }

    private void buildNormalProxy() {
        float row1 = contentTop + 28f;
        float row2 = row1 + 40f;
        float wide = contentWidth * 0.66f;

        ipBox = addField("IP address", contentLeft, row1, wide - 5f, false, proxy == null ? "" : proxy.host);
        portBox = addField("Port", contentLeft + wide + 5f, row1, contentWidth - wide - 5f,
                false, proxy == null ? "" : String.valueOf(proxy.port));
        usernameBox = addField("Username (optional)", contentLeft, row2, contentWidth / 2f - 5f,
                false, proxy == null || proxy.username == null ? "" : proxy.username);
        passwordBox = addField("Password (optional)", contentLeft + contentWidth / 2f + 5f, row2,
                contentWidth / 2f - 5f, true, proxy == null || proxy.password == null ? "" : proxy.password);

        float buttonY = row2 + 40f;
        addButton("Set Proxy", contentLeft, buttonY, contentWidth / 2f - 5f, 22f, this::handleSetProxy);
        addButton("Clear Proxy", contentLeft + contentWidth / 2f + 5f, buttonY,
                contentWidth / 2f - 5f, 22f, this::handleClearProxy);
        outputY = buttonY + 38f;

        if (proxy != null) {
            statusMessage = "Current Proxy: " + proxy.host + ":" + proxy.port;
            statusColor = COLOR_OK;
        } else if (!statusMessage.startsWith("Proxy")) {
            statusMessage = "No Proxy Set";
            statusColor = COLOR_IDLE;
        }
    }

    private void buildFisProxy() {
        float navY = panelTop + 36f;
        float gap = 4f;
        float navWidth = (contentWidth - gap * 3f) / 4f;
        addButton(fisPage == FisPage.SESSION ? "• Session" : "Session", contentLeft, navY, navWidth, 22f,
                () -> selectFisPage(FisPage.SESSION));
        addButton(fisPage == FisPage.START ? "• Start / IP" : "Start / IP", contentLeft + navWidth + gap,
                navY, navWidth, 22f, () -> selectFisPage(FisPage.START));
        addButton(fisPage == FisPage.OPERATIONS ? "• Operations" : "Operations",
                contentLeft + (navWidth + gap) * 2f, navY, navWidth, 22f,
                () -> selectFisPage(FisPage.OPERATIONS));
        addButton(fisPage == FisPage.ADVANCED ? "• Advanced" : "Advanced",
                contentLeft + (navWidth + gap) * 3f, navY, navWidth, 22f,
                () -> selectFisPage(FisPage.ADVANCED));

        switch (fisPage) {
            case SESSION -> buildFisSessionPage();
            case START -> buildFisStartPage();
            case OPERATIONS -> buildFisOperationsPage();
            case ADVANCED -> buildFisAdvancedPage();
        }
    }

    private void buildFisSessionPage() {
        FisProxyManager manager = manager();
        float row1 = contentTop;
        float row2 = row1 + 38f;
        float row3 = row2 + 38f;

        apiKeyBox = addField("API key (blank keeps current)", contentLeft, row1, contentWidth * 0.35f - 5f,
                true, "");
        baseUrlBox = addField("API base URL", contentLeft + contentWidth * 0.35f + 5f, row1,
                contentWidth * 0.65f - 5f, false, manager.getBaseUrl());
        clientIdBox = addField("Stable client ID", contentLeft, row2, contentWidth * 0.72f - 5f,
                false, manager.getClientId());
        httpTimeoutBox = addField("HTTP timeout (seconds)", contentLeft + contentWidth * 0.72f + 5f, row2,
                contentWidth * 0.28f - 5f, false, String.valueOf(manager.getTimeoutSeconds()));
        serviceFilterBox = addField("Service ID for entrances (optional)", contentLeft, row3, contentWidth,
                false, "");

        float buttonY = row3 + 38f;
        addButtonRow(buttonY,
                new ButtonSpec("Save Settings", this::saveFisSettings),
                new ButtonSpec("Clear Key", this::clearFisKey),
                new ButtonSpec("Account", this::loadProfile),
                new ButtonSpec("Services", this::loadServices));
        addButtonRow(buttonY + 28f,
                new ButtonSpec("Status", this::loadStatus),
                new ButtonSpec("Entrances", this::loadEntrances),
                new ButtonSpec("Copy Address", this::copyAddress),
                new ButtonSpec("Connect", this::connectFisProxy));
        outputY = buttonY + 64f;
        if (detailLines.isEmpty()) {
            detailLines = List.of(
                    "API key: " + (manager.hasApiKey() ? "configured" : "not configured"),
                    "Use Account or Status to verify the current session."
            );
        }
    }

    private void buildFisStartPage() {
        float row1 = contentTop;
        float row2 = row1 + 38f;
        float row3 = row2 + 38f;
        float row4 = row3 + 38f;
        float row5 = row4 + 38f;

        serviceIdBox = addField("Service ID (optional)", contentLeft, row1, contentWidth * 0.4f - 5f, false, "");
        targetBox = addField("Target, e.g. mc.hypixel.net", contentLeft + contentWidth * 0.4f + 5f, row1,
                contentWidth * 0.6f - 5f, false, "");

        float sixth = (contentWidth - 25f) / 6f;
        autoNfaBox = addField("autoNfa", contentLeft, row2, sixth, false, "default");
        tryPreviousNfaBox = addField("tryPreviousNfa", contentLeft + (sixth + 5f), row2, sixth, false, "default");
        waitBox = addField("wait", contentLeft + (sixth + 5f) * 2f, row2, sixth, false, "true");
        waitRouteAckBox = addField("waitRouteAck", contentLeft + (sixth + 5f) * 3f, row2, sixth, false, "true");
        operationTimeoutBox = addField("timeout", contentLeft + (sixth + 5f) * 4f, row2, sixth, false, "180");
        intervalBox = addField("interval", contentLeft + (sixth + 5f) * 5f, row2, sixth, false, "1");

        nfaItemIdBox = addField("NFA item ID (optional)", contentLeft, row3, contentWidth / 2f - 5f, false, "");
        reuseNfaItemIdBox = addField("Reuse NFA item ID (optional)", contentLeft + contentWidth / 2f + 5f,
                row3, contentWidth / 2f - 5f, false, "");
        nfaSourceBox = addField("NFA source: local/solar", contentLeft, row4, contentWidth * 0.35f - 5f,
                false, "");
        nfaSkuBox = addField("NFA SKU (optional)", contentLeft + contentWidth * 0.35f + 5f, row4,
                contentWidth * 0.65f - 5f, false, "");
        idempotencyKeyBox = addField("Idempotency key (optional)", contentLeft, row5, contentWidth,
                false, "");

        float buttonY = row5 + 38f;
        addButtonRow(buttonY,
                new ButtonSpec("Start", this::startFisSession),
                new ButtonSpec("Change IP", this::changeFisIp),
                new ButtonSpec("Stop", this::stopFisSession),
                new ButtonSpec("Refresh Status", this::loadStatus));
        outputY = buttonY + 36f;
    }

    private void buildFisOperationsPage() {
        float row1 = contentTop;
        float row2 = row1 + 38f;

        operationIdBox = addField("Operation ID", contentLeft, row1, contentWidth * 0.55f - 5f, false, "");
        waitTimeoutBox = addField("Wait timeout", contentLeft + contentWidth * 0.55f + 5f, row1,
                contentWidth * 0.225f - 5f, false, "180");
        waitIntervalBox = addField("Poll interval", contentLeft + contentWidth * 0.775f + 5f, row1,
                contentWidth * 0.225f - 5f, false, "1");

        operationStatusBox = addField("List status (optional)", contentLeft, row2, contentWidth / 3f - 5f,
                false, "");
        operationKindBox = addField("List kind (optional)", contentLeft + contentWidth / 3f + 2.5f, row2,
                contentWidth / 3f - 5f, false, "");
        operationLimitBox = addField("List limit", contentLeft + contentWidth * 2f / 3f + 5f, row2,
                contentWidth / 3f - 5f, false, "20");

        float buttonY = row2 + 38f;
        addButtonRow(buttonY,
                new ButtonSpec("Get", this::getOperation),
                new ButtonSpec("Wait", this::waitOperation),
                new ButtonSpec("Cancel", this::cancelOperation),
                new ButtonSpec("List", this::listOperations));
        outputY = buttonY + 38f;
    }

    private void buildFisAdvancedPage() {
        float row1 = contentTop;
        float row2 = row1 + 38f;
        float row3 = row2 + 38f;
        float row4 = row3 + 38f;

        rawMethodBox = addField("HTTP method", contentLeft, row1, contentWidth * 0.2f - 5f, false, "GET");
        rawPathBox = addField("Absolute API path", contentLeft + contentWidth * 0.2f + 5f, row1,
                contentWidth * 0.8f - 5f, false, "/api/v1/sessions/status");
        rawQueryBox = addField("Query JSON object (optional)", contentLeft, row2, contentWidth, false, "");
        rawBodyBox = addField("Body JSON object (optional)", contentLeft, row3, contentWidth, false, "");
        rawIdempotencyBox = addField("Idempotency key (optional)", contentLeft, row4,
                contentWidth * 0.7f - 5f, false, "");
        rawSignBox = addField("Sign: default/true/false", contentLeft + contentWidth * 0.7f + 5f, row4,
                contentWidth * 0.3f - 5f, false, "default");

        float buttonY = row4 + 38f;
        addButton("Send SDK Request", contentLeft, buttonY, contentWidth, 22f, this::sendRawRequest);
        outputY = buttonY + 38f;
    }

    private AdaptiveTextBox addField(String label, float x, float y, float fieldWidth, boolean password, String value) {
        fieldLabels.add(new FieldLabel(label, x, y));
        AdaptiveTextBox box = new AdaptiveTextBox(label);
        box.setPasswordMode(password);
        box.setBounds(x, y + 11f, Math.max(36f, fieldWidth), 21f);
        box.setText(value == null ? "" : value);
        textBoxes.add(box);
        return box;
    }

    private void addButton(String text, float x, float y, float buttonWidth, float height, Runnable action) {
        AdaptiveButton button = new AdaptiveButton(text, action);
        button.setBounds(x, y, Math.max(36f, buttonWidth), height);
        buttons.add(button);
    }

    private void addButtonRow(float y, ButtonSpec... specs) {
        float gap = 5f;
        float buttonWidth = (contentWidth - gap * (specs.length - 1)) / specs.length;
        for (int index = 0; index < specs.length; index++) {
            ButtonSpec spec = specs[index];
            addButton(spec.label(), contentLeft + index * (buttonWidth + gap), y, buttonWidth, 22f, spec.action());
        }
    }

    private void selectTab(ProxyTab selected) {
        if (tab == selected) return;
        tab = selected;
        detailLines = List.of();
        statusMessage = selected == ProxyTab.NORMAL ? "SOCKS5 proxy settings" : "FisProxy session control";
        statusColor = COLOR_IDLE;
        clearAndInit();
    }

    private void selectFisPage(FisPage selected) {
        if (fisPage == selected) return;
        fisPage = selected;
        clearAndInit();
    }

    private void handleSetProxy() {
        String ip = ipBox.getText().trim();
        String portText = portBox.getText().trim();
        if (ip.isEmpty() || portText.isEmpty()) {
            setError("IP and port are required.");
            return;
        }
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            proxy = new Proxy(ip, port, blankToNull(usernameBox.getText()), blankToNull(passwordBox.getText()));
            statusMessage = "Proxy Set: " + ip + ":" + port;
            statusColor = COLOR_OK;
            detailLines = List.of("New Minecraft connections will use this SOCKS5 proxy.");
        } catch (NumberFormatException exception) {
            setError("Port must be a number from 1 to 65535.");
        }
    }

    private void handleClearProxy() {
        proxy = null;
        for (AdaptiveTextBox box : textBoxes) box.setText("");
        statusMessage = "Proxy Cleared";
        statusColor = COLOR_IDLE;
        detailLines = List.of();
    }

    private void saveFisSettings() {
        try {
            manager().updateSettings(blankToNull(apiKeyBox.getText()), baseUrlBox.getText(), clientIdBox.getText(),
                    parseInt(httpTimeoutBox.getText(), "HTTP timeout"));
            apiKeyBox.setText("");
            statusMessage = "FisProxy settings saved";
            statusColor = COLOR_OK;
            detailLines = List.of("API key: " + (manager().hasApiKey() ? "configured" : "not configured"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            setError(exception.getMessage());
        }
    }

    private void clearFisKey() {
        try {
            manager().setApiKey("");
            if (apiKeyBox != null) apiKeyBox.setText("");
            statusMessage = "FisProxy API key cleared";
            statusColor = COLOR_IDLE;
            detailLines = List.of("API calls are disabled until a new key is saved.");
        } catch (IllegalStateException exception) {
            setError(exception.getMessage());
        }
    }

    private void loadProfile() {
        runAction("Loading account...", () -> manager().me(), FisProxyFormatter::profile);
    }

    private void loadServices() {
        runAction("Loading services...", () -> manager().services(), FisProxyFormatter::services);
    }

    private void loadStatus() {
        runAction("Loading session status...", () -> manager().status(), this::formatStatus);
    }

    private void loadEntrances() {
        String serviceId = serviceFilterBox == null ? null : blankToNull(serviceFilterBox.getText());
        runAction("Loading entrances...", () -> manager().entrances(serviceId), FisProxyFormatter::entrances);
    }

    private void startFisSession() {
        runAction("Starting FisProxy session...", () -> manager().start(buildStartOptions()), operation -> {
            rememberAddress(operation);
            return FisProxyFormatter.operation(operation);
        });
    }

    private void changeFisIp() {
        runAction("Changing exit IP...", () -> manager().changeIp(buildChangeIpOptions()), FisProxyFormatter::operation);
    }

    private void stopFisSession() {
        runAction("Stopping FisProxy session...", () -> manager().stop(), result -> {
            lastFisAddress = null;
            return FisProxyFormatter.stopped(result);
        });
    }

    private void copyAddress() {
        if (lastFisAddress == null || lastFisAddress.isBlank()) {
            runAction("Loading address...", () -> manager().status(), status -> {
                List<String> lines = formatStatus(status);
                if (lastFisAddress == null || lastFisAddress.isBlank()) {
                    throw new IllegalStateException("No running session address is available.");
                }
                mc.keyboard.setClipboard(lastFisAddress);
                List<String> result = new ArrayList<>(lines);
                result.add("Copied: " + lastFisAddress);
                return result;
            });
            return;
        }
        mc.keyboard.setClipboard(lastFisAddress);
        statusMessage = "Address copied";
        statusColor = COLOR_OK;
    }

    private void connectFisProxy() {
        runAction("Resolving connection address...", () -> manager().status(), status -> {
            formatStatus(status);
            if (!status.running() || lastFisAddress == null || lastFisAddress.isBlank()) {
                throw new IllegalStateException("No running FisProxy session address is available.");
            }
            String address = lastFisAddress;
            FisProxyConnector.connect(mc, address);
            return List.of("Connecting to " + address);
        });
    }

    private void getOperation() {
        runAction("Loading operation...", () -> manager().getOperation(operationId()), FisProxyFormatter::operation);
    }

    private void waitOperation() {
        runAction("Waiting for operation...", () -> manager().waitOperation(operationId(), WaitOptions.builder()
                .timeoutSeconds(parsePositiveDouble(waitTimeoutBox.getText(), "wait timeout"))
                .intervalSeconds(parsePositiveDouble(waitIntervalBox.getText(), "poll interval"))
                .build()), FisProxyFormatter::operation);
    }

    private void cancelOperation() {
        runAction("Canceling operation...", () -> manager().cancelOperation(operationId()), FisProxyFormatter::operation);
    }

    private void listOperations() {
        runAction("Loading operations...", () -> manager().listOperations(ListOperationsOptions.builder()
                .status(blankToNull(operationStatusBox.getText()))
                .kind(blankToNull(operationKindBox.getText()))
                .limit(parseOptionalInt(operationLimitBox.getText(), "operation limit"))
                .build()), FisProxyFormatter::operations);
    }

    private void sendRawRequest() {
        runAction("Sending signed SDK request...", () -> {
            RequestOptions.Builder options = RequestOptions.builder();
            String query = rawQueryBox.getText().trim();
            String body = rawBodyBox.getText().trim();
            if (!query.isEmpty()) options.query(parseJsonObject(query, "query"));
            if (!body.isEmpty()) options.jsonBody(parseJsonObject(body, "body"));
            String idempotency = blankToNull(rawIdempotencyBox.getText());
            if (idempotency != null) options.idempotencyKey(idempotency);
            Boolean sign = parseOptionalBoolean(rawSignBox.getText(), "sign");
            if (sign != null) options.sign(sign);
            return manager().request(rawMethodBox.getText().trim(), rawPathBox.getText().trim(), options.build());
        }, FisProxyFormatter::raw);
    }

    private StartOptions buildStartOptions() {
        StartOptions.Builder builder = StartOptions.builder();
        String value;
        if ((value = blankToNull(serviceIdBox.getText())) != null) builder.serviceId(value);
        if ((value = blankToNull(targetBox.getText())) != null) builder.target(value);
        builder.autoNfa(parseOptionalBoolean(autoNfaBox.getText(), "autoNfa"));
        builder.tryPreviousNfa(parseOptionalBoolean(tryPreviousNfaBox.getText(), "tryPreviousNfa"));
        builder.wait(parseRequiredBoolean(waitBox.getText(), "wait"));
        if ((value = blankToNull(nfaItemIdBox.getText())) != null) builder.nfaItemId(value);
        if ((value = blankToNull(reuseNfaItemIdBox.getText())) != null) builder.reuseNfaItemId(value);
        if ((value = blankToNull(nfaSourceBox.getText())) != null) builder.nfaSource(value);
        if ((value = blankToNull(nfaSkuBox.getText())) != null) builder.nfaSku(value);
        if ((value = blankToNull(idempotencyKeyBox.getText())) != null) builder.idempotencyKey(value);
        builder.timeoutSeconds(parsePositiveDouble(operationTimeoutBox.getText(), "operation timeout"));
        builder.intervalSeconds(parsePositiveDouble(intervalBox.getText(), "poll interval"));
        return builder.build();
    }

    private ChangeIpOptions buildChangeIpOptions() {
        ChangeIpOptions.Builder builder = ChangeIpOptions.builder()
                .wait(parseRequiredBoolean(waitBox.getText(), "wait"))
                .waitRouteAck(parseRequiredBoolean(waitRouteAckBox.getText(), "waitRouteAck"))
                .timeoutSeconds(parsePositiveDouble(operationTimeoutBox.getText(), "operation timeout"))
                .intervalSeconds(parsePositiveDouble(intervalBox.getText(), "poll interval"));
        String idempotency = blankToNull(idempotencyKeyBox.getText());
        if (idempotency != null) builder.idempotencyKey(idempotency);
        return builder.build();
    }

    private <T> void runAction(String action, Supplier<CompletableFuture<T>> request,
                               Function<T, List<String>> formatter) {
        if (busy) {
            setError("Another FisProxy request is still running.");
            return;
        }

        final CompletableFuture<T> future;
        try {
            future = request.get();
        } catch (RuntimeException exception) {
            setError(errorMessage(exception));
            return;
        }

        busy = true;
        statusMessage = action;
        statusColor = COLOR_BUSY;
        future.whenComplete((result, error) -> mc.execute(() -> {
            busy = false;
            if (error != null) {
                setError(errorMessage(unwrap(error)));
                return;
            }
            try {
                detailLines = List.copyOf(formatter.apply(result));
                statusMessage = "FisProxy request completed";
                statusColor = COLOR_OK;
            } catch (RuntimeException exception) {
                setError(errorMessage(exception));
            }
        }));
    }

    private List<String> formatStatus(SessionStatus status) {
        lastFisAddress = status.running() ? status.address() : null;
        return FisProxyFormatter.status(status);
    }

    private void rememberAddress(Operation operation) {
        if (!operation.entrances().isEmpty()) lastFisAddress = operation.entrances().get(0).address();
    }

    private String operationId() {
        String value = blankToNull(operationIdBox.getText());
        if (value == null) throw new IllegalArgumentException("Operation ID is required.");
        return value;
    }

    private void setError(String message) {
        statusMessage = message == null || message.isBlank() ? "Unknown error" : message;
        statusColor = COLOR_ERROR;
    }

    @Override
    protected void renderScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        TrueTypeFont titleFont = instance.getFontManager().getFont(32);
        TrueTypeFont labelFont = instance.getFontManager().getFont(15);
        TrueTypeFont bodyFont = instance.getFontManager().getFont(17);

        String title = tab == ProxyTab.NORMAL ? "Proxy / SOCKS5" : "Proxy / FisProxy";
        titleFont.drawString(context, title, panelLeft, panelTop, -1, true);
        for (FieldLabel label : fieldLabels) {
            labelFont.drawString(context, label.text(), label.x(), label.y(), new Color(175, 175, 175).getRGB(), false);
        }

        if (outputY > 0f) {
            Render2D.drawRect(context, contentLeft, outputY - 6f, contentWidth, 1f,
                    new Color(255, 255, 255, 32).getRGB());
            bodyFont.drawString(context, ellipsize(bodyFont, statusMessage, contentWidth), contentLeft, outputY,
                    statusColor, true);
            float lineY = outputY + bodyFont.getHeight() + 5f;
            for (String line : detailLines) {
                if (lineY + bodyFont.getHeight() > height - 5f) break;
                bodyFont.drawString(context, ellipsize(bodyFont, line, contentWidth), contentLeft, lineY,
                        new Color(205, 205, 205).getRGB(), false);
                lineY += bodyFont.getHeight() + 3f;
            }
        }
    }

    private static String ellipsize(TrueTypeFont font, String input, float maxWidth) {
        String value = input == null ? "" : input;
        if (font.getStringWidth(value) <= maxWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.getStringWidth(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (super.keyPressed(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (tab == ProxyTab.NORMAL) {
                handleSetProxy();
            } else {
                switch (fisPage) {
                    case SESSION -> saveFisSettings();
                    case START -> startFisSession();
                    case OPERATIONS -> getOperation();
                    case ADVANCED -> sendRawRequest();
                }
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_TAB && !textBoxes.isEmpty()) {
            for (int index = 0; index < textBoxes.size(); index++) {
                if (textBoxes.get(index).isFocused()) {
                    textBoxes.get(index).setFocused(false);
                    textBoxes.get((index + 1) % textBoxes.size()).setFocused(true);
                    return true;
                }
            }
            textBoxes.get(0).setFocused(true);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private FisProxyManager manager() {
        return instance.getFisProxyManager();
    }

    private static Map<String, Object> parseJsonObject(String input, String name) {
        try {
            JsonElement element = JsonParser.parseString(input);
            if (!element.isJsonObject()) throw new IllegalArgumentException(name + " must be a JSON object.");
            Map<?, ?> raw = GSON.fromJson(element, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
            return result;
        } catch (com.google.gson.JsonParseException exception) {
            throw new IllegalArgumentException(name + " must be valid JSON.", exception);
        }
    }

    private static Boolean parseOptionalBoolean(String input, String name) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("default")) return null;
        return parseRequiredBoolean(value, name);
    }

    private static boolean parseRequiredBoolean(String input, String name) {
        if (input == null || (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false"))) {
            throw new IllegalArgumentException(name + " must be true or false.");
        }
        return Boolean.parseBoolean(input);
    }

    private static int parseInt(String input, String name) {
        try {
            return Integer.parseInt(input.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException(name + " must be an integer.", exception);
        }
    }

    private static Integer parseOptionalInt(String input, String name) {
        return input == null || input.isBlank() ? null : parseInt(input, name);
    }

    private static double parsePositiveDouble(String input, String name) {
        try {
            double value = Double.parseDouble(input.trim());
            if (!Double.isFinite(value) || value <= 0d) throw new NumberFormatException();
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException(name + " must be a positive number.", exception);
        }
    }

    private static String blankToNull(String input) {
        return input == null || input.isBlank() ? null : input.trim();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof CompletionException || current instanceof ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof FisProxyException fisError) {
            String prefix = fisError.errorCode().isBlank() ? "" : fisError.errorCode() + ": ";
            return prefix + fisError.detail();
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private enum ProxyTab { NORMAL, FISPROXY }

    private enum FisPage { SESSION, START, OPERATIONS, ADVANCED }

    private record FieldLabel(String text, float x, float y) { }

    private record ButtonSpec(String label, Runnable action) { }
}
