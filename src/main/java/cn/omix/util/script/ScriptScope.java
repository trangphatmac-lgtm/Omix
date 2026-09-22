package cn.omix.util.script;

import cn.omix.script.api.Registration;
import java.util.*;

/** Owns a generation's resources; invalidation happens before reverse-order cleanup. */
public final class ScriptScope implements Registration {
    private final List<AutoCloseable> resources = new ArrayList<>();
    private boolean closed;
    private volatile boolean active;
    public synchronized <T extends AutoCloseable> T own(T resource) {
        if (closed) {
            try { resource.close(); } catch (Exception ignored) { }
            throw new IllegalStateException("Script generation is unloaded.");
        }
        resources.add(resource); return resource;
    }
    public synchronized void release(AutoCloseable resource) { resources.remove(resource); }
    public synchronized void activate() { if (closed) throw new IllegalStateException("Script is unloaded."); active = true; }
    public boolean active() { return active; }
    public synchronized boolean closed() { return closed; }
    public void pause() { active = false; }
    @Override public void close() {
        List<AutoCloseable> closing;
        synchronized (this) {
            if (closed) return;
            closed = true; active = false;
            closing = new ArrayList<>(resources); resources.clear();
        }
        RuntimeException failure = null;
        for (AutoCloseable resource : closing.reversed()) {
            try { resource.close(); }
            catch (Throwable error) {
                ScriptFailures.rethrowFatal(error);
                if (failure == null) failure = new IllegalStateException("Script cleanup failed");
                failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
