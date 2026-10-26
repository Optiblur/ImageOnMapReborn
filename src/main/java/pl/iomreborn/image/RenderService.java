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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Turns an image URL (or a picture of the upload folder) into maps: download, decode, scale and convert colors in
 * the background, then allocate Minecraft maps and register the image on the main thread. Animated GIFs become
 * animated maps.
 */
public final class RenderService {

    /** Animated GIFs with more pixels per frame are only used as a still picture (memory needed per frame). */
    static final long ANIMATION_MAX_PIXELS = 4_000_000L;

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

    /**
     * A rendered picture: the tiles saved as PNG files, their map colors, and for animated pictures the frames of
     * every map (the tiles and colors then show the first frame).
     */
    record Prepared(int columns, int rows, BufferedImage[] tiles, byte[][] pixels, Animation[] animations,
                            BufferedImage full) {
        int frameCount() {
            return animations == null ? 1 : animations[0].frameCount();
        }
    }

    /** A message for the requester sent once the picture is ready. */
    record Note(String key, Object... args) {
    }

    /** Whether a GIF may become an animated map. */
    enum AnimationPolicy { ALLOWED, NOT_ALLOWED, DISABLED }

    @FunctionalInterface
    private interface Job {
        void run() throws Exception;
    }

    /** Reads the bytes of the picture (in the background). */
    @FunctionalInterface
    private interface Source {
        byte[] read() throws RenderException;
    }

    @FunctionalInterface
    interface MapCountCheck {
        void check(int maps) throws Exception;
    }

    /**
     * /tomap: renders a new image for the player. widthMaps/heightMaps are only used with a scaling mode.
     *
     * @param ditherRequest TRUE/FALSE when the player asked for dithering on/off, null for the configured default
     */
    public void create(Player player, String url, ScalingMode mode, int widthMaps, int heightMaps,
                       Boolean ditherRequest) {
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            plugin.messages().send(player, "render.busy");
            return;
        }
        LimitContext limits = LimitContext.of(player);
        PluginConfig config = plugin.settings();
        Source picture;
        try {
            picture = source(player, url, config);
            plugin.mapManager().checkCreateLimits(uuid, 1, limits);
        } catch (RenderException ex) {
            sendFailure(player, ex);
            return;
        }

        boolean bypassSize = Perm.BYPASS_SIZE.has(player);
        Dithering dithering = Dithering.choose(ditherRequest, config.dithering());
        AnimationPolicy policy = animationPolicy(player, config);
        Requester requester = Requester.of(player);
        busy.add(uuid);
        startActionBar(player, "render.started");

