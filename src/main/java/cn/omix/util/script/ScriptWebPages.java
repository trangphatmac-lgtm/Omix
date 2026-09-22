package cn.omix.util.script;

import cn.omix.script.api.*;
import com.google.gson.*;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenType;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptWebPages {
    private static final ConcurrentHashMap<String, WebPageHandle> PAGES = new ConcurrentHashMap<>();
    private static volatile WebPageHandle active;
    private ScriptWebPages() {}
    public static Registration install(WebPageHandle page) {
        if (PAGES.putIfAbsent(page.id(), page) != null) throw new IllegalArgumentException("Web page exists: " + page.id());
        return () -> {
            PAGES.remove(page.id(), page);
            if (active == page) {
                active = null;
                var mc = net.minecraft.client.MinecraftClient.getInstance();
                if (mc.currentScreen instanceof im.webui.screen.WebUiScreen screen && screen.getType().equals(WebScreenType.SCRIPT_PAGE))
                    WebUiRuntime.getInstance().closeScreen();
            }
        };
    }
    public static WebPageHandle get(String id, long generation) {
        var page = PAGES.get(id);
        if (page == null || !page.active() || page.generation() != generation) throw new IllegalStateException("Page generation is no longer active");
        return page;
    }
    public static void open(WebPageHandle page) {
        get(page.id(), page.generation()); active = page;
        WebUiRuntime.getInstance().openScreen(WebScreenType.SCRIPT_PAGE);
    }
    public static JsonObject active() {
        JsonObject value = new JsonObject(); var page = active;
        if (page != null && page.active()) { value.addProperty("id", page.id()); value.addProperty("generation", page.generation()); }
        return value;
    }
    public static String html(WebPageHandle page) {
        // Install before user markup, so inline scripts may immediately use the bridge.
        return "<!doctype html><meta charset='UTF-8'><script>window.omixScript={send:async(message)=>{const r=await fetch('/api/v1/scripts/pageMessage',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:"
                + new Gson().toJson(page.id()) + ",generation:" + page.generation() + ",message})});const v=await r.json();if(v.error)throw Error(v.error);return v.value;}};</script>" + page.html();
    }
}
