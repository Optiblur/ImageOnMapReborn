package pl.iomreborn.map;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerMapStoreTest {

    private final UUID owner = UUID.randomUUID();

    @Test
    void generatesIdsLikeImageOnMap() {
        PlayerMapStore store = new PlayerMapStore(owner);
        assertEquals("Map", store.nextAvailableId("Map", null));
        SingleMap first = new SingleMap(owner, "Map", "Map", 1);
        store.add(first);
        assertEquals("Map-1", store.nextAvailableId("Map", null));
        store.add(new SingleMap(owner, "Map-1", "Map", 2));
        assertEquals("Map-2", store.nextAvailableId("Map", null));
        // Renaming a map to its own ID keeps it.
        assertEquals("Map", store.nextAvailableId("Map", first));
    }

    @Test
    void findsByIdThenByName() {
        PlayerMapStore store = new PlayerMapStore(owner);
        SingleMap cat = new SingleMap(owner, "Map", "My cat", 1);
        SingleMap dog = new SingleMap(owner, "Map-1", "Dog", 2);
        store.add(cat);
        store.add(dog);
        assertSame(cat, store.find("Map"));
        assertSame(cat, store.find("map"));
        assertSame(dog, store.find("Map-1"));
        assertSame(cat, store.find("my cat"));
        assertSame(dog, store.find(" Dog "));
        assertNull(store.find("nothing"));
        assertEquals(2, store.getMapCount());
    }

    @Test
    void sanitizesNames() {
        assertEquals("Hello world", MapManager.sanitizeName("  Hello \"   world§c "));
        assertEquals("", MapManager.sanitizeName("   "));
        assertEquals(64, MapManager.sanitizeName("x".repeat(100)).length());
        assertEquals("Tom & Jerry", MapManager.sanitizeName("Tom & Jerry"));
    }
}
