package pl.iomreborn.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An image split over several maps. Map IDs are stored row by row, starting at the top-left part.
 * <p>
 * Posters migrated from very old ImageOnMap versions have no layout data (columns = rows = 0);
 * they can still be listed and given as separate parts, but not placed as a splatter map.
 */
public final class PosterMap extends ImageMap {

    private final int[] mapIds;
    private final int columns;
    private final int rows;

    public PosterMap(UUID owner, String id, String name, int[] mapIds, int columns, int rows) {
        super(owner, id, name);
        if (mapIds.length == 0) {
            throw new IllegalArgumentException("A poster needs at least one map");
        }
        this.mapIds = mapIds.clone();
        this.columns = Math.max(columns, 0);
        this.rows = Math.max(rows, 0);
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    /** True when the poster knows its layout and it matches the number of maps. */
    public boolean hasColumnData() {
        return columns > 0 && rows > 0 && columns * rows == mapIds.length;
    }

    /** Columns used to lay the poster out; posters without layout data are treated as a single row. */
    public int layoutColumns() {
        return hasColumnData() ? columns : mapIds.length;
    }

    public int layoutRows() {
        return hasColumnData() ? rows : 1;
    }

    public int getMapIdAt(int index) {
        return mapIds[index];
    }

    public int getMapIdAt(int column, int row) {
        return mapIds[row * columns + column];
    }

    public int indexOf(int mapId) {
        for (int i = 0; i < mapIds.length; i++) {
            if (mapIds[i] == mapId) {
                return i;
            }
        }
        return -1;
    }

    public int columnOf(int index) {
        return columns == 0 ? 0 : index % columns;
    }

    public int rowOf(int index) {
        return columns == 0 ? 0 : index / columns;
    }

    @Override
    public Type getType() {
        return Type.POSTER;
    }

    @Override
    public int[] getMapIds() {
        return mapIds.clone();
    }

    @Override
    public int getMapCount() {
        return mapIds.length;
    }

    @Override
    public boolean managesMap(int mapId) {
        return indexOf(mapId) >= 0;
    }

    @Override
    public int getFirstMapId() {
        return mapIds[0];
    }

    @Override
    protected void serializeFields(Map<String, Object> out) {
        out.put("columns", columns);
        out.put("rows", rows);
        List<Integer> ids = new ArrayList<>(mapIds.length);
        for (int id : mapIds) {
            ids.add(id);
        }
        out.put("mapsIDs", ids);
    }
}
