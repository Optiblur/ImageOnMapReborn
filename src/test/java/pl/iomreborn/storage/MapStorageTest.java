package pl.iomreborn.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.PlayerMapStore;
import pl.iomreborn.map.PosterMap;
import pl.iomreborn.map.SingleMap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapStorageTest {

    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    /** A store as written by ImageOnMap 4.x (HashMap key order, int[] written as a YAML list). */
    private static final String LEGACY_FILE = String.join("\n",
            "PlayerMapStore:",
            "  mapList:",
            "  - mapID: 0",
            "    name: Map",
            "    id: Map",
            "    type: SINGLE",
            "  - mapsIDs:",
            "    - 1",
            "    - 2",
            "    - 3",
            "    - 4",
            "    - 5",
            "    - 6",
            "    rows: 2",
            "    name: Wall poster",
            "    id: Map-1",
            "    type: POSTER",
            "    columns: 3",
            "  - mapsIDs:",
            "    - 7",
            "    - 8",
            "    rows: 0",
            "    name: Old poster",
            "    id: Old poster",
            "    type: POSTER",
            "    columns: 0",
            "  - type: SOMETHING",
            "    id: broken",
            "");

    @Test
    void readsImageOnMapFiles(@TempDir File dir) throws Exception {
        File file = new File(dir, OWNER + ".yml");
        Files.writeString(file.toPath(), LEGACY_FILE, StandardCharsets.UTF_8);

        PlayerMapStore store = MapStorage.read(file, OWNER, "Map", null);

        assertEquals(3, store.getImageCount());
        assertEquals(9, store.getMapCount());
        assertEquals(1, store.getPreservedEntries().size());

        SingleMap single = assertInstanceOf(SingleMap.class, store.getMaps().get(0));
        assertEquals(0, single.getMapId());
        assertEquals("Map", single.getId());
        assertEquals(OWNER, single.getOwner());

        PosterMap poster = assertInstanceOf(PosterMap.class, store.getMaps().get(1));
        assertEquals("Map-1", poster.getId());
        assertEquals("Wall poster", poster.getName());
        assertEquals(3, poster.getColumns());
        assertEquals(2, poster.getRows());
        assertTrue(poster.hasColumnData());
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6}, poster.getMapIds());
        assertEquals(6, poster.getMapIdAt(2, 1));
        assertEquals(1, poster.rowOf(poster.indexOf(5)));

        PosterMap old = assertInstanceOf(PosterMap.class, store.getMaps().get(2));
        assertFalse(old.hasColumnData());
        assertEquals(2, old.layoutColumns());
        assertEquals(1, old.layoutRows());
    }

    @Test
    void writesTheSameFormatAndKeepsUnknownEntries(@TempDir File dir) throws Exception {
        File file = new File(dir, OWNER + ".yml");
        Files.writeString(file.toPath(), LEGACY_FILE, StandardCharsets.UTF_8);
        PlayerMapStore store = MapStorage.read(file, OWNER, "Map", null);

        String yaml = new MapStorage(dir).serialize(store);
        assertTrue(yaml.startsWith("PlayerMapStore:\n  mapList:\n"), yaml);
        assertTrue(yaml.contains("mapID: 0"), yaml);
        assertTrue(yaml.contains("mapsIDs:"), yaml);

        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(yaml);
        PlayerMapStore copy = MapStorage.read(reread, OWNER, "Map", null, "test");
        assertEquals(store.getImageCount(), copy.getImageCount());
        assertEquals(store.getMapCount(), copy.getMapCount());
        assertEquals(1, copy.getPreservedEntries().size());
        assertEquals("broken", copy.getPreservedEntries().get(0).get("id"));
        for (int i = 0; i < store.getImageCount(); i++) {
            ImageMap a = store.getMaps().get(i);
            ImageMap b = copy.getMaps().get(i);
            assertEquals(a.getId(), b.getId());
            assertEquals(a.getName(), b.getName());
            assertEquals(a.getType(), b.getType());
            assertArrayEquals(a.getMapIds(), b.getMapIds());
        }

        File target = new File(dir, "out.yml");
        MapStorage.writeAtomic(target, yaml);
        assertEquals(yaml, Files.readString(target.toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(dir, "out.yml.tmp").exists());
    }

    @Test
    void emptyFilesAreEmptyStores(@TempDir File dir) throws Exception {
        File file = new File(dir, OWNER + ".yml");
        Files.writeString(file.toPath(), "PlayerMapStore:\n  mapList: []\n", StandardCharsets.UTF_8);
        assertTrue(MapStorage.read(file, OWNER, "Map", null).isEmpty());
        Files.writeString(file.toPath(), "", StandardCharsets.UTF_8);
        assertTrue(MapStorage.read(file, OWNER, "Map", null).isEmpty());
    }

    @Test
    void recognizesStoreFileNames() {
        assertEquals(OWNER, MapStorage.uuidFromFileName(OWNER + ".yml"));
        assertEquals(null, MapStorage.uuidFromFileName(OWNER + ".yml.tmp"));
        assertEquals(null, MapStorage.uuidFromFileName("config.yml"));
    }
}
