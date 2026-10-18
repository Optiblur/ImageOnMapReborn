package pl.iomreborn.i18n;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pl.iomreborn.util.Text;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Translations stored in {@code lang/<locale>.yml}. The bundled files are copied to the plugin folder
 * so they can be edited; keys missing from an edited file fall back to the bundled version, then to English.
 * Values use {@code &} color codes and {@code {0}}, {@code {1}}... placeholders.
 */
public final class Messages {

    public static final String FALLBACK_LOCALE = "en_US";
    private static final String[] BUNDLED = {"en_US", "pl_PL"};

    private final JavaPlugin plugin;
    private final Map<String, Map<String, Object>> bundles = new HashMap<>();
    private Map<String, Object> fallback = Collections.emptyMap();
    private String serverLocale = FALLBACK_LOCALE;
    private boolean perPlayer;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String configuredLanguage, boolean perPlayerLanguage) {
        this.perPlayer = perPlayerLanguage;
        bundles.clear();

        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Cannot create " + dir + "; using the bundled translations only.");
        }

        Map<String, Map<String, Object>> defaults = new HashMap<>();
        for (String code : BUNDLED) {
            defaults.put(code, readBundled(code));
            File file = new File(dir, code + ".yml");
            if (dir.isDirectory() && !file.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
        fallback = defaults.getOrDefault(FALLBACK_LOCALE, Collections.emptyMap());

        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = normalize(file.getName().substring(0, file.getName().length() - 4));
                YamlConfiguration yaml = new YamlConfiguration();
                try {
                    yaml.load(file);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Cannot read translation file " + file.getName(), ex);
                    continue;
                }
                Map<String, Object> values = new HashMap<>();
                Map<String, Object> bundled = defaults.get(code);
                if (bundled != null) {
                    values.putAll(bundled);
                }
                values.putAll(flatten(yaml));
                bundles.put(code, values);
            }
        }
        for (String code : BUNDLED) {
            bundles.putIfAbsent(code, defaults.get(code));
        }

        String requested = configuredLanguage == null || configuredLanguage.isBlank()
                ? Locale.getDefault().toString() : configuredLanguage;
        serverLocale = resolve(requested);
    }

    private Map<String, Object> readBundled(String code) {
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in == null) {
                return Collections.emptyMap();
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            return flatten(yaml);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Cannot read bundled translation " + code, ex);
            return Collections.emptyMap();
        }
    }

    private static Map<String, Object> flatten(ConfigurationSection section) {
        Map<String, Object> out = new HashMap<>();
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            if (value instanceof List<?> list) {
                List<String> lines = new ArrayList<>(list.size());
                for (Object line : list) {
                    lines.add(line == null ? "" : String.valueOf(line));
                }
                out.put(key, lines);
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        }
        return out;
    }

    /** Normalizes "pl-pl", "PL_PL" or "pl" into "pl_PL" / "pl". */
    public static String normalize(String code) {
        String cleaned = code.trim().replace('-', '_');
        int sep = cleaned.indexOf('_');
        if (sep < 0) {
            return cleaned.toLowerCase(Locale.ROOT);
        }
        return cleaned.substring(0, sep).toLowerCase(Locale.ROOT) + "_"
                + cleaned.substring(sep + 1).toUpperCase(Locale.ROOT);
    }

    private String resolve(String requested) {
        if (requested == null || requested.isBlank()) {
            return serverLocale;
        }
        String code = normalize(requested);
        if (bundles.containsKey(code)) {
            return code;
        }
        String language = code.contains("_") ? code.substring(0, code.indexOf('_')) : code;
        for (String available : bundles.keySet()) {
            if (available.equals(language) || available.startsWith(language + "_")) {
                return available;
            }
        }
        return FALLBACK_LOCALE;
    }

    public String serverLocale() {
        return serverLocale;
    }

    /** Locale used for a recipient: the player's client language if enabled, else the server language. */
    public String localeOf(CommandSender sender) {
        if (perPlayer && sender instanceof Player player) {
            String requested = player.getLocale();
            if (requested != null && !requested.isBlank()) {
                String code = normalize(requested);
                String language = code.contains("_") ? code.substring(0, code.indexOf('_')) : code;
                for (String available : bundles.keySet()) {
                    if (available.equals(code)) {
                        return available;
                    }
                }
                for (String available : bundles.keySet()) {
                    if (available.startsWith(language + "_") || available.equals(language)) {
                        return available;
                    }
                }
            }
        }
        return serverLocale;
    }

    private Object raw(String locale, String key) {
        Map<String, Object> bundle = bundles.get(locale);
        Object value = bundle == null ? null : bundle.get(key);
        if (value == null) {
            value = fallback.get(key);
        }
        return value;
    }

    /** Formatted message for a recipient (null recipient = server language). Lists are joined with new lines. */
    public String get(CommandSender sender, String key, Object... args) {
        return getIn(localeOf(sender), key, args);
    }

    public String getIn(String locale, String key, Object... args) {
        Object value = raw(locale, key);
        if (value == null) {
            return key;
        }
        String template = value instanceof List<?> list ? String.join("\n", stringList(list)) : value.toString();
        return format(Text.color(template), args);
    }

    /** Multi-line message (YAML list, or a string containing new lines). */
    public List<String> lines(CommandSender sender, String key, Object... args) {
        Object value = raw(localeOf(sender), key);
        List<String> out = new ArrayList<>();
        if (value == null) {
            out.add(key);
            return out;
        }
        List<String> source = value instanceof List<?> list ? stringList(list) : List.of(value.toString().split("\n", -1));
        for (String line : source) {
            out.add(format(Text.color(line), args));
        }
        return out;
    }

    /**
     * Plural-aware message: looks up {@code key.one}, {@code key.few}, {@code key.many} or {@code key.other}
     * depending on the language rules. The count is placeholder {0}; extra arguments start at {1}.
     */
    public String plural(CommandSender sender, String key, long count, Object... args) {
        String locale = localeOf(sender);
        Object[] all = new Object[args.length + 1];
        all[0] = count;
        System.arraycopy(args, 0, all, 1, args.length);
        String form = pluralForm(locale, count);
        for (String candidate : new String[] {form, "other", "many", "one"}) {
            String fullKey = key + "." + candidate;
            if (raw(locale, fullKey) != null) {
                return getIn(locale, fullKey, all);
            }
        }
        return getIn(locale, key, all);
    }

    public void send(CommandSender sender, String key, Object... args) {
        sendRaw(sender, get(sender, key, args));
    }

    public void sendPlural(CommandSender sender, String key, long count, Object... args) {
        sendRaw(sender, plural(sender, key, count, args));
    }

    public static void sendRaw(CommandSender sender, String text) {
        if (sender == null || text == null || text.isEmpty()) {
            return;
        }
        for (String line : text.split("\n", -1)) {
            sender.sendMessage(line);
        }
    }

    public void actionBar(Player player, String key, Object... args) {
        Text.actionBar(player, get(player, key, args));
    }

    private static List<String> stringList(List<?> list) {
        List<String> out = new ArrayList<>(list.size());
        for (Object line : list) {
            out.add(line == null ? "" : line.toString());
        }
        return out;
    }

    static String pluralForm(String locale, long count) {
        String language = locale.toLowerCase(Locale.ROOT);
        int sep = language.indexOf('_');
        if (sep > 0) {
            language = language.substring(0, sep);
        }
        long n = Math.abs(count);
        switch (language) {
            case "pl": {
                if (n == 1) {
                    return "one";
                }
                long mod10 = n % 10;
                long mod100 = n % 100;
                if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
                    return "few";
                }
                return "many";
            }
            case "fr":
                return n <= 1 ? "one" : "other";
            default:
                return n == 1 ? "one" : "other";
        }
    }

    static String format(String template, Object... args) {
        if (args == null || args.length == 0 || template.indexOf('{') < 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 16);
        int i = 0;
        int length = template.length();
        while (i < length) {
            char c = template.charAt(i);
            if (c == '{') {
                int close = template.indexOf('}', i + 1);
                if (close > i + 1 && close - i <= 4 && isDigits(template, i + 1, close)) {
                    int index = Integer.parseInt(template.substring(i + 1, close));
                    if (index < args.length) {
                        out.append(args[index]);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isDigits(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
