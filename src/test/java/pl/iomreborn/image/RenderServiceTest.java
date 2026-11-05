package pl.iomreborn.image;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import pl.iomreborn.config.PluginConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderServiceTest {

    private static final PluginConfig DEFAULTS = PluginConfig.from(new YamlConfiguration());

    /** Like {@code /tomap <gif>}: the picture keeps its size. */
    private static RenderService.Prepared animate(byte[] gif, List<RenderService.Note> notes) throws Exception {
        return RenderService.animate(gif, (width, height) -> { }, RenderService.AnimationPolicy.ALLOWED, DEFAULTS,
                Dithering.NONE, notes,
                (width, height) -> RenderService.posterSize(width, height, ScalingMode.NONE, 0, 0),
                frame -> frame, maps -> { });
    }

    /** Like {@code /tomap <gif> resize}: any GIF fits on one map, so the map limits never stop it. */
    private static RenderService.Prepared animateOnOneMap(byte[] gif, List<RenderService.Note> notes)
            throws Exception {
        return RenderService.animate(gif, (width, height) -> { }, RenderService.AnimationPolicy.ALLOWED, DEFAULTS,
                Dithering.NONE, notes,
                (width, height) -> RenderService.posterSize(width, height, ScalingMode.CONTAINED, 1, 1),
                frame -> ImageProcessing.resize(frame, ImageProcessing.MAP_SIZE, ImageProcessing.MAP_SIZE,
                        ScalingMode.CONTAINED, false),
                maps -> { });
    }

    @Test
    void gifsBecomeAnimations() throws Exception {
        List<RenderService.Note> notes = new ArrayList<>();
        RenderService.Prepared prepared = animate(GifDecoderTest.disposalGif(), notes);
        assertNotNull(prepared);
        assertNotNull(prepared.animations());
        assertEquals(4, prepared.frameCount());
        assertTrue(notes.isEmpty(), notes.toString());
    }

    /** A GIF of {@code count} frames that all cover the whole picture. */
    private static byte[] frames(int size, int count) {
        int[][] frames = new int[count][];
        Arrays.fill(frames, new int[] {size, size});
        return TestGifs.withFrameSizes(size, size, frames);
    }

    @Test
    void gifsNeedingTooMuchWorkBecomeStillPictures() throws Exception {
        // 130 frames of 2000 x 2000 px on one map: within every other limit, but 520 million pixels to go through.
        List<RenderService.Note> notes = new ArrayList<>();
        long start = System.nanoTime();
        assertNull(animateOnOneMap(frames(2000, 130), notes), "the first frame is used as a still picture");
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(1, notes.size());
        assertEquals("render.animation-too-heavy", notes.get(0).key());
        assertTrue(millis < 2000, "refused before the frames are decoded, took " + millis + " ms");
    }

    @Test
    void gifsJustWithinTheWorkLimitPlay() throws Exception {
        // 120 frames of 2000 x 2000 px on one map: 480 million pixels, just below the limit.
        List<RenderService.Note> notes = new ArrayList<>();
        RenderService.Prepared prepared = animateOnOneMap(frames(2000, 120), notes);
        assertNotNull(prepared);
        assertEquals(1, prepared.tiles().length);
        assertEquals(60, prepared.frameCount(), "max-frames (100) keeps every 2nd frame");
        assertTrue(notes.isEmpty(), notes.toString());
    }

    @Test
    void longGifsWithSmallFramesStillPlay() throws Exception {
        // 2000 frames of 200 x 200 px, 80 million pixels in all.
        List<RenderService.Note> notes = new ArrayList<>();
        RenderService.Prepared prepared = animate(frames(200, 2000), notes);
        assertNotNull(prepared);
        assertNotNull(prepared.animations());
        assertEquals(100, prepared.frameCount(), "max-frames (100) keeps every 20th frame");
        assertTrue(notes.isEmpty(), notes.toString());
    }

    @Test
    void gifsWithFramesClaimingAHugeSizeBecomeStillPictures() throws Exception {
        byte[] gif = TestGifs.withFrameSizes(10, 10, new int[] {10, 10}, new int[] {16384, 16384});
        List<RenderService.Note> notes = new ArrayList<>();
        assertNull(animate(gif, notes), "the first frame is used as a still picture");
        assertEquals(1, notes.size());
        assertEquals("render.animation-too-many-pixels", notes.get(0).key());
        assertArrayEquals(new Object[] {16384, 16384}, notes.get(0).args(), "the player is told the size claimed");
    }
}
