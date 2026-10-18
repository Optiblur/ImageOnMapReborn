package pl.iomreborn.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.PlayerMapStore;
import pl.iomreborn.map.PosterMap;
import pl.iomreborn.map.SingleMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads and writes {@code maps/<uuid>.yml} files in the exact format of ImageOnMap 3.x/4.x:
 * <pre>
 * PlayerMapStore:
 *   mapList:
 *   - id: Map
 *     type: SINGLE
 *     name: Map
 *     mapID: 12
 *   - id: Map-1
 *     type: POSTER
 *     name: Map
 *     columns: 2
 *     rows: 1
 *     mapsIDs:
 *     - 13
 *     - 14
 * </pre>
 */
public final class MapStorage {

    public static final String ROOT_KEY = "PlayerMapStore";
    public static final String LIST_KEY = "mapList";

    private final File mapsDir;

    public MapStorage(File mapsDir) {
        this.mapsDir = mapsDir;
    }

    public File getMapsDir() {
        return mapsDir;
    }

    public File fileFor(UUID owner) {
        return new File(mapsDir, owner + ".yml");
    }

    /** Returns the UUID encoded in a store file name, or null if the file is not a store. */
    public static UUID uuidFromFileName(String fileName) {
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            return null;
        }
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - 4));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static PlayerMapStore read(File file, UUID owner, String defaultName, Logger logger)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return read(yaml, owner, defaultName, logger, file.getName());
    }

    public static PlayerMapStore read(YamlConfiguration yaml, UUID owner, String defaultName, Logger logger,
                                      String sourceName) {
        PlayerMapStore store = new PlayerMapStore(owner);
        ConfigurationSection root = yaml.getConfigurationSection(ROOT_KEY);
        if (root == null) {
            return store;
        }
        List<?> list = root.getList(LIST_KEY);
        if (list == null) {
            return store;
        }
        for (Object entry : list) {
            Map<String, Object> raw = toStringKeyedMap(entry);
            if (raw == null) {
                if (logger != null) {
                    logger.warning("Ignoring a malformed entry in " + sourceName + ": " + entry);
                }
                continue;
            }
            try {
                store.add(parseEntry(raw, owner, defaultName));
            } catch (InvalidMapDataException ex) {
                if (logger != null) {
                    logger.warning("Keeping an unreadable map entry of " + sourceName + " untouched ("
                            + ex.getMessage() + "): " + raw);
                }
                store.addPreserved(raw);
            }
        }
        return store;
    }

    public static ImageMap parseEntry(Map<String, Object> raw, UUID owner, String defaultName)
            throws InvalidMapDataException {
        ImageMap.Type type = parseType(raw);

        String name = asString(raw.get("name"));
        if (name == null || name.isBlank()) {
            name = defaultName;
        }
        String id = asString(raw.get("id"));
        if (id == null || id.isBlank()) {
            id = name;
        }

        if (type == ImageMap.Type.SINGLE) {
            Integer mapId = asInt(raw.get("mapID"));
            if (mapId == null) {
                throw new InvalidMapDataException("missing or invalid 'mapID'");
            }
            return new SingleMap(owner, id, name, mapId);
        }

        int[] ids = asIntArray(raw.get("mapsIDs"));
        if (ids == null || ids.length == 0) {
            throw new InvalidMapDataException("missing or invalid 'mapsIDs'");
        }
        Integer columns = asInt(raw.get("columns"));
        Integer rows = asInt(raw.get("rows"));
        return new PosterMap(owner, id, name, ids, columns == null ? 0 : columns, rows == null ? 0 : rows);
    }

    private static ImageMap.Type parseType(Map<String, Object> raw) throws InvalidMapDataException {
        Object type = raw.get("type");
        if (type != null) {
            try {
                return ImageMap.Type.valueOf(type.toString().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new InvalidMapDataException("unknown type '" + type + "'");
            }
        }
        if (raw.containsKey("mapID")) {
            return ImageMap.Type.SINGLE;
        }
        if (raw.containsKey("mapsIDs")) {
            return ImageMap.Type.POSTER;
        }
        throw new InvalidMapDataException("missing 'type'");
    }

    public String serialize(PlayerMapStore store) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ImageMap map : store.getMaps()) {
            list.add(map.serialize());
        }
        list.addAll(store.getPreservedEntries());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(ROOT_KEY + "." + LIST_KEY, list);
        return yaml.saveToString();
    }

    /** Writes the content to a temporary file first, then moves it over the target. */
    public static void writeAtomic(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent);
        }
        File tmp = new File(parent, target.getName() + ".tmp");
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
        moveReplacing(tmp, target);
    }

    static void moveReplacing(File from, File to) throws IOException {
        try {
            retrying(() -> {
                try {
                    Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            });
        } catch (FileSystemException ex) {
            // The target is still open somewhere (Windows): overwrite its content instead of replacing it.
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(from.toPath());
        }
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws IOException;
    }

    /** Windows briefly locks files that are being read or scanned by an antivirus; retries for about 2 s. */
    static void retrying(IoAction action) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (FileSystemException ex) {
                boolean locked = ex instanceof AccessDeniedException || ex.getClass() == FileSystemException.class;
                if (!locked || attempt >= 8) {
                    throw ex;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    private static Map<String, Object> toStringKeyedMap(Object entry) {
        if (entry instanceof ConfigurationSection section) {
            return new LinkedHashMap<>(section.getValues(false));
        }
        if (entry instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static int[] asIntArray(Object value) {
        if (value instanceof int[] array) {
            return array.clone();
        }
        List<?> list;
        if (value instanceof List<?> l) {
            list = l;
        } else if (value instanceof Object[] array) {
            list = List.of(array);
        } else {
            return null;
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            Integer id = asInt(list.get(i));
            if (id == null) {
                return null;
            }
            out[i] = id;
        }
        return out;
    }
}