        submit(requester, () -> {
            byte[] data = picture.read();
            ImageDecoder.SizeCheck sizeCheck = (w, h) -> checkSourceSize(w, h, bypassSize, config);
            List<Note> notes = new ArrayList<>();
            Prepared prepared = null;
            if (policy != AnimationPolicy.DISABLED && GifDecoder.isGif(data)) {
                prepared = animate(data, sizeCheck, policy, config, dithering, notes,
                        (w, h) -> posterSize(w, h, mode, widthMaps, heightMaps),
                        frame -> fit(frame, mode, widthMaps, heightMaps, config),
                        maps -> checkLimitsFromWorker(uuid, maps, limits));
            }
            if (prepared == null) {
                BufferedImage source = ImageDecoder.decode(data, sizeCheck);
                BufferedImage canvas = fit(source, mode, widthMaps, heightMaps, config);
                checkLimitsFromWorker(uuid, ImageProcessing.mapsFor(canvas.getWidth())
                        * ImageProcessing.mapsFor(canvas.getHeight()), limits);
                prepared = convert(canvas, dithering, config.saveFullImage());
            }
            Prepared result = prepared;
            Tasks.sync(() -> finishCreate(requester, result, limits, notes));
        });
    }

    /** /maptool update: draws a new image on the maps of an existing image, keeping its size. */
    public void update(CommandSender sender, ImageMap map, String url, ScalingMode mode, Boolean ditherRequest) {
        Requester requester = Requester.of(sender);
        if (requester.playerId() != null && busy.contains(requester.playerId())) {
            plugin.messages().send(sender, "render.busy");
            return;
        }
        PluginConfig config = plugin.settings();
        Source picture;
        try {
            picture = source(sender, url, config);
        } catch (RenderException ex) {
            sendFailure(sender, ex);
            return;
        }

        ScalingMode scaling = mode == null || mode == ScalingMode.NONE ? ScalingMode.CONTAINED : mode;
        int columns = map instanceof PosterMap poster ? poster.layoutColumns() : 1;
        int rows = map instanceof PosterMap poster ? poster.layoutRows() : 1;
        int[] mapIds = map.getMapIds();
        boolean isPlayer = sender instanceof Player;
        boolean bypassSize = !isPlayer || Perm.BYPASS_SIZE.has(sender);
        Dithering dithering = Dithering.choose(ditherRequest, config.dithering());
        AnimationPolicy policy = animationPolicy(sender, config);

        if (requester.playerId() != null) {
            busy.add(requester.playerId());
            startActionBar((Player) sender, "render.update-started");
        }
        submit(requester, () -> {
            byte[] data = picture.read();
            ImageDecoder.SizeCheck sizeCheck = (w, h) -> checkSourceSize(w, h, bypassSize, config);
            Function<BufferedImage, BufferedImage> resize = image -> ImageProcessing.resize(image,
                    columns * ImageProcessing.MAP_SIZE, rows * ImageProcessing.MAP_SIZE, scaling, config.smoothScaling());
            List<Note> notes = new ArrayList<>();
            Prepared prepared = null;
            if (policy != AnimationPolicy.DISABLED && GifDecoder.isGif(data)) {
                prepared = animate(data, sizeCheck, policy, config, dithering, notes,
                        (w, h) -> new int[] {columns, rows}, resize::apply, maps -> { });
            }
            if (prepared == null) {
                BufferedImage source = ImageDecoder.decode(data, sizeCheck);
                prepared = convert(resize.apply(source), dithering, config.saveFullImage());
            }
            if (prepared.tiles().length != mapIds.length) {
                throw new IllegalStateException("Expected " + mapIds.length + " tiles, got " + prepared.tiles().length);
            }
            Prepared result = prepared;
            Tasks.sync(() -> finishUpdate(requester, map, mapIds, result, notes));
        });
    }

    private static AnimationPolicy animationPolicy(CommandSender sender, PluginConfig config) {
        if (!config.animationsEnabled()) {
            return AnimationPolicy.DISABLED;
        }
        return !(sender instanceof Player) || Perm.ANIMATED.has(sender)
                ? AnimationPolicy.ALLOWED : AnimationPolicy.NOT_ALLOWED;
    }

    /**
     * Where the picture comes from: the upload folder for {@code file:<name>} (players need
     * {@code imageonmap.localfiles}), otherwise the Internet. Checked on the main thread, read in the background.
     */
    private Source source(CommandSender sender, String reference, PluginConfig config) throws RenderException {
        long maxBytes = config.maxDownloadMegabytes() * 1024L * 1024L;
        if (LocalImages.isReference(reference)) {
            if (sender instanceof Player && !Perm.LOCAL_FILES.has(sender)) {
                throw new RenderException("error.file-not-allowed");
            }
            File file = plugin.localImages().resolve(reference);
            return () -> LocalImages.read(file, maxBytes);
        }
        ImageDownloader.parseUrl(reference);
        boolean enforceWhitelist = sender instanceof Player && !Perm.BYPASS_WHITELIST.has(sender);
        ImageDownloader.Request request = request(reference, config, enforceWhitelist);
        return () -> ImageDownloader.download(request);
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

    /** Size in maps of the picture {@link #fit} makes from a source of the given size. */
    static int[] posterSize(int width, int height, ScalingMode mode, int widthMaps, int heightMaps) {
        if (mode == ScalingMode.NONE) {
            return new int[] {ImageProcessing.mapsFor(width), ImageProcessing.mapsFor(height)};
        }
        if (widthMaps <= 1 && heightMaps <= 1) {
            return new int[] {1, 1};
        }
        return new int[] {widthMaps, heightMaps};
    }

    /** Converts a still picture. Dithered pictures are saved with their map colors so the dots are kept. */
    static Prepared convert(BufferedImage canvas, Dithering dithering, boolean keepFull) {
        BufferedImage padded = ImageProcessing.pad(canvas);
        byte[][] pixels = ImageProcessing.splitPixels(MapColors.quantize(padded, dithering),
                padded.getWidth(), padded.getHeight());
        ImageProcessing.Poster poster = ImageProcessing.cut(padded);
        BufferedImage[] tiles = poster.tiles();
        if (dithering != Dithering.NONE) {
            for (int i = 0; i < tiles.length; i++) {
                tiles[i] = MapColors.toImage(pixels[i], ImageProcessing.MAP_SIZE, ImageProcessing.MAP_SIZE);
            }
        }
        return new Prepared(poster.columns(), poster.rows(), tiles, pixels, null, keepFull ? canvas : null);
    }

    /** Keeps one array per distinct frame of a map, so repeated frames take no extra memory. */
    private static final class FramePool {
        private final Map<Integer, List<byte[]>> byHash = new HashMap<>();

        byte[] intern(byte[] frame) {
            List<byte[]> candidates = byHash.computeIfAbsent(Arrays.hashCode(frame), key -> new ArrayList<>(1));
            for (byte[] candidate : candidates) {
                if (Arrays.equals(candidate, frame)) {
                    return candidate;
                }
            }
            candidates.add(frame);
            return frame;
        }
    }

    @FunctionalInterface
    interface SizeFor {
        int[] maps(int width, int height);
    }

    @FunctionalInterface
    interface Fitter {
        BufferedImage apply(BufferedImage frame) throws Exception;
    }

    /**
     * Renders an animated GIF. Returns null when it has to be used as a still picture instead: a GIF with one
     * frame, an animation the requester may not make, or one too big to animate (a note explains why).
     */
    static Prepared animate(byte[] data, ImageDecoder.SizeCheck sizeCheck, AnimationPolicy policy,
                                    PluginConfig config, Dithering dithering, List<Note> notes, SizeFor sizeFor,
                                    Fitter fitter, MapCountCheck mapCheck) throws Exception {
        GifDecoder gif;
        try {
            gif = GifDecoder.open(data);
        } catch (IOException ex) {
            return null; // the still-image decoder reports broken files
        }
        if (gif == null) {
            return null;
        }
        try (gif) {
            if (policy == AnimationPolicy.NOT_ALLOWED) {
                notes.add(new Note("render.animation-not-allowed"));
                return null;
            }
            sizeCheck.check(gif.width(), gif.height());
            if ((long) gif.width() * gif.height() > ANIMATION_MAX_PIXELS) {
                notes.add(new Note("render.animation-too-many-pixels", gif.width(), gif.height()));
                return null;
            }
            int[] size = sizeFor.maps(gif.width(), gif.height());
            int maps = size[0] * size[1];
            if (maps > config.animationMaxMaps()) {
                notes.add(new Note("render.animation-too-big", size[0], size[1], config.animationMaxMaps()));
                return null;
            }
            mapCheck.check(maps);

            int total = gif.frameCount();
            int step = (total + config.animationMaxFrames() - 1) / config.animationMaxFrames();
            int slots = (total + step - 1) / step;
            byte[][][] frames = new byte[maps][slots][];
            int[] delays = new int[slots];
            FramePool[] pools = new FramePool[maps];
            for (int i = 0; i < maps; i++) {
                pools[i] = new FramePool();
            }
            Dithering frameDithering = dithering.forAnimation();
            MapColors.PatternCache patterns = new MapColors.PatternCache();
            boolean keepFull = config.saveFullImage();
            BufferedImage[] full = new BufferedImage[1];
            byte[][][] first = new byte[1][][];
            int[] made = new int[1];

            int delivered = gif.forEachFrame((index, frame, delay) -> {
                int slot = index / step;
                delays[slot] += delay;
                if (index % step != 0) {
                    return;
                }
                BufferedImage canvas = fitter.apply(frame);
                BufferedImage padded = ImageProcessing.pad(canvas);
                if (padded.getWidth() != size[0] * ImageProcessing.MAP_SIZE
                        || padded.getHeight() != size[1] * ImageProcessing.MAP_SIZE) {
                    throw new IllegalStateException("Unexpected frame size " + padded.getWidth() + "x"
                            + padded.getHeight());
                }
                byte[][] tiles = ImageProcessing.splitPixels(MapColors.quantize(padded, frameDithering, patterns),
                        padded.getWidth(), padded.getHeight());
                for (int i = 0; i < maps; i++) {
                    frames[i][slot] = pools[i].intern(tiles[i]);
                }
                if (slot == 0) {
                    first[0] = tiles;
                    if (keepFull) {
                        full[0] = ImageProcessing.copy(canvas);
                    }
                }
                made[0] = slot + 1;
            });
            int kept = Math.min(made[0], (delivered + step - 1) / step);
            if (kept < 2) {
                return null;
            }

            Animation[] animations = new Animation[maps];
            int[] keptDelays = Arrays.copyOf(delays, kept);
            BufferedImage[] tiles = new BufferedImage[maps];
            for (int i = 0; i < maps; i++) {
                animations[i] = new Animation(Arrays.copyOf(frames[i], kept), keptDelays);
                tiles[i] = MapColors.toImage(first[0][i], ImageProcessing.MAP_SIZE, ImageProcessing.MAP_SIZE);
            }
            return new Prepared(size[0], size[1], tiles, first[0], animations, full[0]);
        }
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

    private void sendNotes(CommandSender sender, List<Note> notes) {
        if (sender == null) {
            return;
        }
        for (Note note : notes) {
            plugin.messages().send(sender, note.key(), note.args());
        }
    }

    private void finishCreate(Requester requester, Prepared prepared, LimitContext limits, List<Note> notes) {
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
        show(mapIds, prepared);
        saveImages(requester, mapIds, prepared);

        if (player != null) {
            sendNotes(player, notes);
            if (prepared.animations() != null) {
                plugin.messages().actionBar(player, "render.finished-animated", prepared.frameCount());
            } else {
                plugin.messages().actionBar(player, "render.finished");
            }
            if (plugin.mapItems().give(player, image)) {
                plugin.messages().send(player, "general.inventory-overflow");
            }
        }
    }

    private void finishUpdate(Requester requester, ImageMap map, int[] mapIds, Prepared prepared, List<Note> notes) {
        release(requester);
        CommandSender sender = requester.resolve();
        if (!plugin.mapManager().exists(map)) {
            if (sender != null) {
                plugin.messages().send(sender, "general.map-not-found");
            }
            return;
        }
        show(mapIds, prepared);
        saveImages(requester, mapIds, prepared);
        sendNotes(sender, notes);
        boolean animated = prepared.animations() != null;
        String key = animated ? "render.update-finished-animated" : "render.update-finished";
        if (sender instanceof Player player) {
            plugin.messages().actionBar(player, key, prepared.frameCount());
        } else if (sender != null) {
            plugin.messages().send(sender, key, prepared.frameCount());
        }
    }

    private void show(int[] mapIds, Prepared prepared) {
        for (int i = 0; i < mapIds.length; i++) {
            if (prepared.animations() != null) {
                plugin.rendererManager().setAnimation(mapIds[i], prepared.animations()[i]);
            } else {
                plugin.rendererManager().setPixels(mapIds[i], prepared.pixels()[i]);
            }
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
                    if (prepared.animations() == null) {
                        storage.deleteAnimation(mapIds[i]);
                        storage.write(storage.fileFor(mapIds[i]), prepared.tiles()[i]);
                    } else {
                        storage.write(storage.fileFor(mapIds[i]), prepared.tiles()[i]);
                        storage.writeAnimation(mapIds[i], prepared.animations()[i]);
                    }
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
