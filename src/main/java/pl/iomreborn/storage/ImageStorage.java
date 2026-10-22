package pl.iomreborn.storage;

import pl.iomreborn.image.Animation;
import pl.iomreborn.image.MapColors;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Image files of the maps, using the ImageOnMap layout: {@code images/map<id>.png} for every map
 * and {@code images/_<first>-<last>.png} for the optional full-size copy. Animated maps also have an
 * {@code images/map<id>.anim} file with all their frames.
 */
public final class ImageStorage {

    public static final String ANIMATION_EXTENSION = ".anim";
    private static final int ANIMATION_MAGIC = 0x494F4D41; // "IOMA"
    private static final int ANIMATION_VERSION = 1;
    private static final int MAX_STORED_FRAMES = 10_000;

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
        deleteAnimation(mapId);
    }

    /**
     * Frames of an animated map. The {@code .png} file next to it keeps the first frame, so the map still shows
     * a picture with plugins that only know the ImageOnMap format.
     */
    public File animationFile(int mapId) {
        return new File(imagesDir, "map" + mapId + ANIMATION_EXTENSION);
    }

    public boolean hasAnimation(int mapId) {
        return animationFile(mapId).isFile();
    }

    /**
     * File format (GZIP compressed): magic "IOMA", version, frame count, the delay of every frame in milliseconds,
     * then for every frame either {@code 1} and 128x128 map colors, or {@code 0} and the index of an earlier
     * frame with the same colors.
     */
    public void writeAnimation(int mapId, Animation animation) throws IOException {
        if (!imagesDir.isDirectory() && !imagesDir.mkdirs()) {
            throw new IOException("Cannot create directory " + imagesDir);
        }
        File target = animationFile(mapId);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        // The file stream is its own resource: it is closed even if the GZIP stream cannot be created.
        try (FileOutputStream file = new FileOutputStream(tmp);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                     new GZIPOutputStream(file, 1 << 16)))) {
            out.writeInt(ANIMATION_MAGIC);
            out.writeInt(ANIMATION_VERSION);
            int count = animation.frameCount();
            out.writeInt(count);
            for (int i = 0; i < count; i++) {
                out.writeInt(animation.delay(i));
            }
            Map<byte[], Integer> written = new IdentityHashMap<>();
            for (int i = 0; i < count; i++) {
                byte[] frame = animation.frame(i);
                Integer earlier = written.get(frame);
                if (earlier != null) {
                    out.writeByte(0);
                    out.writeInt(earlier);
                } else {
                    out.writeByte(1);
                    out.write(frame);
                    written.put(frame, i);
                }
            }
        }
        MapStorage.moveReplacing(tmp, target);
    }

    /** Reads the frames of an animated map, or returns null if the map is not animated. */
    public Animation readAnimation(int mapId) throws IOException {
        File file = animationFile(mapId);
        if (!file.isFile()) {
            return null;
        }
        // The file stream is its own resource: a damaged file (not GZIP) must not stay open and locked.
        try (FileInputStream raw = new FileInputStream(file);
             DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16)))) {
            return readAnimation(in);
        }
    }

    static Animation readAnimation(DataInputStream in) throws IOException {
        if (in.readInt() != ANIMATION_MAGIC) {
            throw new IOException("Not an animation file");
        }
        int version = in.readInt();
        if (version != ANIMATION_VERSION) {
            throw new IOException("Unsupported animation file version " + version);
        }
        int count = in.readInt();
        if (count < 1 || count > MAX_STORED_FRAMES) {
            throw new IOException("Invalid frame count " + count);
        }
        int[] delays = new int[count];
        for (int i = 0; i < count; i++) {
            delays[i] = in.readInt();
        }
        byte[][] frames = new byte[count][];
        for (int i = 0; i < count; i++) {
            int kind = in.readUnsignedByte();
            if (kind == 0) {
                int earlier = in.readInt();
                if (earlier < 0 || earlier >= i) {
                    throw new IOException("Invalid frame reference " + earlier + " in frame " + i);
                }
                frames[i] = frames[earlier];
            } else if (kind == 1) {
                byte[] frame = new byte[MapColors.PIXELS];
                in.readFully(frame);
                frames[i] = frame;
            } else {
                throw new IOException("Invalid frame type " + kind);
            }
        }
        try {
            return new Animation(frames, delays);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid animation file", ex);
        }
    }

    public void deleteAnimation(int mapId) throws IOException {
        File file = animationFile(mapId);
        MapStorage.retrying(() -> Files.deleteIfExists(file.toPath()));
    }

    public void deleteFullImage(int firstMapId, int lastMapId) throws IOException {
        File file = fullImageFile(firstMapId, lastMapId);
        MapStorage.retrying(() -> Files.deleteIfExists(file.toPath()));
    }
}
