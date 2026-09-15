package cn.omix.management.rotation;

import cn.omix.event.base.EventManager;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.RotationRequestEvent;
import cn.omix.management.movement.MovementCorrection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RotationRequestTest {
    private static RotationRequest.Builder request(String owner, int priority, float yaw, float pitch) {
        return RotationRequest.builder(owner, new float[]{yaw, pitch}, priority);
    }

    @Test
    void configuredZeroCanPreserveLegacySmoothingWithoutChangingInstantClients() {
        assertFalse(request("Aura", 400, 90, 45).speed(0).instant(false).build().instant());
        assertFalse(request("Scaffold", 500, 90, 45).speed(0).instant(false).build().instant());
        assertFalse(request("ScaffoldX", 600, 90, 45).speed(0).instant(false).build().instant());
        assertTrue(request("Derp", 900, 90, 120).speed(0).build().instant());
        assertTrue(request("AutoBlockIn", 700, 90, 45).speed(0).build().instant());
        assertFalse(request("Smooth", 100, 90, 45).speed(180).build().instant());
    }

    @Test
    void higherPriorityWinsWithItsOwnApplicationSettings() {
        RotationRequestEvent event = new RotationRequestEvent();
        event.submit(request("Low", -10, 30, 40).build());
        RotationRequest high = request("High", 80, 90, -25).speed(0).silent(false)
                .movementCorrection(MovementCorrection.Strict).build();
        event.submit(high);
        var selection = event.resolve(0, 0);
        assertSame(high, selection.request());
        assertArrayEquals(new float[]{90, -25}, selection.rotations());
        assertEquals(0, selection.request().speed());
        assertFalse(selection.request().silent());
        assertEquals(MovementCorrection.Strict, selection.request().movementCorrection());
    }

    @Test
    void tiesDoNotDependOnRegistrationOrSubmissionOrder() {
        RotationRequest alpha = request("Alpha", Integer.MAX_VALUE, 10, 20).build();
        RotationRequest beta = request("Beta", Integer.MAX_VALUE, 30, 40).build();
        for (boolean reverse : new boolean[]{false, true}) {
            RotationRequestEvent event = new RotationRequestEvent();
            event.submit(reverse ? alpha : beta);
            event.submit(reverse ? beta : alpha);
            assertSame(alpha, event.resolve(0, 0).request());
        }
    }

    @Test
    void laterRequestReplacesSameOwnersPreviousRequest() {
        RotationRequestEvent event = new RotationRequestEvent();
        event.submit(request("Module", 100, 10, 20).build());
        event.submit(request("Module", 0, 30, 40).build());
        assertArrayEquals(new float[]{30, 40}, event.resolve(0, 0).rotations());
    }

    @Test
    void yawOnlyPreservesCameraPitchEvenWithAnotherPitchRequest() {
        RotationRequestEvent event = new RotationRequestEvent();
        event.submit(request("Yaw", 100, 90, 0).axes(RotationRequest.Axes.YAW_ONLY).build());
        event.submit(request("Pitch", 90, 0, 90).axes(RotationRequest.Axes.PITCH_ONLY).build());
        assertArrayEquals(new float[]{90, -35}, event.resolve(0, -35).rotations());
    }

    @Test
    void pitchOverrideInheritsBestYawAndContinuityButOwnsOtherSettings() {
        RotationRequestEvent event = new RotationRequestEvent();
        event.submit(request("Low", 10, 20, 30).build());
        event.submit(request("Chest", 100, 720, 45).continuousYaw(true).silent(false)
                .movementCorrection(MovementCorrection.Silent).build());
        event.submit(request("OtherPitch", 150, 999, -80).axes(RotationRequest.Axes.PITCH_ONLY).build());
        RotationRequest override = request("NoFall", 200, 0, 90).speed(0)
                .axes(RotationRequest.Axes.PITCH_ONLY).build();
        event.submit(override);
        var selection = event.resolve(15, 25);
        assertSame(override, selection.request());
        assertArrayEquals(new float[]{720, 90}, selection.rotations());
        assertTrue(selection.continuousYaw());
        assertTrue(selection.request().silent());
        assertEquals(MovementCorrection.None, selection.request().movementCorrection());
    }

    @Test
    void pitchOverrideFallsBackToCameraYawAndLosesToHigherFullRequest() {
        RotationRequest pitch = request("Pitch", 50, 0, 90).axes(RotationRequest.Axes.PITCH_ONLY).build();
        RotationRequestEvent event = new RotationRequestEvent();
        event.submit(pitch);
        assertArrayEquals(new float[]{345, 90}, event.resolve(345, 0).rotations());
        event = new RotationRequestEvent();
        event.submit(pitch);
        event.submit(request("Full", 100, 10, 20).build());
        assertArrayEquals(new float[]{10, 20}, event.resolve(345, 0).rotations());
    }

    @Test
    void requestsCannotLeakAcrossTicksOrBeSubmittedAfterResolution() {
        EventManager manager = new EventManager();
        Object module = new Object() {
            @EventTarget
            public void onRequest(RotationRequestEvent event) {
                event.submit(request("Module", 100, 10, 20).build());
            }
        };
        manager.register(module);
        RotationRequestEvent firstTick = new RotationRequestEvent();
        manager.call(firstTick);
        assertNotNull(firstTick.resolve(0, 0));
        assertThrows(IllegalStateException.class,
                () -> firstTick.submit(request("Late", 200, 0, 0).build()));
        manager.unregister(module);
        RotationRequestEvent secondTick = new RotationRequestEvent();
        manager.call(secondTick);
        assertNull(secondTick.resolve(0, 0));
    }

    @Test
    void moduleArraysCannotMutateRequestsOrResolvedAngles() {
        float[] angles = {10, 120};
        var builder = RotationRequest.builder("Derp", angles, 1).speed(0);
        angles[0] = 100;
        RotationRequestEvent event = new RotationRequestEvent();
        event.submit(builder.build());
        var selection = event.resolve(0, 0);
        selection.rotations()[1] = 0;
        // Intentionally preserve unsafe pitch modes such as Derp's Safe Pitch=false.
        assertArrayEquals(new float[]{10, 120}, selection.rotations());
    }

    @Test
    void invalidRequestsFailBeforeEnteringArbitration() {
        assertThrows(IllegalArgumentException.class, () -> request(" ", 0, 0, 0).build());
        assertThrows(IllegalArgumentException.class, () -> request("Bad", 0, Float.NaN, 0).build());
        assertThrows(IllegalArgumentException.class, () -> request("Bad", 0, 0, Float.POSITIVE_INFINITY).build());
        assertThrows(IllegalArgumentException.class, () -> request("Bad", 0, 0, 0).speed(-1).build());
        assertThrows(IllegalArgumentException.class, () -> request("Bad", 0, 0, 0).speed(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> request("Bad", 0, 0, 0).speed(Double.POSITIVE_INFINITY).build());
        assertThrows(IllegalArgumentException.class, () -> RotationRequest.builder("Bad", new float[]{0}, 0));
        assertThrows(IllegalArgumentException.class, () -> RotationRequest.builder("Bad", null, 0));
        assertThrows(NullPointerException.class, () -> request("Bad", 0, 0, 0).axes(null).build());
        assertThrows(NullPointerException.class, () -> request("Bad", 0, 0, 0).movementCorrection(null).build());
    }
}
