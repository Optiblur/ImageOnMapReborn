package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.map.MapManager;
import pl.iomreborn.update.UpdateChecker;

/**
 * /maptool info: version, update status and statistics of the plugin. The console gets the same framed summary
 * as at startup, players get it as chat lines in their language.
 */
public final class InfoCommand extends SubCommand {

    public InfoCommand(ImageOnMapReborn plugin) {
        super(plugin, "info", "version");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.ADMINISTRATIVE.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        UpdateChecker updates = plugin.updateChecker();
        if (!(sender instanceof Player)) {
            plugin.summary(updates.describe()).print();
            return;
        }
        MapManager maps = maps();
        messages().send(sender, "command.info.header", plugin.getDescription().getVersion());
        messages().send(sender, "command.info.author", String.join(", ", plugin.getDescription().getAuthors()));
        messages().send(sender, "command.info.images", maps.getTotalImageCount(), maps.getTotalMapCount(),
                maps.getStores().size());
        messages().send(sender, "command.info.uploads", plugin.localImages().names().size());
        messages().send(sender, "command.info.language", messages().serverLocale());
        messages().send(sender, "command.info.server", ImageOnMapReborn.serverName());
        UpdateChecker.Snapshot now = updates.snapshot();
        switch (now.status()) {
            case OFF -> messages().send(sender, "command.info.update-off");
            case UNKNOWN -> messages().send(sender, "command.info.update-unknown");
            case LATEST -> messages().send(sender, "command.info.update-latest");
            case NEWER_BUILD -> messages().send(sender, "command.info.update-newer", now.latest().version());
            case UPDATE -> messages().send(sender, "command.info.update-available", now.newer().version(),
                    now.newer().url());
        }
    }
}
