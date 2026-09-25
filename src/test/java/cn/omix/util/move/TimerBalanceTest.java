package cn.omix.util.move;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TimerBalanceTest {
    @Test
    void replaysOriginalBytecodeVerifiedTimelineWithExactFloats() throws Exception {
        Map<Integer, String[]> expected;
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/timer-balance/timeline.csv"), StandardCharsets.UTF_8))) {
            expected = reader.lines().skip(1).map(line -> line.split(","))
                    .collect(Collectors.toMap(row -> Integer.parseInt(row[0]), row -> row));
        }
        Host host = new Host();
        TimerBalance timer = new TimerBalance(host);
        Draw draw = new Draw();
        for (int i = 0; i < 2000; i++) {
            host.pressed = (i >= 60 && i < 150) || (i >= 220 && i < 500);
            host.moving = i >= 500;
            host.target = i >= 160 && i < 200;
            host.longJump = i >= 1000 && i < 1050;
            host.enabled = !(i >= 1500 && i < 1510);
            timer.settings.mode = i >= 700 && i < 750 ? "Classic" : "Balance";
            timer.settings.maxBalance = i >= 1800 ? 3 : 9;
            timer.onPlayerUpdate();
            draw.calls.clear();
            timer.onRender(draw);
            if (i == 1500) timer.onDisable();
            if (expected.containsKey(i)) {
                String[] row = expected.get(i);
                assertEquals(Float.parseFloat(row[3]), timer.balance(), "balance at " + i);
                assertEquals(Float.parseFloat(row[4]), host.speed, "speed at " + i);
                assertEquals(Float.parseFloat(row[5]), timer.opacity(), "opacity at " + i);
                assertEquals(Float.parseFloat(row[6]), timer.displayedRatio(), "bar at " + i);
            }
        }
        assertEquals(285, expected.size());
    }

    @Test
    void chargingCountsAndHeldButtonDeadZoneMatchReference() {
        Host host = new Host();
        TimerBalance timer = new TimerBalance(host);
        for (int i = 0; i < 45; i++) timer.onPlayerUpdate();
        assertTrue(timer.balance() < 9);
        timer.onPlayerUpdate();
        assertEquals(9, timer.balance());
        host.pressed = true;
        for (int i = 0; i < 15; i++) {
            timer.onPlayerUpdate();
            assertEquals(1.6F, host.speed);
        }
        assertEquals(4.7683716E-7F, timer.balance());
        timer.onPlayerUpdate();
        assertEquals(0.8F, host.speed);
        float stuck = timer.balance();
        for (int i = 0; i < 20; i++) timer.onPlayerUpdate();
        assertEquals(stuck, timer.balance());
        assertEquals(1, host.speed);

        timer = new TimerBalance(host);
        host.pressed = false;
        host.moving = true;
        for (int i = 0; i < 112; i++) timer.onPlayerUpdate();
        assertTrue(timer.balance() < 9);
        timer.onPlayerUpdate();
        assertEquals(9, timer.balance());
    }

    @Test
    void targetBlocksOnlyChargingAndMissingPlayerAndDisableKeepState() {
        Host host = new Host();
        TimerBalance timer = new TimerBalance(host);
        for (int i = 0; i < 10; i++) timer.onPlayerUpdate();
        float saved = timer.balance();
        host.target = true;
        timer.onPlayerUpdate();
        assertEquals(saved, timer.balance());
        assertEquals(1, host.speed);
        host.pressed = true;
        timer.onPlayerUpdate();
        assertEquals(1.6F, host.speed);
        saved = timer.balance();
        float opacity = timer.opacity();
        host.player = false;
        timer.onPlayerUpdate();
        assertEquals(1.6F, host.speed);
        assertEquals(opacity, timer.opacity());
        timer.onDisable();
        assertEquals(1, host.speed);
        assertEquals(saved, timer.balance());
        assertEquals(opacity, timer.opacity());
        assertTrue(host.baseDisabled);
    }

    @Test
    void clampsOnlyAfterBalanceBranchAndHonorsStrictPointOneThreshold() throws Exception {
        Host host = new Host();
        TimerBalance timer = new TimerBalance(host);
        var field = TimerBalance.class.getDeclaredField("balance");
        field.setAccessible(true);
        host.pressed = true;
        field.setFloat(timer, 0.1F);
        timer.onPlayerUpdate();
        assertEquals(0.8F, host.speed);
        field.setFloat(timer, Math.nextUp(0.1F));
        timer.onPlayerUpdate();
        assertEquals(1, host.speed);
        field.setFloat(timer, 1.6F - 1.0F);
        timer.onPlayerUpdate();
        assertEquals(1.6F, host.speed);
        assertEquals(0, timer.balance());
        field.setFloat(timer, 9);
        timer.settings.maxBalance = 1;
        host.longJump = true;
        timer.onPlayerUpdate();
        assertEquals(9, timer.balance());
        assertEquals(1, host.speed);
        host.longJump = false;
        timer.onPlayerUpdate();
        assertEquals(1.6F, host.speed);
        assertEquals(1, timer.balance());
    }

    @Test
    void hudPreservesLayoutInterpolationColorsAndMouseMapping() {
        Host host = new Host();
        TimerBalance timer = new TimerBalance(host);
        assertEquals(330, host.x);
        assertEquals(420, host.y);
        Draw draw = new Draw();
        timer.onPlayerUpdate();
        timer.onRender(draw);
        assertEquals(140, host.width);
        assertEquals(32, host.height);
        assertEquals(0.0033333332F, timer.displayedRatio());
        assertEquals(List.of("save", "translate", "scale", "translate", "shadow", "rect", "rect", "rect",
                "font", "text:Timer Balance", "width", "text:2%", "restore"), draw.calls);
        assertEquals(new Color(28, 27, 31, 26), draw.colors.get(1));
        assertEquals(0.86800003F, draw.scale);
        float shown = timer.displayedRatio();
        timer.settings.mode = "Classic";
        draw.calls.clear();
        timer.onRender(draw);
        assertTrue(draw.calls.isEmpty());
        assertEquals(shown, timer.displayedRatio());
        for (String button : List.of("Middle", "Side 1", "Side 2", "side 1")) {
            timer.settings.releaseButton = button;
            timer.isReleaseButtonPressed();
            assertEquals(switch (button) { case "Side 1" -> 3; case "Side 2" -> 4; default -> 2; }, host.button);
        }
    }

    private static final class Host implements TimerBalance.Host, TimerBalance.DragPosition {
        boolean player = true, enabled = true, longJump, target, moving, pressed, baseDisabled;
        float speed = 1, x, y, width, height;
        int button;
        public boolean hasPlayer() { return player; }
        public boolean isTimerEnabled() { return enabled; }
        public boolean isLongJumpEnabled() { return longJump; }
        public boolean hasKillAuraTarget() { return target; }
        public boolean forwardPressed() { return moving; }
        public boolean backPressed() { return false; }
        public boolean leftPressed() { return false; }
        public boolean rightPressed() { return false; }
        public int mouseButtonState(int index) { button = index; return pressed ? 1 : 0; }
        public float scaledWindowWidth() { return 800; }
        public float scaledWindowHeight() { return 600; }
        public TimerBalance.DragPosition createDrag(String id, float x, float y) { this.x = x; this.y = y; return this; }
        public void setTimerMultiplier(float value) { speed = value; }
        public Color themeColor() { return Color.CYAN; }
        public void baseOnDisable() { assertEquals(1, speed); baseDisabled = true; }
        public float x() { return x; }
        public float y() { return y; }
        public void width(float value) { width = value; }
        public void height(float value) { height = value; }
    }

    private static final class Draw implements TimerBalance.HudRenderer {
        final List<String> calls = new ArrayList<>();
        final List<Color> colors = new ArrayList<>();
        float scale;
        public void save() { calls.add("save"); }
        public void restore() { calls.add("restore"); }
        public void translate(float x, float y) { calls.add("translate"); }
        public void scale(float value) { scale = value; calls.add("scale"); }
        public void shadow(float x, float y, float w, float h, float r, Color c) { calls.add("shadow"); colors.add(c); }
        public void roundedRect(float x, float y, float w, float h, float r, Color c) { calls.add("rect"); colors.add(c); }
        public Object font(float size) { assertEquals(8, size); calls.add("font"); return this; }
        public float textWidth(String text, Object font) { calls.add("width"); return text.length() * 4; }
        public void text(String text, float x, float y, Color c, Object font) { calls.add("text:" + text); }
    }
}
