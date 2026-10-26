package pl.iomreborn.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalImagesTest {

    @TempDir
    Path temp;

    private LocalImages uploads() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("upload"));
        Files.write(folder.resolve("logo.png"), new byte[] {1, 2, 3});
        Files.createDirectories(folder.resolve("event"));
        Files.write(folder.resolve("event").resolve("Day1.JPG"), new byte[] {4});
        Files.write(folder.resolve("with space.png"), new byte[] {5});
        Files.writeString(folder.resolve("README.txt"), "notes");
        Files.write(temp.resolve("secret.png"), new byte[] {9});
        return new LocalImages(folder.toFile());
    }

    @Test
    void recognizesFileReferences() {
        assertTrue(LocalImages.isReference("file:logo.png"));
        assertTrue(LocalImages.isReference("FILE:logo.png"));
        assertFalse(LocalImages.isReference("https://example.org/file:logo.png"));
        assertFalse(LocalImages.isReference("file"));
        assertFalse(LocalImages.isReference(null));
    }

    @Test
    void findsPicturesInsideTheFolder() throws Exception {
        LocalImages uploads = uploads();
        File logo = uploads.resolve("file:logo.png");
        assertArrayEquals(new byte[] {1, 2, 3}, LocalImages.read(logo, 1024));
        assertEquals("Day1.JPG", uploads.resolve("file:event/Day1.JPG").getName());
        assertEquals("Day1.JPG", uploads.resolve("file:event\\Day1.JPG").getName());
    }

    @Test
    void neverReadsOutsideTheFolder() throws Exception {
        LocalImages uploads = uploads();
        for (String reference : List.of("file:../secret.png", "file:event/../../secret.png", "file:/etc/x.png",
                "file:C:/Windows/x.png", "file:" + temp.resolve("secret.png").toAbsolutePath())) {
            RenderException ex = assertThrows(RenderException.class, () -> uploads.resolve(reference), reference);
            assertEquals("error.invalid-file-name", ex.getKey(), reference);
        }
    }

    @Test
    void onlyAcceptsPictures() throws Exception {
        LocalImages uploads = uploads();
        assertEquals("error.invalid-file-name",
                assertThrows(RenderException.class, () -> uploads.resolve("file:README.txt")).getKey());
        assertEquals("error.invalid-file-name",
                assertThrows(RenderException.class, () -> uploads.resolve("file:")).getKey());
        RenderException missing = assertThrows(RenderException.class, () -> uploads.resolve("file:nope.png"));
        assertEquals("error.file-not-found", missing.getKey());
        assertEquals("nope.png", missing.getArgs()[0]);
    }

    @Test
    void refusesFilesOverTheSizeLimit() throws Exception {
        File logo = uploads().resolve("file:logo.png");
        RenderException ex = assertThrows(RenderException.class, () -> LocalImages.read(logo, 2));
        assertEquals("error.download-too-large", ex.getKey());
    }

    @Test
    void suggestsThePicturesForTabCompletion() throws Exception {
        LocalImages uploads = uploads();
        assertEquals(List.of("event/Day1.JPG", "logo.png", "with space.png"), uploads.names());
        assertEquals(List.of("file:event/Day1.JPG", "file:logo.png"), uploads.suggestions(),
                "names with spaces can't be typed in a command");
        assertTrue(new LocalImages(temp.resolve("missing").toFile()).names().isEmpty());
    }
}
