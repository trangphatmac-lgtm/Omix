package cn.omix.util.sigma;

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/** Jello ImageUtil's padded, separable Gaussian filter, without framebuffer readback. */
public final class SigmaImageBlur {
    private SigmaImageBlur() {}
    public static BufferedImage blur(BufferedImage source, int radius) {
        if (radius < 0) throw new IllegalArgumentException("Negative blur radius");
        int w = source.getWidth(), h = source.getHeight();
        BufferedImage padded = new BufferedImage(w + radius * 2, h + radius * 2, BufferedImage.TYPE_INT_RGB);
        var graphics = padded.createGraphics();
        try { graphics.drawImage(source, 0, 0, padded.getWidth(), padded.getHeight(), null); }
        finally { graphics.dispose(); }
        if (radius == 0) return padded;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) padded.setRGB(x + radius, y + radius, source.getRGB(x, y));
        float[] weights = new float[radius * 2 + 1]; float sum = 0, sigma = radius / 3f;
        for (int i = -radius; i <= radius; i++) { float weight = (float) Math.exp(-(i * i) / (2 * sigma * sigma)); weights[i + radius] = weight; sum += weight; }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        var filter = new ConvolveOp(new Kernel(weights.length, 1, weights));
        var horizontal = filter.filter(padded, null);
        var result = transpose(filter.filter(transpose(horizontal), null));
        return result.getSubimage(radius, radius, w, h);
    }
    private static BufferedImage transpose(BufferedImage source) {
        int w = source.getWidth(), h = source.getHeight();
        var result = new BufferedImage(h, w, source.getType());
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) result.setRGB(h - y - 1, w - x - 1, source.getRGB(x, y));
        return result;
    }
}
