package pl.iomreborn.image;

import java.util.Locale;

/**
 * How colors that maps cannot show are approximated. Maps only have about 200 colors: without dithering every
 * pixel takes the closest one (flat areas, visible bands in gradients); with dithering the available colors are
 * mixed in small dots, which looks much closer to the original from a distance.
 */
public enum Dithering {
    /** Closest color only, like ImageOnMap. Best for pixel art and logos. */
    NONE("none"),
    /** Error diffusion: the best quality for photos. */
    FLOYD_STEINBERG("floyd-steinberg"),
    /**
     * A regular dot pattern (Knoll pattern dithering). Nearly as good as error diffusion, and a pixel only depends
     * on its own color and position, so the still parts of animations don't flicker.
     */
    ORDERED("ordered");

    private final String configName;

    Dithering(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    /** Animations never use error diffusion: it makes the still parts of a moving picture flicker. */
    public Dithering forAnimation() {
        return this == FLOYD_STEINBERG ? ORDERED : this;
    }

    /** Parses the config value; unknown values give {@code fallback}. YAML booleans are accepted too. */
    public static Dithering parse(String value, Dithering fallback) {
        if (value == null) {
            return fallback;
        }
        switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "none":
            case "off":
            case "no":
            case "false":
                return NONE;
            case "floyd-steinberg":
            case "floyd":
            case "fs":
            case "on":
            case "yes":
            case "true":
                return FLOYD_STEINBERG;
            case "ordered":
            case "bayer":
                return ORDERED;
            default:
                return fallback;
        }
    }

    /**
     * Recognizes the words of /tomap and /maptool update that turn dithering on or off for one image.
     *
     * @return TRUE for "dither", FALSE for "nodither", null if the word is something else
     */
    public static Boolean parseCommandWord(String word) {
        if (word == null) {
            return null;
        }
        String w = word.toLowerCase(Locale.ROOT);
        if (w.startsWith("--")) {
            w = w.substring(2);
        }
        switch (w) {
            case "dither":
            case "dithering":
                return Boolean.TRUE;
            case "nodither":
            case "no-dither":
            case "nodithering":
            case "no-dithering":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    /** The method used for one image: the configured one, unless the player asked for dithering on or off. */
    public static Dithering choose(Boolean requested, Dithering configured) {
        if (requested == null) {
            return configured;
        }
        if (!requested) {
            return NONE;
        }
        return configured == NONE ? FLOYD_STEINBERG : configured;
    }
}
