package cn.omix.util.sigma;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigmaPrimitivesTest {
    @Test void sourcePaletteAndNametagBackgroundRemainExact() {
        assertEquals(0xfffefefe, SigmaColors.WHITE);
        assertEquals(0xff010101, SigmaColors.BLACK);
        // The source passes 75, not .75; the original integer component wrap is visible.
        assertEquals(0xff202020, SigmaColors.sourceBlend(SigmaColors.WHITE, SigmaColors.BLACK, 75));
        assertEquals(0x7f202020, SigmaColors.alpha(SigmaColors.sourceBlend(SigmaColors.WHITE, SigmaColors.BLACK, 75), .5f));
        assertEquals(0x72fefefe, SigmaColors.alpha(SigmaColors.WHITE, .45f));
        assertEquals(0x00fefefe, SigmaColors.alpha(SigmaColors.WHITE, 0));
    }

    @Test void contourOmitsInternalAndHiddenEdges() {
        var front = SigmaGeometry.silhouetteEdges(0, 0, 0, 1, 1, 1, .5, .5, -3);
        assertEquals(4, front.size());
        for (int[] edge : front) { assertTrue(edge[0] < 4); assertTrue(edge[1] < 4); }
        var diagonal = SigmaGeometry.silhouetteEdges(0, 0, 0, 1, 1, 1, 3, 3, -3);
        assertEquals(6, diagonal.size());
        int[] degree = new int[8];
        for (int[] edge : diagonal) { degree[edge[0]]++; degree[edge[1]]++; }
        for (int count : degree) assertTrue(count == 0 || count == 2, "Silhouette must be a closed contour");
        assertTrue(SigmaGeometry.silhouetteEdges(0, 0, 0, 1, 1, 1, .5, .5, .5).isEmpty());
    }

    @Test void animationSpeedDoesNotDependOnFrameRateAndReversesContinuously() {
        SigmaAnimation slow = new SigmaAnimation(), fast = new SigmaAnimation();
        slow.update(true, 1, 150); fast.update(true, 1, 150);
        for (int i = 1; i <= 3; i++) slow.update(true, 1 + i * 25_000_000L, 150);
        for (int i = 1; i <= 15; i++) fast.update(true, 1 + i * 5_000_000L, 150);
        assertEquals(.5f, slow.value(), .00001f);
        assertEquals(slow.value(), fast.value(), .00001f);
        assertEquals(.3f, slow.update(false, 105_000_001L, 150), .00001f);
        assertEquals(0, slow.update(false, 1_000_000_000L, 150));
        assertEquals(1, slow.update(true, 2_000_000_000L, 150));
    }

    @Test void labelMagnificationAndCompassWrapMatchSource() {
        assertEquals(1, SigmaGeometry.magnification(0, 1));
        assertEquals(2, SigmaGeometry.magnification(120, 1));
        assertEquals(.8f, SigmaGeometry.magnification(0, .8f));
        assertEquals(359, SigmaGeometry.wrapDegrees(-1));
        assertEquals(5, SigmaGeometry.wrapDegrees(725));
    }

    @Test void waypointFacesRetainOriginalTiltAndSquareEquator() {
        var peak = SigmaGeometry.waypointVertex(0, 0, 0);
        assertEquals(.3981198028, peak.y(), .000001);
        assertEquals(.3 + .4985 * Math.sin(Math.toRadians(-37)), peak.z(), .000001);
        var first = SigmaGeometry.waypointVertex(0, 0, 1);
        var second = SigmaGeometry.waypointVertex(0, 1, 2);
        assertEquals(-.3, first.x());
        assertEquals(.3, first.z());
        assertEquals(.3, second.x(), .000001);
        assertEquals(-.3, second.z(), .000001);
        var lower = SigmaGeometry.waypointVertex(1, 0, 0);
        assertEquals(-peak.y(), lower.y());
        assertEquals(-peak.z(), lower.z());
    }
}
