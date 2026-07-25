package im.webui;

import cn.remix.Client;
import com.google.gson.JsonObject;
import im.webui.backend.Browser;
import im.webui.backend.BrowserBackendManager;
import im.webui.backend.BrowserLoadState;
import im.webui.backend.BrowserPreparationProgress;
import im.webui.backend.BrowserViewport;
import im.webui.backend.BrowserSettings;
import im.webui.backend.input.BrowserInputRouter;
import im.webui.backend.input.InputAcceptor;
import im.webui.interop.InteropServer;
import im.webui.interop.InteropResponse;
import im.webui.interop.PersistentLocalStorage;
import im.webui.render.BrowserRenderer;
import im.webui.screen.WebUiScreen;
import im.webui.screen.WebScreenManager;
import im.webui.screen.WebScreenOpenResult;
import im.webui.screen.WebScreenType;
import im.webui.theme.WebThemeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.file.Path;

public final class WebUiRuntime {
    private static final WebUiRuntime INSTANCE = new WebUiRuntime();

    private final BrowserBackendManager backendManager = new BrowserBackendManager();
    private final BrowserInputRouter inputRouter = new BrowserInputRouter(backendManager::getBrowsers);
    private final WebThemeManager themeManager = new WebThemeManager();
    private volatile WebUiState state = WebUiState.NEW;
    private volatile Throwable failure;
    private volatile BrowserPreparationProgress preparationProgress = BrowserPreparationProgress.IDLE;
    private final WebScreenManager screenManager = new WebScreenManager();
    private volatile boolean openWhenReady;
    private volatile boolean autoTestEnabled;
    private int screenRetryFrames;
    private volatile boolean inputProbeSent;
    private volatile int inputProbeFrames = -1;
    private volatile double inputProbeX;
    private volatile double inputProbeY;
    private InteropServer interopServer;
    private PersistentLocalStorage localStorage;
    private Browser mainBrowser;

    private WebUiRuntime() {
    }

    public static WebUiRuntime getInstance() {
        return INSTANCE;
    }

    public WebUiState getState() {
        return state;
    }

    public Throwable getFailure() {
        return failure;
    }

    public BrowserPreparationProgress getPreparationProgress() {
        return preparationProgress;
    }

    public InteropServer getInteropServer() {
        if (interopServer == null) {
            throw new IllegalStateException("WebUI interop server has not started");
        }
        return interopServer;
    }

    public WebThemeManager getThemeManager() {
        return themeManager;
    }

    public BrowserBackendManager getBackendManager() {
        return backendManager;
    }

    public void start() {
        if (state != WebUiState.NEW && state != WebUiState.STOPPED) {
            return;
        }
        try {
            interopServer = new InteropServer(
                    this::getCurrentRoute,
                    this::acknowledgeScreen,
                    this::acceptTestReport
            );
            interopServer.start();
            localStorage = new PersistentLocalStorage(
                    Path.of(MinecraftClient.getInstance().runDirectory.getPath(), "Remix", "webui", "local-storage.json")
            );
            localStorage.load();
            registerStorageRoutes();
            themeManager.useBundled(interopServer.getAuthenticatedBaseUrl());
            autoTestEnabled = Boolean.parseBoolean(
                    System.getProperty("remix.webui.autoTest", "false")
            );
            openWhenReady = autoTestEnabled;
            state = WebUiState.SERVER_READY;
            state = WebUiState.CEF_PREPARING;
            backendManager.prepareAsync(
                    this::startBrowserBackend,
                    progress -> preparationProgress = progress,
                    this::fail
            );
        } catch (Throwable throwable) {
            fail(throwable);
        }
    }

    private void startBrowserBackend() {
        try {
            backendManager.getBackend().start();
            preparationProgress = BrowserPreparationProgress.determinate("Chromium initialized", 1.0F);
            state = WebUiState.CEF_READY;
            mainBrowser = backendManager.getBackend().createBrowser(
                    themeManager.getScreenUrl(WebScreenType.TEST),
                    BrowserViewport.fullFrame(),
                    BrowserSettings.DEFAULT,
                    (short) 0,
                    this::acceptsMainBrowserInput
            );
            mainBrowser.setVisible(false);
            state = WebUiState.BROWSER_LOADING;
        } catch (Throwable throwable) {
            fail(throwable);
        }
    }

    public void onFrame() {
        if (state == WebUiState.STOPPED || state == WebUiState.FAILED) {
            return;
        }
        try {
            backendManager.update();
            if (mainBrowser != null) {
                for (Browser browser : backendManager.getBrowsers()) {
                    browser.update();
                }
                if (state == WebUiState.BROWSER_LOADING
                        && mainBrowser.isInitialized()
                        && mainBrowser.getState().status() == BrowserLoadState.Status.SUCCESS) {
                    state = WebUiState.READY;
                    JsonObject event = new JsonObject();
                    event.addProperty("state", "ready");
                    interopServer.broadcast("browserReady", event);
                    if (openWhenReady) {
                        WebScreenType pendingType = screenManager.current();
                        activateScreen(pendingType == null ? WebScreenType.TEST : pendingType);
                    }
                }
                keepAutoTestScreenSynchronized();
                runInputProbe();
            }
        } catch (Throwable throwable) {
            fail(throwable);
        }
    }

