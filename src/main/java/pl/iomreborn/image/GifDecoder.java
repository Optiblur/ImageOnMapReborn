package pl.iomreborn.image;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Reads the frames of an animated GIF. GIF frames are often only the part of the picture that changed, so every
 * frame is drawn over the previous ones, following the disposal rules of the format, and handed out as a full
 * picture of the GIF's size.
 */
public final class GifDecoder implements AutoCloseable {

    /** GIFs with more frames are cut at this point before anything is decoded. */
    public static final int MAX_DECODED_FRAMES = 2000;

    private static final String IMAGE_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String STREAM_FORMAT = "javax_imageio_gif_stream_1.0";

    enum Disposal { KEEP, RESTORE_BACKGROUND, RESTORE_PREVIOUS }

    record FrameInfo(int x, int y, int width, int height, int delay, Disposal disposal) {
    }

    @FunctionalInterface
    public interface FrameConsumer {
        /**
         * Receives one finished frame. The image object is reused for the next frame: copy what has to be kept.
         */
        void accept(int index, BufferedImage frame, int delayMillis) throws Exception;
    }

    private final ImageReader reader;
    private final ImageInputStream input;
    private final int width;
    private final int height;
    private final FrameInfo[] frames;

    private GifDecoder(ImageReader reader, ImageInputStream input, int width, int height, FrameInfo[] frames) {
        this.reader = reader;
        this.input = input;
        this.width = width;
        this.height = height;
        this.frames = frames;
    }

    public static boolean isGif(byte[] data) {
        return data != null && data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    /**
     * Opens a GIF and reads the description of its frames (not their pixels yet).
     *
     * @return the decoder, or null when the GIF has a single frame
     * @throws IOException when the file cannot be read as a GIF
     */
    public static GifDecoder open(byte[] data) throws IOException {
        if (!isGif(data)) {
            return null;
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data));
        boolean keep = false;
        try {
            reader.setInput(input, false, false);
            int count;
            try {
                count = reader.getNumImages(true);
            } catch (IOException | RuntimeException ex) {
                count = countReadable(reader);
            }
            count = Math.min(count, MAX_DECODED_FRAMES);
            if (count < 2) {
                return null;
            }
            FrameInfo[] infos = new FrameInfo[count];
            int readable = 0;
            for (int i = 0; i < count; i++) {
                try {
                    infos[i] = frameInfo(reader.getImageMetadata(i));
                } catch (IOException | RuntimeException ex) {
                    break;
                }
                readable++;
            }
            if (readable < 2) {
                return null;
            }
            if (readable < count) {
                FrameInfo[] shorter = new FrameInfo[readable];
                System.arraycopy(infos, 0, shorter, 0, readable);
                infos = shorter;
            }

            int[] screen = logicalScreen(reader.getStreamMetadata());
            int w = screen[0];
            int h = screen[1];
            if (w <= 0 || h <= 0) {
                for (FrameInfo info : infos) {
                    w = Math.max(w, info.x() + info.width());
                    h = Math.max(h, info.y() + info.height());
                }
            }
            if (w <= 0 || h <= 0) {
                return null;
            }
            keep = true;
            return new GifDecoder(reader, input, w, h, infos);
        } finally {
            if (!keep) {
                reader.dispose();
                input.close();
            }
        }
    }

