package pl.iomreborn.image;

import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Converts a 128x128 tile into Minecraft map color indexes. Safe to call from worker threads. */
public final class MapColors {

    public static final int PIXELS = ImageProcessing.MAP_SIZE * ImageProcessing.MAP_SIZE;

    private MapColors() {
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
    }

    @SuppressWarnings("deprecation")
    public static byte[] toMapBytes(BufferedImage image) {
        BufferedImage tile = image;
        if (tile.getWidth() != ImageProcessing.MAP_SIZE || tile.getHeight() != ImageProcessing.MAP_SIZE) {
            tile = new BufferedImage(ImageProcessing.MAP_SIZE, ImageProcessing.MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = tile.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
        }
        try {
            return MapPalette.imageToBytes(tile);
        } catch (IllegalStateException ex) {
            // Lost the race described in warmUp(); the cache is being built now, so the next lookup works.
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return MapPalette.imageToBytes(tile);
        }
    }
}
