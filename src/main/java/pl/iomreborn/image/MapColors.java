package pl.iomreborn.image;

import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts images into Minecraft map color indexes, with optional dithering. Safe to call from worker threads
 * (once {@link #warmUp()} ran on the main thread).
 */
public final class MapColors {

    public static final int PIXELS = ImageProcessing.MAP_SIZE * ImageProcessing.MAP_SIZE;

    /** 4x4 Bayer matrix: the order in which the 16 colors of a mixing plan are spread over a 4x4 square. */
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
    private static final int PLAN_SIZE = 16;

    /** RGB of every map color index (by unsigned byte value); -1 for transparent or unused indexes. */
    private static volatile int[] palette;
    /** Brightness of every map color index, used to order the colors of a mixing plan. */
    private static volatile float[] luma;

    private MapColors() {
    }

    /**
     * Mixing plans of the colors already seen by ordered dithering. Plans only depend on the color, so one cache
     * can serve all frames of an animation. Not thread-safe: one per job.
     */
    public static final class PatternCache {
        private static final int MAX_ENTRIES = 1 << 18;
        private final Map<Integer, byte[]> plans = new HashMap<>();

        byte[] plan(int rgb) {
            byte[] plan = plans.get(rgb);
            if (plan == null) {
                if (plans.size() >= MAX_ENTRIES) {
                    plans.clear();
                }
                plan = mixingPlan(rgb);
                plans.put(rgb, plan);
            }
            return plan;
        }
    }

    /**
     * Recent servers build their map color cache lazily, on the first color lookup, without any locking:
     * two threads doing their first lookup at the same time make one of them fail. Doing one lookup on the
     * main thread at startup starts that build safely.
     */
    @SuppressWarnings("deprecation")
    public static void warmUp() {
        try {
            MapPalette.matchColor(new Color(16, 32, 48));
        } catch (RuntimeException ignored) {
            // the cache is already being built
        }
        palette();
    }

    @SuppressWarnings("deprecation")
    private static int[] palette() {
        int[] table = palette;
        if (table != null) {
            return table;
        }
        table = new int[256];
        float[] brightness = new float[256];
        for (int i = 0; i < 256; i++) {
            int rgb = -1;
            try {
                Color color = MapPalette.getColor((byte) i);
                if (color != null && color.getAlpha() == 255) {
                    rgb = color.getRGB() & 0xFFFFFF;
                }
            } catch (RuntimeException ignored) {
                // index past the end of the palette of this server version
            }
            table[i] = rgb;
            brightness[i] = rgb < 0 ? 0f
                    : 0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF);
        }
        luma = brightness;
        palette = table;
        return table;
    }

    /** RGB (0xRRGGBB) shown by a map color index, or -1 for transparent and unused indexes. */
    public static int rgbOf(byte index) {
        return palette()[index & 0xFF];
    }

    /** Converts one 128x128 tile without dithering (also used for the image files of existing maps). */
    public static byte[] toMapBytes(BufferedImage image) {
        BufferedImage tile = image;
        if (tile.getWidth() != ImageProcessing.MAP_SIZE || tile.getHeight() != ImageProcessing.MAP_SIZE) {
            tile = new BufferedImage(ImageProcessing.MAP_SIZE, ImageProcessing.MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = tile.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
        }
        return quantize(tile, Dithering.NONE);
    }

    /**
     * Converts a whole image (any size) into map color indexes, row by row. Dithering is done over the whole
     * image at once, so posters have no seams between their maps.
     */
    public static byte[] quantize(BufferedImage image, Dithering dithering) {
        return quantize(image, dithering, null);
    }

    /** Same, reusing the mixing plans of ordered dithering between calls (frames of one animation). */
    public static byte[] quantize(BufferedImage image, Dithering dithering, PatternCache cache) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        PatternCache plans = cache != null ? cache : new PatternCache();
        try {
            return quantize(argb, width, height, dithering, plans);
        } catch (IllegalStateException ex) {
            // Lost the race described in warmUp(); the cache is being built now, so the next lookup works.
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return quantize(argb, width, height, dithering, plans);
        }
    }

    private static byte[] quantize(int[] argb, int width, int height, Dithering dithering, PatternCache cache) {
        switch (dithering) {
            case FLOYD_STEINBERG:
                return floydSteinberg(argb, width, height);
            case ORDERED:
                return ordered(argb, width, height, cache);
            default:
                byte[] out = new byte[argb.length];
                for (int i = 0; i < argb.length; i++) {
                    int pixel = argb[i];
                    out[i] = opaque(pixel) ? match((pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF) : 0;
                }
                return out;
        }
    }

    /** Same rule as the server: pixels less than half opaque are transparent. */
    private static boolean opaque(int argb) {
        return (argb >>> 24) >= 128;
    }

    @SuppressWarnings("deprecation")
    private static byte match(int r, int g, int b) {
        return MapPalette.matchColor(r, g, b);
    }

    private static int clamp(float value) {
        int v = Math.round(value);
        return v < 0 ? 0 : Math.min(255, v);
    }

    /**
     * Floyd-Steinberg error diffusion, scanning rows in alternating directions (serpentine), which avoids the
     * diagonal "worm" patterns of the plain version. Transparent pixels neither receive nor pass on errors.
     */
    private static byte[] floydSteinberg(int[] argb, int width, int height) {
        int[] colors = palette();
        byte[] out = new byte[argb.length];
        // Errors of the current and the next row; index x + 1, so x - 1 and x + 1 never go out of bounds.
        float[][] current = new float[3][width + 2];
        float[][] next = new float[3][width + 2];
        for (int y = 0; y < height; y++) {
            boolean leftToRight = (y & 1) == 0;
            int step = leftToRight ? 1 : -1;
            for (int c = 0; c < 3; c++) {
                Arrays.fill(next[c], 0f);
            }
            for (int n = 0; n < width; n++) {
                int x = leftToRight ? n : width - 1 - n;
                int i = y * width + x;
                int pixel = argb[i];
                if (!opaque(pixel)) {
                    out[i] = 0;
                    continue;
                }
                int e = x + 1;
                int r = clamp(((pixel >> 16) & 0xFF) + current[0][e]);
                int g = clamp(((pixel >> 8) & 0xFF) + current[1][e]);
                int b = clamp((pixel & 0xFF) + current[2][e]);
                byte index = match(r, g, b);
                out[i] = index;
                int shown = colors[index & 0xFF];
                if (shown < 0) {
                    continue;
                }
                spread(current, next, 0, e, step, r - ((shown >> 16) & 0xFF));
                spread(current, next, 1, e, step, g - ((shown >> 8) & 0xFF));
                spread(current, next, 2, e, step, b - (shown & 0xFF));
            }
            float[][] swap = current;
            current = next;
            next = swap;
        }
        return out;
    }

    private static void spread(float[][] current, float[][] next, int channel, int e, int step, int error) {
        if (error == 0) {
            return;
        }
        current[channel][e + step] += error * (7f / 16f);
        next[channel][e - step] += error * (3f / 16f);
        next[channel][e] += error * (5f / 16f);
        next[channel][e + step] += error * (1f / 16f);
    }

    /**
     * Ordered dithering for any palette (Thomas Knoll's pattern dithering): every color gets a "mixing plan" of
     * 16 map colors whose average is as close as possible to it, and each pixel of a 4x4 square shows one of them.
     * The result only depends on the color and the position of a pixel, never on its neighbors, so the still
     * parts of an animation look exactly the same in every frame.
     */
    private static byte[] ordered(int[] argb, int width, int height, PatternCache cache) {
        byte[] out = new byte[argb.length];
        for (int y = 0; y < height; y++) {
            int row = (y & 3) << 2;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int pixel = argb[i];
                if (!opaque(pixel)) {
                    out[i] = 0;
                    continue;
                }
                out[i] = cache.plan(pixel & 0xFFFFFF)[BAYER[row | (x & 3)]];
            }
        }
        return out;
    }

    /** The 16 map colors mixed for one color, from darkest to brightest. */
    private static byte[] mixingPlan(int rgb) {
        int[] colors = palette();
        float[] brightness = luma;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float errorR = 0;
        float errorG = 0;
        float errorB = 0;
        byte[] plan = new byte[PLAN_SIZE];
        for (int i = 0; i < PLAN_SIZE; i++) {
            byte chosen = match(clamp(r + errorR), clamp(g + errorG), clamp(b + errorB));
            plan[i] = chosen;
            int shown = colors[chosen & 0xFF];
            if (shown < 0) {
                continue;
            }
            errorR += r - ((shown >> 16) & 0xFF);
            errorG += g - ((shown >> 8) & 0xFF);
            errorB += b - (shown & 0xFF);
        }
        // Insertion sort by brightness: dark and bright colors alternate evenly in the Bayer pattern.
        for (int i = 1; i < PLAN_SIZE; i++) {
            byte value = plan[i];
            float key = brightness[value & 0xFF];
            int j = i - 1;
            while (j >= 0 && brightness[plan[j] & 0xFF] > key) {
                plan[j + 1] = plan[j];
                j--;
            }
            plan[j + 1] = value;
        }
        return plan;
    }

    /**
     * Turns map color indexes back into a picture with exactly the colors the map shows. Saving such a picture
     * keeps the dithering: converting it again gives the same indexes.
     */
    public static BufferedImage toImage(byte[] pixels, int width, int height) {
        int[] colors = palette();
        int[] argb = new int[width * height];
        for (int i = 0; i < argb.length && i < pixels.length; i++) {
            int rgb = colors[pixels[i] & 0xFF];
            argb[i] = rgb < 0 ? 0 : 0xFF000000 | rgb;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }
}
