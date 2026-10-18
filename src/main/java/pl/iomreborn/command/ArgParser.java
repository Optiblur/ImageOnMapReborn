package pl.iomreborn.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses command arguments like ImageOnMap: names may be quoted ({@code "A very cool map"}), a player can be
 * put in front of a map name with a colon ({@code Player:"Map name"}), URLs are kept whole and {@code --flags}
 * are collected separately.
 */
public final class ArgParser {

    /** One argument; {@code qualifier} is true for a {@code player:} prefix. */
    public record Token(String value, boolean qualifier, boolean quoted) {
    }

    public record Parsed(List<Token> tokens, Set<String> flags) {

        public int size() {
            return tokens.size();
        }

        public Token get(int index) {
            return tokens.get(index);
        }

        public boolean hasFlag(String flag) {
            return flags.contains(flag);
        }

        /** Values from {@code from} (inclusive) to {@code to} (exclusive) joined with spaces. */
        public String join(int from, int to) {
            StringBuilder out = new StringBuilder();
            for (int i = from; i < to; i++) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(tokens.get(i).value());
            }
            return out.toString();
        }

        public String joinFrom(int from) {
            return join(from, tokens.size());
        }
    }

    private ArgParser() {
    }

    public static boolean isUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    public static Parsed parse(String[] args) {
        List<Token> tokens = new ArrayList<>();
        Set<String> flags = new HashSet<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean quoted = false;

        for (String arg : args) {
            if (!inQuotes && current.length() == 0 && !quoted) {
                if (isUrl(arg)) {
                    tokens.add(new Token(arg, false, false));
                    continue;
                }
                if (arg.startsWith("--") && arg.length() > 2) {
                    flags.add(arg.substring(2).toLowerCase(Locale.ROOT));
                    continue;
                }
            }
            for (char c : arg.toCharArray()) {
                if (c == '"') {
                    inQuotes = !inQuotes;
                    quoted = true;
                } else if (c == ':' && !inQuotes && !quoted && current.length() > 0) {
                    tokens.add(new Token(current.toString(), true, false));
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            if (inQuotes) {
                current.append(' ');
            } else {
                if (current.length() > 0 || quoted) {
                    tokens.add(new Token(current.toString(), false, quoted));
                }
                current.setLength(0);
                quoted = false;
            }
        }
        if (inQuotes) {
            String rest = current.toString().trim();
            if (!rest.isEmpty()) {
                tokens.add(new Token(rest, false, true));
            }
        }
        return new Parsed(Collections.unmodifiableList(tokens), Collections.unmodifiableSet(flags));
    }
}
