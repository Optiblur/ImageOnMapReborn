package pl.iomreborn.image;

import java.util.Locale;
import java.util.Optional;

/** How an image is fitted into the requested number of maps (same modes as ImageOnMap). */
public enum ScalingMode {
    /** Keep the original size; the image is cut into as many maps as needed. */
    NONE,
    /** Fit inside the area, keeping proportions (transparent borders may appear). Command word: "resize". */
    CONTAINED,
    /** Fill the whole area, keeping proportions (edges may be cut). Command word: "resize-covered". */
    COVERED,
    /** Fill the whole area, ignoring proportions. Command word: "resize-stretched". */
    STRETCHED;

    /** Parses the mode words accepted by ImageOnMap's /tomap and /maptool update. */
    public static Optional<ScalingMode> parse(String word) {
        if (word == null) {
            return Optional.empty();
        }
        switch (word.toLowerCase(Locale.ROOT)) {
            case "resize":
            case "contain":
            case "contained":
                return Optional.of(CONTAINED);
            case "resize-stretched":
            case "stretch":
            case "stretched":
                return Optional.of(STRETCHED);
            case "resize-covered":
            case "cover":
            case "covered":
                return Optional.of(COVERED);
            default:
                return Optional.empty();
        }
    }
}
