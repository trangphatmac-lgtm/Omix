package cn.omix.fisproxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fisproxy.Entrance;
import org.fisproxy.Operation;
import org.fisproxy.SessionStatus;
import org.fisproxy.StopResult;
import org.fisproxy.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FisProxyFormatter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FisProxyFormatter() {
    }

    public static List<String> profile(UserProfile profile) {
        List<String> lines = new ArrayList<>();
        lines.add("User: " + value(profile.username(), "(unnamed)") + " [" + profile.id() + "]");
        lines.add("Service Point: " + profile.balances().servicePoint()
                + " | NFA Coin: " + profile.balances().nfaCoin()
                + " | Subscription: " + profile.balances().subscriptionPass());
        lines.add("Current session: " + (profile.session() == null ? "none" : sessionSummary(profile.session())));
        return lines;
    }

    public static List<String> services(List<Map<String, Object>> services) {
        List<String> lines = new ArrayList<>();
        lines.add("Visible services: " + services.size());
        for (Map<String, Object> service : services) {
            lines.add(value(service.get("id"), "?") + " | "
                    + value(service.get("name"), "unnamed") + " | access="
                    + value(service.get("hasAccess"), "?"));
        }
        return lines;
    }

    public static List<String> status(SessionStatus status) {
        List<String> lines = new ArrayList<>();
        lines.add("Session: " + (status.running() ? "running" : "stopped"));
        if (status.session() != null) {
            lines.add(sessionSummary(status.session()));
        }
        if (status.address() != null && !status.address().isBlank()) {
            lines.add("Address: " + status.address());
        }
        appendEntrances(lines, status.entrances());
        return lines;
    }

    public static List<String> entrances(List<Map<String, Object>> entrances) {
        List<String> lines = new ArrayList<>();
        lines.add("Entrances: " + entrances.size());
        for (Map<String, Object> entrance : entrances) {
            lines.add(value(entrance.get("id"), "?") + " | "
                    + value(entrance.get("name"), "unnamed") + " | "
                    + value(entrance.get("address"), value(entrance.get("host"), "?")));
        }
        return lines;
    }

    public static List<String> operation(Operation operation) {
        List<String> lines = new ArrayList<>();
        lines.add("Operation: " + operation.id() + " | " + operation.kind() + " | " + operation.status());
        if (!operation.progressPhase().isBlank()) {
            lines.add("Phase: " + operation.progressPhase());
        }
        if (operation.sessionId() != null) {
            lines.add("Session ID: " + operation.sessionId());
        }
        lines.add("Route ACK: " + operation.routeAcked());
        if (operation.message() != null && !operation.message().isBlank()) {
            lines.add("Message: " + operation.message());
        }
        if (operation.errorCode() != null && !operation.errorCode().isBlank()) {
            lines.add("Error: " + operation.errorCode());
        }
        appendEntrances(lines, operation.entrances());
        return lines;
    }

    public static List<String> operations(List<Operation> operations) {
        List<String> lines = new ArrayList<>();
        lines.add("Operations: " + operations.size());
        for (Operation operation : operations) {
            lines.add(operation.id() + " | " + operation.kind() + " | " + operation.status()
                    + (operation.progressPhase().isBlank() ? "" : " | " + operation.progressPhase()));
        }
        return lines;
    }

    public static List<String> stopped(StopResult result) {
        return List.of(
                "Session stopped.",
                "Duration: " + result.duration() + " minute(s) | deduction: " + result.deduction(),
                "Canceled operations: " + result.canceledOperations()
        );
    }

    public static List<String> raw(Map<String, Object> response) {
        return GSON.toJson(response).lines().toList();
    }

    private static void appendEntrances(List<String> lines, List<Entrance> entrances) {
        for (int index = 0; index < entrances.size(); index++) {
            Entrance entrance = entrances.get(index);
            lines.add("Entrance " + index + ": " + entrance.name() + " | " + entrance.address());
        }
    }

    private static String sessionSummary(Map<String, Object> session) {
        return "id=" + value(session.get("sessionId"), value(session.get("id"), "?"))
                + " | service=" + value(session.get("serviceId"), "?")
                + " | state=" + value(session.get("state"), "?")
                + " | target=" + value(session.get("target"), "?");
    }

    private static String value(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }
}
