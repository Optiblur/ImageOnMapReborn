package pl.iomreborn.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.MapManager;
import pl.iomreborn.map.PosterMap;
import pl.iomreborn.map.SingleMap;

import java.util.ArrayList;
import java.util.List;

/** Details of one image: its parts (scrollable for big posters), and rename / delete / get actions. */
public final class MapDetailGui extends Gui {

    private static final int VIEW_COLUMNS = 9;
    private static final int VIEW_ROWS = 5;
    private static final int PAGE_SIZE = VIEW_COLUMNS * VIEW_ROWS;

    private final ImageMap map;
    private final MapListGui parent;
    private int offsetX;
    private int offsetY;
    private int page;

    public MapDetailGui(ImageOnMapReborn plugin, Player viewer, ImageMap map, MapListGui parent) {
        super(plugin, viewer);
        this.map = map;
        this.parent = parent;
    }

    MapListGui parent() {
        return parent;
    }

    private boolean isOwn() {
        return map.getOwner().equals(viewer.getUniqueId());
    }

    private boolean canGet() {
        return isOwn() ? Perm.GET.has(viewer) : Perm.GET_OTHER.has(viewer);
    }

    private boolean canRename() {
        return isOwn() ? Perm.RENAME.has(viewer) : Perm.RENAME_OTHER.has(viewer);
    }

    boolean canDelete() {
        return isOwn() ? Perm.DELETE.has(viewer) : Perm.DELETE_OTHER.has(viewer);
    }

    @Override
    protected String title() {
        return isOwn()
                ? text("gui.detail.title.own", map.getName())
                : text("gui.detail.title.other", map.getName(), parent == null ? "?" : parent.ownerName());
    }

    @Override
    protected void build() {
        set(45, icon(Material.EMERALD, text("gui.detail.back.name"), lines("gui.detail.back.lore")), click -> back());
        if (!plugin.mapManager().exists(map)) {
            set(22, icon(Material.BARRIER, text("general.map-not-found"), null));
            return;
        }

        if (map instanceof PosterMap poster) {
            if (poster.hasColumnData()) {
                buildGrid(poster);
            } else {
                buildList(poster);
            }
        } else {
            set(22, partIcon(Material.MAP, null, null, null), click -> giveSingle());
        }

        if (canRename()) {
            set(47, icon(Material.WRITABLE_BOOK, text("gui.detail.rename.name"), lines("gui.detail.rename.lore")),
                    click -> rename());
        }
        if (canDelete()) {
            set(48, icon(Material.BARRIER, text("gui.detail.delete.name"), lines("gui.detail.delete.lore")),
                    click -> new ConfirmDeleteGui(plugin, viewer, map, this).open());
        }
        if (canGet()) {
            set(49, icon(Material.FILLED_MAP, text("gui.detail.get-all.name"), lines("gui.detail.get-all.lore")),
                    click -> giveAll());
        }
    }

    private void buildGrid(PosterMap poster) {
        int columns = poster.getColumns();
        int rows = poster.getRows();
        offsetX = Math.max(0, Math.min(offsetX, columns - VIEW_COLUMNS));
        offsetY = Math.max(0, Math.min(offsetY, rows - VIEW_ROWS));
        for (int r = 0; r < VIEW_ROWS; r++) {
            for (int c = 0; c < VIEW_COLUMNS; c++) {
                int column = offsetX + c;
                int row = offsetY + r;
                if (column >= columns || row >= rows) {
                    continue;
                }
                int index = row * columns + column;
                Material material = (column + row) % 2 == 0 ? Material.MAP : Material.PAPER;
                set(r * 9 + c, partIcon(material, row + 1, column + 1, null), click -> givePart(poster, index));
            }
        }
        if (offsetX > 0) {
            set(50, icon(Material.ARROW, text("gui.nav.left"), lines("gui.nav.shift-hint")),
                    click -> scroll(click, -1, 0));
        }
        if (offsetY > 0) {
            set(51, icon(Material.ARROW, text("gui.nav.up"), lines("gui.nav.shift-hint")),
                    click -> scroll(click, 0, -1));
        }
        if (offsetY + VIEW_ROWS < rows) {
            set(52, icon(Material.ARROW, text("gui.nav.down"), lines("gui.nav.shift-hint")),
                    click -> scroll(click, 0, 1));
        }
        if (offsetX + VIEW_COLUMNS < columns) {
            set(53, icon(Material.ARROW, text("gui.nav.right"), lines("gui.nav.shift-hint")),
                    click -> scroll(click, 1, 0));
        }
    }

