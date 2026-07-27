package im.webui.backend.cef;

import cn.omix.Client;
import im.webui.backend.Browser;
import im.webui.backend.BrowserBackend;
import im.webui.backend.BrowserLoadState;
import im.webui.backend.BrowserPreparationProgress;
import im.webui.backend.BrowserViewport;
import im.webui.backend.BrowserSettings;
import im.webui.backend.BrowserAccelerationFlags;
import im.webui.backend.input.InputAcceptor;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.ccbluex.liquidbounce.mcef.MCEFAccelerationSupport;
import net.ccbluex.liquidbounce.mcef.listeners.MCEFProgressListener;
import net.minecraft.client.MinecraftClient;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.Comparator;

public final class CefBrowserBackend implements BrowserBackend {
    private static final long CACHE_CLEANUP_THRESHOLD = TimeUnit.DAYS.toMillis(7);

    private final List<CefBrowser> browsers = new CopyOnWriteArrayList<>();
    private File cacheRoot;
    private File cacheDirectory;
    private BrowserAccelerationFlags accelerationFlags = BrowserAccelerationFlags.UNSUPPORTED;

    @Override
    public boolean isInitialized() {
        return MCEF.INSTANCE.isInitialized();
    }

    @Override
    public List<CefBrowser> getBrowsers() {
        return List.copyOf(browsers);
    }

    @Override
    public BrowserAccelerationFlags getAccelerationFlags() {
        return accelerationFlags;
    }

    @Override
    public void prepareAsync(
            Runnable whenAvailable,
            Consumer<BrowserPreparationProgress> onProgress,
            Consumer<Throwable> onFailure
    ) {
        Thread.ofVirtual().name("Omix-MCEF-Prepare").start(() -> {
            try {
                onProgress.accept(BrowserPreparationProgress.indeterminate("Checking JCEF runtime"));
                MinecraftClient client = MinecraftClient.getInstance();
                File root = new File(client.runDirectory, "Omix/mcef");
                File libraries = new File(root, "libraries");
                cacheRoot = new File(root, "cache");
                cleanupOldCacheDirectories();
                cacheDirectory = new File(cacheRoot, Long.toHexString(System.currentTimeMillis()));

                var settings = MCEF.INSTANCE.getSettings();
                settings.setUserAgent("Omix/" + Client.version + " MCEF");
                settings.setLibrariesDirectory(libraries);
                settings.setCacheDirectory(cacheDirectory);
                settings.appendCefSwitches("--no-proxy-server");

                var resourceManager = MCEF.INSTANCE.newResourceManager();
                resourceManager.registerProgressListener(new MCEFProgressListener() {
                    @Override
                    public void onProgressUpdate(String task, float progress) {
                        onProgress.accept(BrowserPreparationProgress.determinate(task, progress));
                    }

                    @Override
                    public void onComplete() {
                        onProgress.accept(BrowserPreparationProgress.determinate("JCEF runtime ready", 1.0F));
                    }

                    @Override
                    public void onFileStart(String task) {
                        onProgress.accept(BrowserPreparationProgress.indeterminate(task));
                    }

                    @Override
                    public void onFileProgress(
                            String task,
                            long bytesRead,
                            long contentLength,
                            boolean done
                    ) {
                        onProgress.accept(BrowserPreparationProgress.file(task, bytesRead, contentLength));
                    }

                    @Override
                    public void onFileEnd(String task) {
                        onProgress.accept(BrowserPreparationProgress.determinate(task, 1.0F));
                    }
                });
                if (!resourceManager.isSystemCompatible()) {
                    throw new IllegalStateException("MCEF/JCEF is not compatible with this system");
                }
                if (resourceManager.requiresDownload()) {
                    Client.logger.info("Downloading MCEF/JCEF runtime...");
                    onProgress.accept(BrowserPreparationProgress.indeterminate(
                            "Downloading JCEF (first launch)"
                    ));
                    resourceManager.downloadJcef();
                }

                onProgress.accept(BrowserPreparationProgress.indeterminate("Starting Chromium"));
                client.execute(whenAvailable);
            } catch (Throwable throwable) {
                onFailure.accept(throwable);
            }
        });
    }

