package cn.omix.fisproxy;

import cn.omix.Client;
import org.fisproxy.ChangeIpOptions;
import org.fisproxy.ClientOptions;
import org.fisproxy.ListOperationsOptions;
import org.fisproxy.Operation;
import org.fisproxy.RequestOptions;
import org.fisproxy.SessionStatus;
import org.fisproxy.StartOptions;
import org.fisproxy.StopResult;
import org.fisproxy.UserProfile;
import org.fisproxy.WaitOptions;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FisProxyManager implements AutoCloseable {
    private final FisProxyConfig config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Omix-FisProxy");
        thread.setDaemon(true);
        return thread;
    });
    private final Object clientLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    private org.fisproxy.Client client;

    public FisProxyManager(Path configFile) {
        config = new FisProxyConfig(configFile);
    }

    public boolean hasApiKey() {
        return !config.snapshot().apiKey().isBlank();
    }

    public String getBaseUrl() {
        return config.snapshot().baseUrl();
    }

    public String getClientId() {
        return config.snapshot().clientId();
    }

    public int getTimeoutSeconds() {
        return config.snapshot().timeoutSeconds();
    }

    public void updateSettings(String apiKeyOrNull, String baseUrl, String clientId, int timeoutSeconds) {
        config.update(apiKeyOrNull, baseUrl, clientId, timeoutSeconds);
        resetClient();
    }

    public void setApiKey(String apiKey) {
        config.setApiKey(apiKey);
        resetClient();
    }

    public void setBaseUrl(String baseUrl) {
        config.setBaseUrl(baseUrl);
        resetClient();
    }

    public void setClientId(String clientId) {
        config.setClientId(clientId);
        resetClient();
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        config.setTimeoutSeconds(timeoutSeconds);
        resetClient();
    }

    public CompletableFuture<UserProfile> me() {
        return submit(() -> client().me());
    }

    public CompletableFuture<List<Map<String, Object>>> services() {
        return submit(() -> client().services());
    }

    public CompletableFuture<SessionStatus> status() {
        return submit(() -> client().status());
    }

    public CompletableFuture<List<Map<String, Object>>> entrances(String serviceId) {
        return submit(() -> client().entrances(blankToNull(serviceId)));
    }

    public CompletableFuture<Operation> start(StartOptions options) {
        return submit(() -> client().start(options));
    }

    public CompletableFuture<Operation> changeIp(ChangeIpOptions options) {
        return submit(() -> client().changeIp(options));
    }

    public CompletableFuture<StopResult> stop() {
        return submit(() -> client().stop());
    }

    public CompletableFuture<Operation> getOperation(String operationId) {
        return submit(() -> client().getOperation(requireOperationId(operationId)));
    }

    public CompletableFuture<List<Operation>> listOperations(ListOperationsOptions options) {
        return submit(() -> client().listOperations(options));
    }

    public CompletableFuture<Operation> waitOperation(String operationId, WaitOptions options) {
        return submit(() -> client().waitOperation(requireOperationId(operationId), options));
    }

    public CompletableFuture<Operation> cancelOperation(String operationId) {
        return submit(() -> client().cancelOperation(requireOperationId(operationId)));
    }

    public CompletableFuture<Map<String, Object>> request(String method, String path, RequestOptions options) {
        return submit(() -> client().request(method, path, options));
    }

    private <T> CompletableFuture<T> submit(Callable<T> action) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("FisProxy manager is closed."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private org.fisproxy.Client client() {
        synchronized (clientLock) {
            if (client != null) {
                return client;
            }
            FisProxyConfig.Snapshot settings = config.snapshot();
            if (settings.apiKey().isBlank()) {
                throw new IllegalStateException("Set the FisProxy API key first with .fis apikey <key>.");
            }
            client = new org.fisproxy.Client(settings.apiKey(), ClientOptions.builder()
                    .baseUrl(settings.baseUrl())
                    .clientId(settings.clientId())
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .userAgent(Client.name + "/" + Client.version + " fisproxy-java/0.1.0")
                    .build());
            return client;
        }
    }

    private void resetClient() {
        synchronized (clientLock) {
            if (client != null) {
                client.close();
                client = null;
            }
        }
    }

    private static String requireOperationId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Operation ID cannot be empty.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        resetClient();
        executor.shutdownNow();
    }
}
