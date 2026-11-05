package pl.iomreborn.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginConfigTest {

    private static YamlConfiguration bundled() throws Exception {
        try (InputStream in = PluginConfigTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void newInstallationsGetLimits() throws Exception {
        PluginConfig config = PluginConfig.from(bundled());
        assertEquals(200, config.mapPlayerLimit());
        assertEquals(PluginConfig.DEFAULT_MAP_PLAYER_LIMIT, config.mapPlayerLimit());
        assertEquals(16_000_000L, config.maxImagePixels());
        assertEquals(PluginConfig.DEFAULT_MAX_IMAGE_PIXELS, config.maxImagePixels());
        assertEquals(0, config.mapGlobalLimit());
    }

    @Test
    void missingOptionsUseTheDefaultsOfTheBundledFile() throws Exception {
        PluginConfig defaults = PluginConfig.from(new YamlConfiguration());
        PluginConfig bundled = PluginConfig.from(bundled());
        assertEquals(bundled.mapPlayerLimit(), defaults.mapPlayerLimit());
        assertEquals(bundled.mapGlobalLimit(), defaults.mapGlobalLimit());
        assertEquals(bundled.maxImagePixels(), defaults.maxImagePixels());
        assertEquals(bundled.downloadTimeoutSeconds(), defaults.downloadTimeoutSeconds());
        assertEquals(bundled.animationMaxFrames(), defaults.animationMaxFrames());
    }

    @Test
    void theValuesOfExistingFilesStayEvenWithoutLimits() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("map-player-limit", 0);
        yaml.set("max-image-pixels", 40_000_000L);
        PluginConfig config = PluginConfig.from(yaml);
        assertEquals(0, config.mapPlayerLimit(), "0 still means no limit");
        assertEquals(40_000_000L, config.maxImagePixels());
    }

    @Test
    void theOptionNamesOfOldImageOnMapVersionsStillWork() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Limit-map-by-player", 15);
        yaml.set("Limit-map-by-server", 300);
        PluginConfig config = PluginConfig.from(yaml);
        assertEquals(15, config.mapPlayerLimit());
        assertEquals(300, config.mapGlobalLimit());
    }
}
