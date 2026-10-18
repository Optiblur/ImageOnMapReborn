package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.PlayerLookup;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.gui.MapListGui;

import java.util.Collections;
import java.util.List;

/** /maps and /maptool explore [player]: opens the map GUI. */
public final class ExploreCommand extends SubCommand {

    public ExploreCommand(ImageOnMapReborn plugin) {
        super(plugin, "explore");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.EXPLORE.has(sender) || Perm.LIST.has(sender);
    }

    private static boolean canSeeOthers(CommandSender sender) {
        return Perm.EXPLORE_OTHER.has(sender) || Perm.LIST_OTHER.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        ArgParser.Parsed parsed = ArgParser.parse(args);
        if (parsed.size() > 1) {
            usage(sender);
            return;
        }
        if (parsed.size() == 0 || parsed.get(0).value().equalsIgnoreCase(player.getName())) {
            new MapListGui(plugin, player, player.getUniqueId(), player.getName()).open();
            return;
        }
        if (!canSeeOthers(player)) {
            messages().send(sender, "general.no-permission");
            return;
        }
        String name = parsed.get(0).value();
        PlayerLookup.find(plugin, name, owner -> {
            if (owner == null) {
                messages().send(sender, "general.player-not-found", name);
                return;
            }
            if (player.isOnline()) {
                new MapListGui(plugin, player, owner.getUniqueId(), PlayerLookup.nameOf(owner, name)).open();
            }
        });
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && canSeeOthers(sender)) {
            return onlinePlayers(args[0]);
        }
        return Collections.emptyList();
    }
}
