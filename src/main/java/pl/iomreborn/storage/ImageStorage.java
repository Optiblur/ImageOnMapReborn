package pl.iomreborn.storage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Image files of the maps, using the ImageOnMap layout: {@code images/map<id>.png} for every map
 * and {@code images/_<first>-<last>.png} for the optional full-size copy.
 */
public final class ImageStorage {

    private final File imagesDir;
    private volatile File legacyImagesDir;

    public ImageStorage(File imagesDir) {
        this.imagesDir = imagesDir;
    }

    public File getImagesDir() {
        return imagesDir;
    }

    /** Folder of the old plugin, used as a fallback when an image was not copied yet. */
    public void setLegacyImagesDir(File legacyImagesDir) {
        this.legacyImagesDir = legacyImagesDir;
    }

    public File fileFor(int mapId) {
        return new File(imagesDir, "map" + mapId + ".png");
    }

    public File fullImageFile(int firstMapId, int lastMapId) {
        return new File(imagesDir, "_" + firstMapId + "-" + lastMapId + ".png");
    }

    public void write(File target, BufferedImage image) throws IOException {
        if (!imagesDir.isDirectory() && !imagesDir.mkdirs()) {
            throw new IOException("Cannot create directory " + imagesDir);
        }
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        if (!ImageIO.write(image, "png", tmp)) {
            throw new IOException("No PNG writer available");
        }
        MapStorage.moveReplacing(tmp, target);
    }

    /**
     * Reads the image of a map. If it only exists in the old ImageOnMap folder, it is copied first.
     *
     * @return the image, or null if no file exists for this map
     */
    public BufferedImage read(int mapId) throws IOException {
        File file = fileFor(mapId);
        if (!file.isFile()) {
            File legacyDir = legacyImagesDir;
            File legacy = legacyDir == null ? null : new File(legacyDir, file.getName());
            if (legacy == null || !legacy.isFile()) {
                return null;
            }
            try {
                Files.copy(legacy.toPath(), file.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (FileAlreadyExistsException ignored) {
                // copied concurrently
            } catch (IOException ex) {
                return ImageIO.read(legacy);
            }
        }
        return ImageIO.read(file);
    }

    public void delete(int mapId) throws IOException {
        File file = fileFor(mapId);
        MapStorage.retrying(() -> Files.deleteIfExists(file.toPath()));
    }

    public void deleteFullImage(int firstMapId, int lastMapId) throws IOException {
        File file = fullImageFile(firstMapId, lastMapId);
        MapStorage.retrying(() -> Files.deleteIfExists(file.toPath()));
    }
}
