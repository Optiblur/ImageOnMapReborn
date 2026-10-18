package pl.iomreborn.image;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes downloaded bytes into an image. PNG, JPEG, GIF (first frame) and BMP use the JDK readers,
 * WebP uses the bundled TwelveMonkeys reader (never registered globally, so other plugins are not affected).
 * The image size is checked before the pixels are decoded.
 */
public final class ImageDecoder {

    @FunctionalInterface
    public interface SizeCheck {
        void check(int width, int height) throws RenderException;
    }

    private ImageDecoder() {
    }

    public static BufferedImage decode(byte[] data, SizeCheck sizeCheck) throws RenderException {
        if (data == null || data.length < 8) {
            throw new RenderException("error.not-an-image");
        }
        try {
            if (isWebP(data)) {
                ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
                return read(reader, data, sizeCheck);
            }
            try (ImageInputStream probe = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(probe);
                if (!readers.hasNext()) {
                    throw new RenderException("error.not-an-image");
                }
                return read(readers.next(), data, sizeCheck);
            }
        } catch (RenderException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new RenderException("error.not-an-image");
        }
    }

    private static BufferedImage read(ImageReader reader, byte[] data, SizeCheck sizeCheck)
            throws IOException, RenderException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0) {
                throw new RenderException("error.not-an-image");
            }
            sizeCheck.check(width, height);
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw new RenderException("error.not-an-image");
            }
            return ImageProcessing.toArgb(image);
        } finally {
            reader.dispose();
        }
    }

    static boolean isWebP(byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }
}
