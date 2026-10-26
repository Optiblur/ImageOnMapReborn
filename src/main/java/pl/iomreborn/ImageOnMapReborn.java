package pl.iomreborn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
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
import pl.iomreborn.command.sub.InfoCommand;
import pl.iomreborn.command.sub.ListCommand;
import pl.iomreborn.command.sub.NewCommand;
import pl.iomreborn.command.sub.ReloadCommand;
import pl.iomreborn.command.sub.RenameCommand;
import pl.iomreborn.command.sub.UpdateCommand;
import pl.iomreborn.config.ConfigUpdater;
import pl.iomreborn.config.PluginConfig;
import pl.iomreborn.gui.GuiListener;
import pl.iomreborn.i18n.Messages;
import pl.iomreborn.image.LocalImages;
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
import pl.iomreborn.update.UpdateChecker;
import pl.iomreborn.util.ConsoleBox;
import pl.iomreborn.util.Tasks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
    private LocalImages localImages;
    private MapManager mapManager;
    private LegacyImporter legacyImporter;
    private RendererManager rendererManager;
    private MapItems mapItems;
    private PendingItems pendingItems;
    private RenderService renderService;
    private PosterPlacement posterPlacement;
    private ChatPrompts chatPrompts;
    private GuiListener guiListener;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        Tasks.init(this);
        saveDefaultConfig();
        upgradeConfigFiles();
        messages = new Messages(this);
        reloadSettings();

        File imagesDir = new File(getDataFolder(), "images");
        File mapsDir = new File(getDataFolder(), "maps");
        if (!directory(imagesDir) || !directory(mapsDir)) {
            getLogger().severe("Cannot create the plugin folders in " + getDataFolder() + ", disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        localImages = new LocalImages(new File(getDataFolder(), "upload"));
        createUploadFolder(localImages.folder());

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
        updateChecker = new UpdateChecker(this, getDescription().getVersion());
        updateChecker.applySettings();
        printStartupBanner();
    }

    /** The summary printed in the console when the plugin has started. */
    private void printStartupBanner() {
        summary(settings.checkForUpdates()
                ? "checking SpigotMC for a new version..."
                : updateChecker.describe()).print();
    }

    /** The plugin at a glance, for the console (at startup and with /maptool info). */
    public ConsoleBox summary(String updates) {
        String language = messages.serverLocale() + (settings.perPlayerLanguage() ? " (or each player's own)" : "");
        int uploads = localImages.names().size();
        return new ConsoleBox()
                .line(ChatColor.AQUA + "ImageOnMap" + ChatColor.DARK_AQUA + "Reborn " + ChatColor.WHITE
                        + getDescription().getVersion() + ChatColor.GRAY + "  by "
                        + String.join(", ", getDescription().getAuthors()))
                .line(ChatColor.GRAY + "Pictures on maps - the successor of ImageOnMap")
                .separator()
                .row("Images", mapManager.getTotalImageCount() + ChatColor.GRAY.toString() + " ("
                        + count(mapManager.getTotalMapCount(), "map") + ", "
                        + count(mapManager.getStores().size(), "player") + ")")
                .row("Uploads", uploads + ChatColor.GRAY.toString() + (uploads == 1 ? " picture" : " pictures")
                        + " in upload/ (/tomap file:<name>)")
                .row("Language", language)
                .row("Server", serverName())
                .row("Updates", updates);
    }

    /** "1 map", "5 maps". */
    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }

    /** For example "Paper 1.21.11". */
    public static String serverName() {
        String version = Bukkit.getBukkitVersion();
        int suffix = version.indexOf("-R");
        return Bukkit.getName() + " " + (suffix > 0 ? version.substring(0, suffix) : version);
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
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
        if (updateChecker != null) {
            updateChecker.applySettings();
        }
    }

    /**
     * Brings files written by older versions up to date: the options added by newer versions are appended to
     * config.yml (with their comments), so admins who update the plugin can see and change them, and the Polish
     * comments of version 1.0.0 become English. Existing values and the admin's own comments are not touched.
     */
    private void upgradeConfigFiles() {
        File file = new File(getDataFolder(), "config.yml");
        try (InputStream in = getResource("config.yml")) {
            if (in == null || !file.isFile()) {
                return;
            }
            String bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String original = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String text = ConfigUpdater.translateComments(original, ConfigUpdater.OLD_CONFIG_COMMENTS);
            YamlConfiguration current = new YamlConfiguration();
            current.loadFromString(text);
            List<String> added = new ArrayList<>();
            String missing = ConfigUpdater.missingOptions(bundled, key -> current.contains(key, true), added);
            if (!missing.isEmpty()) {
                if (!text.isEmpty() && !text.endsWith("\n")) {
                    text += "\n";
                }
                text += text.contains("\r\n") ? missing.replace("\n", "\r\n") : missing;
            }
            if (!text.equals(original)) {
                MapStorage.writeAtomic(file, text);
            }
            if (!added.isEmpty()) {
                getLogger().info("Added new options to config.yml: " + String.join(", ", added) + ".");
            }
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Cannot add the new options to config.yml (defaults are used)", ex);
        }

        File polish = new File(new File(getDataFolder(), "lang"), "pl_PL.yml");
        try {
            if (polish.isFile()) {
                String original = Files.readString(polish.toPath(), StandardCharsets.UTF_8);
                String text = ConfigUpdater.translateComments(original, ConfigUpdater.OLD_POLISH_LANG_COMMENTS);
                if (!text.equals(original)) {
                    MapStorage.writeAtomic(polish, text);
                }
            }
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Cannot update the comments of lang/pl_PL.yml", ex);
        }
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
                new ReloadCommand(this),
                new InfoCommand(this));
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

    /** Creates the folder for /tomap file:<name>, with a short note for the admins the first time. */
    private void createUploadFolder(File folder) {
        if (folder.isDirectory()) {
            return;
        }
        if (!folder.mkdirs()) {
            getLogger().warning("Cannot create " + folder + "; /tomap file:<name> will not find any picture.");
            return;
        }
        String note = String.join(System.lineSeparator(),
                "Put pictures (PNG, JPEG, GIF, BMP or WebP) in this folder to use them without uploading them",
                "to a website first:",
                "",
                "    /tomap file:logo.png [resize 4 3]",
                "    /maptool update <map name> file:logo.png",
                "",
                "Subfolders work too (file:event/day1.png). File names must not contain spaces.",
                "Players need the imageonmap.localfiles permission (OPs have it by default).",
                "");
        try {
            Files.writeString(new File(folder, "README.txt").toPath(), note, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Cannot write " + folder + "/README.txt", ex);
        }
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

    public LocalImages localImages() {
        return localImages;
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

    public UpdateChecker updateChecker() {
        return updateChecker;
    }
}
