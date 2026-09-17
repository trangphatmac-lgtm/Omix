package cn.omix.util.ai;

import java.util.regex.Pattern;

/** Bounded startup diagnostics that preserve the error before a long stack trace. */
final class HarnessStartupLog {
    private static final int LIMIT = 65_536;
    private static final Pattern ERROR = Pattern.compile("\\b(?:[A-Za-z]*Error|[A-Za-z]*Exception):\\s*(.+)");
    private final StringBuilder content = new StringBuilder();
    private String summary;

    void append(String raw) {
        String line = redact(raw).replaceAll("\\x1B\\[[0-9;]*[A-Za-z]", "");
        var error = ERROR.matcher(line);
        if (summary == null && error.find()) {
            String message = error.group(1).strip();
            summary = message.length() > 500 ? message.substring(0, 500) + "…" : message;
        }
        int remaining = LIMIT - content.length();
        if (remaining > 0) {
            String entry = line + '\n';
            content.append(entry, 0, Math.min(entry.length(), remaining));
        }
    }

    String summary() {
        return summary == null ? "Harness exited before its Web server was ready." : summary;
    }

    String content() { return content.toString(); }

    static String redact(String text) {
        return text.replaceAll("(?i)((?:token|api[_-]?key|authorization)[\"']?\\s*[=:]\\s*[\"']?)(?:Bearer\\s+)?[^\\s&,\"']+", "$1[redacted]")
                .replaceAll("(?i)\\bBearer\\s+[^\\s,\"']+", "Bearer [redacted]");
    }
}
