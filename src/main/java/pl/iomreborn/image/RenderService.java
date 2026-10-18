package pl.iomreborn.image;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.config.PluginConfig;
import pl.iomreborn.i18n.Messages;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.LimitContext;
import pl.iomreborn.map.MapManager;
import pl.iomreborn.map.PosterMap;
import pl.iomreborn.map.SingleMap;
import pl.iomreborn.storage.ImageStorage;
import pl.iomreborn.util.Tasks;
import pl.iomreborn.util.Text;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Turns an image URL into maps: download, decode, scale and convert colors in the background,
 * then allocate Minecraft maps and register the image on the main thread.
 */
public final class RenderService {

    private final ImageOnMapReborn plugin;
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, BukkitTask> actionBars = new HashMap<>();

    public RenderService(ImageOnMapReborn plugin) {
        this.plugin = plugin;
    }

    /** Who asked for the work; players are looked up again when it ends, as they may have left. */
    private record Requester(UUID playerId, CommandSender other) {
        static Requester of(CommandSender sender) {
            return sender instanceof Player player ? new Requester(player.getUniqueId(), null) : new Requester(null, sender);
        }

        CommandSender resolve() {
            return playerId != null ? Bukkit.getPlayer(playerId) : other;
        }
    }

    private record Prepared(int columns, int rows, BufferedImage[] tiles, byte[][] pixels, BufferedImage full) {
    }

    @FunctionalInterface
    private interface Job {
        void run() throws Exception;
    }

    /** /tomap: renders a new image for the player. widthMaps/heightMaps are only used with a scaling mode. */
    public void create(Player player, String url, ScalingMode mode, int widthMaps, int heightMaps) {
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            plugin.messages().send(player, "render.busy");
            return;
        }
        LimitContext limits = LimitContext.of(player);
        try {
            ImageDownloader.parseUrl(url);
            plugin.mapManager().checkCreateLimits(uuid, 1, limits);
        } catch (RenderException ex) {
            sendFailure(player, ex);
            return;
        }

        PluginConfig config = plugin.settings();
        ImageDownloader.Request request = request(url, config, !Perm.BYPASS_WHITELIST.has(player));
        boolean bypassSize = Perm.BYPASS_SIZE.has(player);
        Requester requester = Requester.of(player);
        busy.add(uuid);
        startActionBar(player, "render.started");

