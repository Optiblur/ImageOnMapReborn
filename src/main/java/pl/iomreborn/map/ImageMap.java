package pl.iomreborn.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An image rendered by the plugin, made of one or more Minecraft maps.
 * <p>
 * The serialized form is identical to the one used by ImageOnMap 3.x/4.x in
 * {@code plugins/ImageOnMap/maps/<uuid>.yml}, so data can move between both plugins.
 */
public abstract class ImageMap {

    public enum Type { SINGLE, POSTER }

    private final UUID owner;
    private String id;
    private String name;

    protected ImageMap(UUID owner, String id, String name) {
        this.owner = owner;
        this.id = id;
        this.name = name;
    }

    public UUID getOwner() {
        return owner;
    }

    /** Identifier used in commands; derived from the name and unique per player ("Map", "Map-1"...). */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    void rename(String newId, String newName) {
        this.id = newId;
        this.name = newName;
    }

    public abstract Type getType();

    /** Minecraft map IDs used by this image, in poster order (row by row, top-left first). */
    public abstract int[] getMapIds();

    public abstract int getMapCount();

    public abstract boolean managesMap(int mapId);

    public int getFirstMapId() {
        return getMapIds()[0];
    }

    public Map<String, Object> serialize() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("type", getType().name());
        out.put("name", name);
        serializeFields(out);
        return out;
    }

    protected abstract void serializeFields(Map<String, Object> out);
}
