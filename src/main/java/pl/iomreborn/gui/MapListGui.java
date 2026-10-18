package pl.iomreborn.gui;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.config.PluginConfig;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.MapManager;
import pl.iomreborn.map.PosterMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /maps: paginated list of a player's images with usage statistics. */
public final class MapListGui extends Gui {

    private static final int PAGE_SIZE = 45;

    private final UUID owner;
    private final String ownerName;
    private int page;

    public MapListGui(ImageOnMapReborn plugin, Player viewer, UUID owner, String ownerName) {
        super(plugin, viewer);
        this.owner = owner;
        this.ownerName = ownerName;
    }

    boolean isOwn() {
        return owner.equals(viewer.getUniqueId());
    }

    String ownerName() {
        return ownerName;
    }

    @Override
    protected String title() {
        int count = plugin.mapManager().getImageCount(owner);
        return isOwn() ? text("gui.list.title.own", count) : text("gui.list.title.other", count, ownerName);
    }

    @Override
    protected void build() {
        List<ImageMap> maps = plugin.mapManager().getMaps(owner);
        int pages = Math.max(1, (maps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        if (maps.isEmpty()) {
            set(22, emptyIcon());
        }
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < maps.size(); i++) {
            ImageMap map = maps.get(start + i);
            set(i, mapIcon(map), click -> onMapClick(map, click));
        }
        if (page > 0) {
            set(45, icon(Material.ARROW, text("gui.nav.previous"), List.of(text("gui.nav.page", page, pages))),
                    click -> {
                        page--;
                        refresh();
                    });
        }
        if (page < pages - 1) {
            set(53, icon(Material.ARROW, text("gui.nav.next"), List.of(text("gui.nav.page", page + 2, pages))),
                    click -> {
                        page++;
                        refresh();
                    });
        }
        set(49, statsIcon());
    }

    private boolean canGet() {
        return isOwn() ? Perm.GET.has(viewer) : Perm.GET_OTHER.has(viewer);
    }

    private ItemStack mapIcon(ImageMap map) {
        String description;
        if (map instanceof PosterMap poster) {
            description = poster.hasColumnData()
                    ? text("gui.list.item.poster", poster.getColumns(), poster.getRows())
                    : text("gui.list.item.poster-parts", poster.getMapCount());
        } else {
            description = text("gui.list.item.single");
        }
        List<String> lore = new ArrayList<>();
        lore.add(description);
        lore.add("");
        lore.add(text("gui.list.item.id", map.getId()));
        lore.add("");
        if (canGet()) {
            lore.add(text("gui.list.item.left-click"));
        }
        lore.add(text("gui.list.item.right-click"));
        ItemStack item = icon(Material.FILLED_MAP, text("gui.list.item.name", map.getName()), lore);
        if (item.getItemMeta() instanceof MapMeta meta) {
            meta.setColor(Color.GREEN);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void onMapClick(ImageMap map, ClickType click) {
        if (!plugin.mapManager().exists(map)) {
            refresh();
            return;
        }
        if (click.isRightClick()) {
            new MapDetailGui(plugin, viewer, map, this).open();
            return;
        }
        if (click.isLeftClick()) {
            if (!canGet()) {
                messages().send(viewer, "general.no-permission");
                return;
            }
            if (plugin.mapItems().give(viewer, map)) {
                messages().send(viewer, "general.inventory-overflow");
            }
        }
    }

    private ItemStack emptyIcon() {
        if (isOwn()) {
            List<String> lore = Perm.NEW.has(viewer)
                    ? lines("gui.list.empty.own-hint")
                    : lines("gui.list.empty.own-no-permission");
            return icon(Material.BARRIER, text("gui.list.empty.own"), lore);
        }
        return icon(Material.BARRIER, text("gui.list.empty.other", ownerName), null);
    }

    private ItemStack statsIcon() {
        MapManager maps = plugin.mapManager();
        PluginConfig config = plugin.settings();
        int images = maps.getImageCount(owner);
        int used = maps.getMapCount(owner);
        int global = config.mapGlobalLimit();
        int personal = config.mapPlayerLimit();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(messages().plural(viewer, "gui.stats.images", images));
        lore.add(messages().plural(viewer, "gui.stats.maps", used));
        if (global > 0 || personal > 0) {
            int left = Integer.MAX_VALUE;
            if (global > 0) {
                left = Math.min(left, global - maps.getTotalMapCount());
            }
            if (personal > 0) {
                left = Math.min(left, personal - used);
            }
            left = Math.max(0, left);
            int percent = used + left == 0 ? 100 : (int) Math.round(100.0 * used / (used + left));
            lore.add("");
            lore.add(text("gui.stats.limits-title"));
            lore.add("");
            lore.add(text("gui.stats.server-limit", global > 0 ? String.valueOf(global) : text("gui.stats.unlimited")));
            lore.add(text("gui.stats.player-limit", personal > 0 ? String.valueOf(personal) : text("gui.stats.unlimited")));
            lore.add("");
            lore.add(text("gui.stats.quota-used", percent));
            lore.add(messages().plural(viewer, "gui.stats.maps-left", left));
        }
        return icon(Material.ENCHANTED_BOOK, text("gui.stats.title"), lore);
    }
}
