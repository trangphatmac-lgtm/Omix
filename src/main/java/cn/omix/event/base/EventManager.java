package cn.omix.event.base;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.script.api.Registration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Exact-type, synchronous dispatch. Callbacks retain the emitting thread. */
public class EventManager {
    private final Map<Class<? extends Event>, List<Listener>> listeners = new ConcurrentHashMap<>();
    private static final class Listener {
        final Object owner;
        final Method method;
        final int priority;
        final Consumer<Event> callback;
        volatile boolean active = true;
        Listener(Object owner, Method method, int priority, Consumer<Event> callback) {
            this.owner = owner; this.method = method; this.priority = priority; this.callback = callback;
        }
    }
    public void register(Object... objects) { for (Object object : objects) register(object); }
    public synchronized void register(Object owner) {
        Set<String> overridden = new HashSet<>();
        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                String signature = method.getName() + Arrays.toString(method.getParameterTypes());
                if (!overridden.add(signature) || !method.isAnnotationPresent(EventTarget.class)
                        || method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
                var event = method.getParameterTypes()[0].asSubclass(Event.class);
                var entries = listeners.getOrDefault(event, List.of());
                if (entries.stream().anyMatch(entry -> entry.owner == owner && method.equals(entry.method))) continue;
                method.setAccessible(true);
                EventPriority priority = method.getAnnotation(EventPriority.class);
                add(event, new Listener(owner, method, priority == null ? 10 : priority.value(), value -> {
                    try { method.invoke(owner, value); }
                    catch (InvocationTargetException error) {
                        Throwable cause = error.getCause();
                        if (cause instanceof Error fatal) throw fatal;
                        if (cause instanceof RuntimeException failure) throw failure;
                        throw new IllegalStateException(cause);
                    } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                }));
            }
        }
    }
    public synchronized <E extends Event> Registration subscribe(Object owner, Class<E> type, int priority, Consumer<E> callback) {
        Objects.requireNonNull(owner); Objects.requireNonNull(callback);
        Listener listener = new Listener(owner, null, priority, event -> callback.accept(type.cast(event)));
        add(type, listener);
        return () -> remove(type, listener);
    }
    private void add(Class<? extends Event> type, Listener listener) {
        var entries = new ArrayList<>(listeners.getOrDefault(type, List.of()));
        entries.add(listener);
        entries.sort(Comparator.comparingInt(entry -> entry.priority));
        listeners.put(type, List.copyOf(entries));
    }
    private synchronized void remove(Class<? extends Event> type, Listener listener) {
        listener.active = false;
        var entries = listeners.get(type);
        if (entries != null) {
            var remaining = entries.stream().filter(entry -> entry != listener).toList();
            if (remaining.isEmpty()) listeners.remove(type); else listeners.put(type, remaining);
        }
    }
    public synchronized void unregister(Object owner) {
        for (var type : List.copyOf(listeners.keySet())) {
            for (Listener entry : listeners.getOrDefault(type, List.of())) if (entry.owner == owner) remove(type, entry);
        }
    }
    public void call(Event event) {
        var entries = listeners.get(event.getClass());
        if (entries == null) return;
        try (var phase = cn.omix.util.script.ScriptRenderPhase.enter(event)) {
        for (Listener entry : entries) {
            if (!entry.active) continue;
            if (entry.owner instanceof cn.omix.module.Module module && !module.isNativeBehaviorActive()) continue;
            try { entry.callback.accept(event); }
            catch (Exception error) {
                if (Client.logger != null) Client.logger.debug("Event listener failed: {}", event.getClass().getSimpleName(), error);
            }
        }
    }
    }
    public boolean hasListeners(Class<? extends Event> type) {
        var entries = listeners.get(type);
        return entries != null && entries.stream().anyMatch(entry -> entry.active);
    }
}
