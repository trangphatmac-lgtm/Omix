package im.webui.backend.cef;

import cn.remix.Client;
import im.webui.backend.Browser;
import im.webui.backend.BrowserLoadState;
import im.webui.backend.BrowserViewport;
import im.webui.backend.BrowserSettings;
import im.webui.backend.input.InputAcceptor;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowser;
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowserSettings;
import net.minecraft.util.Identifier;

public final class CefBrowser implements Browser {
    private final CefBrowserBackend backend;
    private final MCEFBrowser browserApi;
    private final BrowserSettings settings;
    private final InputAcceptor inputAcceptor;
    private String requestedUrl;
    private volatile boolean initialized;
    private volatile BrowserLoadState state = BrowserLoadState.idle();
    private BrowserViewport viewport;
    private boolean visible = true;
    private short priority;

    CefBrowser(
            CefBrowserBackend backend,
            String url,
            BrowserViewport viewport,
            BrowserSettings settings,
            short priority,
            InputAcceptor inputAcceptor
    ) {
        this.backend = backend;
        this.viewport = viewport;
        this.settings = settings;
        this.priority = priority;
        this.inputAcceptor = inputAcceptor;
        this.requestedUrl = url;
        this.browserApi = MCEF.INSTANCE.createBrowser(
                url,
                true,
                viewport.scaledWidth(settings.quality()),
                viewport.scaledHeight(settings.quality()),
                new MCEFBrowserSettings(settings.fps(), settings.accelerated())
        );
    }

    MCEFBrowser getBrowserApi() {
        return browserApi;
    }

    void markInitialized() {
        if (!initialized) {
            initialized = true;
            browserApi.loadURL(requestedUrl);
            browserApi.setZoomLevel(viewport.zoomLevel(settings.quality()));
            Client.logger.info("MCEF browser initialized: {}", requestedUrl);
        }
    }

    void setState(BrowserLoadState state) {
        this.state = state;
        if (state.status() == BrowserLoadState.Status.SUCCESS) {
            Client.logger.info("WebUI page loaded with HTTP {}", state.httpStatusCode());
        } else if (state.status() == BrowserLoadState.Status.FAILURE) {
            Client.logger.error("WebUI page failed: {} ({})", state.errorText(), state.errorCode());
        }
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public BrowserLoadState getState() {
        return state;
    }

    @Override
    public BrowserViewport getViewport() {
        return viewport;
    }

    @Override
    public void setViewport(BrowserViewport viewport) {
        this.viewport = viewport;
        browserApi.resize(
                viewport.scaledWidth(settings.quality()),
                viewport.scaledHeight(settings.quality())
        );
        browserApi.setZoomLevel(viewport.zoomLevel(settings.quality()));
        browserApi.clear();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public short getPriority() {
        return priority;
    }

    @Override
    public void setPriority(short priority) {
        this.priority = priority;
        backend.sortBrowsers();
    }

    @Override
    public BrowserSettings getSettings() {
        return settings;
    }

    @Override
    public boolean acceptsInput() {
        return visible && inputAcceptor != null && inputAcceptor.acceptsInput();
    }

    @Override
    public String getUrl() {
        String currentUrl = browserApi.getURL();
        return currentUrl == null || currentUrl.isBlank() ? requestedUrl : currentUrl;
    }

    @Override
    public void setUrl(String url) {
        requestedUrl = url;
        state = BrowserLoadState.idle();
        browserApi.loadURL(url);
    }

    @Override
    public Identifier getTextureIdentifier() {
        return browserApi.getRenderer().getIdentifier();
    }

    @Override
    public int getTextureWidth() {
        return browserApi.getRenderer().getTextureWidth();
    }

    @Override
    public int getTextureHeight() {
        return browserApi.getRenderer().getTextureHeight();
    }

    @Override
    public boolean isTextureReady() {
        return browserApi.getRenderer().isTextureReady() && !browserApi.getRenderer().isUnpainted();
    }

    @Override
    public void forceReload() {
        browserApi.reloadIgnoreCache();
    }

    @Override
    public void reload() {
        browserApi.reload();
    }

    @Override
    public void goForward() {
        browserApi.goForward();
    }

    @Override
    public void goBack() {
        browserApi.goBack();
    }

    @Override
    public void update() {
        if (viewport.fullscreen()) {
            BrowserViewport current = BrowserViewport.fullFrame();
            if (current.width() != viewport.width() || current.height() != viewport.height()) {
                setViewport(current);
            }
        }
    }

    @Override
    public void invalidate() {
        browserApi.clear();
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        browserApi.setFocus(true);
        browserApi.sendMousePress(
                viewport.transformMouseX(mouseX, settings.quality()),
                viewport.transformMouseY(mouseY, settings.quality()),
                button
        );
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        browserApi.sendMouseRelease(
                viewport.transformMouseX(mouseX, settings.quality()),
                viewport.transformMouseY(mouseY, settings.quality()),
                button
        );
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        browserApi.sendMouseMove(
                viewport.transformMouseX(mouseX, settings.quality()),
                viewport.transformMouseY(mouseY, settings.quality())
        );
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double delta) {
        browserApi.sendMouseWheel(
                viewport.transformMouseX(mouseX, settings.quality()),
                viewport.transformMouseY(mouseY, settings.quality()),
                delta
        );
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        browserApi.setFocus(true);
        browserApi.sendKeyPress(keyCode, scanCode, modifiers);
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        browserApi.sendKeyRelease(keyCode, scanCode, modifiers);
    }

    @Override
    public void charTyped(int codePoint, int modifiers) {
        for (char character : Character.toChars(codePoint)) {
            browserApi.sendKeyTyped(character, modifiers);
        }
    }

    @Override
    public void close() {
        backend.remove(this);
        browserApi.close();
    }
}
