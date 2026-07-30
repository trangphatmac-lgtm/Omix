package im.webui;

import cn.omix.Client;
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
import im.webui.interop.AiInteropBridge;
import im.webui.interop.ClickGuiInteropBridge;
import im.webui.interop.PersistentLocalStorage;
import im.webui.render.BrowserRenderer;
import im.webui.screen.WebUiScreen;
import im.webui.screen.MusicPanelLayout;
import im.webui.screen.WebScreenManager;
import im.webui.screen.WebScreenOpenResult;
import im.webui.screen.WebScreenType;
import im.webui.theme.WebThemeManager;
import im.music.MusicRuntimeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.file.Path;
import java.util.Set;

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
    private InteropServer interopServer;
    private PersistentLocalStorage localStorage;
    private AiInteropBridge aiInteropBridge;
    private ClickGuiInteropBridge clickGuiInteropBridge;
    private MusicRuntimeManager musicRuntime;
    private PersistentLocalStorage musicStorage;
    private Browser mainBrowser;
    private Browser musicBrowser;

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

    public MusicRuntimeManager getMusicRuntime() {
        if (musicRuntime == null) {
            throw new IllegalStateException("Music runtime has not started");
        }
        return musicRuntime;
    }

    public String getWebPanelUrl() {
        return getInteropServer().getAuthenticatedBaseUrl()
                + "#/" + WebScreenType.CLICK_GUI.routeName();
    }

    public void start() {
        if (state != WebUiState.NEW && state != WebUiState.STOPPED) {
            return;
        }
        try {
            Path webUiDirectory = Path.of(
                    MinecraftClient.getInstance().runDirectory.getPath(),
                    "Omix",
                    "webui"
            );
            musicRuntime = new MusicRuntimeManager(Path.of(
                    MinecraftClient.getInstance().runDirectory.getPath(),
                    "Omix",
                    "music"
            ));
            localStorage = new PersistentLocalStorage(
                    webUiDirectory.resolve("local-storage.json")
            );
            localStorage.load();
            musicStorage = new PersistentLocalStorage(webUiDirectory.resolve("music-storage.json"));
            musicStorage.load();
            interopServer = new InteropServer(
                    this::getCurrentRoute,
                    this::acknowledgeScreen,
                    musicRuntime,
                    musicStorage
            );
            interopServer.start();
            aiInteropBridge = new AiInteropBridge(interopServer, Client.instance.getAiBackend());
            clickGuiInteropBridge = new ClickGuiInteropBridge(interopServer);
            registerStorageRoutes();
            registerMusicRoutes();
            themeManager.useBundled(interopServer.getAuthenticatedBaseUrl());
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
                    themeManager.getScreenUrl(WebScreenType.AI),
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
                        activateScreen(pendingType == null ? WebScreenType.AI : pendingType);
                    }
                }
            }
        } catch (Throwable throwable) {
            fail(throwable);
        }
    }

    public WebScreenOpenResult openAiScreen() {
        return openScreen(WebScreenType.AI);
    }

    public WebScreenOpenResult openMusicScreen() {
        return openScreen(WebScreenType.MUSIC);
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
            if (type.equals(WebScreenType.MUSIC)) {
                musicRuntime.startAsync();
            }
            if (!(client.currentScreen instanceof WebUiScreen screen)
                    || !screen.getType().equals(type)) {
                client.setScreen(new WebUiScreen(client.currentScreen, type));
            }
            Client.logger.info("WebUI is {}, screen {} will open when ready", state, type.routeName());
            return WebScreenOpenResult.QUEUED;
        }

        if (type.equals(WebScreenType.MUSIC)
                && musicRuntime.getState() != im.music.MusicServiceState.READY) {
            screenManager.open(type);
            if (!(client.currentScreen instanceof WebUiScreen screen)
                    || !screen.getType().equals(type)) {
                client.setScreen(new WebUiScreen(client.currentScreen, type));
            }
            prepareMusicAndActivate();
            return WebScreenOpenResult.QUEUED;
        }

        activateScreen(type);
        return WebScreenOpenResult.OPENED;
    }

    private void activateScreen(WebScreenType type) {
        if (type.equals(WebScreenType.MUSIC)
                && musicRuntime.getState() != im.music.MusicServiceState.READY) {
            prepareMusicAndActivate();
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        openWhenReady = false;
        if (!type.equals(screenManager.current())) {
            screenManager.open(type);
        }
        boolean needsAcknowledgement = !screenManager.isAcknowledged();
        Browser browser = browserFor(type);
        String screenUrl = themeManager.getScreenUrl(type);
        if (!screenUrl.equals(browser.getUrl())) {
            browser.setUrl(screenUrl);
        } else if (needsAcknowledgement && !type.equals(WebScreenType.MUSIC)) {
            // The shared browser may have loaded its route while hidden and exhausted the
            // front-end acknowledgement retries. Reload when a user opens it later.
            browser.forceReload();
        }
        if (type.equals(WebScreenType.MUSIC)) {
            mainBrowser.setVisible(false);
        } else if (musicBrowser != null) {
            musicBrowser.setVisible(false);
        }
        browser.setVisible(true);
        if (!(client.currentScreen instanceof WebUiScreen screen)
                || !screen.getType().equals(type)) {
            client.setScreen(new WebUiScreen(client.currentScreen, type));
        }
    }

    public void closeScreen() {
        openWhenReady = false;
        WebScreenType current = screenManager.current();
        Browser browser = current == null ? null : existingBrowserFor(current);
        if (browser != null) {
            browser.setVisible(false);
        }
        screenManager.close();
    }

    public void beginScreenCloseAnimation() {
        if (interopServer == null) {
            return;
        }
        JsonObject event = new JsonObject();
        event.addProperty("route", getCurrentRoute());
        interopServer.broadcast("screenClosing", event);
    }

    public boolean isBrowserTextureReady() {
        WebScreenType current = screenManager.current();
        Browser browser = current == null ? null : existingBrowserFor(current);
        return state == WebUiState.READY
                && browser != null
                && browser.isVisible()
                && browser.isTextureReady();
    }

    public void render(DrawContext context) {
        BrowserRenderer.renderAll(context, backendManager.getBrowsers());
    }

    public void resize(int width, int height) {
        for (Browser browser : backendManager.getBrowsers()) {
            if (browser == musicBrowser) {
                browser.setViewport(MusicPanelLayout.current().browserViewport());
            } else {
                browser.setViewport(browser.getViewport().resized(width, height));
            }
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
        closeScreen();
        backendManager.stop();
        if (interopServer != null) {
            interopServer.stop();
        }
        if (musicRuntime != null) {
            musicRuntime.stop();
        }
        state = WebUiState.STOPPED;
    }

    private boolean acceptsMainBrowserInput() {
        return MinecraftClient.getInstance().currentScreen instanceof WebUiScreen screen
                && !screen.getType().equals(WebScreenType.MUSIC);
    }

    private boolean acceptsMusicBrowserInput() {
        return MinecraftClient.getInstance().currentScreen instanceof WebUiScreen screen
                && screen.getType().equals(WebScreenType.MUSIC);
    }

    private Browser browserFor(WebScreenType type) {
        if (!type.equals(WebScreenType.MUSIC)) return mainBrowser;
        if (musicBrowser == null) {
            musicBrowser = backendManager.getBackend().createBrowser(
                    themeManager.getScreenUrl(WebScreenType.MUSIC),
                    MusicPanelLayout.current().browserViewport(),
                    BrowserSettings.DEFAULT,
                    (short) 10,
                    this::acceptsMusicBrowserInput
            );
            musicBrowser.setVisible(false);
        }
        return musicBrowser;
    }

    private Browser existingBrowserFor(WebScreenType type) {
        return type.equals(WebScreenType.MUSIC) ? musicBrowser : mainBrowser;
    }

    private void prepareMusicAndActivate() {
        musicRuntime.startAsync().whenComplete((endpoint, throwable) ->
                MinecraftClient.getInstance().execute(() -> {
                    if (throwable != null
                            || !(MinecraftClient.getInstance().currentScreen instanceof WebUiScreen screen)
                            || !screen.getType().equals(WebScreenType.MUSIC)) {
                        return;
                    }
                    activateScreen(WebScreenType.MUSIC);
                })
        );
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
        routes.put("/api/v1/client/backgroundBlur", request -> {
            JsonObject body = request.body();
            if (!body.has("enabled") || !body.get("enabled").isJsonPrimitive()) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, "Missing enabled state");
            }
            boolean enabled = body.get("enabled").getAsBoolean();
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.currentScreen instanceof WebUiScreen screen
                        && screen.getType().equals(WebScreenType.AI)) {
                    screen.setBackgroundBlurEnabled(enabled);
                }
            });
            return InteropResponse.noContent();
        });
        routes.post("/api/v1/client/closeScreen", ignored -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.currentScreen instanceof WebUiScreen screen) {
                    screen.close();
                }
            });
            return InteropResponse.noContent();
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

    private void registerMusicRoutes() {
        var routes = interopServer.getRoutes();
        routes.get("/api/v1/music/status", ignored ->
                InteropResponse.json(HttpResponseStatus.OK, musicRuntime.statusJson()));
        routes.post("/api/v1/music/retry", ignored -> {
            musicRuntime.retryAsync();
            return InteropResponse.json(HttpResponseStatus.ACCEPTED, musicRuntime.statusJson());
        });
        routes.delete("/api/v1/music/runtime", ignored -> {
            musicRuntime.clearRuntimeAndRetryAsync();
            return InteropResponse.json(HttpResponseStatus.ACCEPTED, musicRuntime.statusJson());
        });
        routes.get("/api/v1/music/storage", ignored ->
                InteropResponse.json(HttpResponseStatus.OK, musicStorage.all()));
        routes.put("/api/v1/music/storage", request -> {
            JsonObject filtered = new JsonObject();
            for (String key : MUSIC_STORAGE_KEYS) {
                if (request.body().has(key)
                        && request.body().get(key).isJsonPrimitive()
                        && request.body().get(key).getAsJsonPrimitive().isString()) {
                    filtered.addProperty(key, request.body().get(key).getAsString());
                }
            }
            // A refresh response may rotate a NetEase cookie in Java while the hidden
            // page still has the previous value in localStorage. Preserve the newer
            // server-side value until the next page hydration. Omitting the key still
            // clears it immediately during logout.
            for (String cookieKey : MUSIC_COOKIE_STORAGE_KEYS) {
                if (!filtered.has(cookieKey)) continue;
                var current = musicStorage.get(cookieKey);
                if (current != null
                        && current.isJsonPrimitive()
                        && current.getAsJsonPrimitive().isString()
                        && !current.getAsString().equals(filtered.get(cookieKey).getAsString())) {
                    filtered.add(cookieKey, current);
                }
            }
            try {
                musicStorage.replace(filtered);
                return InteropResponse.noContent();
            } catch (Exception exception) {
                Client.logger.error("Failed to persist music storage", exception);
                return InteropResponse.text(
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "Music storage write failed"
                );
            }
        });
        routes.delete("/api/v1/music/storage", ignored -> {
            try {
                musicStorage.replace(new JsonObject());
                return InteropResponse.noContent();
            } catch (Exception exception) {
                Client.logger.error("Failed to clear music storage", exception);
                return InteropResponse.text(
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "Music storage clear failed"
                );
            }
        });
    }

    private static final Set<String> MUSIC_STORAGE_KEYS = Set.of(
            "appVersion",
            "settings",
            "data",
            "player",
            "playerCurrentTrackTime",
            "cookie-MUSIC_U",
            "cookie-__csrf"
    );
    private static final Set<String> MUSIC_COOKIE_STORAGE_KEYS = Set.of(
            "cookie-MUSIC_U",
            "cookie-__csrf"
    );

    private static String firstQueryValue(java.util.Map<String, java.util.List<String>> query, String key) {
        java.util.List<String> values = query.get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
