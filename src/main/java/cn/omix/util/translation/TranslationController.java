package cn.omix.util.translation;

import cn.omix.module.impl.render.InGameTranslation;
import cn.omix.util.IMinecraft;
import cn.omix.util.ai.HarnessRuntime;
import cn.omix.util.network.GameConnectionContext;
import im.webui.WebUiRuntime;
import injection.accessor.ChatHudAccessor;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** All Minecraft objects are confined to the client thread. Network/cache workers see templates only. */
public final class TranslationController implements IMinecraft, AutoCloseable {
    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("Simplified Chinese", "zh-Hans"), Map.entry("Traditional Chinese", "zh-Hant"), Map.entry("English", "en"),
            Map.entry("Japanese", "ja"), Map.entry("Korean", "ko"), Map.entry("Russian", "ru"), Map.entry("German", "de"),
            Map.entry("French", "fr"), Map.entry("Spanish", "es"), Map.entry("Portuguese", "pt"), Map.entry("Italian", "it"),
            Map.entry("Turkish", "tr"), Map.entry("Indonesian", "id"));
    private final InGameTranslation module;
    private final TranslationCache cache;
    private final TranslationQueue queue;
    private final Set<Text> chatMessages = Collections.newSetFromMap(new IdentityHashMap<>());
    private final LinkedHashMap<Text, StyledTranslation> styled = new LinkedHashMap<>(32, .75f, true);
    private List<String> playerNames = List.of();
    private final LinkedHashSet<String> recentSpeakers = new LinkedHashSet<>();
    private final Set<String> reportedStatuses = new HashSet<>();
    private HarnessRuntime runtime;
    private HarnessRuntime.TranslationEndpoint endpoint;
    private TranslationClient transport;
    private CompletableFuture<?> catalogRequest;
    private Map<String, List<String>> models = Map.of();
    private long generation, nextCatalogAt, nextPlayersAt, lastRevision;
    private long endpointMissingSince;
    private Object world, player, connection;
    private String lastSettings = "", lastStatus = "";
    private boolean enabled, refreshing, catalogFailed;

    public TranslationController(InGameTranslation module) {
        this.module = module;
        cache = new TranslationCache(Path.of(mc.runDirectory.getPath(), "Omix", "translation", "ui-cache.json"));
        queue = new TranslationQueue(cache, (context, items) -> transport.translate(context, items));
    }
    public void enable() {
        enabled = true; generation++; nextCatalogAt = 0; endpointMissingSince = 0; reportedStatuses.clear();
        world = mc.world; player = mc.player; connection = mc.getNetworkHandler();
        try {
            runtime = WebUiRuntime.getInstance().getAiRuntime();
            runtime.startAsync(); status("Preparing");
        } catch (RuntimeException error) { status("Unavailable"); }
    }
    public void disable() {
        enabled = false; generation++; queue.reset(); chatMessages.clear(); styled.clear();
        recentSpeakers.clear(); playerNames = List.of();
        if (catalogRequest != null) catalogRequest.cancel(true);
        catalogRequest = null; refreshChat();
    }
    public void worldChanged() {
        queue.reset(); chatMessages.clear(); styled.clear(); lastSettings = ""; playerNames = List.of(); recentSpeakers.clear(); nextPlayersAt = 0;
        refreshChat();
    }
    public void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (world != mc.world || player != mc.player || connection != mc.getNetworkHandler()) {
            world = mc.world; player = mc.player; connection = mc.getNetworkHandler(); worldChanged();
        }
        if (runtime == null) { status("Unavailable"); return; }
        var discovered = runtime.getTranslationEndpoint();
        if (!Objects.equals(discovered, endpoint)) {
            generation++; if (catalogRequest != null) catalogRequest.cancel(true); catalogRequest = null;
            endpoint = discovered; transport = endpoint == null ? null : new TranslationClient(endpoint);
            queue.reset(); styled.clear(); nextCatalogAt = 0; lastSettings = ""; refreshChat();
        }
        if (runtime.getState() == HarnessRuntime.State.FAILED) { status("Harness failed: .ai restart"); return; }
        if (transport == null) {
            if (runtime.getState() == HarnessRuntime.State.READY) {
                if (endpointMissingSince == 0) endpointMissingSince = now;
                status(now - endpointMissingSince > 15000 ? "Translation plugin unavailable" : "Preparing");
            } else { endpointMissingSince = 0; status("Preparing"); }
            return;
        }
        endpointMissingSince = 0;
        if (now >= nextCatalogAt && catalogRequest == null) loadModels(now);
        updateModelChoices();
        boolean selected = !module.getProvider().is(InGameTranslation.UNSELECTED) && !module.getModel().is(InGameTranslation.UNSELECTED);
        String settings = List.of(module.getProvider().getValue(), module.getModel().getValue(), module.getLanguage().getValue(),
                module.getChat().getValue(), module.getScoreboard().getValue(), module.getNameTags().getValue(),
                module.getChatDisplay().getValue(), module.getScoreboardDisplay().getValue(), module.getNameTagsDisplay().getValue()).toString();
        if (!settings.equals(lastSettings)) { queue.reset(); styled.clear(); lastSettings = settings; refreshChat(); }
        if (!selected) { status("Select model / 请选择模型"); return; }
        if (mc.world == null || mc.player == null) { status("Ready"); return; }
        if (now >= nextPlayersAt) {
            nextPlayersAt = now + 1000;
            var names = new TreeSet<String>(recentSpeakers); names.add(GameConnectionContext.username(mc));
            if (mc.getNetworkHandler() != null) mc.getNetworkHandler().getPlayerList().forEach(entry -> names.add(entry.getProfile().name()));
            mc.world.getPlayers().forEach(entity -> names.add(entity.getName().getString()));
            var next = List.copyOf(names);
            if (!next.equals(playerNames)) { playerNames = next; styled.clear(); }
        }
        String server = GameConnectionContext.serverAddress(mc);
        if (server == null) server = mc.getServer() == null ? "local" : "singleplayer:" + mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT);
        queue.configure(new TranslationQueue.Context(server, module.getProvider().getValue(), module.getModel().getValue(),
                LANGUAGES.get(module.getLanguage().getValue()), endpoint.generation()));
        var retained = ((ChatHudAccessor) mc.inGameHud.getChatHud()).omix$getMessages();
        Set<Text> live = Collections.newSetFromMap(new IdentityHashMap<>());
        retained.forEach(line -> live.add(line.content())); chatMessages.retainAll(live);
        if (module.getChat().getValue()) for (Text message : chatMessages) translate(message, "chat", module.getChatDisplay().is("Bilingual"));
        queue.tick(now);
        if (queue.revision() != lastRevision) { lastRevision = queue.revision(); refreshChat(); }
        status(queue.error() != null ? "Translation failed (retrying)" : catalogFailed ? "Model list unavailable" : "Ready");
    }
    private void loadModels(long now) {
        nextCatalogAt = now + 10000; long requestedGeneration = generation;
        var request = transport.models(); catalogRequest = request;
        request.whenComplete((catalog, error) -> mc.execute(() -> {
            if (!enabled || generation != requestedGeneration) return;
            catalogRequest = null; catalogFailed = error != null;
            if (error == null) {
                models = catalog;
                var providers = new ArrayList<String>(); providers.add(InGameTranslation.UNSELECTED); providers.addAll(models.keySet());
                module.getProvider().replaceModes(providers); updateModelChoices();
                WebUiRuntime.getInstance().notifyModulesChanged();
            }
        }));
    }
    private void updateModelChoices() {
        var choices = new ArrayList<String>(); choices.add(InGameTranslation.UNSELECTED);
        choices.addAll(models.getOrDefault(module.getProvider().getValue(), List.of()));
        // replaceModes preserves a saved ID even when the provider cannot currently advertise it.
        if (!Arrays.equals(module.getModel().getModes(), withSaved(choices, module.getModel().getValue()))) module.getModel().replaceModes(choices);
    }
    public void providerChanged() {
        queue.reset(); styled.clear(); lastSettings = "";
        updateModelChoices(); WebUiRuntime.getInstance().notifyModulesChanged(); refreshChat();
    }
    public void protectPlayer(String name) {
        if (name == null || name.isBlank() || name.length() > 256) return;
        recentSpeakers.remove(name); recentSpeakers.add(name);
        while (recentSpeakers.size() > 256) recentSpeakers.remove(recentSpeakers.getFirst());
        if (!playerNames.contains(name)) {
            var names = new ArrayList<>(playerNames); names.add(name); playerNames = List.copyOf(names); styled.clear();
        }
    }
    private static String[] withSaved(List<String> choices, String saved) {
        var result = new ArrayList<>(choices); if (!result.contains(saved)) result.add(saved); return result.toArray(String[]::new);
    }
    public void received(Text message) {
        if (enabled && module.getChat().getValue()) {
            if (chatMessages.size() >= 100) {
                Set<Text> live = Collections.newSetFromMap(new IdentityHashMap<>());
                ((ChatHudAccessor) mc.inGameHud.getChatHud()).omix$getMessages().forEach(line -> live.add(line.content()));
                chatMessages.retainAll(live);
            }
            if (chatMessages.size() < 101) chatMessages.add(message);
        }
    }
    public Text chat(Text message) {
        return enabled && module.getChat().getValue() && chatMessages.contains(message)
                ? translate(message, "chat", module.getChatDisplay().is("Bilingual")) : message;
    }
    public Text scoreboard(Text message) {
        return enabled && module.getScoreboard().getValue() ? translate(message, "scoreboard", module.getScoreboardDisplay().is("Bilingual")) : message;
    }
    public Text nameTag(Text message, String protectedName) {
        if (!enabled || !module.getNameTags().getValue() || message == null) return message;
        if (protectedName != null && !playerNames.contains(protectedName)) {
            var names = new ArrayList<>(playerNames); names.add(protectedName);
            return translated(message, StyledTranslation.capture(message, names), "nametag", module.getNameTagsDisplay().is("Bilingual"));
        }
        return translate(message, "nametag", module.getNameTagsDisplay().is("Bilingual"));
    }
    private Text translate(Text original, String kind, boolean bilingual) {
        if (original == null) return null;
        StyledTranslation snapshot = styled.get(original);
        if (snapshot == null) {
            snapshot = StyledTranslation.capture(original, playerNames); styled.put(original.copy(), snapshot);
            while (styled.size() > 512) styled.remove(styled.keySet().iterator().next());
        }
        return translated(original, snapshot, kind, bilingual);
    }
    private Text translated(Text original, StyledTranslation snapshot, String kind, boolean bilingual) {
        String result = queue.lookup(kind, snapshot.template(), System.currentTimeMillis());
        return result == null ? original : snapshot.display(original, result, bilingual, kind.equals("chat"));
    }
    private void status(String status) {
        if (!status.equals(lastStatus)) {
            lastStatus = status; module.setSuffix(status);
        }
        if (!status.equals("Preparing") && !status.equals("Ready") && mc.player != null && reportedStatuses.add(status))
            cn.omix.util.Util.log("InGameTranslation: " + status);
    }
    private void refreshChat() {
        if (refreshing || mc.inGameHud == null) return;
        refreshing = true;
        try {
            ChatHudAccessor hud = (ChatHudAccessor) mc.inGameHud.getChatHud();
            var anchor = TranslationChatAnchor.capture(hud);
            hud.omix$setScrolledLines(0); hud.omix$refresh(); anchor.restore(hud);
        } finally { refreshing = false; }
    }
    @Override public void close() { disable(); cache.close(); }
}
