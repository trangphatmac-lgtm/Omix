package cn.omix.script.api;

import cn.omix.Client;
import cn.omix.event.base.Event;
import cn.omix.module.value.Value;
import cn.omix.util.script.ScriptScope;
import java.util.function.*;

/** Lifecycle and event ownership common to modules, modes and HUDs. */
public abstract class FeatureHandle {
    protected final ScriptContext context;
    protected final String id;
    private Runnable enable = () -> {}, disable = () -> {};
    private volatile boolean active;
    private volatile boolean faulted;
    private ScriptScope activation = new ScriptScope();
    protected FeatureHandle(ScriptContext context, String id) { this.context = context; this.id = context.qualify(id); }
    public String id() { return id; }
    public boolean active() { return active && context.active() && !faulted; }
    public FeatureHandle onEnable(Runnable callback) { context.ensurePreparing(); enable = callback; return this; }
    public FeatureHandle onDisable(Runnable callback) { context.ensurePreparing(); disable = callback; return this; }
    public <E extends Event> FeatureHandle on(Class<E> event, Consumer<E> callback) { return on(event, 10, callback); }
    public <E extends Event> FeatureHandle on(Class<E> event, int priority, Consumer<E> callback) {
        context.ensurePreparing();
        context.own(Client.instance.getEventManager().subscribe(this, event, priority, value -> {
            if (active()) invoke(event.getSimpleName(), () -> callback.accept(value));
        }));
        return this;
    }
    public synchronized <T extends AutoCloseable> T own(T resource) {
        if (!active()) {
            try { resource.close(); } catch (Exception error) { context.error(id + ":late-resource", error); }
            throw new IllegalStateException("Feature is not active: " + id);
        }
        return activation.own(resource);
    }
    public void activate() {
        if (active) return;
        faulted = false; activation = new ScriptScope(); activation.activate(); active = true;
        invoke("onEnable", enable);
    }
    public void deactivate() {
        if (!active) return;
        active = false;
        try { context.invoke(id + ":onDisable", disable); }
        finally { try { activation.close(); } catch (Exception error) { context.error(id + ":cleanup", error); } }
    }
    public void invoke(String callback, Runnable action) {
        try { action.run(); }
        catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error);
            faulted = true; context.error(id + ":" + callback, error);
            net.minecraft.client.MinecraftClient.getInstance().execute(this::stopAfterError);
        }
    }
    protected abstract void stopAfterError();
    public abstract <T extends Value> T setting(T value);
}
