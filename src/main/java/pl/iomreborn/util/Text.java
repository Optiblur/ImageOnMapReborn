package pl.iomreborn.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Text() {
    }

    /** Translates {@code &} color codes, including {@code &#RRGGBB}, into section-sign codes. */
    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = HEX_COLOR.matcher(text);
        if (matcher.find()) {
            StringBuilder out = new StringBuilder();
            do {
                StringBuilder replacement = new StringBuilder("&x");
                for (char c : matcher.group(1).toCharArray()) {
                    replacement.append('&').append(c);
                }
                matcher.appendReplacement(out, replacement.toString());
            } while (matcher.find());
            matcher.appendTail(out);
            text = out.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static void actionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    /** Wraps a string in double quotes so it survives the command argument parser. */
    public static String quote(String value) {
        return "\"" + value.replace("\"", "") + "\"";
    }
}
