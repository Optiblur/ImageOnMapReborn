package pl.iomreborn.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings config files written by older versions up to date: options introduced by new versions are copied, with
 * their comments, from the config.yml bundled in the jar and appended at the end of the file, and the Polish
 * comments of version 1.0.0 are replaced with the English ones. The admin's values and own comments are kept.
 */
public final class ConfigUpdater {

    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_.\\-]+)\\s*:.*$");

    /** A top-level option of the bundled file: its key and its lines (comments above it included). */
    record Block(String key, List<String> lines) {
    }

    private ConfigUpdater() {
    }

    /** Splits a config file into top-level options. The header comment of the file belongs to no option. */
    static List<Block> blocks(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<Block> blocks = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.isBlank()) {
                comments.clear();
                i++;
                continue;
            }
            if (line.startsWith("#")) {
                comments.add(line);
                i++;
                continue;
            }
            Matcher matcher = TOP_LEVEL_KEY.matcher(line);
            if (!matcher.matches()) {
                comments.clear();
                i++;
                continue;
            }
            List<String> blockLines = new ArrayList<>(comments);
            comments.clear();
            blockLines.add(line);
            i++;
            // Indented lines (values, nested options and their comments) belong to the option.
            while (i < lines.length && (lines[i].startsWith(" ") || lines[i].startsWith("\t")
                    || (lines[i].isBlank() && nextIsIndented(lines, i)))) {
                blockLines.add(lines[i]);
                i++;
            }
            while (!blockLines.isEmpty() && blockLines.get(blockLines.size() - 1).isBlank()) {
                blockLines.remove(blockLines.size() - 1);
            }
            blocks.add(new Block(matcher.group(1), blockLines));
        }
        return blocks;
    }

    private static boolean nextIsIndented(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                return lines[i].startsWith(" ") || lines[i].startsWith("\t");
            }
        }
        return false;
    }

    /**
     * The comments of the config.yml written by version 1.0.0 (Polish) and their English text, so old files get
     * the same English comments as new ones. Keys and values are the text after {@code #}.
     */
    public static final Map<String, String> OLD_CONFIG_COMMENTS = Map.ofEntries(
            Map.entry("ImageOnMapReborn – konfiguracja / configuration", "ImageOnMapReborn – configuration"),
            Map.entry("Opcje wspólne z ImageOnMap mają te same nazwy i przy pierwszym uruchomieniu",
                    "Options shared with ImageOnMap have the same names and are copied from"),
            Map.entry("są kopiowane z plugins/ImageOnMap/config.yml.",
                    "plugins/ImageOnMap/config.yml on the first start."),
            Map.entry("Język pluginu: pl-PL lub en-US. Pusty = język systemu.",
                    "Language of the plugin: pl-PL or en-US. Empty = the language of the system."),
            Map.entry("Pliki tłumaczeń są w plugins/ImageOnMapReborn/lang/ i można je edytować",
                    "The translation files are in plugins/ImageOnMapReborn/lang/ and can be edited"),
            Map.entry("(albo dodać nowy język, np. de_DE.yml).", "(or add a new language, e.g. de_DE.yml)."),
            Map.entry("true = wiadomości i GUI w języku klienta gracza (jeśli jest takie tłumaczenie),",
                    "true = messages and menus in the language of each player's game (if it is translated),"),
            Map.entry("false = zawsze język z opcji \"lang\".", "false = always the language of the \"lang\" option."),
            Map.entry("Każdy obraz zużywa mapy Minecrafta (ID). Limit liczby map na cały serwer",
                    "Every image uses Minecraft maps (map IDs). Limit of maps for the whole server"),
            Map.entry("i na jednego gracza. 0 = bez limitu.", "and for each player. 0 = no limit."),
            Map.entry("Limit można też dać uprawnieniem: imageonmap.maplimit.<liczba> i imageonmap.imagelimit.<liczba>.",
                    "Limits can also be given with permissions: imageonmap.maplimit.<number> and "
                            + "imageonmap.imagelimit.<number>."),
            Map.entry("Maksymalny rozmiar obrazka źródłowego w pikselach. 0 = bez limitu.",
                    "Maximum size of the source picture, in pixels. 0 = no limit."),
            Map.entry("Czy zapisywać też pełny obraz (images/_<pierwsza>-<ostatnia>.png).",
                    "Also save the full picture (images/_<first>-<last>.png)."),
            Map.entry("Lista dozwolonych stron z obrazkami. Pusta = wszystkie.",
                    "Websites pictures may come from. Empty = all of them."),
            Map.entry("Uwaga: jak w ImageOnMap, uprawnienie imageonmap.bypasswhitelist (domyślnie dla wszystkich)",
                    "Note: like in ImageOnMap, the imageonmap.bypasswhitelist permission (everyone has it by default)"),
            Map.entry("pomija tę listę – żeby ją wymusić, odbierz to uprawnienie (np. w LuckPerms ustaw false).",
                    "skips this list – to enforce the list, take that permission away (e.g. set it to false in "
                            + "LuckPerms)."),
            Map.entry("Można użyć *.domena.pl dla wszystkich subdomen.", "Use *.example.com for all subdomains."),
            Map.entry("Limit czasu połączenia i odczytu (sekundy).",
                    "Time limit for connecting and for reading (seconds)."),
            Map.entry("Maksymalny rozmiar pobieranego pliku (MB).", "Maximum size of a downloaded file (MB)."),
            Map.entry("Blokuje linki do adresów w sieci lokalnej (127.0.0.1, 192.168.x.x itd.),",
                    "Blocks links to local network addresses (127.0.0.1, 192.168.x.x and so on),"),
            Map.entry("żeby gracze nie mogli przez serwer odpytywać wewnętrznych usług.",
                    "so players cannot use the server to reach internal services."),
            Map.entry("Maksymalna liczba pikseli obrazu (szerokość x wysokość) – chroni RAM serwera.",
                    "Maximum number of pixels of a picture (width x height) – protects the server's RAM."),
            Map.entry("40 000 000 to ok. 6300 x 6300 px. Dotyczy też docelowego rozmiaru plakatu.",
                    "40 000 000 is about 6300 x 6300 px. It also applies to the size of a poster."),
            Map.entry("true = płynne skalowanie przy zmniejszaniu (lepsze zdjęcia),",
                    "true = smooth scaling when shrinking (better photos),"),
            Map.entry("false = skalowanie \"piksel w piksel\" jak w starym ImageOnMap.",
                    "false = \"pixel by pixel\" scaling like the old ImageOnMap."),
            Map.entry("Chroni ramki z obrazami przed mobami, strzałami i wybuchami",
                    "Protects item frames holding pictures from mobs, arrows and explosions"),
            Map.entry("(zdjąć mapę może tylko gracz).", "(only players can take a map down)."),
            Map.entry("Automatyczny jednorazowy import danych ze starego ImageOnMap przy pierwszym starcie.",
                    "One-time automatic import of the old ImageOnMap data on the first start."),
            Map.entry("Po imporcie powstaje plik legacy-import.yml. Ponowny import: /maptool import",
                    "It creates the legacy-import.yml file. To import again: /maptool import"),
            Map.entry("Nazwa folderu starego pluginu w katalogu plugins/.", "Name of the old plugin's folder in plugins/."));

    /** The Polish header comments of lang/pl_PL.yml written by version 1.0.0, and their English text. */
    public static final Map<String, String> OLD_POLISH_LANG_COMMENTS = Map.of(
            "ImageOnMapReborn – wiadomości po polsku.", "ImageOnMapReborn – Polish messages.",
            "Kolory: &a, &c... (oraz &#RRGGBB). Zmienne: {0}, {1}...",
            "Color codes: &a, &c... (and &#RRGGBB). Placeholders: {0}, {1}...",
            "Brakujące klucze są brane z wersji wbudowanej w plugin.",
            "Keys missing from this file are taken from the version bundled with the plugin.");

    /**
     * Replaces comment lines that exactly match a known old comment with its new text. Everything else (values,
     * the admin's own comments, line endings) stays as it is.
     *
     * @param translations old comment text (after {@code #}, trimmed) to new comment text
     */
    public static String translateComments(String text, Map<String, String> translations) {
        String separator = text.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = text.split("\r?\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int hash = line.indexOf('#');
            if (hash >= 0 && line.substring(0, hash).isBlank()) {
                String replacement = translations.get(line.substring(hash + 1).strip());
                if (replacement != null) {
                    line = line.substring(0, hash) + "# " + replacement;
                }
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append(separator);
            }
        }
        return out.toString();
    }

    /**
     * Text to append to a config file so it gets the options it lacks.
     *
     * @param bundled the config.yml bundled in the jar
     * @param present tells whether the admin's file already has a top-level key
     * @param added   receives the names of the added options
     * @return the text to append, or an empty string if nothing is missing
     */
    public static String missingOptions(String bundled, Predicate<String> present, List<String> added) {
        StringBuilder out = new StringBuilder();
        for (Block block : blocks(bundled)) {
            if (present.test(block.key())) {
                continue;
            }
            added.add(block.key());
            out.append('\n');
            for (String line : block.lines()) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
}
