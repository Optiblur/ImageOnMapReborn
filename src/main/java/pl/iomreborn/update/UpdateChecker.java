package pl.iomreborn.update;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.util.Tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks for a newer version of the plugin on SpigotMC (GitHub releases if SpigotMC does not answer), after the
 * server starts and then every 12 hours. A newer version is announced in the console and to players with
 * {@code imageonmap.notifyupdate} when they join. Nothing is downloaded or installed automatically, and no data
 * about the server is sent: it is a plain request for the latest version number.
 */
public final class UpdateChecker implements Listener {

    public static final String SPIGOT_PAGE = "https://www.spigotmc.org/resources/imageonmapreborn.139800/";
    static final String SPIGOT_API = "https://api.spigotmc.org/legacy/update.php?resource=139800";
    static final String GITHUB_API = "https://api.github.com/repos/Optiblur/ImageOnMapReborn/releases/latest";
    static final String GITHUB_RELEASES = "https://github.com/Optiblur/ImageOnMapReborn/releases/latest";

    private static final long FIRST_CHECK_TICKS = 20L * 10;
    private static final long PERIOD_TICKS = 20L * 60 * 60 * 12;
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]{1,40})\"");
    private static final Pattern RELEASE_URL =
            Pattern.compile("\"html_url\"\\s*:\\s*\"(https://github\\.com/[^\"\\s]{1,200}/releases/tag/[^\"\\s]{1,60})\"");

    /** A published version and the page to download it from. */
    public record Release(String version, String url) {
    }

    /** Somewhere the latest version can be read from. */
    @FunctionalInterface
    public interface Source {
        /** The latest release, or null if the answer was not usable. */
        Release fetch() throws IOException;
    }

    private final ImageOnMapReborn plugin;
    private final String currentVersion;
    private final List<Source> sources;
    private volatile Release available;
    private volatile boolean failureLogged;
    private BukkitTask task;
    private boolean listening;

    public UpdateChecker(ImageOnMapReborn plugin, String currentVersion) {
        this(plugin, currentVersion, defaultSources(currentVersion));
    }

    UpdateChecker(ImageOnMapReborn plugin, String currentVersion, List<Source> sources) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.sources = List.copyOf(sources);
    }

    static List<Source> defaultSources(String currentVersion) {
        String userAgent = "ImageOnMapReborn/" + currentVersion + " (update check)";
        return List.of(
                () -> parseSpigot(get(SPIGOT_API, userAgent, null)),
                () -> parseGitHub(get(GITHUB_API, userAgent, "application/vnd.github+json")));
    }

    /** Starts or stops the checks to match config.yml (at startup and after /maptool reload). */
    public void applySettings() {
        boolean enabled = plugin.settings().checkForUpdates();
        if (enabled && task == null) {
            if (!listening) {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                listening = true;
            }
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkNow, FIRST_CHECK_TICKS,
                    PERIOD_TICKS);
        } else if (!enabled && task != null) {
            task.cancel();
            task = null;
            available = null;
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
    }

    /** The newer version found, or null. */
    public Release available() {
        return available;
    }

    /** Asks the sources in turn for the latest version. Blocks: never call it on the main thread. */
    void checkNow() {
        Release release = latest(sources);
        if (release != null) {
            failureLogged = false;
            handle(release);
        } else if (!failureLogged) {
            failureLogged = true;
            plugin.getLogger().info("Could not check for a new version (no answer from SpigotMC or GitHub).");
        }
    }

    /** The first usable answer of the sources, trying the next one when a source fails. */
    static Release latest(List<Source> sources) {
        for (Source source : sources) {
            try {
                Release release = source.fetch();
                if (release != null) {
                    return release;
                }
            } catch (IOException | RuntimeException ex) {
                // try the next source
            }
        }
        return null;
    }

    /** True if the release is a valid version newer than the running one. */
    static boolean isUpdate(Release release, String currentVersion) {
        return release != null && Version.isValid(release.version())
                && Version.isNewer(release.version(), currentVersion);
    }

    /**
     * Remembers a release if it is newer than the running version and announces it once.
     *
     * @return true if the release is newer
     */
    public boolean handle(Release release) {
        if (!isUpdate(release, currentVersion)) {
            return false;
        }
        Release previous = available;
        available = release;
        if (previous == null || !previous.version().equals(release.version())) {
            Tasks.sync(() -> announce(release));
        }
        return true;
    }

    private void announce(Release release) {
        plugin.getLogger().info("A new version of ImageOnMapReborn is available: " + release.version()
                + " (this server runs " + currentVersion + "). Download: " + release.url());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Perm.NOTIFY_UPDATE.has(player)) {
                tell(player, release);
            }
        }
    }

    private void tell(CommandSender target, Release release) {
        plugin.messages().send(target, "update.available", release.version(), currentVersion);
        plugin.messages().send(target, "update.download", release.url());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Release release = available;
        Player player = event.getPlayer();
        if (release == null || task == null || !Perm.NOTIFY_UPDATE.has(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                tell(online, release);
            }
        }, 40L);
    }

    /** SpigotMC answers with the bare version number of the latest update. */
    static Release parseSpigot(String body) {
        String version = body == null ? "" : body.trim();
        return Version.isValid(version) ? new Release(version, SPIGOT_PAGE) : null;
    }

    /** GitHub answers with the JSON of the latest release; only its tag and page are needed. */
    static Release parseGitHub(String body) {
        if (body == null) {
            return null;
        }
        Matcher tag = TAG_NAME.matcher(body);
        if (!tag.find()) {
            return null;
        }
        String version = tag.group(1).trim();
        if (!Version.isValid(version)) {
            return null;
        }
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        Matcher url = RELEASE_URL.matcher(body);
        return new Release(version, url.find() ? url.group(1) : GITHUB_RELEASES);
    }

    @SuppressWarnings("deprecation")
    static String get(String address, String userAgent, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", userAgent);
            if (accept != null) {
                connection.setRequestProperty("Accept", accept);
            }
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_RESPONSE_BYTES) {
                        throw new IOException("Response too large");
                    }
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
