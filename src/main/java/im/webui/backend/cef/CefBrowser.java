package im.webui.backend.cef;

import cn.omix.Client;
import im.webui.backend.Browser;
import im.webui.backend.BrowserLoadState;
import im.webui.backend.BrowserViewport;
import im.webui.backend.BrowserSettings;
import im.webui.backend.input.InputAcceptor;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowser;
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowserSettings;
import net.minecraft.util.Identifier;
import org.cef.browser.CefFrame;
import org.lwjgl.glfw.GLFW;

public final class CefBrowser implements Browser {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final CefBrowserBackend backend;
    private final MCEFBrowser browserApi;
    private final BrowserSettings settings;
    private final InputAcceptor inputAcceptor;
    private String requestedUrl;
    private volatile boolean initialized;
    private volatile BrowserLoadState state = BrowserLoadState.idle();
    private BrowserViewport viewport;
    private boolean visible = true;
    private char pendingHighSurrogate;
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
        if (initialized) {
            String script = """
                    document.documentElement.classList.toggle(
                        "omix-browser-hidden",
                        %s
                    );
                    """.formatted(!visible);
            browserApi.executeJavaScript(script, browserApi.getURL(), 0);
        }
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
        if (isClipboardShortcut(modifiers)) {
            CefFrame frame = browserApi.getFocusedFrame();
            if (frame == null) {
                frame = browserApi.getMainFrame();
            }
            if (frame != null && keyCode == GLFW.GLFW_KEY_C) {
                frame.copy();
                return;
            }
            if (frame != null && keyCode == GLFW.GLFW_KEY_V) {
                frame.paste();
                return;
            }
        }
        browserApi.sendKeyPress(keyCode, scanCode, modifiers);
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        browserApi.sendKeyRelease(keyCode, scanCode, modifiers);
    }

    @Override
    public void charTyped(int codePoint, int modifiers) {
        if (codePoint >= Character.MIN_HIGH_SURROGATE && codePoint <= Character.MAX_HIGH_SURROGATE) {
            pendingHighSurrogate = (char) codePoint;
            return;
        }
        if (codePoint >= Character.MIN_LOW_SURROGATE && codePoint <= Character.MAX_LOW_SURROGATE) {
            if (pendingHighSurrogate != 0) {
                insertCommittedText(new String(new char[]{pendingHighSurrogate, (char) codePoint}));
                pendingHighSurrogate = 0;
            } else {
                insertCommittedText("\uFFFD");
            }
            return;
        }
        if (pendingHighSurrogate != 0) {
            insertCommittedText("\uFFFD");
            pendingHighSurrogate = 0;
        }
        if (codePoint > 0x7F) {
            insertCommittedText(Character.isValidCodePoint(codePoint)
                    ? new String(Character.toChars(codePoint))
                    : "\uFFFD");
            return;
        }
        for (char character : Character.toChars(codePoint)) {
            browserApi.sendKeyTyped(character, modifiers);
        }
    }

    private void insertCommittedText(String text) {
        String encodedText = encodeJavaScriptString(text);
        String script = """
                (() => {
                    const element = document.activeElement;
                    if (!element) return;
                    const text = %s;
                    if (document.execCommand("insertText", false, text)) return;
                    if (!(element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement)) return;
                    const start = element.selectionStart ?? element.value.length;
                    const end = element.selectionEnd ?? start;
                    element.setRangeText(text, start, end, "end");
                    element.dispatchEvent(new InputEvent("input", {
                        bubbles: true,
                        inputType: "insertText",
                        data: text
                    }));
                })();
                """.formatted(encodedText);
        browserApi.executeJavaScript(script, browserApi.getURL(), 0);
    }

    private static boolean isClipboardShortcut(int modifiers) {
        return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    private static String encodeJavaScriptString(String text) {
        StringBuilder encoded = new StringBuilder(2 + text.length() * 6);
        encoded.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            encoded.append("\\u")
                    .append(HEX[(character >>> 12) & 0xF])
                    .append(HEX[(character >>> 8) & 0xF])
                    .append(HEX[(character >>> 4) & 0xF])
                    .append(HEX[character & 0xF]);
        }
        return encoded.append('"').toString();
    }

    @Override
    public void close() {
        backend.remove(this);
        browserApi.close();
    }
}
