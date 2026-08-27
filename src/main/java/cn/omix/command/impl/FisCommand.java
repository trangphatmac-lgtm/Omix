package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.command.Command;
import cn.omix.fisproxy.FisProxyConnector;
import cn.omix.fisproxy.FisProxyFormatter;
import cn.omix.fisproxy.FisProxyManager;
import cn.omix.util.Util;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.fisproxy.ChangeIpOptions;
import org.fisproxy.ConflictException;
import org.fisproxy.FisProxyException;
import org.fisproxy.ListOperationsOptions;
import org.fisproxy.Operation;
import org.fisproxy.RequestOptions;
import org.fisproxy.SessionStatus;
import org.fisproxy.StartOptions;
import org.fisproxy.WaitOptions;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class FisCommand extends Command {
    private static final Gson GSON = new Gson();
    private static final Set<String> START_KEYS = Set.of(
            "serviceid", "target", "autonfa", "nfaitemid", "reusenfaitemid",
            "nfasource", "nfasku", "trypreviousnfa", "idempotencykey", "wait",
            "timeoutseconds", "intervalseconds"
    );
    private static final Set<String> CHANGE_IP_KEYS = Set.of(
            "idempotencykey", "wait", "waitrouteack", "timeoutseconds", "intervalseconds"
    );

    public FisCommand() {
        super(".fis <apikey/baseurl/clientid/timeout/me/services/status/entrances/start/changeip/stop/connect/op/request>", "fis");
    }

    @Override
    public void execute(String[] arguments) {
        if (arguments.length == 1) {
            showHelp();
            return;
        }

        try {
            switch (arguments[1].toLowerCase(Locale.ROOT)) {
                case "help" -> showHelp();
                case "apikey" -> configureApiKey(arguments);
                case "baseurl" -> configureBaseUrl(arguments);
                case "clientid" -> configureClientId(arguments);
                case "timeout" -> configureTimeout(arguments);
                case "me" -> requireLength(arguments, 2, () -> run("account", manager().me(), FisProxyFormatter::profile));
                case "services" -> requireLength(arguments, 2, () -> run("services", manager().services(), FisProxyFormatter::services));
                case "status" -> requireLength(arguments, 2, () -> run("status", manager().status(), FisProxyFormatter::status));
                case "entrances" -> handleEntrances(arguments);
                case "start" -> handleStart(arguments);
                case "changeip", "change-ip" -> handleChangeIp(arguments);
                case "stop" -> requireLength(arguments, 2, () -> run("stop", manager().stop(), FisProxyFormatter::stopped));
                case "connect" -> handleConnect(arguments);
                case "op", "operation", "operations" -> handleOperation(arguments);
                case "request", "raw" -> handleRequest(arguments);
                default -> showHelp();
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Util.logToChat("&c" + exception.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] arguments) {
        if (arguments.length == 2) {
            return List.of("help", "apikey", "baseurl", "clientid", "timeout", "me", "services",
                    "status", "entrances", "start", "changeip", "stop", "connect", "op", "request");
        }
        if (arguments.length == 3) {
            return switch (arguments[1].toLowerCase(Locale.ROOT)) {
                case "apikey" -> List.of("clear");
                case "baseurl" -> List.of(org.fisproxy.Client.DEFAULT_BASE_URL, "default");
                case "clientid" -> List.of("auto");
                case "timeout" -> List.of("30");
                case "start" -> startOptionCompletions();
                case "changeip", "change-ip" -> changeIpOptionCompletions();
                case "op", "operation", "operations" -> List.of("get", "list", "wait", "cancel");
                case "request", "raw" -> List.of("GET", "POST", "PUT", "PATCH", "DELETE");
                default -> List.of();
            };
        }
        if (arguments[1].equalsIgnoreCase("start")) {
            return startOptionCompletions();
        }
        if (arguments[1].equalsIgnoreCase("changeip") || arguments[1].equalsIgnoreCase("change-ip")) {
            return changeIpOptionCompletions();
        }
        if ((arguments[1].equalsIgnoreCase("op")
                || arguments[1].equalsIgnoreCase("operation")
                || arguments[1].equalsIgnoreCase("operations"))
                && arguments.length >= 4
                && arguments[2].equalsIgnoreCase("list")) {
            return List.of("status=running", "kind=session.start", "kind=session.change_ip", "limit=20");
        }
        if ((arguments[1].equalsIgnoreCase("request") || arguments[1].equalsIgnoreCase("raw"))
                && arguments.length >= 5) {
            return List.of("query={}", "body={}", "idempotencyKey=", "sign=true", "sign=false");
        }
        return List.of();
    }

    private void configureApiKey(String[] arguments) {
        if (arguments.length == 2) {
            Util.logToChat("FisProxy API key: " + (manager().hasApiKey() ? "&aconfigured" : "&cnot configured"));
            return;
        }
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Usage: .fis apikey <key/clear>");
        }
        boolean clear = arguments[2].equalsIgnoreCase("clear");
        manager().setApiKey(clear ? "" : arguments[2]);
        Util.logToChat(clear ? "FisProxy API key has been cleared." : "FisProxy API key has been updated.");
    }

    private void configureBaseUrl(String[] arguments) {
        if (arguments.length == 2) {
            Util.logToChat("FisProxy base URL: &b" + manager().getBaseUrl());
            return;
        }
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Usage: .fis baseurl <url/default>");
        }
        String value = arguments[2].equalsIgnoreCase("default")
                ? org.fisproxy.Client.DEFAULT_BASE_URL
                : arguments[2];
        manager().setBaseUrl(value);
        Util.logToChat("FisProxy base URL has been set to &b" + manager().getBaseUrl());
    }

    private void configureClientId(String[] arguments) {
        if (arguments.length == 2) {
            Util.logToChat("FisProxy client ID: &b" + manager().getClientId());
            return;
        }
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Usage: .fis clientid <id/auto>");
        }
        manager().setClientId(arguments[2].equalsIgnoreCase("auto") ? null : arguments[2]);
        Util.logToChat("FisProxy client ID has been set to &b" + manager().getClientId());
    }

    private void configureTimeout(String[] arguments) {
        if (arguments.length == 2) {
            Util.logToChat("FisProxy HTTP timeout: &b" + manager().getTimeoutSeconds() + " &fseconds");
            return;
        }
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Usage: .fis timeout <1-300>");
        }
        manager().setTimeoutSeconds(parseInt(arguments[2], "timeout"));
        Util.logToChat("FisProxy HTTP timeout has been set to &b" + manager().getTimeoutSeconds() + " &fseconds.");
    }

    private void handleEntrances(String[] arguments) {
        if (arguments.length > 3) {
            throw new IllegalArgumentException("Usage: .fis entrances [serviceId]");
        }
        run("entrances", manager().entrances(arguments.length == 3 ? arguments[2] : null), FisProxyFormatter::entrances);
    }

    private void handleStart(String[] arguments) {
        Map<String, String> options = parseOptions(arguments, 2, START_KEYS);
        StartOptions.Builder builder = StartOptions.builder();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            switch (entry.getKey()) {
                case "serviceid" -> builder.serviceId(entry.getValue());
                case "target" -> builder.target(entry.getValue());
                case "autonfa" -> builder.autoNfa(parseBoolean(entry.getValue(), "autoNfa"));
                case "nfaitemid" -> builder.nfaItemId(entry.getValue());
                case "reusenfaitemid" -> builder.reuseNfaItemId(entry.getValue());
                case "nfasource" -> builder.nfaSource(entry.getValue());
                case "nfasku" -> builder.nfaSku(entry.getValue());
                case "trypreviousnfa" -> builder.tryPreviousNfa(parseBoolean(entry.getValue(), "tryPreviousNfa"));
                case "idempotencykey" -> builder.idempotencyKey(entry.getValue());
                case "wait" -> builder.wait(parseBoolean(entry.getValue(), "wait"));
                case "timeoutseconds" -> builder.timeoutSeconds(parsePositiveDouble(entry.getValue(), "timeoutSeconds"));
                case "intervalseconds" -> builder.intervalSeconds(parsePositiveDouble(entry.getValue(), "intervalSeconds"));
                default -> throw new IllegalArgumentException("Unknown start option: " + entry.getKey());
            }
        }
        run("start", manager().start(builder.build()), FisProxyFormatter::operation);
    }

    private void handleChangeIp(String[] arguments) {
        Map<String, String> options = parseOptions(arguments, 2, CHANGE_IP_KEYS);
        ChangeIpOptions.Builder builder = ChangeIpOptions.builder();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            switch (entry.getKey()) {
                case "idempotencykey" -> builder.idempotencyKey(entry.getValue());
                case "wait" -> builder.wait(parseBoolean(entry.getValue(), "wait"));
                case "waitrouteack" -> builder.waitRouteAck(parseBoolean(entry.getValue(), "waitRouteAck"));
                case "timeoutseconds" -> builder.timeoutSeconds(parsePositiveDouble(entry.getValue(), "timeoutSeconds"));
                case "intervalseconds" -> builder.intervalSeconds(parsePositiveDouble(entry.getValue(), "intervalSeconds"));
                default -> throw new IllegalArgumentException("Unknown change-ip option: " + entry.getKey());
            }
        }
        run("change IP", manager().changeIp(builder.build()), FisProxyFormatter::operation);
    }

    private void handleConnect(String[] arguments) {
        if (arguments.length > 3) {
            throw new IllegalArgumentException("Usage: .fis connect [entranceIndex]");
        }
        int entranceIndex = arguments.length == 3 ? parseInt(arguments[2], "entrance index") : -1;
        run("connection address", manager().status(), status -> {
            String address = selectAddress(status, entranceIndex);
            FisProxyConnector.connect(MinecraftClient.getInstance(), address);
            return List.of("Connecting to " + address);
        });
    }

    private void handleOperation(String[] arguments) {
        if (arguments.length < 3) {
            throw new IllegalArgumentException("Usage: .fis op <get/list/wait/cancel> ...");
        }
        switch (arguments[2].toLowerCase(Locale.ROOT)) {
            case "get" -> {
                if (arguments.length != 4) throw new IllegalArgumentException("Usage: .fis op get <operationId>");
                run("operation", manager().getOperation(arguments[3]), FisProxyFormatter::operation);
            }
            case "cancel" -> {
                if (arguments.length != 4) throw new IllegalArgumentException("Usage: .fis op cancel <operationId>");
                run("cancel operation", manager().cancelOperation(arguments[3]), FisProxyFormatter::operation);
            }
            case "wait" -> handleWaitOperation(arguments);
            case "list" -> handleListOperations(arguments);
            default -> throw new IllegalArgumentException("Usage: .fis op <get/list/wait/cancel> ...");
        }
    }

    private void handleWaitOperation(String[] arguments) {
        if (arguments.length < 4) {
            throw new IllegalArgumentException("Usage: .fis op wait <operationId> [timeoutSeconds=180 intervalSeconds=1]");
        }
        Map<String, String> options = parseOptions(arguments, 4, Set.of("timeoutseconds", "intervalseconds"));
        WaitOptions.Builder builder = WaitOptions.builder();
        if (options.containsKey("timeoutseconds")) {
            builder.timeoutSeconds(parsePositiveDouble(options.get("timeoutseconds"), "timeoutSeconds"));
        }
        if (options.containsKey("intervalseconds")) {
            builder.intervalSeconds(parsePositiveDouble(options.get("intervalseconds"), "intervalSeconds"));
        }
        run("wait operation", manager().waitOperation(arguments[3], builder.build()), FisProxyFormatter::operation);
    }

    private void handleListOperations(String[] arguments) {
        Map<String, String> options = parseOptions(arguments, 3, Set.of("status", "kind", "limit"));
        ListOperationsOptions.Builder builder = ListOperationsOptions.builder();
        if (options.containsKey("status")) builder.status(options.get("status"));
        if (options.containsKey("kind")) builder.kind(options.get("kind"));
        if (options.containsKey("limit")) builder.limit(parseInt(options.get("limit"), "limit"));
        run("operations", manager().listOperations(builder.build()), FisProxyFormatter::operations);
    }

    private void handleRequest(String[] arguments) {
        if (arguments.length < 4) {
            throw new IllegalArgumentException("Usage: .fis request <method> <path> [query={} body={} idempotencyKey=... sign=true/false]");
        }
        Map<String, String> options = parseOptions(arguments, 4, Set.of("query", "body", "idempotencykey", "sign"));
        RequestOptions.Builder builder = RequestOptions.builder();
        if (options.containsKey("query")) builder.query(parseJsonObject(options.get("query"), "query"));
        if (options.containsKey("body")) builder.jsonBody(parseJsonObject(options.get("body"), "body"));
        if (options.containsKey("idempotencykey")) builder.idempotencyKey(options.get("idempotencykey"));
        if (options.containsKey("sign")) builder.sign(parseBoolean(options.get("sign"), "sign"));
        run("raw request", manager().request(arguments[2], arguments[3], builder.build()), FisProxyFormatter::raw);
    }

    private <T> void run(String action, CompletableFuture<T> future, java.util.function.Function<T, List<String>> formatter) {
        Util.logToChat("FisProxy " + action + " request started...");
        future.whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
            if (error != null) {
                logError(error);
                return;
            }
            printLines(formatter.apply(result));
        }));
    }

    private static void printLines(List<String> lines) {
        if (lines.isEmpty()) {
            Util.logToChat("FisProxy request completed with no results.");
            return;
        }
        Util.logToChat("&fFisProxy result:");
        int count = Math.min(lines.size(), 40);
        for (int index = 0; index < count; index++) {
            Util.logRaw("&8» &7" + lines.get(index));
        }
        if (lines.size() > count) {
            Util.logRaw("&8» &7... " + (lines.size() - count) + " more line(s)");
        }
    }

    private static void logError(Throwable error) {
        Throwable cause = unwrap(error);
        Util.logToChat("&cFisProxy request failed: " + errorMessage(cause));
        if (cause instanceof ConflictException conflict && conflict.existingOperation() != null) {
            Util.logRaw("&8» &7Existing operation: " + GSON.toJson(conflict.existingOperation()));
        }
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof FisProxyException fisError) {
            return (fisError.errorCode().isBlank() ? "" : fisError.errorCode() + ": ") + fisError.detail();
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof CompletionException || current instanceof ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    private static Map<String, String> parseOptions(String[] arguments, int start, Set<String> allowed) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = start; index < arguments.length; index++) {
            String argument = arguments[index];
            int separator = argument.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Expected key=value option, got: " + argument);
            }
            String key = normalizeKey(argument.substring(0, separator));
            String value = argument.substring(separator + 1);
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown option: " + argument.substring(0, separator));
            }
            if (result.put(key, value) != null) {
                throw new IllegalArgumentException("Duplicate option: " + argument.substring(0, separator));
            }
        }
        return result;
    }

    private static Map<String, Object> parseJsonObject(String input, String name) {
        try {
            JsonElement element = JsonParser.parseString(input);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(name + " must be a JSON object.");
            }
            Map<?, ?> raw = GSON.fromJson(element, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        } catch (com.google.gson.JsonParseException exception) {
            throw new IllegalArgumentException(name + " must be valid compact JSON.", exception);
        }
    }

    private static boolean parseBoolean(String input, String name) {
        if (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(name + " must be true or false.");
        }
        return Boolean.parseBoolean(input);
    }

    private static int parseInt(String input, String name) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer.", exception);
        }
    }

    private static double parsePositiveDouble(String input, String name) {
        try {
            double value = Double.parseDouble(input);
            if (!Double.isFinite(value) || value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive number.", exception);
        }
    }

    private static String selectAddress(SessionStatus status, int entranceIndex) {
        if (!status.running()) {
            throw new IllegalStateException("No FisProxy session is running.");
        }
        if (entranceIndex >= 0) {
            if (entranceIndex >= status.entrances().size()) {
                throw new IllegalArgumentException("Entrance index is out of range.");
            }
            return status.entrances().get(entranceIndex).address();
        }
        return status.address();
    }

    private static String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static void requireLength(String[] arguments, int length, Runnable action) {
        if (arguments.length != length) {
            throw new IllegalArgumentException("Unexpected arguments: " + String.join(" ", Arrays.copyOfRange(arguments, length, arguments.length)));
        }
        action.run();
    }

    private static FisProxyManager manager() {
        return Client.instance.getFisProxyManager();
    }

    private static List<String> startOptionCompletions() {
        return List.of("serviceId=", "target=", "autoNfa=true", "nfaItemId=", "reuseNfaItemId=",
                "nfaSource=local", "nfaSource=solar", "nfaSku=", "tryPreviousNfa=true",
                "idempotencyKey=", "wait=true", "timeoutSeconds=180", "intervalSeconds=1");
    }

    private static List<String> changeIpOptionCompletions() {
        return List.of("idempotencyKey=", "wait=true", "waitRouteAck=true", "timeoutSeconds=180", "intervalSeconds=1");
    }

    private static void showHelp() {
        Util.logToChat("&fFisProxy commands:");
        List<String> lines = List.of(
                ".fis apikey <key/clear> | baseurl <url/default> | clientid <id/auto> | timeout <seconds>",
                ".fis me | services | status | entrances [serviceId]",
                ".fis start [serviceId=... target=... autoNfa=true ... wait=true timeoutSeconds=180 intervalSeconds=1]",
                ".fis changeip [wait=true waitRouteAck=true timeoutSeconds=180 intervalSeconds=1] | stop",
                ".fis connect [entranceIndex]",
                ".fis op get <id> | list [status=... kind=... limit=20] | wait <id> [...] | cancel <id>",
                ".fis request <method> <path> [query={} body={} idempotencyKey=... sign=true/false]"
        );
        for (String line : lines) {
            Util.logRaw("&8» &7" + line);
        }
    }
}
