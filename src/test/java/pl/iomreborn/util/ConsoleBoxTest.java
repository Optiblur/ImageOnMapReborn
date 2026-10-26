package pl.iomreborn.util;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleBoxTest {

    private static List<String> plain(ConsoleBox box) {
        return box.render().stream().map(ChatColor::stripColor).toList();
    }

    @Test
    void everyLineHasTheSameWidthWhateverTheColors() {
        List<String> lines = plain(new ConsoleBox()
                .line(ChatColor.AQUA + "ImageOnMap" + ChatColor.DARK_AQUA + "Reborn " + ChatColor.WHITE + "1.2.0")
                .separator()
                .row("Images", "12" + ChatColor.GRAY + " (48 maps, 3 players)")
                .row("Language", "en_US"));
        int width = lines.get(0).length();
        for (String line : lines) {
            assertEquals(width, line.length(), line);
        }
        assertEquals(6, lines.size(), "two borders, a separator and three rows");
        assertTrue(lines.get(0).startsWith("+-") && lines.get(0).endsWith("-+"));
        assertEquals(lines.get(0), lines.get(2), "a separator looks like the border");
    }

    @Test
    void valuesStartInTheSameColumn() {
        List<String> lines = plain(new ConsoleBox().row("Images", "12").row("Language", "en_US"));
        assertEquals(lines.get(1).indexOf("12"), lines.get(2).indexOf("en_US"));
    }

    @Test
    void growsForLongLines() {
        String url = "https://www.spigotmc.org/resources/imageonmapreborn.139800/";
        List<String> lines = plain(new ConsoleBox().row("Download", url));
        assertTrue(lines.get(1).contains(url));
        assertEquals(lines.get(0).length(), lines.get(1).length());
    }

    @Test
    void usesOnlyAsciiForTheFrame() {
        for (String line : plain(new ConsoleBox().line("x").separator().row("a", "b"))) {
            assertTrue(line.chars().allMatch(c -> c < 128), line);
        }
    }
}
