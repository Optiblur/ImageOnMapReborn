package pl.iomreborn.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    void russianPlurals() {
        assertEquals("one", Messages.pluralForm("ru_RU", 1));
        assertEquals("one", Messages.pluralForm("ru_RU", 21));
        assertEquals("few", Messages.pluralForm("ru_RU", 3));
        assertEquals("many", Messages.pluralForm("ru_RU", 11));
        assertEquals("many", Messages.pluralForm("ru_RU", 25));
        assertEquals("one", Messages.pluralForm("pt_BR", 0));
        assertEquals("other", Messages.pluralForm("pt_BR", 2));
    }

    @Test
    void everyLanguageHasTheSameKeysAsEnglish() throws Exception {
        Set<String> english = leafKeys("lang/en_US.yml");
        for (String code : new String[] {"pl_PL", "de_DE", "es_ES", "fr_FR", "pt_BR", "ru_RU"}) {
            Set<String> other = leafKeys("lang/" + code + ".yml");
            Set<String> missing = new HashSet<>(english);
            missing.removeAll(other);
            Set<String> extra = new HashSet<>(other);
            extra.removeAll(english);
            assertTrue(missing.isEmpty(), "Missing in " + code + ": " + missing);
            assertTrue(extra.isEmpty(), "Not in en_US but in " + code + ": " + extra);
        }
    }

    @Test
    void everyLanguageKeepsThePlaceholdersOfEnglish() throws Exception {
        Map<String, String> english = leafValues("lang/en_US.yml");
        for (String code : new String[] {"pl_PL", "de_DE", "es_ES", "fr_FR", "pt_BR", "ru_RU"}) {
            Map<String, String> other = leafValues("lang/" + code + ".yml");
            for (Map.Entry<String, String> entry : other.entrySet()) {
                String base = english.get(entry.getKey().replaceAll("\\.(few|many)$", ".other"));
                if (base == null) {
                    continue;
                }
                assertEquals(placeholders(base), placeholders(entry.getValue()), code + " " + entry.getKey());
            }
        }
    }

    private static Map<String, String> leafValues(String resource) throws Exception {
        try (InputStream in = MessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> values = new HashMap<>();
            for (String key : yaml.getKeys(true)) {
                if (!yaml.isConfigurationSection(key)) {
                    values.put(key, String.valueOf(yaml.get(key)));
                }
            }
            return values;
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile("\\{\\d+}").matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
