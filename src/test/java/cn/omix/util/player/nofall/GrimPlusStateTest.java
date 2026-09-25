package cn.omix.util.player.nofall;

import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PlayerUpdateEvent;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrimPlusStateTest {
    private final Host host = new Host();
    private final GrimPlusState flow = new GrimPlusState(host);

    @AfterEach
    void cleanup() { flow.onDisable(); }

    @Test
    void matchesAll700SuppliedNativeCasesIncludingEffectOrder() throws Exception {
        var inputs = resource("differential-input.tsv").lines().filter(s -> !s.isBlank()).toList();
        var expected = resource("differential-native.tsv").lines().filter(s -> !s.isBlank()).toList();
        assertEquals(700, inputs.size());
        assertEquals(inputs.size(), expected.size());
        for (int i = 0; i < inputs.size(); i++) {
            String[] a = inputs.get(i).split("\t");
            restore(Double.parseDouble(a[1]), Boolean.parseBoolean(a[2]), Integer.parseInt(a[3]),
                    Integer.parseInt(a[4]), Long.parseLong(a[5]));
            host.fall = Double.parseDouble(a[6]);
            host.noSlow = Boolean.parseBoolean(a[8]);
            host.collision = Boolean.parseBoolean(a[9]);
            host.ground = Boolean.parseBoolean(a[10]);
            host.now = Long.parseLong(a[11]);
            run(a[0], Boolean.parseBoolean(a[7]), a[12].equals("PRE"));
            assertEquals(expected.get(i), result(), "Native case " + i + ": " + inputs.get(i));
        }
    }

    @Test
    void matches108ContinuousNativeStepsWithoutRestoringBetweenCallbacks() throws Exception {
        flow.onDisable();
        var timeline = JsonParser.parseString(resource("timeline.json")).getAsJsonArray();
        assertEquals(108, timeline.size());
        for (var step : timeline) {
            var entry = step.getAsJsonObject();
            var options = entry.getAsJsonObject("options");
            host.fall = options.get("fallDistance").getAsDouble();
            host.noSlow = options.get("noSlow").getAsBoolean();
            host.collision = options.get("horizontalCollision").getAsBoolean();
            host.ground = options.get("playerGround").getAsBoolean();
            host.now = options.get("now").getAsLong();
            run(entry.get("method").getAsString(), options.get("eventGround").getAsBoolean(),
                    options.get("phase").getAsString().equals("PRE"));
            assertEquals(entry.get("result").getAsString(), result(), entry.toString());
        }
    }

    @Test
    void recentQueryRetainsAll392ClockAndGuardBoundaryCombinations() throws Exception {
        long[] values = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -401, -400, -399, -1,
                0, 1, 399, 400, 401, 1000000, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (boolean guard : new boolean[]{false, true}) {
            host.guard = guard;
            for (long now : values) for (long last : values) {
                host.now = now;
                field("lastTriggerMillis").setLong(null, last);
                assertEquals(guard ? now - last != 400 : now - last < 400,
                        GrimPlusState.triggeredRecently(host), guard + ":" + now + ":" + last);
            }
        }
    }

    @Test
    void packetFailureKeepsOnlyEffectsAlreadyPerformedAndDoesNotUpdateTheSample() throws Exception {
        restore(3.2, false, 0, 0, 0);
        host.fall = 0;
        host.now = 1000;
        host.failSend = true;
        MotionEvent event = motion(true, true);
        assertThrows(IllegalStateException.class, () -> flow.onMotion(event));
        assertFalse(event.isOnGround());
        assertEquals("400999999999999a\tfalse\t0\t0\t1000\tground:false;send:true:false", result());
    }

    @Test
    void sampleIsReadBeforeSendingAndInstancesShareOnlyTheTimestamp() throws Exception {
        restore(3, false, 0, 0, 0);
        host.fall = 4;
        host.now = 1000000;
        host.changeFallOnSend = true;
        flow.onMotion(motion(true, true));
        assertEquals(4.0, field("previousFallDistance").getDouble(flow));
        GrimPlusState other = new GrimPlusState(host);
        PlayerUpdateEvent update = new PlayerUpdateEvent();
        other.onPlayerUpdate(update);
        assertFalse(update.isCancelled());
        assertTrue(GrimPlusState.triggeredRecently(host));
        other.onDisable();
        assertFalse(GrimPlusState.triggeredRecently(host));
        flow.onPlayerUpdate(update);
        assertTrue(update.isCancelled());
    }

    private void run(String method, boolean ground, boolean pre) {
        host.effects.clear();
        switch (method) {
            case "motion" -> flow.onMotion(motion(ground, pre));
            case "update" -> {
                PlayerUpdateEvent event = new PlayerUpdateEvent();
                flow.onPlayerUpdate(event);
                if (event.isCancelled()) host.effects.add("cancel:true");
            }
            case "input" -> flow.onInput(new MoveInputEvent(1, -1, false, true) {
                @Override public void setForward(float value) { super.setForward(value); host.effects.add("axis1:" + value); }
                @Override public void setStrafe(float value) { super.setStrafe(value); host.effects.add("axis2:" + value); }
                @Override public void setJumping(boolean value) { super.setJumping(value); host.effects.add("jump:" + value); }
                @Override public void setSneaking(boolean value) { super.setSneaking(value); host.effects.add("sneak:" + value); }
            });
            case "reset" -> flow.onDisable();
            default -> fail("Unknown fixture method " + method);
        }
    }

    private MotionEvent motion(boolean ground, boolean pre) {
        MotionEvent event = new MotionEvent(0, 0, 0, 0, 0, ground, !host.collision) {
            @Override public void setOnGround(boolean value) {
                super.setOnGround(value);
                host.effects.add("ground:" + value);
            }
        };
        if (!pre) event.setPost();
        return event;
    }

    private String result() throws Exception {
        return Long.toHexString(Double.doubleToLongBits(field("previousFallDistance").getDouble(flow))) + "\t"
                + field("jumpPending").getBoolean(flow) + "\t" + field("updatesToCancel").getInt(flow) + "\t"
                + field("inputsToFreeze").getInt(flow) + "\t" + field("lastTriggerMillis").getLong(null)
                + "\t" + String.join(";", host.effects);
    }

    private void restore(double fall, boolean jump, int updates, int inputs, long last) throws Exception {
        field("previousFallDistance").setDouble(flow, fall);
        field("jumpPending").setBoolean(flow, jump);
        field("updatesToCancel").setInt(flow, updates);
        field("inputsToFreeze").setInt(flow, inputs);
        field("lastTriggerMillis").setLong(null, last);
    }

    private static Field field(String name) throws Exception {
        Field field = GrimPlusState.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String resource(String name) throws IOException {
        try (var stream = GrimPlusStateTest.class.getResourceAsStream("/nofall-grimplus/" + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static class Host implements GrimPlusState.Host {
        double fall;
        boolean ground, collision, noSlow, guard, failSend, changeFallOnSend;
        long now;
        final List<String> effects = new ArrayList<>();
        @Override public double fallDistance() { return fall; }
        @Override public boolean playerOnGround() { return ground; }
        @Override public boolean horizontalCollision() { return collision; }
        @Override public boolean noSlowActivePhase() { return noSlow; }
        @Override public long currentTimeMillis() { return now; }
        @Override public boolean obfuscationGuardPresent() { return guard; }
        @Override public void sendGroundPacket(boolean ground, boolean collision) {
            effects.add("send:" + ground + ":" + collision);
            if (failSend) throw new IllegalStateException("test send failure");
            if (changeFallOnSend) fall = 0;
        }
    }
}
