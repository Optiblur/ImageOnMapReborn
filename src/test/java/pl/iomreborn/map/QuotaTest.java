package pl.iomreborn.map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import pl.iomreborn.config.PluginConfig;
import pl.iomreborn.image.RenderException;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaTest {

    private static PluginConfig config(int serverLimit, int playerLimit) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("map-global-limit", serverLimit);
        yaml.set("map-player-limit", playerLimit);
        return PluginConfig.from(yaml);
    }

    /** A player with the given imageonmap.maplimit.<n> / imageonmap.imagelimit.<n> and no bypass. */
    private static LimitContext permissions(OptionalInt maps, OptionalInt images) {
        return new LimitContext(false, false, maps, images);
    }

    private static RenderException refused(Quota quota, int newMaps, int playerMaps, int playerImages,
                                           int serverMaps) {
        return assertThrows(RenderException.class, () -> quota.check(newMaps, playerMaps, playerImages, serverMaps));
    }

    @Test
    void withoutLimitsEverythingIsAllowed() {
        Quota quota = Quota.of(config(0, 0), LimitContext.CONFIG_ONLY);
        assertFalse(quota.hasLimits());
        assertEquals(OptionalInt.empty(), quota.playerMapLimit());
        assertEquals(OptionalInt.empty(), quota.mapsLeft(5000, 100_000));
        assertDoesNotThrow(() -> quota.check(5000, 5000, 500, 100_000));
    }

    @Test
    void theConfigLimitOfEveryPlayer() throws Exception {
        Quota quota = Quota.of(config(0, 200), LimitContext.CONFIG_ONLY);
        assertTrue(quota.hasLimits());
        assertEquals(OptionalInt.of(200), quota.playerMapLimit());
        assertEquals(OptionalInt.of(50), quota.mapsLeft(150, 1000));
        assertEquals(OptionalInt.of(0), quota.mapsLeft(250, 1000), "never below zero");

        quota.check(50, 150, 3, 1000);
        RenderException ex = refused(quota, 51, 150, 3, 1000);
        assertEquals("error.limit-player-maps", ex.getKey());
        assertArrayEquals(new Object[] {200}, ex.getArgs());
    }

    @Test
    void permissionMapLimitsCountTooAndTheLowestWins() {
        assertEquals(OptionalInt.of(50),
                Quota.of(config(0, 200), permissions(OptionalInt.of(50), OptionalInt.empty())).playerMapLimit());
        assertEquals(OptionalInt.of(200),
                Quota.of(config(0, 200), permissions(OptionalInt.of(500), OptionalInt.empty())).playerMapLimit(),
                "both limits apply, so the config one is the lowest here");
        Quota permissionOnly = Quota.of(config(0, 0), permissions(OptionalInt.of(40), OptionalInt.empty()));
        assertTrue(permissionOnly.hasLimits(), "a permission limit alone counts (the /maps overview missed it)");
        assertEquals(OptionalInt.of(40), permissionOnly.playerMapLimit());
        assertEquals(OptionalInt.of(5), permissionOnly.mapsLeft(35, 35));

        RenderException ex = refused(permissionOnly, 10, 35, 1, 35);
        assertEquals("error.limit-permission-maps", ex.getKey());
        assertArrayEquals(new Object[] {40, 35}, ex.getArgs());
    }

    @Test
    void bypassIgnoresThePlayersLimitsButNotTheServerLimit() throws Exception {
        Quota quota = Quota.of(config(1000, 200),
                new LimitContext(true, false, OptionalInt.of(10), OptionalInt.empty()));
        assertEquals(OptionalInt.empty(), quota.playerMapLimit());
        assertEquals(OptionalInt.of(10), quota.mapsLeft(500, 990));

        quota.check(10, 500, 30, 990);
        assertEquals("error.limit-server-maps", refused(quota, 11, 500, 30, 990).getKey());
        assertEquals(1000, Quota.of(config(1000, 200), LimitContext.UNLIMITED).serverMaps(),
                "the console and OPs are limited by the server limit as well");
    }

    @Test
    void theImageLimit() throws Exception {
        Quota quota = Quota.of(config(0, 0), permissions(OptionalInt.empty(), OptionalInt.of(3)));
        assertTrue(quota.hasLimits());
        assertEquals(OptionalInt.empty(), quota.mapsLeft(10, 10), "an image limit is not a map limit");

        quota.check(1, 10, 2, 10);
        RenderException ex = refused(quota, 1, 10, 3, 10);
        assertEquals("error.limit-permission-images", ex.getKey());
        assertArrayEquals(new Object[] {3, 3}, ex.getArgs());

        Quota bypassed = Quota.of(config(0, 0), new LimitContext(false, true, OptionalInt.empty(), OptionalInt.of(3)));
        assertFalse(bypassed.hasLimits());
        assertDoesNotThrow(() -> bypassed.check(1, 10, 3, 10));
    }

    @Test
    void theServerLimitComesFirstAndCountsEveryPlayer() {
        Quota quota = Quota.of(config(100, 200), permissions(OptionalInt.of(10), OptionalInt.of(1)));
        assertEquals("error.limit-server-maps", refused(quota, 5, 50, 5, 98).getKey());
        assertEquals(OptionalInt.of(0), Quota.of(config(100, 200), LimitContext.CONFIG_ONLY).mapsLeft(5, 100));
        assertEquals(OptionalInt.of(2), Quota.of(config(100, 0), LimitContext.CONFIG_ONLY).mapsLeft(5, 98));
    }
}
