package pl.iomreborn;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import pl.iomreborn.config.PluginConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageOnMapRebornTest {

    private static String limits(int serverLimit, int playerLimit) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("map-global-limit", serverLimit);
        yaml.set("map-player-limit", playerLimit);
        return ChatColor.stripColor(ImageOnMapReborn.limitsSummary(PluginConfig.from(yaml)));
    }

    @Test
    void theStartupSummaryShowsTheMapLimits() {
        assertEquals("200 maps per player", limits(0, 200));
        assertEquals("1 map per player, 5000 maps on the server", limits(5000, 1));
        assertEquals("1000 maps on the server", limits(1000, 0));
    }

    @Test
    void serversWithoutLimitsAreToldWhereToSetThem() {
        assertEquals("none (map-player-limit in config.yml)", limits(0, 0));
    }
}
