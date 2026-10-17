package cn.omix.util.world.anticheat;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static cn.omix.util.world.anticheat.AntiCheatCheck.*;
import static org.junit.jupiter.api.Assertions.*;

class AntiCheatTrackerTest {
    private final AntiCheatTracker tracker = new AntiCheatTracker();

    private static final class Observation {
        double x, y = 80, z, distanceSquared = 10;
        float yaw, pitch;
        boolean sneaking, sprinting, using, blocking, swinging, block, rod, air, voidBelow, hurt, ladder, adventure;
        boolean recentPacket = true, noFallAllowed = true, scaffoldAllowed = true;
        int swing;

        AntiCheatTracker.Sample sample() {
            return new AntiCheatTracker.Sample(x, y, z, yaw, pitch, sneaking, sprinting, using, blocking,
                    swinging, swing, block, rod, air, voidBelow, hurt, ladder, adventure, recentPacket,
                    noFallAllowed, scaffoldAllowed, distanceSquared);
        }
    }

    private AntiCheatCheck tick(Observation p, AntiCheatCheck... enabled) {
        EnumSet<AntiCheatCheck> checks = EnumSet.noneOf(AntiCheatCheck.class);
        for (var check : enabled) checks.add(check);
        return tracker.update(p.sample(), checks);
    }

    @Test void autoBlockNeedsFifteenConsecutiveTicksAndRespectsSwitch() {
        Observation p = new Observation();
        p.swinging = p.blocking = true;
        tick(p, AUTO_BLOCK);
        for (int i = 0; i < 14; i++) assertNull(tick(p, AUTO_BLOCK));
        assertEquals(AUTO_BLOCK, tick(p, AUTO_BLOCK));
        assertNull(tick(p));
        p.blocking = false;
        assertNull(tick(p, AUTO_BLOCK));
        p.blocking = true;
        assertNull(tick(p, AUTO_BLOCK));
    }

    @Test void aimAssistRequiresContinuousYawAndUnchangedPitch() {
        Observation p = new Observation();
        tick(p, AIM_ASSIST);
        for (int i = 0; i < 24; i++) { p.yaw += 2; assertNull(tick(p, AIM_ASSIST)); }
        p.yaw += 2;
        assertEquals(AIM_ASSIST, tick(p, AIM_ASSIST));
        p.pitch = 1;
        p.yaw += 2;
        assertNull(tick(p, AIM_ASSIST));
    }

    @Test void protocolUsesOutOfRangePitchAndSwitch() {
        Observation p = new Observation();
        tick(p, PROTOCOL);
        p.pitch = 91;
        assertNull(tick(p, PROTOCOL));
        p.pitch = -92;
        assertEquals(PROTOCOL, tick(p, PROTOCOL));
        assertNull(tick(p));
    }

    @Test void noSlowWaitsAfterReleasingAnItem() {
        Observation p = new Observation();
        p.sprinting = p.using = true;
        tick(p, NO_SLOW);
        for (int i = 0; i < 33; i++) { p.x += 0.1; assertNull(tick(p, NO_SLOW)); }
        p.x += 0.1;
        assertEquals(NO_SLOW, tick(p, NO_SLOW));
        p.using = false;
        tick(p, NO_SLOW);
        p.using = true;
        for (int i = 0; i < 34; i++) { p.x += 0.1; assertNull(tick(p, NO_SLOW)); }
        p.x += 0.1;
        assertEquals(NO_SLOW, tick(p, NO_SLOW));
    }

    @Test void blinkRequiresFreshTrafficAndRejectsTeleport() {
        Observation p = new Observation();
        p.sprinting = true;
        for (int i = 0; i < 8; i++) tick(p, BLINK);
        p.x = 1;
        p.recentPacket = false;
        assertNull(tick(p, BLINK));
        for (int i = 0; i < 7; i++) tick(p, BLINK);
        p.x = 2;
        p.recentPacket = true;
        assertEquals(BLINK, tick(p, BLINK));
        for (int i = 0; i < 7; i++) tick(p, BLINK);
        p.x = 20;
        assertNull(tick(p, BLINK));
        p.x = 21;
        assertNull(tick(p, BLINK));
    }

    @Test void scaffoldRequiresVoidAndStopsWhenSneaking() {
        Observation p = new Observation();
        p.block = p.swinging = true;
        p.pitch = 70;
        tick(p, SCAFFOLD);
        for (int i = 0; i < 30; i++) { p.x += 0.1; assertNull(tick(p, SCAFFOLD)); }
        p.voidBelow = true;
        p.x += 0.1;
        assertEquals(SCAFFOLD, tick(p, SCAFFOLD));
        p.sneaking = true;
        p.x += 0.1;
        assertNull(tick(p, SCAFFOLD));
    }

    @Test void legitScaffoldNeedsThreePerfectSneakTransitions() {
        Observation p = new Observation();
        p.block = p.swinging = true;
        p.pitch = 70;
        tick(p, LEGIT_SCAFFOLD);
        for (int i = 0; i < 3; i++) {
            p.sneaking = false;
            p.swing = 2;
            assertNull(tick(p, LEGIT_SCAFFOLD));
            p.sneaking = true;
            p.swing = 1;
            assertEquals(i == 2 ? LEGIT_SCAFFOLD : null, tick(p, LEGIT_SCAFFOLD));
        }
    }

    @Test void autoClickerExcludesMiningAndFishingRod() {
        Observation p = new Observation();
        p.swinging = true;
        tick(p, AUTO_CLICKER);
        for (int i = 0; i < 10; i++) {
            p.swing = 3;
            tick(p, AUTO_CLICKER);
            p.swing = 0;
            assertEquals(i == 9 ? AUTO_CLICKER : null, tick(p, AUTO_CLICKER));
        }
        p.rod = true;
        assertNull(tick(p, AUTO_CLICKER));
        p.rod = false;
        tracker.breakAnimation(5);
        assertNull(tick(p, AUTO_CLICKER));
    }

    @Test void noFallNeedsAirbornePauseAndShortDropWithoutDamage() {
        Observation p = prepareFall();
        p.y = 78;
        assertNull(tick(p, NO_FALL));
        p.y = 76;
        assertNull(tick(p, NO_FALL));
        p.y = 74;
        p.air = false;
        assertEquals(NO_FALL, tick(p, NO_FALL));
        assertNull(tick(p, NO_FALL));
    }

    @Test void damageClearsNoFallEvidence() {
        Observation p = prepareFall();
        p.y = 78;
        tick(p, NO_FALL);
        p.hurt = true;
        p.y = 76;
        tick(p, NO_FALL);
        p.hurt = false;
        p.y = 74;
        p.air = false;
        assertNull(tick(p, NO_FALL));
    }

    @Test void disablingNoFallDiscardsPendingFall() {
        Observation p = prepareFall();
        p.y = 78;
        tick(p);
        p.y = 76;
        tick(p, NO_FALL);
        p.y = 74;
        p.air = false;
        assertNull(tick(p, NO_FALL));
    }

    private Observation prepareFall() {
        Observation p = new Observation();
        p.air = true;
        for (int i = 0; i < 11; i++) assertNull(tick(p, NO_FALL));
        return p;
    }
}
