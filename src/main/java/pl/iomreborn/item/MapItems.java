package pl.iomreborn.item;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.i18n.Messages;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.PosterMap;
import pl.iomreborn.map.SingleMap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates and recognizes the map items of the plugin.
 * <p>
 * Splatter maps (one item that places a whole poster) are recognized by a persistent data tag, and also
 * by an enchantment, which is how ImageOnMap marked them, so splatter maps made by the old plugin still work.
 */
public final class MapItems {

    private static final Method SET_GLINT_OVERRIDE = findMethod("setEnchantmentGlintOverride", Boolean.class);
    private static final Method HAS_GLINT_OVERRIDE = findMethod("hasEnchantmentGlintOverride");

    private final ImageOnMapReborn plugin;
    private final NamespacedKey splatterKey;

    public MapItems(ImageOnMapReborn plugin) {
        this.plugin = plugin;
        this.splatterKey = new NamespacedKey(plugin, "splatter");
    }

    private static Method findMethod(String name, Class<?>... parameters) {
        try {
            return ItemMeta.class.getMethod(name, parameters);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private Messages messages() {
        return plugin.messages();
    }

    /** Minecraft map ID of a filled map item, or -1. Does not load the map data. */
    @SuppressWarnings("deprecation")
    public static int getMapId(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
            return -1;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof MapMeta mapMeta) || !mapMeta.hasMapId()) {
            return -1;
        }
        return mapMeta.getMapId();
    }

    /** The image an item belongs to, or null if it is not a map of the plugin. */
    public ImageMap getImage(ItemStack item) {
        int mapId = getMapId(item);
        return mapId < 0 ? null : plugin.mapManager().getByMapId(mapId);
    }

    public ItemStack singleItem(SingleMap map, CommandSender viewer) {
        return mapItem(map.getMapId(), messages().get(viewer, "item.single.name", map.getName()), Color.GREEN, null);
    }

    public ItemStack partItem(PosterMap map, int index, CommandSender viewer) {
        return mapItem(map.getMapIdAt(index), partTitle(map, index, viewer), Color.LIME, null);
    }

    private String partTitle(PosterMap map, int index, CommandSender viewer) {
        if (map.hasColumnData()) {
            return messages().get(viewer, "item.part.name-grid", map.getName(), map.rowOf(index) + 1,
                    map.columnOf(index) + 1);
        }
        return messages().get(viewer, "item.part.name-index", map.getName(), index + 1);
    }

    public ItemStack splatterItem(PosterMap map, CommandSender viewer) {
        int columns = map.getColumns();
        int rows = map.getRows();
        ItemStack item = mapItem(map.getMapIdAt(0),
                messages().get(viewer, "item.splatter.name", map.getName(), columns, rows),
                Color.GREEN,
                messages().lines(viewer, "item.splatter.lore", map.getId(), columns, rows, columns * rows));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(splatterKey, PersistentDataType.BYTE, (byte) 1);
        addGlint(meta);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    private ItemStack mapItem(int mapId, String title, Color color, List<String> lore) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapId(mapId);
        meta.setDisplayName(title);
        if (lore != null) {
            meta.setLore(lore);
        }
        meta.setColor(color);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** Plain map item placed in item frames: no name, so nothing shows up when looking at the wall. */
    @SuppressWarnings("deprecation")
    public ItemStack frameItem(int mapId) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapId(mapId);
        item.setItemMeta(meta);
        return item;
    }

    /** Copy of a held map item for an item frame: one item, without its display name. */
    public ItemStack frameCopy(ItemStack held) {
        ItemStack copy = held.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            meta.setDisplayName(null);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    /** Gives back a name to a map taken out of an item frame, like the item the player originally received. */
    public ItemStack withTitle(ItemStack frameItem, ImageMap map, CommandSender viewer) {
        int mapId = getMapId(frameItem);
        String title;
        if (map instanceof PosterMap poster) {
            int index = Math.max(0, poster.indexOf(mapId));
            title = partTitle(poster, index, viewer);
        } else {
            title = messages().get(viewer, "item.single.name", map.getName());
        }
        ItemStack copy = frameItem.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.setDisplayName(title);
        meta.addItemFlags(ItemFlag.values());
        copy.setItemMeta(meta);
        return copy;
    }

    /** True for splatter maps made by this plugin or by ImageOnMap. */
    public boolean isSplatter(ItemStack item) {
        ImageMap map = getImage(item);
        if (!(map instanceof PosterMap poster) || !poster.hasColumnData()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(splatterKey, PersistentDataType.BYTE)) {
            return true;
        }
        // ImageOnMap marked splatter maps with an enchantment. In 4.x it was a custom one registered by the
        // plugin, which the server forgets once ImageOnMap is removed, so it cannot be relied upon. Those items
        // show the first map of the poster and, unlike single parts, always have a lore: that identifies them.
        if (getMapId(item) == poster.getMapIdAt(0) && meta.hasLore() && meta.getLore().size() >= 3) {
            return true;
        }
        if (meta.hasEnchants()) {
            return true;
        }
        if (HAS_GLINT_OVERRIDE != null) {
            try {
                return (Boolean) HAS_GLINT_OVERRIDE.invoke(meta);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    public boolean hasSplatter(Player player, PosterMap poster) {
        for (ItemStack item : player.getInventory().getContents()) {
            int mapId = getMapId(item);
            if (mapId >= 0 && poster.managesMap(mapId) && isSplatter(item)) {
                return true;
            }
        }
        return false;
    }

    private static void addGlint(ItemMeta meta) {
        if (SET_GLINT_OVERRIDE != null) {
            try {
                SET_GLINT_OVERRIDE.invoke(meta, Boolean.TRUE);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall back to an enchantment
            }
        }
        Enchantment mending = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("mending"));
        if (mending != null) {
            meta.addEnchant(mending, 1, true);
        }
    }

    /** Items given when a player gets an image: one map, a splatter map, or every part of an old poster. */
    public List<ItemStack> itemsFor(ImageMap map, CommandSender viewer) {
        List<ItemStack> items = new ArrayList<>();
        if (map instanceof SingleMap single) {
            items.add(singleItem(single, viewer));
        } else if (map instanceof PosterMap poster) {
            if (poster.hasColumnData()) {
                items.add(splatterItem(poster, viewer));
            } else {
                for (int i = 0; i < poster.getMapCount(); i++) {
                    items.add(partItem(poster, i, viewer));
                }
            }
        }
        return items;
    }

    /** Gives the image to a player; returns true when some items had to wait for /maptool getremaining. */
    public boolean give(Player player, ImageMap map) {
        return give(player, itemsFor(map, player));
    }

    public boolean give(Player player, List<ItemStack> items) {
        boolean overflow = false;
        boolean given = false;
        for (ItemStack item : items) {
            int amount = item.getAmount();
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            int leftAmount = 0;
            for (ItemStack rest : left.values()) {
                leftAmount += rest.getAmount();
                overflow = true;
                plugin.pendingItems().add(player.getUniqueId(), rest);
            }
            if (leftAmount < amount) {
                given = true;
            }
        }
        if (given) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
        }
        return overflow;
    }
}