    /** Scrolls by one row/column, or by a whole screen with shift-click. */
    private void scroll(ClickType click, int dx, int dy) {
        boolean fullScreen = click.isShiftClick();
        offsetX += dx * (fullScreen ? VIEW_COLUMNS : 1);
        offsetY += dy * (fullScreen ? VIEW_ROWS : 1);
        refresh();
    }

    private void buildList(PosterMap poster) {
        int count = poster.getMapCount();
        int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < count; i++) {
            int index = start + i;
            Material material = index % 2 == 0 ? Material.MAP : Material.PAPER;
            set(i, partIcon(material, null, null, index + 1), click -> givePart(poster, index));
        }
        if (page > 0) {
            set(52, icon(Material.ARROW, text("gui.nav.previous"), List.of(text("gui.nav.page", page, pages))),
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
    }

    private ItemStack partIcon(Material material, Integer row, Integer column, Integer part) {
        List<String> lore = new ArrayList<>();
        if (row != null) {
            lore.add(text("gui.detail.part.row", row));
            lore.add(text("gui.detail.part.column", column));
        }
        if (part != null) {
            lore.add(text("gui.detail.part.index", part));
        }
        if (canGet()) {
            if (!lore.isEmpty()) {
                lore.add("");
            }
            lore.add(text(map instanceof SingleMap ? "gui.detail.single.click" : "gui.detail.part.click"));
        }
        String name = map instanceof SingleMap ? text("gui.list.item.name", map.getName()) : text("gui.detail.part.name");
        return icon(material, name, lore);
    }

    private boolean checkGet() {
        if (!plugin.mapManager().exists(map)) {
            refresh();
            return false;
        }
        if (!canGet()) {
            messages().send(viewer, "general.no-permission");
            return false;
        }
        return true;
    }

    private void giveSingle() {
        if (checkGet() && map instanceof SingleMap single) {
            give(List.of(plugin.mapItems().singleItem(single, viewer)));
        }
    }

    private void givePart(PosterMap poster, int index) {
        if (checkGet()) {
            give(List.of(plugin.mapItems().partItem(poster, index, viewer)));
        }
    }

    private void giveAll() {
        if (checkGet()) {
            give(plugin.mapItems().itemsFor(map, viewer));
        }
    }

    private void give(List<ItemStack> items) {
        if (plugin.mapItems().give(viewer, items)) {
            messages().send(viewer, "general.inventory-overflow");
        }
    }

    private void rename() {
        if (!canRename()) {
            messages().send(viewer, "general.no-longer-allowed");
            refresh();
            return;
        }
        viewer.closeInventory();
        messages().send(viewer, "command.rename.prompt", map.getName());
        plugin.chatPrompts().ask(viewer, answer -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (!canRename()) {
                messages().send(viewer, "general.no-longer-allowed");
                return;
            }
            if (!plugin.mapManager().exists(map)) {
                messages().send(viewer, "general.map-not-found");
                return;
            }
            String name = MapManager.sanitizeName(answer);
            if (name.isEmpty()) {
                messages().send(viewer, "command.rename.invalid-name");
            } else {
                plugin.mapManager().rename(map, name);
                messages().send(viewer, "command.rename.success", map.getName(), map.getId());
            }
            open();
        }, () -> {
            messages().send(viewer, "command.rename.cancelled");
            if (viewer.isOnline()) {
                open();
            }
        });
    }

    void back() {
        if (parent != null) {
            parent.open();
        } else {
            viewer.closeInventory();
        }
    }
}
