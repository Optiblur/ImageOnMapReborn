package pl.iomreborn.image;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import pl.iomreborn.config.PluginConfig;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTest {

    private static byte[] frame(int value) {
        byte[] frame = new byte[MapColors.PIXELS];
        java.util.Arrays.fill(frame, (byte) value);
        return frame;
    }

    @Test
    void picksTheFrameOfTheMomentAndLoops() {
        byte[] a = frame(4);
        byte[] b = frame(8);
        byte[] c = frame(12);
        Animation animation = new Animation(new byte[][] {a, b, c}, new int[] {100, 50, 250});
        assertEquals(400, animation.duration());
        assertSame(a, animation.frameAt(0));
        assertSame(a, animation.frameAt(99));
        assertSame(b, animation.frameAt(100));
        assertSame(b, animation.frameAt(149));
        assertSame(c, animation.frameAt(150));
        assertSame(c, animation.frameAt(399));
        assertSame(a, animation.frameAt(400));
        assertSame(b, animation.frameAt(400L * 1_000_000 + 120));
        assertSame(c, animation.frameAt(-1), "negative times loop too");
    }

    @Test
    void sharesRepeatedFrames() {
        byte[] a = frame(4);
        byte[] b = frame(8);
        Animation animation = new Animation(new byte[][] {a, b, a, a}, new int[] {100, 100, 100, 100});
        assertEquals(4, animation.frameCount());
        assertEquals(2, animation.uniqueFrameCount());
        assertEquals(2L * MapColors.PIXELS, animation.memoryBytes());
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> new Animation(new byte[0][], new int[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new Animation(new byte[][] {frame(4)}, new int[] {100, 100}));
        assertThrows(IllegalArgumentException.class,
                () -> new Animation(new byte[][] {new byte[10]}, new int[] {100}));
        Animation clamped = new Animation(new byte[][] {frame(4), frame(5)}, new int[] {1, 999_999});
        assertEquals(Animation.MIN_DELAY, clamped.delay(0));
        assertEquals(Animation.MAX_DELAY, clamped.delay(1));
    }

    // --- The whole GIF to animated maps pipeline (what /tomap and /maptool update run in the background) ---

    private static PluginConfig config(String yaml) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return PluginConfig.from(configuration);
    }

    /** A 256x128 GIF: the left half stays blue, a white square moves over the right half. */
    private static byte[] movingSquareGif(int frames) throws Exception {
        List<TestGifs.Frame> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            BufferedImage image = new BufferedImage(256, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 256, 128);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(128 + (i * 16) % 96, 40, 32, 32);
            graphics.dispose();
            list.add(TestGifs.Frame.full(image, 10));
        }
        return TestGifs.write(list);
    }

    private static RenderService.Prepared animate(byte[] gif, PluginConfig config,
                                                  RenderService.AnimationPolicy policy,
                                                  List<RenderService.Note> notes) throws Exception {
        return RenderService.animate(gif, (w, h) -> { }, policy, config, Dithering.FLOYD_STEINBERG, notes,
                (w, h) -> RenderService.posterSize(w, h, ScalingMode.NONE, 0, 0), frame -> frame, maps -> { });
    }

    @Test
    void turnsAGifIntoAnimatedMaps() throws Exception {
        List<RenderService.Note> notes = new ArrayList<>();
        RenderService.Prepared prepared = animate(movingSquareGif(6), config(""),
                RenderService.AnimationPolicy.ALLOWED, notes);
        assertNotNull(prepared);
        assertTrue(notes.isEmpty());
        assertEquals(2, prepared.columns());
        assertEquals(1, prepared.rows());
        assertEquals(2, prepared.animations().length);
        assertEquals(6, prepared.frameCount());

        Animation left = prepared.animations()[0];
        Animation right = prepared.animations()[1];
        assertEquals(1, left.uniqueFrameCount(), "the still half is stored once");
        assertEquals(6, right.uniqueFrameCount());
        assertEquals(600, right.duration());
        assertNotSame(right.frame(0), right.frame(1));

        // The still pictures (PNG files) show the first frame with the exact map colors.
        assertArrayEquals(right.frame(0), prepared.pixels()[1]);
        assertArrayEquals(right.frame(0), MapColors.toMapBytes(prepared.tiles()[1]));
    }

    @Test
    void longGifsKeepTheirLengthWithFewerFrames() throws Exception {
        PluginConfig config = config("animated-gifs:\n  max-frames: 4\n");
        RenderService.Prepared prepared = animate(movingSquareGif(10), config,
                RenderService.AnimationPolicy.ALLOWED, new ArrayList<>());
        assertNotNull(prepared);
        Animation right = prepared.animations()[1];
        // 10 frames, every 3rd is kept: 4 frames of 300, 300, 300 and 100 ms (the whole second is kept).
        assertEquals(4, right.frameCount());
        assertEquals(1000, right.duration());
        assertEquals(300, right.delay(0));
        assertEquals(100, right.delay(3));
    }

    @Test
    void tooBigOrNotAllowedAnimationsBecomeStillPictures() throws Exception {
        List<RenderService.Note> notes = new ArrayList<>();
        assertNull(animate(movingSquareGif(3), config("animated-gifs:\n  max-maps: 1\n"),
                RenderService.AnimationPolicy.ALLOWED, notes));
        assertEquals("render.animation-too-big", notes.get(0).key());
        assertEquals(List.of(2, 1, 1), List.of(notes.get(0).args()));

        notes.clear();
        assertNull(animate(movingSquareGif(3), config(""), RenderService.AnimationPolicy.NOT_ALLOWED, notes));
        assertEquals("render.animation-not-allowed", notes.get(0).key());

        notes.clear();
        byte[] still = TestGifs.write(List.of(TestGifs.Frame.full(TestGifs.filled(16, 16, Color.RED), 10)));
        assertNull(animate(still, config(""), RenderService.AnimationPolicy.ALLOWED, notes));
        assertTrue(notes.isEmpty(), "a GIF with one frame is simply a picture");
    }

    @Test
    void sizeAndLimitChecksStillApply() throws Exception {
        RenderException tooWide = assertThrows(RenderException.class, () -> RenderService.animate(movingSquareGif(3),
                (w, h) -> {
                    throw new RenderException("error.image-too-wide", 100);
                }, RenderService.AnimationPolicy.ALLOWED, config(""), Dithering.NONE, new ArrayList<>(),
                (w, h) -> new int[] {2, 1}, frame -> frame, maps -> { }));
        assertEquals("error.image-too-wide", tooWide.getKey());

        RenderException limit = assertThrows(RenderException.class, () -> RenderService.animate(movingSquareGif(3),
                (w, h) -> { }, RenderService.AnimationPolicy.ALLOWED, config(""), Dithering.NONE, new ArrayList<>(),
                (w, h) -> new int[] {2, 1}, frame -> frame, maps -> {
                    assertEquals(2, maps);
                    throw new RenderException("error.limit-player-maps", 1);
                }));
        assertEquals("error.limit-player-maps", limit.getKey());
    }

    @Test
    void readsTheNewOptions() throws Exception {
        PluginConfig defaults = config("");
        assertEquals(Dithering.FLOYD_STEINBERG, defaults.dithering());
        assertTrue(defaults.animationsEnabled());
        assertEquals(100, defaults.animationMaxFrames());
        assertEquals(16, defaults.animationMaxMaps());
        assertEquals(10, defaults.animationMaxFps());
        assertTrue(defaults.checkForUpdates());

        PluginConfig custom = config("dithering: false\nanimated-gifs:\n  enabled: false\n  max-frames: 1\n"
                + "  max-maps: 0\n  max-fps: 99\ncheck-for-updates: false\n");
        assertEquals(Dithering.NONE, custom.dithering());
        assertEquals(false, custom.animationsEnabled());
        assertEquals(2, custom.animationMaxFrames());
        assertEquals(1, custom.animationMaxMaps());
        assertEquals(20, custom.animationMaxFps());
        assertEquals(false, custom.checkForUpdates());
    }
}
