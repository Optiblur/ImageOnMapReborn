package pl.iomreborn.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All images of one player, stored in {@code maps/<uuid>.yml}. */
public final class PlayerMapStore {

    private final UUID owner;
    private final List<ImageMap> maps = new ArrayList<>();
    /** Entries that could not be understood; written back untouched so no data is ever lost. */
    private final List<Map<String, Object>> preservedEntries = new ArrayList<>();
    private boolean dirty;

    public PlayerMapStore(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public List<ImageMap> getMaps() {
        return Collections.unmodifiableList(maps);
    }

    public List<Map<String, Object>> getPreservedEntries() {
        return Collections.unmodifiableList(preservedEntries);
    }

    public boolean isEmpty() {
        return maps.isEmpty() && preservedEntries.isEmpty();
    }

    public int getImageCount() {
        return maps.size();
    }

    /** Number of Minecraft maps used by all images of this player. */
    public int getMapCount() {
        int count = 0;
        for (ImageMap map : maps) {
            count += map.getMapCount();
        }
        return count;
    }

    public void add(ImageMap map) {
        maps.add(map);
    }

    boolean remove(ImageMap map) {
        return maps.remove(map);
    }

    public void addPreserved(Map<String, Object> raw) {
        preservedEntries.add(raw);
    }

    /** Finds a map by ID, then by name (exact match first, then ignoring case). */
    public ImageMap find(String query) {
        if (query == null) {
            return null;
        }
        String q = query.trim();
        for (ImageMap map : maps) {
            if (map.getId().equals(q)) {
                return map;
            }
        }
        for (ImageMap map : maps) {
            if (map.getId().equalsIgnoreCase(q)) {
                return map;
            }
        }
        for (ImageMap map : maps) {
            if (map.getName().equals(q)) {
                return map;
            }
        }
        for (ImageMap map : maps) {
            if (map.getName().equalsIgnoreCase(q)) {
                return map;
            }
        }
        return null;
    }

    public boolean idExists(String id, ImageMap except) {
        for (ImageMap map : maps) {
            if (map != except && map.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Same scheme as ImageOnMap: "Name", then "Name-1", "Name-2"... */
    public String nextAvailableId(String base, ImageMap except) {
        if (!idExists(base, except)) {
            return base;
        }
        int n = 0;
        String candidate;
        do {
            n++;
            candidate = base + "-" + n;
        } while (idExists(candidate, except));
        return candidate;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
