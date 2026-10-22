package pl.iomreborn.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.iomreborn.image.Animation;
import pl.iomreborn.image.MapColors;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageStorageTest {

    private static byte[] randomFrame(long seed) {
        byte[] frame = new byte[MapColors.PIXELS];
        new Random(seed).nextBytes(frame);
        return frame;
    }

    @Test
    void animationsSurviveARestart(@TempDir File dir) throws Exception {
        ImageStorage storage = new ImageStorage(new File(dir, "images"));
        byte[] a = randomFrame(1);
        byte[] b = randomFrame(2);
        Animation animation = new Animation(new byte[][] {a, b, a, b, b}, new int[] {100, 200, 300, 400, 500});
        assertFalse(storage.hasAnimation(12));
        assertNull(storage.readAnimation(12));

        storage.writeAnimation(12, animation);
        assertTrue(storage.hasAnimation(12));
        assertEquals("map12.anim", storage.animationFile(12).getName());
        // Repeated frames are written once: two frames of 16 KB, well compressed or not.
        assertTrue(storage.animationFile(12).length() < 3L * MapColors.PIXELS);

        Animation read = storage.readAnimation(12);
        assertNotNull(read);
        assertEquals(5, read.frameCount());
        assertEquals(2, read.uniqueFrameCount());
        for (int i = 0; i < 5; i++) {
            assertArrayEquals(animation.frame(i), read.frame(i), "frame " + i);
            assertEquals(animation.delay(i), read.delay(i), "delay " + i);
        }
        assertSame(read.frame(0), read.frame(2));
    }

    @Test
    void deletingAMapRemovesItsFrames(@TempDir File dir) throws Exception {
        ImageStorage storage = new ImageStorage(new File(dir, "images"));
        storage.write(storage.fileFor(5), new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB));
        storage.writeAnimation(5, new Animation(new byte[][] {randomFrame(3), randomFrame(4)}, new int[] {50, 50}));
        storage.delete(5);
        assertFalse(storage.fileFor(5).exists());
        assertFalse(storage.animationFile(5).exists());

        storage.writeAnimation(6, new Animation(new byte[][] {randomFrame(3), randomFrame(4)}, new int[] {50, 50}));
        storage.deleteAnimation(6);
        assertFalse(storage.hasAnimation(6));
        storage.deleteAnimation(6); // nothing to delete is fine
    }

    @Test
    void damagedFilesAreRejected(@TempDir File dir) throws Exception {
        ImageStorage storage = new ImageStorage(new File(dir, "images"));
        storage.writeAnimation(7, new Animation(new byte[][] {randomFrame(5), randomFrame(6)}, new int[] {50, 50}));
        File file = storage.animationFile(7);
        byte[] data = Files.readAllBytes(file.toPath());
        Files.write(file.toPath(), java.util.Arrays.copyOf(data, data.length / 2));
        assertThrows(IOException.class, () -> storage.readAnimation(7));

        Files.write(file.toPath(), "not an animation".getBytes());
        assertThrows(IOException.class, () -> storage.readAnimation(7));
    }

    @Test
    void imageOnMapStillSeesAPicture(@TempDir File dir) throws Exception {
        // The animation file sits next to the PNG of the first frame, which old plugins keep using.
        ImageStorage storage = new ImageStorage(new File(dir, "images"));
        storage.write(storage.fileFor(9), new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB));
        storage.writeAnimation(9, new Animation(new byte[][] {randomFrame(7), randomFrame(8)}, new int[] {50, 50}));
        assertNotNull(storage.read(9));
        assertEquals("map9.png", storage.fileFor(9).getName());
    }
}
