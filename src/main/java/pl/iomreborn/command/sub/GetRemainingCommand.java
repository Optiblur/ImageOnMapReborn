package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.item.PendingItems;

/** /maptool getremaining: gives the map items that did not fit in the inventory. */
public final class GetRemainingCommand extends SubCommand {

    public GetRemainingCommand(ImageOnMapReborn plugin) {
        super(plugin, "getremaining", "getrest");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.NEW.has(sender) || Perm.GET.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PendingItems pending = plugin.pendingItems();
        if (pending.size(player.getUniqueId()) == 0) {
            messages().send(sender, "command.getremaining.none");
            return;
        }
        int given = pending.giveAll(player);
        if (given == 0) {
            messages().send(sender, "command.getremaining.inventory-full");
            return;
        }
        int left = pending.size(player.getUniqueId());
        if (left > 0) {
            messages().sendPlural(sender, "command.getremaining.remaining", left);
        } else {
            messages().send(sender, "command.getremaining.done");
        }
    }
}
