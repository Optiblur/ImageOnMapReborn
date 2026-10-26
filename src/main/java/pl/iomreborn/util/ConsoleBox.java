package pl.iomreborn.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * A framed block of text for the server console, like the one printed when the plugin starts. Only ASCII is used
 * for the frame, so it looks the same in every console (also on Windows); colors are the usual section-sign codes,
 * which Spigot and Paper turn into console colors and strip from the log file.
 */
public final class ConsoleBox {

    private static final String FRAME = ChatColor.DARK_GRAY.toString();
    private static final String LABEL = ChatColor.GRAY.toString();
    private static final String VALUE = ChatColor.WHITE.toString();
    private static final int MIN_WIDTH = 46;

    /** A row of the box: a line of text, a label with its value, or a separator (both null). */
    private record Row(String text, String label) {
    }

    private final List<Row> rows = new ArrayList<>();

    /** Adds a line of text (it may contain color codes). */
    public ConsoleBox line(String text) {
        rows.add(new Row(text == null ? "" : text, null));
        return this;
    }

    /** Adds a "label  value" row; the values of all rows start in the same column. */
    public ConsoleBox row(String label, String value) {
        rows.add(new Row(value == null ? "" : value, label));
        return this;
    }

    /** Adds a horizontal line. */
    public ConsoleBox separator() {
        rows.add(new Row(null, null));
        return this;
    }

    /** The lines of the box, with color codes. */
    public List<String> render() {
        int labelWidth = 0;
        for (Row row : rows) {
            if (row.label() != null) {
                labelWidth = Math.max(labelWidth, row.label().length());
            }
        }
        List<String> contents = new ArrayList<>(rows.size());
        int width = MIN_WIDTH;
        for (Row row : rows) {
            String content = null;
            if (row.text() != null) {
                content = row.label() == null ? row.text()
                        : LABEL + pad(row.label(), labelWidth) + "  " + VALUE + row.text();
                width = Math.max(width, visibleLength(content));
            }
            contents.add(content);
        }

        String border = FRAME + "+" + "-".repeat(width + 2) + "+";
        List<String> lines = new ArrayList<>(rows.size() + 2);
        lines.add(border);
        for (String content : contents) {
            if (content == null) {
                lines.add(border);
            } else {
                String space = " ".repeat(width - visibleLength(content));
                lines.add(FRAME + "| " + VALUE + content + space + " " + FRAME + "|");
            }
        }
        lines.add(border);
        return lines;
    }

    /** Prints the box to the server console. */
    public void print() {
        CommandSender console = Bukkit.getConsoleSender();
        for (String line : render()) {
            console.sendMessage(line);
        }
    }

    /** Length of the text as shown, without its color codes. */
    static int visibleLength(String text) {
        String plain = ChatColor.stripColor(text);
        return plain == null ? 0 : plain.length();
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
