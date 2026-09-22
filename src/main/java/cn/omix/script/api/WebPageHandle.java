package cn.omix.script.api;

import cn.omix.util.script.ScriptWebPages;
import com.google.gson.*;
import java.util.function.Function;

/** Trusted HTML page with generation-bound JSON request/reply messages on the client thread. */
public final class WebPageHandle {
    private final ScriptContext context;
    private final String id, html;
    private final Function<JsonElement, JsonElement> handler;
    private boolean faulted;
    public WebPageHandle(ScriptContext context, String id, String html, Function<JsonElement, JsonElement> handler) {
        this.context = context; this.id = context.qualify(id); this.html = html; this.handler = handler;
        if (html.length() > 524288) throw new IllegalArgumentException("Web page exceeds 512 KiB");
        context.installWith(() -> ScriptWebPages.install(this));
    }
    public String id() { return id; }
    public long generation() { return context.generation(); }
    public boolean active() { return context.active() && !faulted; }
    public String html() { return html; }
    public void open() {
        context.requireActive();
        if (!net.minecraft.client.MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Open UI on the client thread");
        ScriptWebPages.open(this);
    }
    public JsonElement message(JsonElement message) {
        context.requireActive();
        if (faulted) throw new IllegalStateException("Page callback failed; reload the script");
        try { return handler.apply(message); }
        catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error); faulted = true; context.error(id + ":webMessage", error); throw new IllegalStateException("Page callback failed", error); }
    }
}
