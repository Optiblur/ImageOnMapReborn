package pl.iomreborn.update;

import java.util.regex.Pattern;

/** Version numbers like {@code 1.2.0}, {@code v1.2} or {@code 1.3.0-beta1}, compared number by number. */
public final class Version {

    private static final Pattern VALID = Pattern.compile("^[vV]?\\d{1,6}(\\.\\d{1,6}){0,3}([-+][0-9A-Za-z.\\-]{1,20})?$");

    private Version() {
    }

    /** True if the text is a version number (and not, for example, an error page). */
    public static boolean isValid(String text) {
        return text != null && VALID.matcher(text.trim()).matches();
    }

    /**
     * Compares two version numbers: positive if {@code a} is newer, negative if older, 0 if equal.
     * Missing parts count as 0 ({@code 1.2} = {@code 1.2.0}); a pre-release ({@code 1.2.0-beta}) is older than
     * the release with the same numbers.
     */
    public static int compare(String a, String b) {
        String[] partsA = numbers(a);
        String[] partsB = numbers(b);
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            long x = i < partsA.length ? parse(partsA[i]) : 0;
            long y = i < partsB.length ? parse(partsB[i]) : 0;
            if (x != y) {
                return x > y ? 1 : -1;
            }
        }
        boolean preA = isPreRelease(a);
        boolean preB = isPreRelease(b);
        if (preA != preB) {
            return preA ? -1 : 1;
        }
        return 0;
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static String clean(String version) {
        String v = version == null ? "" : version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v;
    }

    private static String[] numbers(String version) {
        String v = clean(version);
        int end = 0;
        while (end < v.length() && (Character.isDigit(v.charAt(end)) || v.charAt(end) == '.')) {
            end++;
        }
        String numeric = v.substring(0, end);
        return numeric.isEmpty() ? new String[0] : numeric.split("\\.");
    }

    private static boolean isPreRelease(String version) {
        String v = clean(version);
        int dash = v.indexOf('-');
        return dash >= 0 && dash < v.length() - 1;
    }

    private static long parse(String part) {
        if (part.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
