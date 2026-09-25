package cn.omix.util.sigma;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class SigmaImageBlurTest {
    @Test void paddedBlurPreservesSolidTerrainToTheEdges() {
        var source = new BufferedImage(13, 30, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 13; x++) source.setRGB(x, y, 0xf7e9a3);
        var blurred = SigmaImageBlur.blur(source, 10);
        assertEquals(13, blurred.getWidth()); assertEquals(30, blurred.getHeight());
        for (int y = 0; y < 30; y++) for (int x = 0; x < 13; x++) {
            int actual = blurred.getRGB(x, y);
            assertEquals(247, actual >> 16 & 255, 2); assertEquals(233, actual >> 8 & 255, 2); assertEquals(163, actual & 255, 2);
        }
    }
    @Test void zeroRadiusCopiesPixelsWithoutMutatingTheSource() {
        var source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(2, 1, 0x123456);
        var copy = SigmaImageBlur.blur(source, 0);
        assertNotSame(source, copy); assertEquals(source.getRGB(2, 1), copy.getRGB(2, 1));
        copy.setRGB(2, 1, 0);
        assertEquals(0xff123456, source.getRGB(2, 1));
        assertThrows(IllegalArgumentException.class, () -> SigmaImageBlur.blur(source, -1));
    }
}