        submit(requester, () -> {
            byte[] data = ImageDownloader.download(request);
            BufferedImage source = ImageDecoder.decode(data, (w, h) -> checkSourceSize(w, h, bypassSize, config));
            BufferedImage canvas = fit(source, mode, widthMaps, heightMaps, config);
            ImageProcessing.Poster poster = ImageProcessing.split(canvas);
            checkLimitsFromWorker(uuid, poster.tiles().length, limits);
            Prepared prepared = convert(poster, config.saveFullImage() ? canvas : null);
            Tasks.sync(() -> finishCreate(requester, prepared, limits));
        });
    }

    /** /maptool update: draws a new image on the maps of an existing image, keeping its size. */
    public void update(CommandSender sender, ImageMap map, String url, ScalingMode mode) {
        Requester requester = Requester.of(sender);
        if (requester.playerId() != null && busy.contains(requester.playerId())) {
            plugin.messages().send(sender, "render.busy");
            return;
        }
        try {
            ImageDownloader.parseUrl(url);
        } catch (RenderException ex) {
            sendFailure(sender, ex);
            return;
        }

        PluginConfig config = plugin.settings();
        ScalingMode scaling = mode == null || mode == ScalingMode.NONE ? ScalingMode.CONTAINED : mode;
        int columns = map instanceof PosterMap poster ? poster.layoutColumns() : 1;
        int rows = map instanceof PosterMap poster ? poster.layoutRows() : 1;
        int[] mapIds = map.getMapIds();
        boolean isPlayer = sender instanceof Player;
        ImageDownloader.Request request = request(url, config, isPlayer && !Perm.BYPASS_WHITELIST.has(sender));
        boolean bypassSize = !isPlayer || Perm.BYPASS_SIZE.has(sender);

        if (requester.playerId() != null) {
            busy.add(requester.playerId());
            startActionBar((Player) sender, "render.update-started");
        }
        submit(requester, () -> {
            byte[] data = ImageDownloader.download(request);
            BufferedImage source = ImageDecoder.decode(data, (w, h) -> checkSourceSize(w, h, bypassSize, config));
            BufferedImage canvas = ImageProcessing.resize(source, columns * ImageProcessing.MAP_SIZE,
                    rows * ImageProcessing.MAP_SIZE, scaling, config.smoothScaling());
            ImageProcessing.Poster poster = ImageProcessing.split(canvas);
            if (poster.tiles().length != mapIds.length) {
                throw new IllegalStateException("Expected " + mapIds.length + " tiles, got " + poster.tiles().length);
            }
            Prepared prepared = convert(poster, config.saveFullImage() ? canvas : null);
            Tasks.sync(() -> finishUpdate(requester, map, mapIds, prepared));
        });
    }

    private static ImageDownloader.Request request(String url, PluginConfig config, boolean enforceWhitelist) {
        return new ImageDownloader.Request(url, config.downloadTimeoutSeconds() * 1000,
                config.maxDownloadMegabytes() * 1024L * 1024L, config.blockPrivateAddresses(), config.userAgent(),
                config.hostnamesWhitelist(), enforceWhitelist);
    }

    private static void checkSourceSize(int width, int height, boolean bypass, PluginConfig config)
            throws RenderException {
        if ((long) width * height > config.maxImagePixels()) {
            throw new RenderException("error.image-too-big", width, height);
        }
        if (bypass) {
            return;
        }
        if (config.limitSizeX() > 0 && width > config.limitSizeX()) {
            throw new RenderException("error.image-too-wide", config.limitSizeX());
        }
        if (config.limitSizeY() > 0 && height > config.limitSizeY()) {
            throw new RenderException("error.image-too-tall", config.limitSizeY());
        }
    }

    private static BufferedImage fit(BufferedImage source, ScalingMode mode, int widthMaps, int heightMaps,
                                     PluginConfig config) throws RenderException {
        if (mode == ScalingMode.NONE) {
            return source;
        }
        if (widthMaps <= 1 && heightMaps <= 1) {
            return ImageProcessing.resize(source, ImageProcessing.MAP_SIZE, ImageProcessing.MAP_SIZE, mode,
                    config.smoothScaling());
        }
        long pixels = (long) widthMaps * ImageProcessing.MAP_SIZE * heightMaps * ImageProcessing.MAP_SIZE;
        if (pixels > config.maxImagePixels()) {
            throw new RenderException("error.poster-too-big", widthMaps, heightMaps);
        }
        return ImageProcessing.resize(source, widthMaps * ImageProcessing.MAP_SIZE,
                heightMaps * ImageProcessing.MAP_SIZE, mode, config.smoothScaling());
    }

    private static Prepared convert(ImageProcessing.Poster poster, BufferedImage full) {
        BufferedImage[] tiles = poster.tiles();
        byte[][] pixels = new byte[tiles.length][];
        for (int i = 0; i < tiles.length; i++) {
            pixels[i] = MapColors.toMapBytes(tiles[i]);
        }
        return new Prepared(poster.columns(), poster.rows(), tiles, pixels, full);
    }

    /** Fails early (before the slow color conversion) when the limits are already exceeded. */
    private void checkLimitsFromWorker(UUID owner, int mapCount, LimitContext limits) throws Exception {
        Future<RenderException> check = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            try {
                plugin.mapManager().checkCreateLimits(owner, mapCount, limits);
                return null;
            } catch (RenderException ex) {
                return ex;
            }
        });
        RenderException failure;
        try {
            failure = check.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException ex) {
            return; // checked again on the main thread before maps are created
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void submit(Requester requester, Job job) {
        try {
            plugin.workExecutor().execute(() -> {
                try {
                    job.run();
                } catch (RenderException ex) {
                    Tasks.sync(() -> fail(requester, ex));
                } catch (OutOfMemoryError ex) {
                    plugin.getLogger().severe("Not enough memory to render an image; lower max-image-pixels in config.yml.");
                    Tasks.sync(() -> fail(requester, new RenderException("error.out-of-memory")));
                } catch (Throwable ex) {
                    plugin.getLogger().log(Level.WARNING, "Rendering an image failed", ex);
                    Tasks.sync(() -> fail(requester, new RenderException("error.internal")));
                }
            });
        } catch (RejectedExecutionException ex) {
            fail(requester, new RenderException("error.internal"));
        }
    }

    private void release(Requester requester) {
        if (requester.playerId() != null) {
            busy.remove(requester.playerId());
            stopActionBar(requester.playerId());
        }
    }

    private void fail(Requester requester, RenderException ex) {
        release(requester);
        CommandSender sender = requester.resolve();
        if (sender != null) {
            sendFailure(sender, ex);
        }
    }

    private void sendFailure(CommandSender sender, RenderException ex) {
        Messages messages = plugin.messages();
        messages.send(sender, "render.failed", messages.get(sender, ex.getKey(), ex.getArgs()));
    }

    private void finishCreate(Requester requester, Prepared prepared, LimitContext limits) {
        release(requester);
        UUID owner = requester.playerId();
        Player player = Bukkit.getPlayer(owner);
        MapManager maps = plugin.mapManager();
        int count = prepared.tiles().length;
        try {
            maps.checkCreateLimits(owner, count, limits);
        } catch (RenderException ex) {
            if (player != null) {
                sendFailure(player, ex);
            }
            return;
        }

        int[] mapIds = allocateMapIds(count);
        String name = maps.defaultName();
        String id = maps.nextId(owner, name);
        ImageMap image = count == 1
                ? new SingleMap(owner, id, name, mapIds[0])
                : new PosterMap(owner, id, name, mapIds, prepared.columns(), prepared.rows());
        maps.addMap(image);
        for (int i = 0; i < count; i++) {
            plugin.rendererManager().setPixels(mapIds[i], prepared.pixels()[i]);
        }
        saveImages(requester, mapIds, prepared);

        if (player != null) {
            plugin.messages().actionBar(player, "render.finished");
            if (plugin.mapItems().give(player, image)) {
                plugin.messages().send(player, "general.inventory-overflow");
            }
        }
    }

    private void finishUpdate(Requester requester, ImageMap map, int[] mapIds, Prepared prepared) {
        release(requester);
        CommandSender sender = requester.resolve();
        if (!plugin.mapManager().exists(map)) {
            if (sender != null) {
                plugin.messages().send(sender, "general.map-not-found");
            }
            return;
        }
        for (int i = 0; i < mapIds.length; i++) {
            plugin.rendererManager().setPixels(mapIds[i], prepared.pixels()[i]);
        }
        saveImages(requester, mapIds, prepared);
        if (sender instanceof Player player) {
            plugin.messages().actionBar(player, "render.update-finished");
        } else if (sender != null) {
            plugin.messages().send(sender, "render.update-finished");
        }
    }

    @SuppressWarnings("deprecation")
    private static int[] allocateMapIds(int count) {
        World world = Bukkit.getWorlds().get(0);
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = Bukkit.createMap(world).getId();
        }
        return ids;
    }

    private void saveImages(Requester requester, int[] mapIds, Prepared prepared) {
        ImageStorage storage = plugin.imageStorage();
        Runnable write = () -> {
            try {
                for (int i = 0; i < mapIds.length; i++) {
                    storage.write(storage.fileFor(mapIds[i]), prepared.tiles()[i]);
                }
                if (prepared.full() != null) {
                    storage.write(storage.fullImageFile(mapIds[0], mapIds[mapIds.length - 1]), prepared.full());
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Cannot save the image files of maps starting at #" + mapIds[0], ex);
                Tasks.sync(() -> {
                    CommandSender sender = requester.resolve();
                    if (sender != null) {
                        sendFailure(sender, new RenderException("error.save-failed"));
                    }
                });
            }
        };
        try {
            plugin.ioExecutor().execute(write);
        } catch (RejectedExecutionException ex) {
            write.run();
        }
    }

    private void startActionBar(Player player, String key) {
        UUID uuid = player.getUniqueId();
        stopActionBar(uuid);
        String text = plugin.messages().get(player, key);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Text.actionBar(online, text);
            }
        }, 0L, 30L);
        actionBars.put(uuid, task);
    }

    private void stopActionBar(UUID uuid) {
        BukkitTask task = actionBars.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        for (BukkitTask task : actionBars.values()) {
            task.cancel();
        }
        actionBars.clear();
        busy.clear();
    }
}
