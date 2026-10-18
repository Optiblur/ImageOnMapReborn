package pl.iomreborn;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import pl.iomreborn.command.CommandRouter;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.command.sub.DeleteCommand;
import pl.iomreborn.command.sub.ExploreCommand;
import pl.iomreborn.command.sub.GetCommand;
import pl.iomreborn.command.sub.GetRemainingCommand;
import pl.iomreborn.command.sub.GiveCommand;
import pl.iomreborn.command.sub.ImportCommand;
import pl.iomreborn.command.sub.ListCommand;
import pl.iomreborn.command.sub.NewCommand;
import pl.iomreborn.command.sub.ReloadCommand;
import pl.iomreborn.command.sub.RenameCommand;
import pl.iomreborn.command.sub.UpdateCommand;
import pl.iomreborn.config.PluginConfig;
import pl.iomreborn.gui.GuiListener;
import pl.iomreborn.i18n.Messages;
import pl.iomreborn.image.MapColors;
import pl.iomreborn.image.RenderService;
import pl.iomreborn.item.MapItems;
import pl.iomreborn.item.PendingItems;
import pl.iomreborn.listener.ChatPrompts;
import pl.iomreborn.listener.FrameListener;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.MapManager;
import pl.iomreborn.placement.PosterPlacement;
import pl.iomreborn.render.RendererManager;
import pl.iomreborn.storage.ImageStorage;
import pl.iomreborn.storage.LegacyImporter;
import pl.iomreborn.storage.MapStorage;
import pl.iomreborn.util.Tasks;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** ImageOnMapReborn: pictures from the Internet on Minecraft maps, compatible with ImageOnMap data. */
public final class ImageOnMapReborn extends JavaPlugin {

    private PluginConfig settings;
    private Messages messages;
    private ExecutorService workExecutor;
    private ExecutorService ioExecutor;
    private ImageStorage imageStorage;
    private MapManager mapManager;
    private LegacyImporter legacyImporter;
    private RendererManager rendererManager;
    private MapItems mapItems;
    private PendingItems pendingItems;
    private RenderService renderService;
    private PosterPlacement posterPlacement;
    private ChatPrompts chatPrompts;
    private GuiListener guiListener;

