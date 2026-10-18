package pl.iomreborn.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDecoderTest {

    /** 1x1 WebP images commonly used for browser feature detection. */
    private static final String WEBP_LOSSY = "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA";
    private static final String WEBP_LOSSLESS = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

    private static byte[] encode(String format, int width, int height) throws Exception {
        int type = format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out), format);
        return out.toByteArray();
    }

    @Test
    void decodesCommonFormats() throws Exception {
        for (String format : new String[] {"png", "jpg", "gif", "bmp"}) {
            BufferedImage image = ImageDecoder.decode(encode(format, 30, 20), (w, h) -> { });
            assertEquals(30, image.getWidth(), format);
            assertEquals(20, image.getHeight(), format);
            assertEquals(BufferedImage.TYPE_INT_ARGB, image.getType(), format);
        }
    }

    @Test
    void decodesWebP() throws Exception {
        for (String sample : new String[] {WEBP_LOSSY, WEBP_LOSSLESS}) {
            byte[] data = Base64.getDecoder().decode(sample);
            assertTrue(ImageDecoder.isWebP(data));
            BufferedImage image = ImageDecoder.decode(data, (w, h) -> { });
            assertEquals(1, image.getWidth());
            assertEquals(1, image.getHeight());
        }
    }

    @Test
    void checksTheSizeBeforeDecoding() throws Exception {
        byte[] png = encode("png", 300, 200);
        RenderException ex = assertThrows(RenderException.class, () -> ImageDecoder.decode(png, (w, h) -> {
            assertEquals(300, w);
            assertEquals(200, h);
            throw new RenderException("error.image-too-wide", 100);
        }));
        assertEquals("error.image-too-wide", ex.getKey());
    }

    @Test
    void rejectsGarbage() {
        RenderException ex = assertThrows(RenderException.class,
                () -> ImageDecoder.decode("<html><body>not an image</body></html>".getBytes(), (w, h) -> { }));
        assertEquals("error.not-an-image", ex.getKey());
    }
}
