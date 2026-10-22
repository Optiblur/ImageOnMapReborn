package pl.iomreborn.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GifDecoderTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int YELLOW = 0xFFFFFF00;

    /**
     * Frame 0: red background. Frame 1: blue square, cleared afterwards (restore to background).
     * Frame 2: green square, undone afterwards (restore to previous). Frame 3: small yellow square.
     */
    static byte[] disposalGif() throws Exception {
        return TestGifs.write(List.of(
                TestGifs.Frame.full(TestGifs.filled(40, 30, Color.RED), 10),
                new TestGifs.Frame(TestGifs.filled(10, 10, Color.BLUE), 5, 5, 0, "restoreToBackgroundColor"),
                new TestGifs.Frame(TestGifs.filled(10, 10, Color.GREEN), 20, 10, 5, "restoreToPrevious"),
                new TestGifs.Frame(TestGifs.filled(4, 4, Color.YELLOW), 0, 0, 2, "none")));
    }

    private record Captured(BufferedImage image, int delay) {
    }

    private static List<Captured> decodeAll(byte[] gif) throws Exception {
        List<Captured> frames = new ArrayList<>();
        try (GifDecoder decoder = GifDecoder.open(gif)) {
            assertNotNull(decoder);
            int delivered = decoder.forEachFrame((index, frame, delay) -> {
                assertEquals(frames.size(), index);
                frames.add(new Captured(ImageProcessing.copy(frame), delay));
            });
            assertEquals(frames.size(), delivered);
        }
        return frames;
    }

    @Test
    void recognizesGifs() throws Exception {
        assertTrue(GifDecoder.isGif(disposalGif()));
        assertFalse(GifDecoder.isGif("GIF".getBytes()));
        assertFalse(GifDecoder.isGif("<html>".getBytes()));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(TestGifs.filled(4, 4, Color.RED), "png", png);
        assertFalse(GifDecoder.isGif(png.toByteArray()));
        assertNull(GifDecoder.open(png.toByteArray()));
    }

    @Test
    void readsTheFrameDescriptions() throws Exception {
        try (GifDecoder decoder = GifDecoder.open(disposalGif())) {
            assertNotNull(decoder);
            assertEquals(40, decoder.width());
            assertEquals(30, decoder.height());
            assertEquals(4, decoder.frameCount());
            assertEquals(100, decoder.delay(0));
            assertEquals(100, decoder.delay(1), "a delay of 0 is shown for 100 ms, like in browsers");
            assertEquals(50, decoder.delay(2));
            assertEquals(20, decoder.delay(3));
        }
    }

    @Test
    void drawsFramesOverEachOtherFollowingTheDisposalRules() throws Exception {
        List<Captured> frames = decodeAll(disposalGif());
        assertEquals(4, frames.size());

        BufferedImage first = frames.get(0).image();
        assertEquals(40, first.getWidth());
        assertEquals(RED, first.getRGB(0, 0));
        assertEquals(RED, first.getRGB(39, 29));

        BufferedImage second = frames.get(1).image();
        assertEquals(BLUE, second.getRGB(5, 5));
        assertEquals(BLUE, second.getRGB(14, 14));
        assertEquals(RED, second.getRGB(15, 15), "outside the small frame the previous picture stays");
        assertEquals(RED, second.getRGB(30, 20));

        BufferedImage third = frames.get(2).image();
        assertEquals(0, third.getRGB(8, 8) >>> 24, "the blue square was cleared to transparent");
        assertEquals(GREEN, third.getRGB(20, 10));
        assertEquals(RED, third.getRGB(35, 25));

        BufferedImage fourth = frames.get(3).image();
        assertEquals(RED, fourth.getRGB(25, 15), "the green square was undone (restore to previous)");
        assertEquals(0, fourth.getRGB(8, 8) >>> 24);
        assertEquals(YELLOW, fourth.getRGB(1, 1));

        assertEquals(List.of(100, 100, 50, 20), frames.stream().map(Captured::delay).toList());
    }

    @Test
    void singleFrameGifIsNotAnAnimation() throws Exception {
        byte[] gif = TestGifs.write(List.of(TestGifs.Frame.full(TestGifs.filled(8, 8, Color.BLUE), 10)));
        assertTrue(GifDecoder.isGif(gif));
        assertNull(GifDecoder.open(gif));
    }

    @Test
    void stillImageDecoderStillReadsTheFirstFrame() throws Exception {
        BufferedImage still = ImageDecoder.decode(disposalGif(), (w, h) -> { });
        assertEquals(40, still.getWidth());
        assertEquals(RED, still.getRGB(10, 10));
    }

    @Test
    void cutFilesKeepTheReadableFrames() throws Exception {
        byte[] gif = disposalGif();
        // Remove the trailer and part of the last frame: the first frames must still play.
        byte[] cut = java.util.Arrays.copyOf(gif, gif.length - 12);
        try (GifDecoder decoder = GifDecoder.open(cut)) {
            if (decoder == null) {
                return; // the JDK reader may refuse the file outright, then it is used as a still picture
            }
            int[] count = new int[1];
            decoder.forEachFrame((index, frame, delay) -> count[0]++);
            assertTrue(count[0] >= 2, "got " + count[0]);
        }
    }
}
