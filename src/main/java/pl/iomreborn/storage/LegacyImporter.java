package pl.iomreborn.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.map.MapManager;
import pl.iomreborn.map.PlayerMapStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Imports the data of the original ImageOnMap plugin ({@code plugins/ImageOnMap}): player stores, image files
 * and the shared config options. The old folder is only read, never modified, so it stays as a backup.
 * The import can be repeated: images whose Minecraft maps are already known are skipped.
 */
public final class LegacyImporter {

    public static final String MARKER_FILE = "legacy-import.yml";
    private static final Pattern IMAGE_FILE = Pattern.compile("^(map-?\\d+|_-?\\d+--?\\d+)\\.png$");

    /** Outcome of an import. */
    public record Result(boolean sourceFound, int storesRead, int imagesImported, int mapsImported,
                         int imagesSkipped, int filesCopied, int errors, boolean configImported) {
    }

    private final ImageOnMapReborn plugin;
    private final Logger logger;

    public LegacyImporter(ImageOnMapReborn plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public File sourceFolder() {
        return new File(plugin.getDataFolder().getParentFile(), plugin.settings().legacyImportFolder());
    }

    private File markerFile() {
        return new File(plugin.getDataFolder(), MARKER_FILE);
    }

    private static boolean hasLegacyData(File folder) {
        return new File(folder, "maps").isDirectory() || new File(folder, "images").isDirectory();
    }

    /** First start: imports everything (including config options) once, if the old folder exists. */
    public void autoImportIfNeeded() {
        if (!plugin.settings().legacyImportEnabled() || markerFile().exists()) {
            return;
        }
        File source = sourceFolder();
        if (!hasLegacyData(source)) {
            return;
        }
        logger.info("Found ImageOnMap data in " + source.getPath() + ", importing it (the old folder is not modified)...");
        Result result = run(source, true);
        logger.info(String.format(Locale.ROOT,
                "ImageOnMap import done: %d player files, %d images (%d Minecraft maps) imported, %d already present, "
                        + "%d image files copied, %d errors%s.",
                result.storesRead(), result.imagesImported(), result.mapsImported(), result.imagesSkipped(),
                result.filesCopied(), result.errors(), result.configImported() ? ", config options copied" : ""));
    }

    public Result run(File source, boolean importConfig) {
        if (!hasLegacyData(source)) {
            return new Result(false, 0, 0, 0, 0, 0, 0, false);
        }
        boolean configImported = importConfig && importConfig(new File(source, "config.yml"));

        MapManager maps = plugin.mapManager();
        int storesRead = 0;
        int imagesImported = 0;
        int mapsImported = 0;
        int skipped = 0;
        int errors = 0;

        File[] storeFiles = new File(source, "maps").listFiles((dir, name) -> MapStorage.uuidFromFileName(name) != null);
        if (storeFiles != null) {
            for (File file : storeFiles) {
                UUID owner = MapStorage.uuidFromFileName(file.getName());
                try {
                    PlayerMapStore store = MapStorage.read(file, owner, maps.defaultName(), logger);
                    storesRead++;
                    int[] merged = maps.merge(store);
                    imagesImported += merged[0];
                    mapsImported += merged[1];
                    skipped += merged[2];
                    if (!store.getPreservedEntries().isEmpty()) {
                        errors += store.getPreservedEntries().size();
                    }
                } catch (Exception ex) {
                    errors++;
                    logger.log(Level.WARNING, "Cannot import " + file.getPath(), ex);
                }
            }
        }

        int filesCopied = 0;
        File targetDir = plugin.imageStorage().getImagesDir();
        File[] imageFiles = new File(source, "images").listFiles((dir, name) -> IMAGE_FILE.matcher(name).matches());
        if (imageFiles != null) {
            for (File file : imageFiles) {
                File target = new File(targetDir, file.getName());
                if (target.exists()) {
                    continue;
                }
                try {
                    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    filesCopied++;
                } catch (FileAlreadyExistsException ignored) {
                    // already there
                } catch (IOException ex) {
                    errors++;
                    logger.log(Level.WARNING, "Cannot copy " + file.getPath(), ex);
                }
            }
        }

        maps.saveAllNow();
        Result result = new Result(true, storesRead, imagesImported, mapsImported, skipped, filesCopied, errors,
                configImported);
        if (!markerFile().exists()) {
            writeMarker(source, result);
        }
        return result;
    }

    /** Copies the options both plugins share (limits, size limits, whitelist...). */
    private boolean importConfig(File oldConfigFile) {
        if (!oldConfigFile.isFile()) {
            return false;
        }
        YamlConfiguration old = YamlConfiguration.loadConfiguration(oldConfigFile);
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;
        changed |= copy(old, "map-global-limit", config, "map-global-limit");
        changed |= copy(old, "Limit-map-by-server", config, "map-global-limit");
        changed |= copy(old, "map-player-limit", config, "map-player-limit");
        changed |= copy(old, "Limit-map-by-player", config, "map-player-limit");
        changed |= copy(old, "limit-map-size-x", config, "limit-map-size-x");
        changed |= copy(old, "limit-map-size-y", config, "limit-map-size-y");
        changed |= copy(old, "save-full-image", config, "save-full-image");
        changed |= copy(old, "check-for-updates", config, "check-for-updates");
        List<String> whitelist = old.getStringList("images-hostnames-whitelist");
        if (!whitelist.isEmpty()) {
            config.set("images-hostnames-whitelist", whitelist);
            changed = true;
        }
        // "lang" is not copied: ImageOnMap had no Polish translation, this plugin defaults to pl-PL.
        if (changed) {
            plugin.saveConfig();
            plugin.reloadSettings();
        }
        return changed;
    }

    private static boolean copy(YamlConfiguration from, String fromKey, FileConfiguration to, String toKey) {
        if (!from.contains(fromKey, true)) {
            return false;
        }
        Object value = from.get(fromKey);
        if (value == null) {
            return false;
        }
        to.set(toKey, value);
        return true;
    }

    private void writeMarker(File source, Result result) {
        YamlConfiguration marker = new YamlConfiguration();
        marker.set("imported-at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()));
        marker.set("source", source.getAbsolutePath());
        marker.set("player-files", result.storesRead());
        marker.set("images-imported", result.imagesImported());
        marker.set("maps-imported", result.mapsImported());
        marker.set("images-already-present", result.imagesSkipped());
        marker.set("image-files-copied", result.filesCopied());
        marker.set("errors", result.errors());
        marker.options().setHeader(List.of(
                "ImageOnMap data was imported into ImageOnMapReborn. Delete this file to import again automatically",
                "on the next start, or run /maptool import at any time (already imported images are skipped)."));
        try {
            marker.save(markerFile());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Cannot write " + MARKER_FILE, ex);
        }
    }
}
