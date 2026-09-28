package cn.omix.util.sigma;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SigmaUiCoordinatesTest {
    @Test void fontRasterizationHasEnoughTexelsWithoutChangingLogicalSize() {
        for (int[] display : new int[][]{
                {1920, 1080, 1920, 1080, 1},
                {1920, 1080, 3840, 2160, 2},
                {1280, 720, 1920, 1080, 2},
                {1280, 720, 3840, 2160, 3},
                {1280, 720, 2560, 2160, 3}}) {
            int density = SigmaUiCoordinates.rasterScale(display[0], display[1], display[2], display[3]);
            assertEquals(display[4], density);
            int logicalFontSize = 20;
            int rasterSize = logicalFontSize * density;
            for (int guiScale : new int[]{1, 2, 3, 4, 6}) {
                for (int axis = 0; axis < 2; axis++) {
                    float projection = SigmaUiCoordinates.renderScale(display[axis], display[axis + 2], guiScale);
                    float physicalSize = rasterSize / (float) density * projection * guiScale;
                    assertEquals(logicalFontSize * (float) display[axis + 2] / display[axis], physicalSize, .001f);
                    assertTrue(rasterSize + .001f >= physicalSize, "Glyph texture must not be magnified");
                }
            }
        }
    }

    @Test void retinaBackgroundCoversFramebufferAndHudAnchorsReachEdges() {
        for (int guiScale : new int[]{1, 2, 3, 4, 6}) {
            var matrix = new Matrix3x2f().scale(
                    SigmaUiCoordinates.renderScale(1920, 3840, guiScale),
                    SigmaUiCoordinates.renderScale(1040, 2080, guiScale));
            // GuiRenderer converts GUI positions back to framebuffer pixels.
            var bottomRight = matrix.transformPosition(new Vector2f(1920, 1040)).mul(guiScale);
            assertEquals(3840, bottomRight.x, .001f);
            assertEquals(2080, bottomRight.y, .001f);
            var hudAnchor = matrix.transformPosition(new Vector2f(1910, 1018)).mul(guiScale);
            assertEquals(3820, hudAnchor.x, .001f);
            assertEquals(2036, hudAnchor.y, .001f);
        }
    }

    @Test void normalDensityPreservesLogicalPixelSize() {
        for (int guiScale : new int[]{1, 2, 3, 4}) {
            float scale = SigmaUiCoordinates.renderScale(1920, 1920, guiScale);
            assertEquals(200, 200 * scale * guiScale, .001f);
        }
    }

    @Test void oddWindowSizesKeepDrawingAndMouseOnTheSameLogicalPosition() {
        // Rendering uses fractional GUI extents; mouse events use rounded extents.
        int[] window = {1279, 719}, framebuffer = {2558, 1438};
        int guiScale = 3;
        for (int axis = 0; axis < 2; axis++) {
            int scaledSize = (int) Math.ceil((double) framebuffer[axis] / guiScale);
            float scale = SigmaUiCoordinates.renderScale(window[axis], framebuffer[axis], guiScale);
            for (double logical : new double[]{0, 30, 230, window[axis] - 1, window[axis]}) {
                double eventPosition = logical * scaledSize / window[axis];
                float hitPosition = SigmaUiCoordinates.fromMouse(eventPosition, window[axis], scaledSize);
                assertEquals(logical, hitPosition, .0001);
                assertEquals(logical * framebuffer[axis] / window[axis], hitPosition * scale * guiScale, .001);
            }
            double dragEvent = -17.5 * scaledSize / window[axis];
            assertEquals(-17.5, SigmaUiCoordinates.fromMouse(dragEvent, window[axis], scaledSize), .0001);
        }
    }

    @Test void minimizedWindowDoesNotProduceInvalidTransforms() {
        assertEquals(1, SigmaUiCoordinates.rasterScale(0, 0, 0, 0));
        assertTrue(Float.isFinite(SigmaUiCoordinates.renderScale(0, 0, 0)));
        assertTrue(Float.isFinite(SigmaUiCoordinates.fromMouse(0, 0, 0)));
    }
}
