package pl.iomreborn.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    @Test
    void polishPlurals() {
        assertEquals("one", Messages.pluralForm("pl_PL", 1));
        assertEquals("few", Messages.pluralForm("pl_PL", 2));
        assertEquals("few", Messages.pluralForm("pl_PL", 4));
        assertEquals("many", Messages.pluralForm("pl_PL", 5));
        assertEquals("many", Messages.pluralForm("pl_PL", 12));
        assertEquals("many", Messages.pluralForm("pl_PL", 14));
        assertEquals("few", Messages.pluralForm("pl_PL", 22));
        assertEquals("many", Messages.pluralForm("pl_PL", 0));
        assertEquals("one", Messages.pluralForm("en_US", 1));
        assertEquals("other", Messages.pluralForm("en_US", 3));
    }

    @Test
    void formatsPlaceholders() {
        assertEquals("a and b {2}", Messages.format("{0} and {1} {2}", "a", "b"));
        assertEquals("{x} {}", Messages.format("{x} {}", "a"));
        assertEquals("§cred", Messages.format("§c{0}", "red"));
    }

    @Test
    void normalizesLocales() {
        assertEquals("pl_PL", Messages.normalize("pl-pl"));
        assertEquals("en_US", Messages.normalize("EN_us"));
        assertEquals("pl", Messages.normalize("PL"));
    }

    private static Set<String> leafKeys(String resource) throws Exception {
        try (InputStream in = MessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> keys = new HashSet<>();
            for (String key : yaml.getKeys(true)) {
                if (!yaml.isConfigurationSection(key)) {
                    keys.add(key.replaceAll("\\.(one|few|many|other)$", ".#plural"));
                }
            }
            return keys;
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws Exception {
        Set<String> english = leafKeys("lang/en_US.yml");
        Set<String> polish = leafKeys("lang/pl_PL.yml");
        Set<String> missingInPolish = new HashSet<>(english);
        missingInPolish.removeAll(polish);
        Set<String> missingInEnglish = new HashSet<>(polish);
        missingInEnglish.removeAll(english);
        assertTrue(missingInPolish.isEmpty(), "Missing in pl_PL: " + missingInPolish);
        assertTrue(missingInEnglish.isEmpty(), "Missing in en_US: " + missingInEnglish);
    }
}
