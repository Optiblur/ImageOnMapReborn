package pl.iomreborn.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import pl.iomreborn.ImageOnMapReborn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Routes inventory events to the open {@link Gui}s. */
public final class GuiListener implements Listener {

    private final ImageOnMapReborn plugin;
    private final Map<UUID, Gui> open = new HashMap<>();

    public GuiListener(ImageOnMapReborn plugin) {
        this.plugin = plugin;
    }

    void track(Player player, Gui gui) {
        open.put(player.getUniqueId(), gui);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Gui gui)) {
            return;
        }
        event.setCancelled(true);
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked.getHolder() instanceof Gui) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType click = event.getClick();
        if (click == ClickType.DOUBLE_CLICK) {
            return;
        }
        int slot = event.getSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && open.get(player.getUniqueId()) == gui) {
                gui.handleClick(slot, click);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Gui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            open.remove(event.getPlayer().getUniqueId(), gui);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    /** Closes every GUI, used when the plugin is disabled. */
    public void closeAll() {
        for (UUID uuid : new ArrayList<>(open.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
        }
        open.clear();
    }
}
