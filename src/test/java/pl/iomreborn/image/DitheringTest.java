package pl.iomreborn.image;

import org.bukkit.map.MapPalette;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DitheringTest {

    /** Smooth color gradient: red grows to the right, blue downwards, green in the middle. */
    private static BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = x * 255 / (width - 1);
                int b = y * 255 / (height - 1);
                int g = 255 - Math.abs(r - b);
                image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private static BufferedImage random(int width, int height, long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt() | 0xFF000000);
            }
        }
        return image;
    }

    /**
     * How far the picture looks from the original when seen from a distance: average color difference of 8x8
     * blocks (dithering moves single pixels away from the original, but blocks get much closer).
     */
    private static double blockError(BufferedImage source, byte[] pixels) {
        int width = source.getWidth();
        int height = source.getHeight();
        double total = 0;
        int blocks = 0;
        for (int by = 0; by + 8 <= height; by += 8) {
            for (int bx = 0; bx + 8 <= width; bx += 8) {
                double[] original = new double[3];
                double[] shown = new double[3];
                for (int y = by; y < by + 8; y++) {
                    for (int x = bx; x < bx + 8; x++) {
                        int s = source.getRGB(x, y);
                        int m = MapColors.rgbOf(pixels[y * width + x]);
                        for (int c = 0; c < 3; c++) {
                            original[c] += (s >> (16 - 8 * c)) & 0xFF;
                            shown[c] += (m >> (16 - 8 * c)) & 0xFF;
                        }
                    }
                }
                for (int c = 0; c < 3; c++) {
                    total += Math.abs(original[c] - shown[c]) / 64.0;
                }
                blocks++;
            }
        }
        return total / (blocks * 3.0);
    }

    @Test
    void parsesConfigValues() {
        assertEquals(Dithering.FLOYD_STEINBERG, Dithering.parse("floyd-steinberg", Dithering.NONE));
        assertEquals(Dithering.FLOYD_STEINBERG, Dithering.parse("Floyd_Steinberg", Dithering.NONE));
        assertEquals(Dithering.FLOYD_STEINBERG, Dithering.parse("true", Dithering.NONE));
        assertEquals(Dithering.ORDERED, Dithering.parse(" ordered ", Dithering.NONE));
        assertEquals(Dithering.NONE, Dithering.parse("none", Dithering.ORDERED));
        assertEquals(Dithering.NONE, Dithering.parse("false", Dithering.ORDERED));
        assertEquals(Dithering.ORDERED, Dithering.parse("sparkly", Dithering.ORDERED));
        assertEquals(Dithering.ORDERED, Dithering.parse(null, Dithering.ORDERED));
    }

    @Test
    void parsesCommandWordsAndChoosesTheMethod() {
        assertEquals(Boolean.TRUE, Dithering.parseCommandWord("dither"));
        assertEquals(Boolean.TRUE, Dithering.parseCommandWord("--DITHER"));
        assertEquals(Boolean.FALSE, Dithering.parseCommandWord("nodither"));
        assertEquals(Boolean.FALSE, Dithering.parseCommandWord("no-dither"));
        assertNull(Dithering.parseCommandWord("resize"));

        assertEquals(Dithering.ORDERED, Dithering.choose(null, Dithering.ORDERED));
        assertEquals(Dithering.NONE, Dithering.choose(false, Dithering.FLOYD_STEINBERG));
        assertEquals(Dithering.FLOYD_STEINBERG, Dithering.choose(true, Dithering.NONE));
        assertEquals(Dithering.ORDERED, Dithering.choose(true, Dithering.ORDERED));

        assertEquals(Dithering.ORDERED, Dithering.FLOYD_STEINBERG.forAnimation());
        assertEquals(Dithering.ORDERED, Dithering.ORDERED.forAnimation());
        assertEquals(Dithering.NONE, Dithering.NONE.forAnimation());
    }

    @Test
    @SuppressWarnings("deprecation")
    void withoutDitheringColorsMatchTheServerConversion() {
        BufferedImage image = random(128, 128, 42);
        assertArrayEquals(MapPalette.imageToBytes(image), MapColors.quantize(image, Dithering.NONE));
        assertArrayEquals(MapPalette.imageToBytes(image), MapColors.toMapBytes(image));
    }

    @Test
    void ditheringGetsMuchCloserToTheOriginal() {
        BufferedImage image = gradient(256, 128);
        double plain = blockError(image, MapColors.quantize(image, Dithering.NONE));
        double floyd = blockError(image, MapColors.quantize(image, Dithering.FLOYD_STEINBERG));
        double ordered = blockError(image, MapColors.quantize(image, Dithering.ORDERED));
        assertTrue(floyd < plain * 0.6, "floyd-steinberg " + floyd + " vs none " + plain);
        assertTrue(ordered < plain * 0.6, "ordered " + ordered + " vs none " + plain);
    }

    @Test
    void ditheringUsesMoreShadesInAGradient() {
        BufferedImage image = gradient(256, 128);
        assertTrue(distinct(MapColors.quantize(image, Dithering.FLOYD_STEINBERG))
                > distinct(MapColors.quantize(image, Dithering.NONE)));
    }

    private static int distinct(byte[] pixels) {
        boolean[] seen = new boolean[256];
        int count = 0;
        for (byte pixel : pixels) {
            if (!seen[pixel & 0xFF]) {
                seen[pixel & 0xFF] = true;
                count++;
            }
        }
        return count;
    }

    @Test
    void exactMapColorsAreNotChanged() {
        // A picture made only of colors the map can show has nothing to dither.
        byte[] source = MapColors.quantize(random(64, 64, 7), Dithering.NONE);
        BufferedImage exact = MapColors.toImage(source, 64, 64);
        assertArrayEquals(source, MapColors.quantize(exact, Dithering.FLOYD_STEINBERG));
        assertArrayEquals(source, MapColors.quantize(exact, Dithering.NONE));
    }

    @Test
    void savedDitheredPicturesLoadBackIdentically() {
        // Dithered tiles are saved with their map colors; loading them (without dithering) must give the same dots.
        BufferedImage image = gradient(128, 128);
        for (Dithering dithering : Dithering.values()) {
            byte[] pixels = MapColors.quantize(image, dithering);
            byte[] reloaded = MapColors.toMapBytes(MapColors.toImage(pixels, 128, 128));
            assertArrayEquals(pixels, reloaded, dithering.name());
        }
    }

    @Test
    void transparentPixelsStayTransparent() {
        BufferedImage image = gradient(64, 64);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 20; x++) {
                image.setRGB(x, y, 0x00FFFFFF);
            }
        }
        for (Dithering dithering : Dithering.values()) {
            byte[] pixels = MapColors.quantize(image, dithering);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int index = pixels[y * 64 + x] & 0xFF;
                    if (x < 20) {
                        assertEquals(0, index, dithering + " at " + x + "," + y);
                    } else {
                        assertTrue(MapColors.rgbOf((byte) index) >= 0, dithering + " gave a transparent pixel");
                    }
                }
            }
        }
    }

    @Test
    void orderedDitheringIsStableWhenAnotherPartChanges() {
        // In an animation, a still background must give the same dots in every frame (no flicker).
        BufferedImage a = gradient(128, 128);
        BufferedImage b = ImageProcessing.copy(a);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                b.setRGB(x, y, Color.MAGENTA.getRGB());
            }
        }
        byte[] orderedA = MapColors.quantize(a, Dithering.ORDERED);
        byte[] orderedB = MapColors.quantize(b, Dithering.ORDERED);
        byte[] floydA = MapColors.quantize(a, Dithering.FLOYD_STEINBERG);
        byte[] floydB = MapColors.quantize(b, Dithering.FLOYD_STEINBERG);
        int floydChanged = 0;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                if (x < 16 && y < 16) {
                    continue;
                }
                int i = y * 128 + x;
                assertEquals(orderedA[i], orderedB[i], "ordered changed at " + x + "," + y);
                if (floydA[i] != floydB[i]) {
                    floydChanged++;
                }
            }
        }
        assertTrue(floydChanged > 0, "error diffusion spreads the change, which is why animations avoid it");
    }

    @Test
    void postersAreDitheredAsOnePictureWithoutChangingTheLayout() {
        BufferedImage image = gradient(300, 140);
        BufferedImage padded = ImageProcessing.pad(image);
        assertEquals(384, padded.getWidth());
        assertEquals(256, padded.getHeight());
        byte[][] tiles = ImageProcessing.splitPixels(MapColors.quantize(padded, Dithering.NONE),
                padded.getWidth(), padded.getHeight());
        ImageProcessing.Poster poster = ImageProcessing.split(image);
        assertEquals(poster.tiles().length, tiles.length);
        for (int i = 0; i < tiles.length; i++) {
            assertArrayEquals(MapColors.toMapBytes(poster.tiles()[i]), tiles[i], "tile " + i);
        }
        byte[][] dithered = ImageProcessing.splitPixels(MapColors.quantize(padded, Dithering.FLOYD_STEINBERG),
                padded.getWidth(), padded.getHeight());
        assertNotEquals(java.util.Arrays.hashCode(tiles[4]), java.util.Arrays.hashCode(dithered[4]));
    }

    @Test
    void stillPicturesKeepTheirOriginalColorsWithoutDithering() {
        BufferedImage image = gradient(128, 128);
        RenderService.Prepared plain = RenderService.convert(image, Dithering.NONE, false);
        assertEquals(image.getRGB(50, 60), plain.tiles()[0].getRGB(50, 60), "saved like ImageOnMap");
        RenderService.Prepared dithered = RenderService.convert(image, Dithering.FLOYD_STEINBERG, true);
        assertArrayEquals(dithered.pixels()[0], MapColors.toMapBytes(dithered.tiles()[0]), "saved with map colors");
        assertEquals(image, dithered.full());
    }
}
