package cn.omix.util.move;

import java.awt.Color;

/**
 * Semantic reconstruction of NewZKMJNIC.ilill11liiii from the supplied JAR.
 * Float arithmetic, branch ordering and HUD calls follow the supplied reverse
 * engineering reference. Host and HudRenderer adapt it to Omix; obfuscation
 * guards and string decryption are outside this behavioral model.
 */
public final class TimerBalance {
    public static final Color PANEL = new Color(28, 27, 31, 220); // x
    public static final Color TRACK = new Color(73, 69, 79, 100); // c
    public static final Color TITLE = new Color(230, 225, 229);  // E
    public static final Color PERCENT = new Color(202, 196, 208);// t
    public static final float WIDTH = 140.0f, HEIGHT = 32.0f;

    /** Values are supplied by the framework's settings UI/configuration layer. */
    public static final class Settings {
        public String mode = "Balance";        // C
        public String releaseButton = "Middle";// F
        public float gameSpeed = 2.0f;          // r
        public float boostSpeed = 1.6f;         // a
        public float maxBalance = 9.0f;         // H
    }

    public interface DragPosition {
        float x();
        float y();
        void width(float width);
        void height(float height);
    }

    public interface Host {
        boolean hasPlayer();
        boolean isTimerEnabled();
        boolean isLongJumpEnabled();
        /** True exactly when KillAura.m != null; no enabled-state test is added. */
        boolean hasKillAuraTarget();
        boolean forwardPressed();
        boolean backPressed();
        boolean leftPressed();
        boolean rightPressed();
        /** Raw GLFW result: 1 means pressed. Uses the Minecraft window handle. */
        int mouseButtonState(int glfwButtonIndex);
        float scaledWindowWidth();
        float scaledWindowHeight();
        DragPosition createDrag(String id, float initialX, float initialY);
        /** Supplies this module's multiplier; the host owns global arbitration. */
        void setTimerMultiplier(float multiplier);
        Color themeColor();
        /** The original superclass onDisable call, after multiplier reset. */
        void baseOnDisable();
    }

    /** These methods correspond to the original shared rendering helpers. */
    public interface HudRenderer {
        void save();
        void restore();
        void translate(float x, float y);
        void scale(float uniformScale);
        void shadow(float x, float y, float width, float height, float radius, Color color);
        void roundedRect(float x, float y, float width, float height, float radius, Color color);
        Object font(float size);
        float textWidth(String text, Object font);
        void text(String text, float x, float y, Color color, Object font);
    }

    private final Host host;
    public final Settings settings = new Settings();
    public final DragPosition drag; // V
    private float balance;         // h: surplus tick budget
    private float displayedRatio;  // P: HUD interpolation state
    private float opacity;         // l: module-enabled interpolation state

    public TimerBalance(Host host) {
        this.host = host;
        drag = host.createDrag("TimerBalance",
            host.scaledWindowWidth() / 2.0f - 70.0f,
            host.scaledWindowHeight() / 2.0f + 120.0f);
    }

    public float balance() { return balance; }
    public float displayedRatio() { return displayedRatio; }
    public float opacity() { return opacity; }

    /** Called for li1ilii1iiii, emitted from the local player's tick mixin. */
    public void onPlayerUpdate() {
        if (!host.hasPlayer()) return;

        opacity += ((host.isTimerEnabled() ? 1.0f : 0.0f) - opacity) * 0.12f;
        if (host.isLongJumpEnabled()) {
            host.setTimerMultiplier(1.0f);
        } else if ("Classic".equalsIgnoreCase(settings.mode)) {
            host.setTimerMultiplier(settings.gameSpeed);
        } else {
            float capacity = settings.maxBalance;
            boolean pressed = isReleaseButtonPressed();
            if (pressed && balance > 0.1f) {
                float boost = settings.boostSpeed;
                if (balance >= boost - 1.0f) {
                    host.setTimerMultiplier(boost);
                    balance -= boost - 1.0f;
                } else {
                    // No charging in this branch, even with unused capacity.
                    host.setTimerMultiplier(1.0f);
                }
            } else if (!host.hasKillAuraTarget() && balance < capacity) {
                boolean moving = host.forwardPressed() || host.backPressed()
                              || host.leftPressed() || host.rightPressed();
                float chargingSpeed = moving ? 0.92f : 0.8f;
                host.setTimerMultiplier(chargingSpeed);
                balance += 1.0f - chargingSpeed;
            } else {
                host.setTimerMultiplier(1.0f);
            }
            balance = Math.max(0.0f, Math.min(balance, capacity));
        }
    }

    /** Despite its label, "Release Button" means hold-to-spend, not mouse-up. */
    public boolean isReleaseButtonPressed() {
        int button = switch (settings.releaseButton) {
            case "Side 1" -> 3;
            case "Side 2" -> 4;
            default -> 2;
        };
        return host.mouseButtonState(button) == 1;
    }

    /** Called for iili1iiiiiii. Retains every original draw call and threshold. */
    public void onRender(HudRenderer renderer) {
        if (!"Balance".equalsIgnoreCase(settings.mode)) return;
        // Keep the original comparison direction (including float NaN behavior).
        if (opacity < 0.01f && !host.isTimerEnabled()) return;

        float x = drag.x();
        float y = drag.y();
        drag.width(WIDTH);
        drag.height(HEIGHT);
        float ratio = balance / settings.maxBalance;
        displayedRatio += (ratio - displayedRatio) * 0.15f;
        float scale = 0.85f + opacity * 0.15f;

        renderer.save();
        renderer.translate(x + 70.0f, y + 16.0f);
        renderer.scale(scale);
        renderer.translate(-(x + 70.0f), -(y + 16.0f));
        renderer.shadow(x, y, WIDTH, HEIGHT, 12.0f, alpha(Color.BLACK, 0.4f * opacity));
        renderer.roundedRect(x, y, WIDTH, HEIGHT, 12.0f, alpha(PANEL, opacity));

        float barX = x + 12.0f;
        float barY = y + 20.0f;
        float barWidth = 116.0f;
        renderer.roundedRect(barX, barY, barWidth, 4.0f, 2.0f, alpha(TRACK, opacity));
        Color accent = host.themeColor();
        if (displayedRatio > 0.001f) {
            renderer.roundedRect(barX, barY, barWidth * displayedRatio,
                                 4.0f, 2.0f, alpha(accent, opacity));
            if (ratio > 0.98f) {
                renderer.shadow(barX, barY, barWidth * displayedRatio,
                                4.0f, 5.0f, alpha(accent, 0.8f * opacity));
            }
        }

        Object font = renderer.font(8.0f);
        String percent = Math.round(ratio * 100.0f) + "%";
        renderer.text("Timer Balance", barX, y + 13.0f, alpha(TITLE, opacity), font);
        renderer.text(percent, x + WIDTH - 12.0f - renderer.textWidth(percent, font),
                      y + 13.0f, alpha(ratio > 0.9f ? accent : PERCENT, opacity), font);
        renderer.restore();
    }

    /** llliilliiiii's shared alpha helper, also recovered from the sample. */
    public static Color alpha(Color color, float multiplier) {
        multiplier = Math.min(1.0f, Math.max(0.0f, multiplier));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                         (int)(color.getAlpha() * multiplier));
    }

    /** No balance/HUD reset exists here; the superclass owns lifecycle wiring. */
    public void onDisable() {
        host.setTimerMultiplier(1.0f);
        host.baseOnDisable();
    }
}
