package pl.iomreborn.map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.config.PluginConfig;
import pl.iomreborn.image.RenderException;
import pl.iomreborn.storage.MapStorage;
import pl.iomreborn.util.Tasks;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns every player store and an index from Minecraft map ID to image. Main thread only. */
public final class MapManager {

    private static final long SAVE_DELAY_TICKS = 100L;
    private static final int MAX_NAME_LENGTH = 64;

    private final ImageOnMapReborn plugin;
    private final MapStorage storage;
    private final Logger logger;
    private final Map<UUID, PlayerMapStore> stores = new LinkedHashMap<>();
    private final Map<Integer, ImageMap> byMapId = new HashMap<>();
    private BukkitTask saveTask;

    public MapManager(ImageOnMapReborn plugin, MapStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.logger = plugin.getLogger();
    }

    public MapStorage getStorage() {
        return storage;
    }

    /** Loads every {@code maps/<uuid>.yml} file. Unreadable files are renamed, never overwritten. */
    public void loadAll() {
        stores.clear();
        byMapId.clear();
        File[] files = storage.getMapsDir().listFiles((dir, name) -> MapStorage.uuidFromFileName(name) != null);
        if (files == null) {
            return;
        }
        for (File file : files) {
            UUID owner = MapStorage.uuidFromFileName(file.getName());
            try {
                PlayerMapStore store = MapStorage.read(file, owner, defaultName(), logger);
                stores.put(owner, store);
                for (ImageMap map : store.getMaps()) {
                    index(map);
                }
            } catch (Exception ex) {
                File backup = new File(file.getParentFile(), file.getName() + ".broken-"
                        + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
                boolean renamed = file.renameTo(backup);
                logger.log(Level.SEVERE, "Cannot read map file " + file.getName()
                        + (renamed ? "; it was renamed to " + backup.getName() + " so it is not overwritten."
                        : "; it could not be renamed, fix or remove it manually."), ex);
            }
        }
    }

    /** Adds a store read from somewhere else (legacy import), merging maps whose IDs are not known yet. */
    public int[] merge(PlayerMapStore incoming) {
        int images = 0;
        int maps = 0;
        int skipped = 0;
        PlayerMapStore store = null;
        for (ImageMap map : incoming.getMaps()) {
            if (isAnyManaged(map)) {
                skipped++;
                continue;
            }
            if (store == null) {
                store = getOrCreateStore(incoming.getOwner());
            }
            String id = store.nextAvailableId(map.getId(), null);
            if (!id.equals(map.getId())) {
                map.rename(id, map.getName());
            }
            store.add(map);
            index(map);
            images++;
            maps += map.getMapCount();
        }
        if (store != null) {
            markDirty(store);
        }
        return new int[] {images, maps, skipped};
    }

    private boolean isAnyManaged(ImageMap map) {
        for (int id : map.getMapIds()) {
            if (byMapId.containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    private void index(ImageMap map) {
        for (int id : map.getMapIds()) {
            ImageMap previous = byMapId.putIfAbsent(id, map);
            if (previous != null && previous != map) {
                logger.warning("Minecraft map #" + id + " is used by two images (" + previous.getOwner() + "/"
                        + previous.getId() + " and " + map.getOwner() + "/" + map.getId() + "); the first one wins.");
            }
        }
    }

    private void unindex(ImageMap map) {
        for (int id : map.getMapIds()) {
            byMapId.remove(id, map);
        }
    }

    public String defaultName() {
        return plugin.messages().getIn(plugin.messages().serverLocale(), "map.default-name");
    }

    public boolean isManaged(int mapId) {
        return byMapId.containsKey(mapId);
    }

    public ImageMap getByMapId(int mapId) {
        return byMapId.get(mapId);
    }

    /** True if the image is still registered (it was not deleted in the meantime). */
    public boolean exists(ImageMap map) {
        return byMapId.get(map.getFirstMapId()) == map;
    }

    public PlayerMapStore getStore(UUID owner) {
        return stores.get(owner);
    }

    public PlayerMapStore getOrCreateStore(UUID owner) {
        return stores.computeIfAbsent(owner, PlayerMapStore::new);
    }

    public Collection<PlayerMapStore> getStores() {
        return Collections.unmodifiableCollection(stores.values());
    }

    public List<ImageMap> getMaps(UUID owner) {
        PlayerMapStore store = stores.get(owner);
        return store == null ? Collections.emptyList() : new ArrayList<>(store.getMaps());
    }

    public ImageMap findMap(UUID owner, String query) {
        PlayerMapStore store = stores.get(owner);
        return store == null ? null : store.find(query);
    }

    public int getMapCount(UUID owner) {
        PlayerMapStore store = stores.get(owner);
        return store == null ? 0 : store.getMapCount();
    }

    public int getImageCount(UUID owner) {
        PlayerMapStore store = stores.get(owner);
        return store == null ? 0 : store.getImageCount();
    }

    public int getTotalMapCount() {
        int count = 0;
        for (PlayerMapStore store : stores.values()) {
            count += store.getMapCount();
        }
        return count;
    }

    public int getTotalImageCount() {
        int count = 0;
        for (PlayerMapStore store : stores.values()) {
            count += store.getImageCount();
        }
        return count;
    }

    public String nextId(UUID owner, String base) {
        PlayerMapStore store = stores.get(owner);
        return store == null ? base : store.nextAvailableId(base, null);
    }

    public void addMap(ImageMap map) {
        PlayerMapStore store = getOrCreateStore(map.getOwner());
        store.add(map);
        index(map);
        markDirty(store);
    }

    /** Removes the image from its store. Renderers and image files are handled by the caller. */
    public boolean removeMap(ImageMap map) {
        PlayerMapStore store = stores.get(map.getOwner());
        if (store == null || !store.remove(map)) {
            return false;
        }
        unindex(map);
        markDirty(store);
        return true;
    }

    public void rename(ImageMap map, String newName) {
        PlayerMapStore store = stores.get(map.getOwner());
        if (store == null) {
            return;
        }
        map.rename(store.nextAvailableId(newName, map), newName);
        markDirty(store);
    }

    /** Cleans a user-provided map name: no colors, quotes or control characters, at most 64 characters. */
    public static String sanitizeName(String input) {
        if (input == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(input);
        StringBuilder out = new StringBuilder(stripped.length());
        for (char c : stripped.toCharArray()) {
            if (c == '"' || c == '§' || Character.isISOControl(c)) {
                continue;
            }
            out.append(c);
        }
        String result = out.toString().trim().replaceAll("\\s+", " ");
        if (result.length() > MAX_NAME_LENGTH) {
            result = result.substring(0, MAX_NAME_LENGTH).trim();
        }
        return result;
    }

    /**
     * Checks the server and player limits before creating {@code newMaps} Minecraft maps.
     *
     * @throws RenderException with a translated message key when a limit would be exceeded
     */
    public void checkCreateLimits(UUID owner, int newMaps, LimitContext limits) throws RenderException {
        PluginConfig config = plugin.settings();
        if (config.mapGlobalLimit() > 0 && getTotalMapCount() + newMaps > config.mapGlobalLimit()) {
            throw new RenderException("error.limit-server-maps");
        }
        int maps = getMapCount(owner);
        int images = getImageCount(owner);
        if (!limits.bypassImageLimit() && limits.imageLimit().isPresent()
                && images + 1 > limits.imageLimit().getAsInt()) {
            throw new RenderException("error.limit-permission-images", limits.imageLimit().getAsInt(), images);
        }
        if (!limits.bypassMapLimit()) {
            if (config.mapPlayerLimit() > 0 && maps + newMaps > config.mapPlayerLimit()) {
                throw new RenderException("error.limit-player-maps", config.mapPlayerLimit());
            }
            if (limits.mapLimit().isPresent() && maps + newMaps > limits.mapLimit().getAsInt()) {
                throw new RenderException("error.limit-permission-maps", limits.mapLimit().getAsInt(), maps);
            }
        }
    }

    public void markDirty(PlayerMapStore store) {
        store.setDirty(true);
        if (saveTask == null && plugin.isEnabled()) {
            saveTask = Bukkit.getScheduler().runTaskLater(plugin, this::flush, SAVE_DELAY_TICKS);
        }
    }

    /** Writes every modified store in the background. */
    public void flush() {
        saveTask = null;
        for (PlayerMapStore store : stores.values()) {
            if (!store.isDirty()) {
                continue;
            }
            String yaml = storage.serialize(store);
            File file = storage.fileFor(store.getOwner());
            store.setDirty(false);
            try {
                plugin.ioExecutor().execute(() -> write(file, yaml, store));
            } catch (RejectedExecutionException ex) {
                write(file, yaml, store);
            }
        }
    }

    private void write(File file, String yaml, PlayerMapStore store) {
        try {
            MapStorage.writeAtomic(file, yaml);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Cannot save " + file.getName() + ", will retry", ex);
            Tasks.sync(() -> markDirty(store));
        }
    }

    /** Writes every modified store right now, on the calling thread (used when the plugin stops). */
    public void saveAllNow() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        for (PlayerMapStore store : stores.values()) {
            if (!store.isDirty()) {
                continue;
            }
            try {
                MapStorage.writeAtomic(storage.fileFor(store.getOwner()), storage.serialize(store));
                store.setDirty(false);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Cannot save maps of " + store.getOwner(), ex);
            }
        }
    }
}
