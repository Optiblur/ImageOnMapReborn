package pl.iomreborn.render;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.image.MapColors;
import pl.iomreborn.item.MapItems;
import pl.iomreborn.map.MapManager;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Attaches {@link ImageMapRenderer}s to the Minecraft maps owned by the plugin.
 * <p>
 * Map data is loaded lazily by the server, so renderers are installed whenever a map shows up:
 * when it is initialized, when item frames are loaded, when a player joins or holds a map.
 * Images are read from disk in the background the first time a map is actually rendered.
 */
public final class RendererManager implements Listener {

    private final ImageOnMapReborn plugin;
    private final ExecutorService loader;
    private final Map<Integer, MapView> installed = new HashMap<>();
    private final Set<Integer> queued = new LinkedHashSet<>();
    private final Set<Integer> warnedMissing = ConcurrentHashMap.newKeySet();
    private boolean flushScheduled;
    private BukkitTask sweepTask;

    public RendererManager(ImageOnMapReborn plugin) {
        this.plugin = plugin;
        this.loader = Executors.newFixedThreadPool(2, ImageOnMapReborn.threadFactory("ImageOnMapReborn-loader"));
    }

    private MapManager maps() {
        return plugin.mapManager();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        rescan(!Bukkit.getOnlinePlayers().isEmpty());
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepHands, 40L, 20L);
    }

    /** Looks for plugin maps in loaded item frames and in the inventories of online players. */
    public void rescan(boolean fullInventories) {
        for (World world : Bukkit.getWorlds()) {
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                queue(frame.getItem());
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (fullInventories) {
                queueInventory(player);
            } else {
                queue(player.getInventory().getItemInMainHand());
                queue(player.getInventory().getItemInOffHand());
            }
        }
    }

    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        loader.shutdownNow();
        try {
            loader.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        for (MapView view : installed.values()) {
            removeOurRenderers(view);
        }
        installed.clear();
        queued.clear();
    }

    @SuppressWarnings("deprecation")
    public void ensure(int mapId) {
        if (installed.containsKey(mapId) || !maps().isManaged(mapId)) {
            return;
        }
        MapView view = Bukkit.getMap(mapId);
        if (view != null) {
            ensure(view);
        }
    }

    @SuppressWarnings("deprecation")
    public void ensure(MapView view) {
        int id = view.getId();
        if (!maps().isManaged(id)) {
            return;
        }
        if (findOurRenderer(view) != null) {
            installed.put(id, view);
            return;
        }
        install(view, null);
    }

    /** Shows freshly rendered pixels on a map, installing the renderer if needed. */
    @SuppressWarnings("deprecation")
    public void setPixels(int mapId, byte[] pixels) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            plugin.getLogger().warning("Minecraft map #" + mapId + " does not exist, cannot display its image.");
            return;
        }
        ImageMapRenderer renderer = findOurRenderer(view);
        if (renderer != null) {
            renderer.offer(pixels);
            installed.put(mapId, view);
        } else {
            install(view, pixels);
        }
    }

    /**
     * Detaches the renderer of a deleted map, which becomes blank. Maps that were never loaded are left alone:
     * the server will load them without any image, like after a restart.
     */
    public void uninstall(int mapId) {
        queued.remove(mapId);
        MapView view = installed.remove(mapId);
        if (view != null) {
            removeOurRenderers(view);
        }
    }

    @SuppressWarnings("deprecation")
    private void install(MapView view, byte[] pixels) {
        for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(renderer);
        }
        ImageMapRenderer renderer = new ImageMapRenderer(this, view.getId(), pixels);
        view.addRenderer(renderer);
        installed.put(view.getId(), view);
        if (pixels == null && renderer.claimLoad()) {
            load(renderer);
        }
    }

    private ImageMapRenderer findOurRenderer(MapView view) {
        for (MapRenderer renderer : view.getRenderers()) {
            if (renderer instanceof ImageMapRenderer ours && ours.getManager() == this) {
                return ours;
            }
        }
        return null;
    }

    private static void removeOurRenderers(MapView view) {
        for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
            if (renderer instanceof ImageMapRenderer) {
                view.removeRenderer(renderer);
            }
        }
    }

    /** Reads the image file of a renderer in the background (retrying, as the file may be locked briefly). */
    void load(ImageMapRenderer renderer) {
        try {
            loader.execute(() -> {
                int id = renderer.getMapId();
                int version = renderer.version();
                for (int attempt = 1; ; attempt++) {
                    try {
                        BufferedImage image = plugin.imageStorage().read(id);
                        if (image == null) {
                            renderer.markFailed();
                            if (warnedMissing.add(id)) {
                                plugin.getLogger().warning("Image file images/map" + id
                                        + ".png is missing, Minecraft map #" + id + " stays blank.");
                            }
                            return;
                        }
                        renderer.offerLoaded(MapColors.toMapBytes(image), version);
                        return;
                    } catch (Exception ex) {
                        if (attempt >= 3) {
                            renderer.markFailed();
                            plugin.getLogger().log(Level.WARNING, "Cannot load the image of Minecraft map #" + id, ex);
                            return;
                        }
                        try {
                            Thread.sleep(250L * attempt);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private void queue(ItemStack item) {
        int mapId = MapItems.getMapId(item);
        if (mapId < 0 || installed.containsKey(mapId) || !maps().isManaged(mapId)) {
            return;
        }
        queued.add(mapId);
        if (!flushScheduled && plugin.isEnabled()) {
            flushScheduled = true;
            Bukkit.getScheduler().runTask(plugin, this::flushQueue);
        }
    }

    private void queueInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            queue(item);
        }
    }

    private void flushQueue() {
        flushScheduled = false;
        List<Integer> ids = new ArrayList<>(queued);
        queued.clear();
        for (int id : ids) {
            ensure(id);
        }
    }

    private void sweepHands() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            checkNow(inventory.getItemInMainHand());
            checkNow(inventory.getItemInOffHand());
        }
    }

    private void checkNow(ItemStack item) {
        int mapId = MapItems.getMapId(item);
        if (mapId >= 0 && !installed.containsKey(mapId)) {
            ensure(mapId);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMapInitialize(MapInitializeEvent event) {
        ensure(event.getMap());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof ItemFrame frame) {
                queue(frame.getItem());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        queueInventory(event.getPlayer());
    }
}
