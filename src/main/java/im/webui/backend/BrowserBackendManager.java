package im.webui.backend;

import im.webui.backend.cef.CefBrowserBackend;

import java.util.function.Consumer;
import java.util.List;

public final class BrowserBackendManager {
    private final BrowserBackend backend = new CefBrowserBackend();

    public BrowserBackend getBackend() {
        return backend;
    }

    public List<? extends Browser> getBrowsers() {
        return backend.getBrowsers();
    }

    public void prepareAsync(Runnable whenAvailable, Consumer<Throwable> onFailure) {
        backend.prepareAsync(whenAvailable, onFailure);
    }

    public void update() {
        if (backend.isInitialized()) {
            backend.update();
        }
    }

    public void stop() {
        backend.stop();
    }
}
