package pl.iomreborn.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.map.ImageMap;

/** Asks for confirmation before an image is deleted forever. */
public final class ConfirmDeleteGui extends Gui {

    private final ImageMap map;
    private final MapDetailGui parent;

    public ConfirmDeleteGui(ImageOnMapReborn plugin, Player viewer, ImageMap map, MapDetailGui parent) {
        super(plugin, viewer);
        this.map = map;
        this.parent = parent;
    }

    @Override
    protected String title() {
        return text("gui.delete.title", map.getName());
    }

    @Override
    protected void build() {
        set(13, icon(Material.FILLED_MAP, text("gui.delete.info.name"),
                lines("gui.delete.info.lore", map.getName(), map.getId(), map.getMapCount())));
        for (int row = 3; row < ROWS; row++) {
            for (int column = 0; column < 4; column++) {
                set(row * 9 + column, icon(Material.RED_STAINED_GLASS_PANE, text("gui.delete.confirm"),
                        lines("gui.delete.confirm-lore")), click -> delete());
            }
            for (int column = 5; column < 9; column++) {
                set(row * 9 + column, icon(Material.LIME_STAINED_GLASS_PANE, text("gui.delete.cancel"),
                        lines("gui.delete.cancel-lore")), click -> parent.open());
            }
        }
    }

    private void delete() {
        if (!parent.canDelete()) {
            messages().send(viewer, "general.no-longer-allowed");
            viewer.closeInventory();
            return;
        }
        if (plugin.mapManager().exists(map)) {
            plugin.removeFromInventory(viewer, map);
            plugin.deleteImage(map);
            messages().send(viewer, "command.delete.success");
        }
        if (parent.parent() != null) {
            parent.parent().open();
        } else {
            viewer.closeInventory();
        }
    }
}
