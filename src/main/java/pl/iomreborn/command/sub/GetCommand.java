package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.SubCommand;

import java.util.Collections;
import java.util.List;

/** /maptool get [player:]<map name>: gives the map (or splatter map) to the sender. */
public final class GetCommand extends SubCommand {

    public GetCommand(ImageOnMapReborn plugin) {
        super(plugin, "get");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.GET.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        ArgParser.Parsed parsed = ArgParser.parse(args);
        if (parsed.size() == 0) {
            usage(sender);
            return;
        }
        resolveMap(sender, mapRef(parsed, 0, parsed.size()), Perm.GET_OTHER, (map, owner) -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.mapItems().give(player, map)) {
                messages().send(player, "general.inventory-overflow");
            }
        });
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? completeMaps(sender, args[0], Perm.GET_OTHER) : Collections.emptyList();
    }
}
