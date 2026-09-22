package im.webui.interop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

public record InteropResponse(HttpResponseStatus status, byte[] body, String contentType) {
    private static final Gson GSON = new Gson();

    public static InteropResponse json(HttpResponseStatus status, JsonObject value) {
        return text(status, GSON.toJson(value), "application/json; charset=UTF-8");
    }

    public static InteropResponse text(HttpResponseStatus status, String value) {
        return text(status, value, "text/plain; charset=UTF-8");
    }

    public static InteropResponse text(HttpResponseStatus status, String value, String contentType) {
        return new InteropResponse(status, value.getBytes(CharsetUtil.UTF_8), contentType);
    }

    public static InteropResponse noContent() {
        return text(HttpResponseStatus.NO_CONTENT, "");
    }
}
