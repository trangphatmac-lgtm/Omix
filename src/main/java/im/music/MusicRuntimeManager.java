package im.music;

import com.google.gson.JsonObject;
import im.webui.backend.BrowserPreparationProgress;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class MusicRuntimeManager {
    private final NodeRuntimeManager nodeRuntime;
    private final MusicSidecarManager sidecar;
    private volatile MusicServiceState state = MusicServiceState.STOPPED;
    private volatile BrowserPreparationProgress progress = BrowserPreparationProgress.IDLE;
    private volatile Throwable failure;
    private volatile CompletableFuture<URI> startup;
    private volatile Path nodeExecutable;
    private volatile boolean automaticRestartUsed;
    private volatile boolean stopped;

    public MusicRuntimeManager(Path root) {
        nodeRuntime = new NodeRuntimeManager(root);
        sidecar = new MusicSidecarManager(root);
        sidecar.setExitListener(this::handleUnexpectedExit);
    }

    public synchronized CompletableFuture<URI> startAsync() {
        stopped = false;
        if (state == MusicServiceState.READY && sidecar.isReady()) {
            return CompletableFuture.completedFuture(sidecar.getEndpoint());
        }
        if (startup != null && !startup.isDone()) return startup;
        failure = null;
        state = MusicServiceState.NODE_PREPARING;
        progress = BrowserPreparationProgress.indeterminate("Preparing Node.js runtime");
        startup = nodeRuntime.prepareAsync(value -> progress = value)
                .thenApply(executable -> {
                    nodeExecutable = executable;
                    state = MusicServiceState.SIDECAR_STARTING;
                    progress = BrowserPreparationProgress.indeterminate("Starting music service");
                    try {
                        return sidecar.start(executable);
                    } catch (Exception exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                });
        startup.whenComplete((endpoint, throwable) -> {
            if (throwable == null) {
                state = MusicServiceState.READY;
                progress = BrowserPreparationProgress.determinate("Music service ready", 1.0F);
                failure = null;
            } else {
                fail(unwrap(throwable));
            }
        });
        return startup;
    }

    public synchronized CompletableFuture<URI> retryAsync() {
        sidecar.stop();
        startup = null;
        automaticRestartUsed = false;
        return startAsync();
    }

    public synchronized CompletableFuture<URI> clearRuntimeAndRetryAsync() {
        sidecar.stop();
        try {
            nodeRuntime.clearCurrent();
        } catch (Exception exception) {
            fail(exception);
            return CompletableFuture.failedFuture(exception);
        }
        startup = null;
        automaticRestartUsed = false;
        return startAsync();
    }

    public synchronized void stop() {
        stopped = true;
        sidecar.stop();
        state = MusicServiceState.STOPPED;
        progress = BrowserPreparationProgress.IDLE;
        startup = null;
    }

    public MusicServiceState getState() {
        if (state == MusicServiceState.READY && !sidecar.isReady()) {
            handleUnexpectedExit(new IllegalStateException("Music sidecar is not running"));
        }
        return state;
    }

    public BrowserPreparationProgress getProgress() {
        return progress;
    }

    public Throwable getFailure() {
        return failure;
    }

    public URI getEndpoint() {
        return sidecar.getEndpoint();
    }

    public String getToken() {
        return sidecar.getToken();
    }

    public JsonObject statusJson() {
        JsonObject result = new JsonObject();
        result.addProperty("state", getState().name());
        result.addProperty("task", progress.task());
        result.addProperty("progress", progress.progress());
        result.addProperty("bytesRead", progress.bytesRead());
        result.addProperty("totalBytes", progress.totalBytes());
        result.addProperty("nodeVersion", NodeRuntimeDescriptor.VERSION);
        try {
            result.addProperty("platform", MusicPlatform.current().id());
        } catch (Exception exception) {
            result.addProperty("platform", "unsupported");
        }
        Throwable currentFailure = failure;
        if (currentFailure != null) {
            result.addProperty(
                    "error",
                    currentFailure.getMessage() == null
                            ? currentFailure.getClass().getSimpleName()
                            : currentFailure.getMessage()
            );
        }
        return result;
    }

    private synchronized void handleUnexpectedExit(Throwable throwable) {
        if (stopped || state == MusicServiceState.STOPPED) return;
        if (!automaticRestartUsed && nodeExecutable != null) {
            automaticRestartUsed = true;
            state = MusicServiceState.SIDECAR_STARTING;
            progress = BrowserPreparationProgress.indeterminate("Restarting music service");
            Thread.ofVirtual().name("Omix-Music-Restart").start(() -> {
                try {
                    sidecar.start(nodeExecutable);
                    state = MusicServiceState.READY;
                    progress = BrowserPreparationProgress.determinate("Music service ready", 1.0F);
                    failure = null;
                } catch (Exception exception) {
                    fail(exception);
                }
            });
            return;
        }
        fail(throwable);
    }

    private void fail(Throwable throwable) {
        failure = throwable;
        state = MusicServiceState.FAILED;
        String message = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
        progress = BrowserPreparationProgress.indeterminate(message);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
