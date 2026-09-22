package cn.omix.util.script;

import cn.omix.event.base.Event;
import cn.omix.event.impl.*;
import cn.omix.script.api.Registration;
import net.minecraft.client.gui.DrawContext;

/** Thread-local event identity prevents retaining a frame's rendering objects across callbacks. */
public final class ScriptRenderPhase {
    private static final ThreadLocal<Event> CURRENT = new ThreadLocal<>();
    private ScriptRenderPhase() {}
    public static Registration enter(Event event) {
        Event previous = CURRENT.get(); CURRENT.set(event);
        return () -> { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); };
    }
    public static void require2D(DrawContext context) {
        if (!(CURRENT.get() instanceof Render2DEvent render) || render.getContext() != context)
            throw new IllegalStateException("Draw only inside the current Render2DEvent or HUD callback");
    }
    public static void require3D(Render3DEvent event) {
        if (CURRENT.get() != event) throw new IllegalStateException("Draw only inside the current Render3DEvent callback");
    }
}