    public WebScreenOpenResult openTestScreen() {
        return openScreen(WebScreenType.TEST);
    }

    public WebScreenOpenResult openScreen(String routeName) {
        WebScreenType type = WebScreenType.byName(routeName);
        if (type == null) {
            Client.logger.warn("Unknown WebUI screen route: {}", routeName);
            return WebScreenOpenResult.FAILED;
        }
        return openScreen(type);
    }

    public WebScreenOpenResult openScreen(WebScreenType type) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (state == WebUiState.FAILED || state == WebUiState.STOPPED) {
            Client.logger.error("Cannot open WebUI screen {} while runtime is {}", type.routeName(), state);
            return WebScreenOpenResult.FAILED;
        }
        if (state != WebUiState.READY) {
            openWhenReady = true;
            screenManager.open(type);
            if (!(client.currentScreen instanceof WebUiScreen screen)
                    || !screen.getType().equals(type)) {
                client.setScreen(new WebUiScreen(client.currentScreen, type));
            }
            Client.logger.info("WebUI is {}, screen {} will open when ready", state, type.routeName());
            return WebScreenOpenResult.QUEUED;
        }

        activateScreen(type);
        return WebScreenOpenResult.OPENED;
    }

    private void activateScreen(WebScreenType type) {
        MinecraftClient client = MinecraftClient.getInstance();
        openWhenReady = false;
        if (!type.equals(screenManager.current())) {
            screenManager.open(type);
        }
        boolean needsAcknowledgement = !screenManager.isAcknowledged();
        inputProbeSent = false;
        String screenUrl = themeManager.getScreenUrl(type);
        if (!screenUrl.equals(mainBrowser.getUrl())) {
            mainBrowser.setUrl(screenUrl);
        } else if (needsAcknowledgement) {
            // The shared browser may have loaded its route while hidden and exhausted the
            // front-end acknowledgement retries. Reload when a user opens it later.
            mainBrowser.forceReload();
        }
        mainBrowser.setVisible(true);
        if (!(client.currentScreen instanceof WebUiScreen screen)
                || !screen.getType().equals(type)) {
            client.setScreen(new WebUiScreen(client.currentScreen, type));
        }
    }

    public void closeTestScreen() {
        openWhenReady = false;
        if (mainBrowser != null) {
            mainBrowser.setVisible(false);
        }
        screenManager.close();
    }

    public boolean isBrowserTextureReady() {
        return state == WebUiState.READY
                && mainBrowser != null
                && mainBrowser.isVisible()
                && mainBrowser.isTextureReady();
    }

    public void render(DrawContext context) {
        BrowserRenderer.renderAll(context, backendManager.getBrowsers());
    }

    public void resize(int width, int height) {
        for (Browser browser : backendManager.getBrowsers()) {
            browser.setViewport(browser.getViewport().resized(width, height));
        }
    }

    public Browser createBrowser(
            WebScreenType type,
            BrowserViewport viewport,
            BrowserSettings settings,
            short priority,
            InputAcceptor inputAcceptor
    ) {
        if (state != WebUiState.READY) {
            throw new IllegalStateException("WebUI backend is not ready: " + state);
        }
        return backendManager.getBackend().createBrowser(
                themeManager.getScreenUrl(type),
                viewport,
                settings,
                priority,
                inputAcceptor
        );
    }

    public void mouseButton(int button, int action) {
        inputRouter.mouseButton(button, action);
    }

    public void mouseMoved(double windowX, double windowY) {
        inputRouter.mouseMoved(windowX, windowY);
    }

    public void mouseScrolled(double vertical) {
        inputRouter.mouseScrolled(vertical);
    }

    public void key(int keyCode, int scanCode, int action, int modifiers) {
        inputRouter.key(keyCode, scanCode, action, modifiers);
    }

    public void character(int codePoint, int modifiers) {
        inputRouter.character(codePoint, modifiers);
    }

    public void stop() {
        if (state == WebUiState.STOPPED) {
            return;
        }
        closeTestScreen();
        backendManager.stop();
        if (interopServer != null) {
            interopServer.stop();
        }
        state = WebUiState.STOPPED;
    }

    private boolean acceptsInput() {
        return mainBrowser != null && mainBrowser.acceptsInput();
    }

    private boolean acceptsMainBrowserInput() {
        return MinecraftClient.getInstance().currentScreen instanceof WebUiScreen;
    }

    private String getCurrentRoute() {
        return MinecraftClient.getInstance().currentScreen instanceof WebUiScreen
                ? screenManager.currentRoute()
                : "none";
    }

    private void acknowledgeScreen(String name) {
        if (screenManager.acknowledge(name)) {
            Client.logger.info("WebUI screen acknowledged by browser");
        }
    }

    private void keepAutoTestScreenSynchronized() {
        if (!autoTestEnabled || state != WebUiState.READY || screenManager.isAcknowledged()) {
            return;
        }
        if (MinecraftClient.getInstance().currentScreen instanceof WebUiScreen) {
            screenRetryFrames = 0;
            return;
        }
        if (screenRetryFrames-- <= 0) {
            Client.logger.info("[WebUI Test] reopening test screen until browser acknowledgement");
            openTestScreen();
            screenRetryFrames = 60;
        }
    }

    private void acceptTestReport(JsonObject report) {
        String step = report.has("step") ? report.get("step").getAsString() : "unknown";
        String status = report.has("status") ? report.get("status").getAsString() : "unknown";
        Client.logger.info("[WebUI Test] {} = {} {}", step, status, report);

        if (!"base-complete".equals(step) || inputProbeSent || mainBrowser == null) {
            return;
        }

        inputProbeSent = true;
        inputProbeX = report.has("x") ? report.get("x").getAsDouble() : 0.0;
        inputProbeY = report.has("y") ? report.get("y").getAsDouble() : 0.0;
        inputProbeFrames = 20;
    }

    private void runInputProbe() {
        int frames = inputProbeFrames;
        if (frames < 0) {
            return;
        }
        if (!acceptsInput()) {
            inputProbeFrames = -1;
            Client.logger.error("[WebUI Test] input-bridge = failed (screen no longer accepts input)");
            return;
        }
        if (frames == 20) {
            mainBrowser.mouseMoved(inputProbeX, inputProbeY);
            mainBrowser.mouseClicked(inputProbeX, inputProbeY, 0);
            mainBrowser.mouseReleased(inputProbeX, inputProbeY, 0);
            Client.logger.info("[WebUI Test] input-focus = sent ({}, {})", inputProbeX, inputProbeY);
        } else if (frames == 10) {
            for (char character : "Remix".toCharArray()) {
                mainBrowser.charTyped(character, 0);
            }
            Client.logger.info("[WebUI Test] input-characters = sent");
        } else if (frames == 0) {
            inputProbeFrames = -1;
            return;
        }
        inputProbeFrames = frames - 1;
    }

    private void fail(Throwable throwable) {
        failure = throwable;
        state = WebUiState.FAILED;
        Client.logger.error("WebUI framework failed", throwable);
    }

    private void registerStorageRoutes() {
        var routes = interopServer.getRoutes();
        routes.get("/api/v1/client/localStorage/all", ignored ->
                InteropResponse.json(HttpResponseStatus.OK, localStorage.all()));
        routes.put("/api/v1/client/localStorage/all", request -> {
            try {
                localStorage.replace(request.body());
                return InteropResponse.noContent();
            } catch (Exception exception) {
                Client.logger.error("Failed to replace WebUI local storage", exception);
                return InteropResponse.text(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Storage write failed");
            }
        });
        routes.get("/api/v1/client/localStorage", request -> {
            String key = firstQueryValue(request.query(), "key");
            if (key == null) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, "Missing key");
            }
            var value = localStorage.get(key);
            if (value == null) {
                return InteropResponse.text(HttpResponseStatus.NOT_FOUND, "No value");
            }
            JsonObject response = new JsonObject();
            response.add("value", value);
            return InteropResponse.json(HttpResponseStatus.OK, response);
        });
        routes.put("/api/v1/client/localStorage", request -> {
            JsonObject body = request.body();
            if (!body.has("key") || !body.has("value")) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, "Missing key or value");
            }
            try {
                localStorage.put(body.get("key").getAsString(), body.get("value"));
                return InteropResponse.noContent();
            } catch (Exception exception) {
                Client.logger.error("Failed to write WebUI local storage", exception);
                return InteropResponse.text(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Storage write failed");
            }
        });
        routes.delete("/api/v1/client/localStorage", request -> {
            String key = firstQueryValue(request.query(), "key");
            if (key == null) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, "Missing key");
            }
            try {
                return localStorage.delete(key)
                        ? InteropResponse.noContent()
                        : InteropResponse.text(HttpResponseStatus.NOT_FOUND, "No value");
            } catch (Exception exception) {
                Client.logger.error("Failed to delete WebUI local storage", exception);
                return InteropResponse.text(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Storage write failed");
            }
        });
    }

    private static String firstQueryValue(java.util.Map<String, java.util.List<String>> query, String key) {
        java.util.List<String> values = query.get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
