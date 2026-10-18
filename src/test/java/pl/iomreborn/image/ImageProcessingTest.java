package pl.iomreborn.image;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessingTest {

    private static BufferedImage filled(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xFF;
    }

    @Test
    void splitsAndCentersLikeImageOnMap() {
        ImageProcessing.Poster poster = ImageProcessing.split(filled(200, 100, Color.RED));
        assertEquals(2, poster.columns());
        assertEquals(1, poster.rows());
        assertEquals(2, poster.tiles().length);

        BufferedImage left = poster.tiles()[0];
        BufferedImage right = poster.tiles()[1];
        // (256 - 200) / 2 = 28 transparent columns on the left, (128 - 100) / 2 = 14 rows on top.
        assertEquals(0, alpha(left, 27, 64));
        assertEquals(255, alpha(left, 28, 64));
        assertEquals(0, alpha(left, 64, 13));
        assertEquals(255, alpha(left, 64, 14));
        assertEquals(255, alpha(right, 99, 64));
        assertEquals(0, alpha(right, 100, 64));
    }

    @Test
    void oddRemaindersAreCenteredTheSameWay() {
        ImageProcessing.Poster poster = ImageProcessing.split(filled(201, 128, Color.BLUE));
        assertEquals(0, alpha(poster.tiles()[0], 26, 0));
        assertEquals(255, alpha(poster.tiles()[0], 27, 0));
    }

    @Test
    void smallImageGivesOneTile() {
        ImageProcessing.Poster poster = ImageProcessing.split(filled(128, 128, Color.GREEN));
        assertEquals(1, poster.tiles().length);
        assertEquals(255, alpha(poster.tiles()[0], 0, 0));
        assertEquals(255, alpha(poster.tiles()[0], 127, 127));
    }

    @Test
    void containedKeepsProportions() {
        BufferedImage out = ImageProcessing.resize(filled(400, 100, Color.RED), 128, 128, ScalingMode.CONTAINED, true);
        assertEquals(128, out.getWidth());
        assertEquals(128, out.getHeight());
        assertEquals(0, alpha(out, 64, 47));
        assertEquals(255, alpha(out, 64, 48));
        assertEquals(255, alpha(out, 64, 79));
        assertEquals(0, alpha(out, 64, 80));
    }

    @Test
    void coveredAndStretchedFillEverything() {
        for (ScalingMode mode : new ScalingMode[] {ScalingMode.COVERED, ScalingMode.STRETCHED}) {
            BufferedImage out = ImageProcessing.resize(filled(400, 100, Color.RED), 256, 128, mode, true);
            assertEquals(256, out.getWidth());
            assertEquals(128, out.getHeight());
            assertEquals(255, alpha(out, 0, 0), mode.name());
            assertEquals(255, alpha(out, 255, 127), mode.name());
        }
    }

    @Test
    void pixelatedUpscalingKeepsHardEdges() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFF0000);
        source.setRGB(1, 0, 0xFF0000FF);
        BufferedImage out = ImageProcessing.scale(source, 128, 64, true);
        assertEquals(0xFFFF0000, out.getRGB(63, 10));
        assertEquals(0xFF0000FF, out.getRGB(64, 10));
    }

    @Test
    void parsesScalingWords() {
        assertEquals(ScalingMode.CONTAINED, ScalingMode.parse("resize").orElseThrow());
        assertEquals(ScalingMode.STRETCHED, ScalingMode.parse("resize-stretched").orElseThrow());
        assertEquals(ScalingMode.STRETCHED, ScalingMode.parse("Stretched").orElseThrow());
        assertEquals(ScalingMode.COVERED, ScalingMode.parse("resize-covered").orElseThrow());
        assertEquals(ScalingMode.COVERED, ScalingMode.parse("cover").orElseThrow());
        assertTrue(ScalingMode.parse("big").isEmpty());
    }

    @Test
    void convertsToMapColors() {
        byte[] transparent = MapColors.toMapBytes(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB));
        assertEquals(128 * 128, transparent.length);
        for (byte b : transparent) {
            assertEquals(0, b);
        }
        byte[] red = MapColors.toMapBytes(filled(128, 128, Color.RED));
        assertTrue(red[0] != 0);
        assertEquals(red[0], red[red.length - 1]);
    }
}
