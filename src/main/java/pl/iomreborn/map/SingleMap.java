package pl.iomreborn.map;

import java.util.Map;
import java.util.UUID;

/** An image that fits on exactly one Minecraft map. */
public final class SingleMap extends ImageMap {

    private final int mapId;

    public SingleMap(UUID owner, String id, String name, int mapId) {
        super(owner, id, name);
        this.mapId = mapId;
    }

    public int getMapId() {
        return mapId;
    }

    @Override
    public Type getType() {
        return Type.SINGLE;
    }

    @Override
    public int[] getMapIds() {
        return new int[] {mapId};
    }

    @Override
    public int getMapCount() {
        return 1;
    }

    @Override
    public boolean managesMap(int id) {
        return id == mapId;
    }

    @Override
    protected void serializeFields(Map<String, Object> out) {
        out.put("mapID", mapId);
    }
}
