import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders the canonical Omix mark without external image dependencies.
 *
 * <p>The high-resolution ARGB canvas is transparent by default. Only the
 * ring geometry is painted, so both the surrounding canvas and the O counter
 * remain fully transparent in the exported PNG.</p>
 */
public final class OmixLogoRenderer {
    private static final int OUTPUT_SIZE = 160;
    private static final int SCALE = 4;

    private OmixLogoRenderer() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> outputs = args.length == 0
            ? List.of(
                Path.of("src/main/resources/assets/omix/textures/mainmenu/omix.png"),
                Path.of("src/main/java/im/src-webui/src/assets/omix.png")
            )
            : java.util.Arrays.stream(args).map(Path::of).toList();

        BufferedImage logo = render();
        validateTransparency(logo);

        for (Path output : outputs) {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!ImageIO.write(logo, "png", output.toFile())) {
                throw new IllegalStateException("No PNG writer is available");
            }
            System.out.println("Wrote transparent Omix logo: " + output);
        }
    }

    private static BufferedImage render() {
        int renderSize = OUTPUT_SIZE * SCALE;
        BufferedImage supersampled = new BufferedImage(
            renderSize,
            renderSize,
            BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = supersampled.createGraphics();
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            );
            graphics.scale(SCALE, SCALE);

            Area ring = new Area(new Ellipse2D.Double(19, 19, 122, 122));
            ring.subtract(new Area(new Ellipse2D.Double(53, 53, 54, 54)));
            graphics.setClip(ring);

            graphics.setPaint(new LinearGradientPaint(
                new Point2D.Double(24, 130),
                new Point2D.Double(128, 31),
                new float[] {0f, 0.55f, 1f},
                new Color[] {
                    new Color(0x11, 0x92, 0x51),
                    new Color(0x1C, 0xAF, 0x61),
                    new Color(0x2B, 0xCB, 0x72)
                }
            ));
            graphics.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);

            Path2D brightField = new Path2D.Double();
            brightField.moveTo(0, 0);
            brightField.lineTo(160, 0);
            brightField.lineTo(160, 39);
            brightField.curveTo(133, 38, 118, 49, 112, 65);
            brightField.curveTo(105, 83, 94, 94, 77, 100);
            brightField.curveTo(55, 108, 40, 125, 36, 160);
            brightField.lineTo(0, 160);
            brightField.closePath();

            graphics.setPaint(new LinearGradientPaint(
                new Point2D.Double(34, 25),
                new Point2D.Double(128, 132),
                new float[] {0f, 0.52f, 1f},
                new Color[] {
                    new Color(0x75, 0xE6, 0xA3),
                    new Color(0x35, 0xD4, 0x7B),
                    new Color(0x20, 0xB9, 0x67)
                }
            ));
            graphics.fill(brightField);

            Path2D deepFold = new Path2D.Double();
            deepFold.moveTo(112, 64);
            deepFold.curveTo(121, 61, 132, 63, 143, 70);
            deepFold.lineTo(143, 104);
            deepFold.curveTo(133, 93, 124, 85, 112, 81);
            deepFold.curveTo(106, 79, 102, 78, 98, 78);
            deepFold.curveTo(104, 74, 109, 69, 112, 64);
            deepFold.closePath();

            graphics.setPaint(new LinearGradientPaint(
                new Point2D.Double(101, 55),
                new Point2D.Double(128, 91),
                new float[] {0f, 1f},
                new Color[] {
                    new Color(0x10, 0x88, 0x4A, 184),
                    new Color(0x10, 0x88, 0x4A, 0)
                }
            ));
            graphics.fill(deepFold);

            Path2D lightFold = new Path2D.Double();
            lightFold.moveTo(48, 96);
            lightFold.curveTo(39, 99, 28, 97, 17, 90);
            lightFold.lineTo(17, 56);
            lightFold.curveTo(27, 67, 36, 75, 48, 79);
            lightFold.curveTo(54, 81, 58, 82, 62, 82);
            lightFold.curveTo(56, 86, 51, 91, 48, 96);
            lightFold.closePath();

            graphics.setColor(new Color(0x8A, 0xF0, 0xB0, 61));
            graphics.fill(lightFold);
        } finally {
            graphics.dispose();
        }

        BufferedImage output = new BufferedImage(
            OUTPUT_SIZE,
            OUTPUT_SIZE,
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D downsampler = output.createGraphics();
        try {
            downsampler.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            downsampler.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            );
            downsampler.drawImage(
                supersampled,
                0,
                0,
                OUTPUT_SIZE,
                OUTPUT_SIZE,
                null
            );
        } finally {
            downsampler.dispose();
        }
        return output;
    }

    private static void validateTransparency(BufferedImage image) {
        int[][] transparentSamples = {
            {0, 0},
            {OUTPUT_SIZE - 1, 0},
            {0, OUTPUT_SIZE - 1},
            {OUTPUT_SIZE - 1, OUTPUT_SIZE - 1},
            {OUTPUT_SIZE / 2, OUTPUT_SIZE / 2}
        };
        for (int[] sample : transparentSamples) {
            int alpha = image.getRGB(sample[0], sample[1]) >>> 24;
            if (alpha != 0) {
                throw new IllegalStateException(
                    "Expected transparent pixel at "
                        + sample[0] + "," + sample[1]
                        + " but alpha was " + alpha
                );
            }
        }

        int ringAlpha = image.getRGB(OUTPUT_SIZE / 2, 30) >>> 24;
        if (ringAlpha != 255) {
            throw new IllegalStateException(
                "Expected opaque ring pixel but alpha was " + ringAlpha
            );
        }
        System.out.println("Validated: corners alpha=0, center alpha=0, ring alpha=255");
    }
}
