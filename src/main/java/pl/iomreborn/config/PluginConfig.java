package pl.iomreborn.config;

import org.bukkit.configuration.file.FileConfiguration;
import pl.iomreborn.image.Dithering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot of config.yml. Option names shared with ImageOnMap keep their old keys. */
public final class PluginConfig {

    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36 ImageOnMapReborn";

    private final String lang;
    private final boolean perPlayerLanguage;
    private final int mapGlobalLimit;
    private final int mapPlayerLimit;
    private final int limitSizeX;
    private final int limitSizeY;
    private final boolean saveFullImage;
    private final List<String> hostnamesWhitelist;
    private final int downloadTimeoutSeconds;
    private final int maxDownloadMegabytes;
    private final boolean blockPrivateAddresses;
    private final String userAgent;
    private final long maxImagePixels;
    private final boolean smoothScaling;
    private final boolean protectItemFrames;
    private final boolean legacyImportEnabled;
    private final String legacyImportFolder;
    private final Dithering dithering;
    private final boolean animationsEnabled;
    private final int animationMaxFrames;
    private final int animationMaxMaps;
    private final int animationMaxFps;
    private final boolean checkForUpdates;

    private PluginConfig(FileConfiguration c) {
        lang = c.getString("lang", "");
        perPlayerLanguage = c.getBoolean("per-player-language", false);
        mapGlobalLimit = Math.max(0, intWithAlias(c, "map-global-limit", "Limit-map-by-server"));
        mapPlayerLimit = Math.max(0, intWithAlias(c, "map-player-limit", "Limit-map-by-player"));
        limitSizeX = Math.max(0, c.getInt("limit-map-size-x", 0));
        limitSizeY = Math.max(0, c.getInt("limit-map-size-y", 0));
        saveFullImage = c.getBoolean("save-full-image", false);

        List<String> whitelist = new ArrayList<>();
        for (String host : c.getStringList("images-hostnames-whitelist")) {
            if (host != null && !host.isBlank()) {
                whitelist.add(host.trim());
            }
        }
        hostnamesWhitelist = Collections.unmodifiableList(whitelist);

        downloadTimeoutSeconds = clamp(c.getInt("download.timeout-seconds", 20), 1, 300);
        maxDownloadMegabytes = clamp(c.getInt("download.max-size-mb", 25), 1, 1024);
        blockPrivateAddresses = c.getBoolean("download.block-private-addresses", true);
        String ua = c.getString("download.user-agent", DEFAULT_USER_AGENT);
        userAgent = ua == null || ua.isBlank() ? DEFAULT_USER_AGENT : ua;
        maxImagePixels = Math.max(1_000_000L, c.getLong("max-image-pixels", 40_000_000L));
        smoothScaling = c.getBoolean("smooth-scaling", true);
        protectItemFrames = c.getBoolean("protect-item-frames", true);
        legacyImportEnabled = c.getBoolean("legacy-import.enabled", true);
        String folder = c.getString("legacy-import.folder", "ImageOnMap");
        legacyImportFolder = folder == null || folder.isBlank() ? "ImageOnMap" : folder.trim();
        dithering = Dithering.parse(c.getString("dithering", Dithering.FLOYD_STEINBERG.configName()),
                Dithering.FLOYD_STEINBERG);
        animationsEnabled = c.getBoolean("animated-gifs.enabled", true);
        animationMaxFrames = clamp(c.getInt("animated-gifs.max-frames", 100), 2, 1000);
        animationMaxMaps = clamp(c.getInt("animated-gifs.max-maps", 16), 1, 1000);
        animationMaxFps = clamp(c.getInt("animated-gifs.max-fps", 10), 1, 20);
        checkForUpdates = c.getBoolean("check-for-updates", true);
    }

    public static PluginConfig from(FileConfiguration config) {
        return new PluginConfig(config);
    }

    private static int intWithAlias(FileConfiguration c, String key, String alias) {
        if (!c.contains(key, true) && c.contains(alias, true)) {
            return c.getInt(alias);
        }
        return c.getInt(key, 0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public String lang() {
        return lang;
    }

    public boolean perPlayerLanguage() {
        return perPlayerLanguage;
    }

    public int mapGlobalLimit() {
        return mapGlobalLimit;
    }

    public int mapPlayerLimit() {
        return mapPlayerLimit;
    }

    public int limitSizeX() {
        return limitSizeX;
    }

    public int limitSizeY() {
        return limitSizeY;
    }

    public boolean saveFullImage() {
        return saveFullImage;
    }

    public List<String> hostnamesWhitelist() {
        return hostnamesWhitelist;
    }

    public int downloadTimeoutSeconds() {
        return downloadTimeoutSeconds;
    }

    public int maxDownloadMegabytes() {
        return maxDownloadMegabytes;
    }

    public boolean blockPrivateAddresses() {
        return blockPrivateAddresses;
    }

    public String userAgent() {
        return userAgent;
    }

    public long maxImagePixels() {
        return maxImagePixels;
    }

    public boolean smoothScaling() {
        return smoothScaling;
    }

    public boolean protectItemFrames() {
        return protectItemFrames;
    }

    public boolean legacyImportEnabled() {
        return legacyImportEnabled;
    }

    public String legacyImportFolder() {
        return legacyImportFolder;
    }

    /** Dithering used for new pictures, unless the player chooses with "dither" / "nodither". */
    public Dithering dithering() {
        return dithering;
    }

    public boolean animationsEnabled() {
        return animationsEnabled;
    }

    public int animationMaxFrames() {
        return animationMaxFrames;
    }

    /** Largest animated picture, in maps. */
    public int animationMaxMaps() {
        return animationMaxMaps;
    }

    /** Highest number of frame changes per second sent for animated maps. */
    public int animationMaxFps() {
        return animationMaxFps;
    }

    public boolean checkForUpdates() {
        return checkForUpdates;
    }
}
