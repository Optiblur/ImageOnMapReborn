package pl.iomreborn.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Scaling and cutting of images into 128x128 map tiles. */
public final class ImageProcessing {

    public static final int MAP_SIZE = 128;

    /** An image cut into tiles, stored row by row starting at the top-left tile. */
    public record Poster(int columns, int rows, BufferedImage[] tiles) {
    }

    private ImageProcessing() {
    }

    public static BufferedImage toArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return out;
    }

    /** Fits the image into a canvas of the given size, using the same rules as ImageOnMap. */
    public static BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight,
                                       ScalingMode mode, boolean smooth) {
        switch (mode) {
            case NONE:
                return source;
            case STRETCHED:
                return place(source, targetWidth, targetHeight, 0, 0, targetWidth, targetHeight, smooth);
            default:
                boolean covered = mode == ScalingMode.COVERED;
                float ratioW = (float) targetWidth / source.getWidth();
                float ratioH = (float) targetHeight / source.getHeight();
                int finalWidth;
                int finalHeight;
                if (covered ? ratioW > ratioH : ratioW < ratioH) {
                    finalWidth = targetWidth;
                    finalHeight = Math.max(1, Math.round(source.getHeight() * ratioW));
                } else {
                    finalWidth = Math.max(1, Math.round(source.getWidth() * ratioH));
                    finalHeight = targetHeight;
                }
                int x = (targetWidth - finalWidth) / 2;
                int y = (targetHeight - finalHeight) / 2;
                return place(source, targetWidth, targetHeight, x, y, finalWidth, finalHeight, smooth);
        }
    }

    private static BufferedImage place(BufferedImage source, int canvasWidth, int canvasHeight,
                                       int x, int y, int width, int height, boolean smooth) {
        BufferedImage scaled = scale(source, width, height, smooth);
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(scaled, x, y, null);
        graphics.dispose();
        return canvas;
    }

    /**
     * Scales an image. When shrinking in smooth mode the image is halved step by step, which keeps photos
     * sharp without aliasing; enlarging always keeps hard pixel edges (good for pixel art).
     */
    public static BufferedImage scale(BufferedImage source, int width, int height, boolean smooth) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        boolean shrinking = width < source.getWidth() || height < source.getHeight();
        if (!smooth || !shrinking) {
            return draw(source, width, height, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        BufferedImage current = source;
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();
        while (currentWidth / 2 >= width && currentHeight / 2 >= height) {
            currentWidth /= 2;
            currentHeight /= 2;
            current = draw(current, currentWidth, currentHeight, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, width, height, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return out;
    }

    /** Cuts an image into 128x128 tiles. Partial tiles are padded on both sides so the image stays centered. */
    public static Poster split(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int columns = Math.max(1, (width + MAP_SIZE - 1) / MAP_SIZE);
        int rows = Math.max(1, (height + MAP_SIZE - 1) / MAP_SIZE);
        int offsetX = (columns * MAP_SIZE - width) / 2;
        int offsetY = (rows * MAP_SIZE - height) / 2;

        BufferedImage[] tiles = new BufferedImage[columns * rows];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                BufferedImage tile = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = tile.createGraphics();
                graphics.drawImage(image, offsetX - column * MAP_SIZE, offsetY - row * MAP_SIZE, null);
                graphics.dispose();
                tiles[row * columns + column] = tile;
            }
        }
        return new Poster(columns, rows, tiles);
    }
}
