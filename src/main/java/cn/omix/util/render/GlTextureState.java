package cn.omix.util.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Restores both native texture state and Minecraft's binding cache after external GL drawing. */
public final class GlTextureState {
    interface Bindings {
        int active();
        int texture();
        void cachedActive(int unit);
        void nativeActive(int unit);
        void cachedBind(int texture);
        void nativeBind(int texture);
    }

    private static final Bindings OPEN_GL = new Bindings() {
        public int active() { return GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); }
        public int texture() { return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
        public void cachedActive(int unit) { GlStateManager._activeTexture(unit); }
        public void nativeActive(int unit) { GL13.glActiveTexture(unit); }
        public void cachedBind(int texture) { GlStateManager._bindTexture(texture); }
        public void nativeBind(int texture) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
    };

    private final Bindings gl;
    private final int active, texture;

    private GlTextureState(Bindings gl) {
        this.gl = gl;
        active = gl.active();
        activate(gl, GL13.GL_TEXTURE0);
        texture = gl.texture();
    }

    public static GlTextureState capture() { return capture(OPEN_GL); }
    static GlTextureState capture(Bindings gl) { return new GlTextureState(gl); }

    public static void activateTexture(int unit) { activate(OPEN_GL, unit); }

    private static void activate(Bindings gl, int unit) {
        gl.cachedActive(unit);
        // External GL may have changed the driver state without changing the cache.
        gl.nativeActive(unit);
    }

    public void restore() {
        activate(gl, GL13.GL_TEXTURE0);
        // Measuring text can bake glyphs even inside an external renderer's scope.
        // Restoring only the driver binding makes the next glyph upload skip its bind
        // and write into the wrong texture, permanently caching a blank glyph.
        gl.cachedBind(texture);
        gl.nativeBind(texture);
        activate(gl, active);
    }
}
