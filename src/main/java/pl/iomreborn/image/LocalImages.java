package pl.iomreborn.image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Pictures the admins put in {@code plugins/ImageOnMapReborn/upload/}, used with {@code /tomap file:<name>}, so a
 * picture does not have to be uploaded to a website first. Only files inside that folder can be read.
 */
public final class LocalImages {

    public static final String PREFIX = "file:";
    static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final int MAX_DEPTH = 3;
    private static final int MAX_SUGGESTIONS = 100;

    private final File folder;

    public LocalImages(File folder) {
        this.folder = folder;
    }

    public File folder() {
        return folder;
    }

    /** True for {@code file:<name>}, as opposed to a link. */
    public static boolean isReference(String value) {
        return value != null && value.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /**
     * The file a {@code file:<name>} reference points to. The name is relative to the upload folder (subfolders
     * are written with {@code /}) and must end with a picture extension.
     */
    public File resolve(String reference) throws RenderException {
        String name = reference.substring(PREFIX.length()).trim().replace('\\', '/');
        if (name.isEmpty() || name.startsWith("/") || name.indexOf(':') >= 0 || !hasImageExtension(name)) {
            throw new RenderException("error.invalid-file-name");
        }
        File file = new File(folder, name);
        try {
            String inside = folder.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(inside)) {
                throw new RenderException("error.invalid-file-name");
            }
        } catch (IOException ex) {
            throw new RenderException("error.invalid-file-name");
        }
        if (!file.isFile()) {
            throw new RenderException("error.file-not-found", name);
        }
        return file;
    }

    /** Reads a picture of the upload folder, refusing files bigger than the download limit. */
    public static byte[] read(File file, long maxBytes) throws RenderException {
        if (file.length() > maxBytes) {
            throw new RenderException("error.download-too-large", maxBytes / (1024 * 1024));
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException ex) {
            throw new RenderException("error.file-unreadable", file.getName());
        }
    }

    /** The pictures in the folder as names for {@code file:}, sorted ({@code logo.png}, {@code event/day1.jpg}). */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        Path root = folder.toPath();
        if (!Files.isDirectory(root)) {
            return names;
        }
        try (Stream<Path> files = Files.walk(root, MAX_DEPTH)) {
            files.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(LocalImages::hasImageExtension)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(names::add);
        } catch (IOException | RuntimeException ex) {
            // an unreadable folder simply has no suggestions
        }
        return names;
    }

    /**
     * {@code file:<name>} suggestions for tab completion. Names with spaces are left out, as a command argument
     * can't contain them.
     */
    public List<String> suggestions() {
        List<String> out = new ArrayList<>();
        for (String name : names()) {
            if (name.indexOf(' ') < 0) {
                out.add(PREFIX + name);
                if (out.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }
        }
        return out;
    }

    static boolean hasImageExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
