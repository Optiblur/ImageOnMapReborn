package pl.iomreborn.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    /** The config.yml written by version 1.0.0, edited by an admin. */
    private static final String OLD_CONFIG = String.join("\n",
            "#  ImageOnMapReborn – konfiguracja / configuration",
            "",
            "# Język pluginu",
            "lang: en-US",
            "per-player-language: true",
            "map-global-limit: 500",
            "map-player-limit: 20",
            "images-hostnames-whitelist:",
            "- i.imgur.com",
            "download:",
            "  # Limit czasu",
            "  timeout-seconds: 5",
            "  max-size-mb: 25",
            "smooth-scaling: false",
            "legacy-import:",
            "  enabled: true",
            "  folder: ImageOnMap",
            "");

    private static String bundled() throws Exception {
        try (InputStream in = ConfigUpdaterTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void splitsTheBundledConfigIntoOptions() throws Exception {
        List<ConfigUpdater.Block> blocks = ConfigUpdater.blocks(bundled());
        List<String> keys = blocks.stream().map(ConfigUpdater.Block::key).toList();
        assertTrue(keys.containsAll(List.of("lang", "download", "dithering", "animated-gifs", "check-for-updates")),
                keys.toString());
        ConfigUpdater.Block animated = blocks.get(keys.indexOf("animated-gifs"));
        assertTrue(animated.lines().stream().anyMatch(line -> line.trim().startsWith("max-fps:")));
        assertTrue(animated.lines().get(0).startsWith("#"), "comments above an option come with it");
        ConfigUpdater.Block lang = blocks.get(keys.indexOf("lang"));
        assertTrue(lang.lines().stream().noneMatch(line -> line.contains("konfiguracja / configuration")),
                "the file header is not part of any option");
    }

    @Test
    void addsOnlyTheMissingOptionsAndKeepsTheAdminsValues() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString(OLD_CONFIG);
        List<String> added = new ArrayList<>();
        String extra = ConfigUpdater.missingOptions(bundled(), key -> old.contains(key, true), added);
        assertEquals(List.of("limit-map-size-x", "limit-map-size-y", "save-full-image", "max-image-pixels",
                "dithering", "animated-gifs", "protect-item-frames", "check-for-updates"), added);

        YamlConfiguration updated = new YamlConfiguration();
        updated.loadFromString(OLD_CONFIG + extra);
        assertEquals("en-US", updated.getString("lang"));
        assertEquals(20, updated.getInt("map-player-limit"));
        assertEquals(5, updated.getInt("download.timeout-seconds"));
        assertEquals(false, updated.getBoolean("smooth-scaling"));
        assertEquals("floyd-steinberg", updated.getString("dithering"));
        assertEquals(10, updated.getInt("animated-gifs.max-fps"));
        assertEquals(true, updated.getBoolean("check-for-updates"));

        List<String> again = new ArrayList<>();
        assertEquals("", ConfigUpdater.missingOptions(bundled(), key -> updated.contains(key, true), again));
        assertTrue(again.isEmpty());
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = ConfigUpdaterTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean hasPolishLetters(String text) {
        return text.chars().anyMatch(c -> "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ".indexOf(c) >= 0);
    }

    @Test
    void theBundledConfigIsCommentedInEnglish() throws Exception {
        for (String line : bundled().split("\n")) {
            if (line.strip().startsWith("#")) {
                assertTrue(!hasPolishLetters(line), "Polish comment: " + line);
            }
        }
        assertTrue(!hasPolishLetters(resource("lang/pl_PL.yml").lines().limit(3).reduce("", String::concat)),
                "the header comments of the Polish messages are in English too");
    }

    @Test
    void oldPolishCommentsBecomeEnglishAndValuesStay() throws Exception {
        String old = resource("config-1.0.0.yml");
        assertTrue(hasPolishLetters(old));
        String translated = ConfigUpdater.translateComments(old, ConfigUpdater.OLD_CONFIG_COMMENTS);
        for (String line : translated.split("\n")) {
            if (line.strip().startsWith("#")) {
                assertTrue(!hasPolishLetters(line), "not translated: " + line);
            }
        }
        // Every translated comment is the one of the new file (same text, same place).
        String bundled = bundled();
        for (String line : translated.split("\n")) {
            if (line.strip().startsWith("#")) {
                assertTrue(bundled.contains(line.strip()), "not in the new config.yml: " + line);
            }
        }
        YamlConfiguration before = new YamlConfiguration();
        before.loadFromString(old);
        YamlConfiguration after = new YamlConfiguration();
        after.loadFromString(translated);
        assertEquals(before.getValues(true).toString(), after.getValues(true).toString());

        // Then the new options are added, and the result has the same comments as a new file.
        List<String> added = new ArrayList<>();
        String upgraded = translated + ConfigUpdater.missingOptions(bundled, key -> after.contains(key, true), added);
        assertEquals(List.of("dithering", "animated-gifs", "check-for-updates"), added);
        assertTrue(!hasPolishLetters(upgraded));
        YamlConfiguration result = new YamlConfiguration();
        result.loadFromString(upgraded);
        assertEquals("floyd-steinberg", result.getString("dithering"));
        assertEquals("pl-PL", result.getString("lang"));
    }

    @Test
    void oldPolishMessageFilesGetTheNewHeader() throws Exception {
        String oldHeader = resource("pl_PL-1.0.0-header.yml");
        assertTrue(hasPolishLetters(oldHeader));
        String translated = ConfigUpdater.translateComments(oldHeader, ConfigUpdater.OLD_POLISH_LANG_COMMENTS);
        String newHeader = String.join("\n", resource("lang/pl_PL.yml").lines().limit(3).toList());
        assertEquals(newHeader, translated.strip());
    }

    @Test
    void translationKeepsTheAdminsOwnCommentsAndLineEndings() {
        String text = "# Mój własny komentarz\r\n  # Maksymalny rozmiar pobieranego pliku (MB).\r\n"
                + "  max-size-mb: 25 # Limit czasu połączenia i odczytu (sekundy).\r\n";
        String translated = ConfigUpdater.translateComments(text, ConfigUpdater.OLD_CONFIG_COMMENTS);
        assertEquals("# Mój własny komentarz\r\n  # Maximum size of a downloaded file (MB).\r\n"
                + "  max-size-mb: 25 # Limit czasu połączenia i odczytu (sekundy).\r\n", translated);

        String header = ConfigUpdater.translateComments("# ImageOnMapReborn – wiadomości po polsku.\nkey: \"&#123456x\"\n",
                ConfigUpdater.OLD_POLISH_LANG_COMMENTS);
        assertEquals("# ImageOnMapReborn – Polish messages.\nkey: \"&#123456x\"\n", header);
    }

    @Test
    void handlesWindowsLineEndings() {
        String text = "# a\r\nfirst: 1\r\n\r\n# b\r\nsecond:\r\n  nested: 2\r\n";
        List<ConfigUpdater.Block> blocks = ConfigUpdater.blocks(text);
        assertEquals(2, blocks.size());
        assertEquals(List.of("# b", "second:", "  nested: 2"), blocks.get(1).lines());
    }
}
