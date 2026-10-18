package pl.iomreborn.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.i18n.Messages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base of the chest GUIs. Every click in a GUI is cancelled; actions run on the next tick and only if the
 * GUI is still the one the player has open, so items can never be taken out.
 */
public abstract class Gui implements InventoryHolder {

    @FunctionalInterface
    public interface ClickAction {
        void onClick(ClickType click);
    }

    protected static final int ROWS = 6;
    protected static final int SIZE = ROWS * 9;

    protected final ImageOnMapReborn plugin;
    protected final Player viewer;
    private final Map<Integer, ClickAction> actions = new HashMap<>();
    private Inventory inventory;

    protected Gui(ImageOnMapReborn plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract String title();

    protected abstract void build();

    /** Opens (or re-opens, refreshing the title and content) this GUI. */
    public final void open() {
        actions.clear();
        inventory = Bukkit.createInventory(this, SIZE, title());
        build();
        viewer.openInventory(inventory);
        plugin.guiListener().track(viewer, this);
    }

    /** Redraws the content without re-opening the inventory (the title stays the same). */
    protected final void refresh() {
        actions.clear();
        inventory.clear();
        build();
    }

    protected final void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected final void set(int slot, ItemStack item, ClickAction action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    final void handleClick(int slot, ClickType click) {
        ClickAction action = actions.get(slot);
        if (action != null) {
            action.onClick(click);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected Messages messages() {
        return plugin.messages();
    }

    protected String text(String key, Object... args) {
        return messages().get(viewer, key, args);
    }

    protected List<String> lines(String key, Object... args) {
        return messages().lines(viewer, key, args);
    }

    /** Builds a simple icon with a name and an optional lore. */
    protected static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
