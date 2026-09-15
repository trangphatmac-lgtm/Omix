package cn.omix.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongJumpAimTest {
    @Test
    void aimsBehindAcrossCardinalDirectionsAndWrappedCameraYaw() {
        for (float cameraYaw : new float[]{-720, -181, -180, -90, 0, 90, 179, 180, 359, 720}) {
            LongJumpAim aim = LongJumpAim.behind(cameraYaw, 80);
            double cameraAngle = Math.toRadians(cameraYaw);
            double aimAngle = Math.toRadians(aim.yaw());
            double horizontalDot = Math.sin(cameraAngle) * Math.sin(aimAngle)
                    + Math.cos(cameraAngle) * Math.cos(aimAngle);
            assertEquals(-1, horizontalDot, 1.0E-10, "Must point behind camera yaw " + cameraYaw);
            assertEquals(80, aim.pitch());
        }
    }

    @Test
    void targetPitchControlsOnlyTheVerticalAngle() {
        for (float pitch : new float[]{-90, -45, 0, 45, 80, 90}) {
            LongJumpAim aim = LongJumpAim.behind(30, pitch);
            assertEquals(210, aim.yaw());
            assertEquals(pitch, aim.pitch());
        }
    }
}