    @Override
    public void start() {
        if (isInitialized()) {
            return;
        }
        if (!MCEF.INSTANCE.initialize()) {
            throw new IllegalStateException("MCEF initialization failed");
        }

        MCEF.INSTANCE.getClient().getHandle().addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public void onAfterCreated(org.cef.browser.CefBrowser browser) {
                CefBrowser wrapped = find(browser);
                if (wrapped != null) {
                    wrapped.markInitialized();
                }
            }
        });

        MCEF.INSTANCE.getClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(org.cef.browser.CefBrowser browser, CefFrame frame,
                                    CefRequest.TransitionType transitionType) {
                CefBrowser wrapped = find(browser);
                if (wrapped != null) {
                    wrapped.setState(BrowserLoadState.loading());
                }
            }

            @Override
            public void onLoadEnd(org.cef.browser.CefBrowser browser, CefFrame frame, int httpStatusCode) {
                CefBrowser wrapped = find(browser);
                if (wrapped != null) {
                    wrapped.setState(BrowserLoadState.success(httpStatusCode));
                }
            }

            @Override
            public void onLoadError(org.cef.browser.CefBrowser browser, CefFrame frame,
                                    CefLoadHandler.ErrorCode errorCode, String errorText, String failedUrl) {
                // CEF reports the first navigation as aborted when loadURL is repeated from
                // onAfterCreated. The second load is intentional and works around CEF issue 17702,
                // as in LiquidBounce's CefBrowser initialization.
                if (errorCode != null && errorCode.getCode() == -3) {
                    return;
                }
                CefBrowser wrapped = find(browser);
                if (wrapped != null) {
                    wrapped.setState(BrowserLoadState.failure(
                            errorCode == null ? -1 : errorCode.getCode(),
                            errorText == null ? "Unknown error" : errorText,
                            failedUrl == null ? "" : failedUrl
                    ));
                }
            }
        });

        var support = MCEFAccelerationSupport.getAccelerationSupport();
        accelerationFlags = new BrowserAccelerationFlags(support.isSupported(), support.isBeta());
    }

    @Override
    public void update() {
        if (isInitialized()) {
            MCEF.INSTANCE.getApp().getHandle().N_DoMessageLoopWork();
        }
    }

    @Override
    public Browser createBrowser(
            String url,
            BrowserViewport viewport,
            BrowserSettings settings,
            short priority,
            InputAcceptor inputAcceptor
    ) {
        if (!isInitialized()) {
            throw new IllegalStateException("Browser backend is not initialized");
        }
        CefBrowser browser = new CefBrowser(this, url, viewport, settings, priority, inputAcceptor);
        browsers.add(browser);
        sortBrowsers();
        return browser;
    }

    void sortBrowsers() {
        browsers.sort(Comparator.comparingInt(CefBrowser::getPriority));
    }

    void remove(CefBrowser browser) {
        browsers.remove(browser);
    }

    private CefBrowser find(org.cef.browser.CefBrowser browser) {
        return browsers.stream().filter(it -> it.getBrowserApi() == browser).findFirst().orElse(null);
    }

    @Override
    public void stop() {
        for (CefBrowser browser : List.copyOf(browsers)) {
            browser.close();
        }
        if (isInitialized()) {
            MCEF.INSTANCE.shutdown();
        }
        if (cacheDirectory != null) {
            deleteRecursively(cacheDirectory);
        }
    }

    private void cleanupOldCacheDirectories() {
        File[] directories = cacheRoot.listFiles(file ->
                file.isDirectory()
                        && System.currentTimeMillis() - file.lastModified() > CACHE_CLEANUP_THRESHOLD
        );
        if (directories == null) {
            return;
        }
        for (File directory : directories) {
            deleteRecursively(directory);
        }
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) {
            file.deleteOnExit();
        }
    }
}
