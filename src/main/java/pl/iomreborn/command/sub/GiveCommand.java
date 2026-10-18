package pl.iomreborn.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /givemap and /maptool give: {@code <player> [owner:]<map name>}. Works from the console and command blocks
 * (the owner is then required); target selectors such as {@code @p} are supported.
 */
public final class GiveCommand extends SubCommand {

    public GiveCommand(ImageOnMapReborn plugin) {
        super(plugin, "give");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.GIVE.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ArgParser.Parsed parsed = ArgParser.parse(args);
        if (parsed.size() < 2 || parsed.get(0).qualifier()) {
            usage(sender);
            return;
        }
        List<Player> targets = targets(sender, parsed.get(0).value());
        if (targets.isEmpty()) {
            messages().send(sender, "general.player-not-online", parsed.get(0).value());
            return;
        }

        MapRef ref;
        if (parsed.get(1).qualifier()) {
            ref = new MapRef(parsed.get(1).value(), parsed.joinFrom(2));
        } else if (parsed.size() >= 3 && !ownMapExists(sender, parsed.joinFrom(1))) {
            // ImageOnMap also accepted "/givemap <player> <owner> <map>".
            ref = new MapRef(parsed.get(1).value(), parsed.joinFrom(2));
        } else {
            ref = new MapRef(null, parsed.joinFrom(1));
        }

        resolveMap(sender, ref, null, (map, owner) -> {
            for (Player target : targets) {
                if (!target.isOnline()) {
                    continue;
                }
                if (plugin.mapItems().give(target, map)) {
                    messages().send(target, "general.inventory-overflow");
                }
                messages().send(sender, "command.give.success", map.getName(), target.getName());
            }
        });
    }

    private boolean ownMapExists(CommandSender sender, String query) {
        return sender instanceof Player player && maps().findMap(player.getUniqueId(), query) != null;
    }

    private static List<Player> targets(CommandSender sender, String name) {
        List<Player> players = new ArrayList<>();
        if (name.startsWith("@")) {
            try {
                for (Entity entity : Bukkit.selectEntities(sender, name)) {
                    if (entity instanceof Player player) {
                        players.add(player);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // invalid selector
            }
            return players;
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(name)) {
                    player = online;
                    break;
                }
            }
        }
        if (player != null) {
            players.add(player);
        }
        return players;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return onlinePlayers(args[0]);
        }
        if (args.length == 2) {
            return completeMaps(sender, args[1], null);
        }
        return Collections.emptyList();
    }
}
