package cn.omix.util.render;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL13;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GlTextureStateTest {
    @Test void textMeasuredInsideExternalRenderingDoesNotRedirectTheNextGlyphUpload() {
        var gl = new Driver();
        gl.cachedBind(10);
        var saved = GlTextureState.capture(gl);
        gl.cachedBind(20); // Minecraft bakes a newly measured glyph into its atlas.
        gl.nativeBind(99); // NanoVG flushes its own atlas.
        saved.restore();
        assertEquals(10, gl.texture());
        gl.cachedBind(20); // Next glyph must bind the atlas, not reuse texture 10.
        assertEquals(20, gl.texture());
    }

    @Test void restoresNativeChangesEvenWhenEngineCacheStillMatchesTheSavedBinding() {
        var gl = new Driver();
        gl.cachedBind(10);
        var saved = GlTextureState.capture(gl);
        gl.nativeBind(99);
        gl.nativeActive(GL13.GL_TEXTURE1);
        saved.restore();
        assertEquals(GL13.GL_TEXTURE0, gl.active());
        assertEquals(10, gl.texture());
    }

    @Test void keepsTheActiveTextureUnitAndItsCacheInSyncAcrossNestedScopes() {
        var gl = new Driver();
        gl.cachedActive(GL13.GL_TEXTURE2);
        gl.cachedBind(30);
        var outer = GlTextureState.capture(gl);
        gl.cachedBind(10);
        var inner = GlTextureState.capture(gl);
        gl.cachedBind(20);
        inner.restore();
        assertEquals(10, gl.texture());
        outer.restore();
        assertEquals(GL13.GL_TEXTURE2, gl.active());
        assertEquals(30, gl.texture());
        gl.cachedActive(GL13.GL_TEXTURE0);
        assertEquals(GL13.GL_TEXTURE0, gl.active());
        assertEquals(0, gl.texture());
    }

    /** Mirrors the engine's redundant-bind suppression, without requiring a GPU. */
    private static final class Driver implements GlTextureState.Bindings {
        int active = GL13.GL_TEXTURE0, cachedActive = active;
        final Map<Integer, Integer> textures = new HashMap<>(), cachedTextures = new HashMap<>();
        public int active() { return active; }
        public int texture() { return textures.getOrDefault(active, 0); }
        public void nativeActive(int unit) { active = unit; }
        public void nativeBind(int texture) { textures.put(active, texture); }
        public void cachedActive(int unit) {
            if (cachedActive != unit) { cachedActive = unit; nativeActive(unit); }
        }
        public void cachedBind(int texture) {
            if (cachedTextures.getOrDefault(cachedActive, 0) != texture) {
                cachedTextures.put(cachedActive, texture); nativeBind(texture);
            }
        }
    }
}
