package im.webui.backend;

import java.util.List;
import java.util.function.Consumer;
import im.webui.backend.input.InputAcceptor;

public interface BrowserBackend {
    boolean isInitialized();

    List<? extends Browser> getBrowsers();

    BrowserAccelerationFlags getAccelerationFlags();

    void prepareAsync(Runnable whenAvailable, Consumer<Throwable> onFailure);

    void start();

    void update();

    Browser createBrowser(
            String url,
            BrowserViewport viewport,
            BrowserSettings settings,
            short priority,
            InputAcceptor inputAcceptor
    );

    void stop();
}