    @Override
    public void onEnable() {
        Tasks.init(this);
        saveDefaultConfig();
        messages = new Messages(this);
        reloadSettings();

        File imagesDir = new File(getDataFolder(), "images");
        File mapsDir = new File(getDataFolder(), "maps");
        if (!directory(imagesDir) || !directory(mapsDir)) {
            getLogger().severe("Cannot create the plugin folders in " + getDataFolder() + ", disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        workExecutor = Executors.newFixedThreadPool(2, threadFactory("ImageOnMapReborn-worker"));
        ioExecutor = Executors.newSingleThreadExecutor(threadFactory("ImageOnMapReborn-io"));

        imageStorage = new ImageStorage(imagesDir);
        mapManager = new MapManager(this, new MapStorage(mapsDir));
        mapManager.loadAll();

        legacyImporter = new LegacyImporter(this);
        try {
            legacyImporter.autoImportIfNeeded();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Importing ImageOnMap data failed; use /maptool import to try again.", ex);
        }
        imageStorage.setLegacyImagesDir(new File(legacyImporter.sourceFolder(), "images"));

        pendingItems = new PendingItems();
        mapItems = new MapItems(this);
        posterPlacement = new PosterPlacement(this);
        renderService = new RenderService(this);
        chatPrompts = new ChatPrompts();
        guiListener = new GuiListener(this);
        rendererManager = new RendererManager(this);

        getServer().getPluginManager().registerEvents(new FrameListener(this), this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(chatPrompts, this);
        MapColors.warmUp();
        rendererManager.start();
        registerCommands();
        warnAboutOriginalPlugin();

        getLogger().info("Loaded " + mapManager.getTotalImageCount() + " images using "
                + mapManager.getTotalMapCount() + " Minecraft maps (" + mapManager.getStores().size() + " players).");
    }

    @Override
    public void onDisable() {
        if (guiListener != null) {
            guiListener.closeAll();
        }
        if (renderService != null) {
            renderService.shutdown();
        }
        if (rendererManager != null) {
            rendererManager.shutdown();
        }
        stop(workExecutor, true, 5);
        stop(ioExecutor, false, 30);
        if (mapManager != null) {
            mapManager.saveAllNow();
        }
    }

    private void stop(ExecutorService executor, boolean interrupt, int seconds) {
        if (executor == null) {
            return;
        }
        if (interrupt) {
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(seconds, TimeUnit.SECONDS)) {
                getLogger().warning("Some background tasks did not finish in time.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Reloads config.yml and the translations. */
    public void reloadSettings() {
        reloadConfig();
        settings = PluginConfig.from(getConfig());
        messages.load(settings.lang(), settings.perPlayerLanguage());
    }

    private void registerCommands() {
        List<SubCommand> subcommands = List.of(
                new NewCommand(this),
                new ListCommand(this),
                new GetCommand(this),
                new GetRemainingCommand(this),
                new ExploreCommand(this),
                new GiveCommand(this),
                new RenameCommand(this),
                new UpdateCommand(this),
                new DeleteCommand(this),
                new ImportCommand(this),
                new ReloadCommand(this));
        CommandRouter maptool = CommandRouter.of(this, subcommands);
        bind("maptool", maptool);
        bind("tomap", CommandRouter.direct(this, maptool.get("new")));
        bind("maps", CommandRouter.direct(this, maptool.get("explore")));
        bind("givemap", CommandRouter.direct(this, maptool.get("give")));
    }

    private void bind(String name, CommandRouter router) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command /" + name + " is missing from plugin.yml");
            return;
        }
        command.setExecutor(router);
        command.setTabCompleter(router);
    }

    private void warnAboutOriginalPlugin() {
        Plugin original = getServer().getPluginManager().getPlugin("ImageOnMap");
        if (original != null && original != this && original.isEnabled()) {
            getLogger().warning("============================================================");
            getLogger().warning("The original ImageOnMap plugin is still installed and enabled!");
            getLogger().warning("Both plugins handle the same maps and commands. Stop the server,");
            getLogger().warning("delete ImageOnMap.jar (keep the plugins/ImageOnMap folder) and start again.");
            getLogger().warning("============================================================");
        }
    }

    private static boolean directory(File dir) {
        return dir.isDirectory() || dir.mkdirs();
    }

    public static ThreadFactory threadFactory(String name) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Deletes an image: unregisters it, blanks its maps and removes its files. */
    public void deleteImage(ImageMap map) {
        if (!mapManager.removeMap(map)) {
            return;
        }
        int[] mapIds = map.getMapIds();
        for (int mapId : mapIds) {
            rendererManager.uninstall(mapId);
        }
        Runnable delete = () -> {
            try {
                for (int mapId : mapIds) {
                    imageStorage.delete(mapId);
                }
                imageStorage.deleteFullImage(mapIds[0], mapIds[mapIds.length - 1]);
            } catch (IOException ex) {
                getLogger().log(Level.WARNING, "Cannot delete image files of map #" + mapIds[0], ex);
            }
        };
        try {
            ioExecutor.execute(delete);
        } catch (RejectedExecutionException ex) {
            delete.run();
        }
    }

    /** Removes every item of an image from a player's inventory (done before deleting, like ImageOnMap). */
    public void removeFromInventory(Player player, ImageMap map) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            int mapId = MapItems.getMapId(contents[slot]);
            if (mapId >= 0 && map.managesMap(mapId)) {
                inventory.setItem(slot, null);
            }
        }
    }

    public PluginConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public ExecutorService workExecutor() {
        return workExecutor;
    }

    public ExecutorService ioExecutor() {
        return ioExecutor;
    }

    public ImageStorage imageStorage() {
        return imageStorage;
    }

    public MapManager mapManager() {
        return mapManager;
    }

    public LegacyImporter legacyImporter() {
        return legacyImporter;
    }

    public RendererManager rendererManager() {
        return rendererManager;
    }

    public MapItems mapItems() {
        return mapItems;
    }

    public PendingItems pendingItems() {
        return pendingItems;
    }

    public RenderService renderService() {
        return renderService;
    }

    public PosterPlacement posterPlacement() {
        return posterPlacement;
    }

    public ChatPrompts chatPrompts() {
        return chatPrompts;
    }

    public GuiListener guiListener() {
        return guiListener;
    }
}