    /** Fallback for broken files: counts the frames that can be located one by one. */
    private static int countReadable(ImageReader reader) {
        int count = 0;
        while (count < MAX_DECODED_FRAMES) {
            try {
                reader.getImageMetadata(count);
            } catch (IOException | RuntimeException ex) {
                break;
            }
            count++;
        }
        return count;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int frameCount() {
        return frames.length;
    }

    /** How long a frame is shown, in milliseconds (with the browser rule for 0 and 10 ms delays). */
    public int delay(int index) {
        return frames[index].delay();
    }

    /**
     * Decodes the frames in order and gives each finished picture to the consumer. A frame that cannot be
     * decoded ends the animation early (if at least two frames were read).
     *
     * @return the number of frames given to the consumer
     */
    public int forEachFrame(FrameConsumer consumer) throws Exception {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] saved = null;
        FrameInfo previous = null;
        int delivered = 0;
        for (int i = 0; i < frames.length; i++) {
            if (previous != null) {
                if (previous.disposal() == Disposal.RESTORE_BACKGROUND) {
                    Graphics2D graphics = canvas.createGraphics();
                    graphics.setComposite(AlphaComposite.Clear);
                    graphics.fillRect(previous.x(), previous.y(), previous.width(), previous.height());
                    graphics.dispose();
                } else if (previous.disposal() == Disposal.RESTORE_PREVIOUS && saved != null) {
                    canvas.setRGB(0, 0, width, height, saved, 0, width);
                }
            }
            FrameInfo info = frames[i];
            if (info.disposal() == Disposal.RESTORE_PREVIOUS) {
                saved = canvas.getRGB(0, 0, width, height, saved, 0, width);
            }
            BufferedImage frame;
            try {
                frame = reader.read(i);
            } catch (IOException | RuntimeException ex) {
                if (delivered >= 2) {
                    break;
                }
                throw new IOException("Cannot decode frame " + i + " of the GIF", ex);
            }
            Graphics2D graphics = canvas.createGraphics();
            graphics.drawImage(frame, info.x(), info.y(), null);
            graphics.dispose();
            consumer.accept(i, canvas, info.delay());
            delivered++;
            previous = info;
        }
        return delivered;
    }

    @Override
    public void close() {
        reader.dispose();
        try {
            input.close();
        } catch (IOException ignored) {
            // memory stream
        }
    }

    static FrameInfo frameInfo(IIOMetadata metadata) {
        Node root = metadata.getAsTree(IMAGE_FORMAT);
        int x = 0;
        int y = 0;
        int w = 0;
        int h = 0;
        int delay = 0;
        Disposal disposal = Disposal.KEEP;
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            NamedNodeMap attributes = node.getAttributes();
            if ("ImageDescriptor".equals(node.getNodeName())) {
                x = intAttribute(attributes, "imageLeftPosition");
                y = intAttribute(attributes, "imageTopPosition");
                w = intAttribute(attributes, "imageWidth");
                h = intAttribute(attributes, "imageHeight");
            } else if ("GraphicControlExtension".equals(node.getNodeName())) {
                delay = intAttribute(attributes, "delayTime");
                Node method = attributes.getNamedItem("disposalMethod");
                String name = method == null ? "" : method.getNodeValue();
                if ("restoreToBackgroundColor".equals(name)) {
                    disposal = Disposal.RESTORE_BACKGROUND;
                } else if ("restoreToPrevious".equals(name)) {
                    disposal = Disposal.RESTORE_PREVIOUS;
                }
            }
        }
        return new FrameInfo(x, y, w, h, delayMillis(delay), disposal);
    }

    /** GIF delays are in 1/100 s; like web browsers, 0 and 1 (often used by mistake) mean 100 ms. */
    static int delayMillis(int centiseconds) {
        return centiseconds <= 1 ? 100 : Math.min(Animation.MAX_DELAY, centiseconds * 10);
    }

    private static int[] logicalScreen(IIOMetadata metadata) {
        if (metadata == null) {
            return new int[] {0, 0};
        }
        try {
            Node root = metadata.getAsTree(STREAM_FORMAT);
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if ("LogicalScreenDescriptor".equals(node.getNodeName())) {
                    NamedNodeMap attributes = node.getAttributes();
                    return new int[] {intAttribute(attributes, "logicalScreenWidth"),
                            intAttribute(attributes, "logicalScreenHeight")};
                }
            }
        } catch (RuntimeException ignored) {
            // no usable stream metadata
        }
        return new int[] {0, 0};
    }

    private static int intAttribute(NamedNodeMap attributes, String name) {
        Node node = attributes == null ? null : attributes.getNamedItem(name);
        if (node == null) {
            return 0;
        }
        try {
            return Integer.parseInt(node.getNodeValue().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
