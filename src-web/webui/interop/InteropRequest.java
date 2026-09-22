package im.webui.interop;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

public record InteropRequest(
        String method,
        String path,
        Map<String, List<String>> query,
        JsonObject body
) {
}
