package cn.omix.util.opai;

import cn.omix.Client;
import cn.omix.module.impl.render.HUD;
import cn.omix.util.opai.clickgui.OpaiStyle;
import cn.omix.util.opai.clickgui.OpaiStyle.Palette;
import java.awt.Color;

/** The HUD's native ColorValue owns the shared Opai HUD and ClickGUI palette. */
public final class OpaiHudTheme {
    public static final int DEFAULT_COLOR = 0xFFBBC3FF;
    private static int cachedColor = DEFAULT_COLOR;
    private static Palette cachedPalette = OpaiStyle.LAVENDER;

    private OpaiHudTheme() { }

    public static Palette currentPalette() {
        var manager = Client.instance == null ? null : Client.instance.getModuleManager();
        HUD hud = manager == null ? null : manager.getModule(HUD.class);
        int color = hud == null ? DEFAULT_COLOR : hud.getOpaiColor().getValue().getRGB();
        if (color != cachedColor) {
            cachedColor = color;
            cachedPalette = palette(color);
        }
        return cachedPalette;
    }

    public static Palette palette(int color) {
        Palette base = OpaiStyle.LAVENDER;
        int accent = color | 0xFF000000;
        if (accent == DEFAULT_COLOR) return base;
        return new Palette(base.header(), base.body(), shade(accent, base.enabled()),
                shade(accent, base.enabledHover()), accent, shade(accent, base.enabledText()),
                base.text(), base.hover(), base.field(), base.fieldLine(), base.track(), base.scrollbar(),
                base.selected(), shade(accent, base.toggleOutline()), shade(accent, base.tick()),
                shade(accent, base.hudProgress()), shade(accent, base.hudKnob()));
    }

    private static int shade(int accent, int reference) {
        float[] from = hsb(DEFAULT_COLOR), to = hsb(accent), target = hsb(reference);
        return (reference & 0xFF000000) | (Color.HSBtoRGB(to[0] + target[0] - from[0],
                Math.clamp(to[1] * target[1] / from[1], 0, 1), to[2] * target[2] / from[2]) & 0xFFFFFF);
    }

    private static float[] hsb(int color) {
        return Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, null);
    }
}
