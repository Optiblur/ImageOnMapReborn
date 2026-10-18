package pl.iomreborn.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.i18n.Messages;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.MapManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/** A /maptool subcommand. Shortcut commands (/tomap, /maps, /givemap) run the same objects. */
public abstract class SubCommand {

    protected final ImageOnMapReborn plugin;
    private final String name;
    private final List<String> aliases;

    protected SubCommand(ImageOnMapReborn plugin, String name, String... aliases) {
        this.plugin = plugin;
        this.name = name;
        this.aliases = List.of(aliases);
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public abstract boolean canUse(CommandSender sender);

    public abstract void execute(CommandSender sender, String label, String[] args);

    public List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    protected Messages messages() {
        return plugin.messages();
    }

    protected MapManager maps() {
        return plugin.mapManager();
    }

    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages().send(sender, "general.player-only");
        return null;
    }

    protected void usage(CommandSender sender) {
        messages().send(sender, "general.usage", messages().get(sender, "command." + name + ".usage"));
    }

    /** A map reference: optional owner (from a {@code player:} prefix) and the name or ID of the map. */
    public record MapRef(String owner, String query) {
    }

    protected static MapRef mapRef(ArgParser.Parsed parsed, int from, int to) {
        if (from >= to) {
            return new MapRef(null, "");
        }
        ArgParser.Token first = parsed.get(from);
        if (first.qualifier()) {
            return new MapRef(first.value(), parsed.join(from + 1, to));
        }
        return new MapRef(null, parsed.join(from, to));
    }

    /**
     * Finds the map a command refers to. Without an owner the sender's own maps are searched; maps of another
     * player need {@code otherPermission} (null = no extra permission). The console may access every map.
     */
    protected void resolveMap(CommandSender sender, MapRef ref, Perm otherPermission,
                              BiConsumer<ImageMap, OfflinePlayer> action) {
        if (ref.query().isBlank()) {
            usage(sender);
            return;
        }
        Player self = sender instanceof Player player ? player : null;
        if (ref.owner() == null || (self != null && ref.owner().equalsIgnoreCase(self.getName()))) {
            if (self == null) {
                messages().send(sender, "general.owner-required");
                return;
            }
            ImageMap map = maps().findMap(self.getUniqueId(), ref.query());
            if (map == null) {
                messages().send(sender, "general.map-not-found");
                return;
            }
            action.accept(map, self);
            return;
        }
        if (self != null) {
            // "Re:Zero" is more likely a map name than the map "Zero" of a player called "Re".
            ImageMap own = maps().findMap(self.getUniqueId(), ref.owner() + ":" + ref.query());
            if (own != null) {
                action.accept(own, self);
                return;
            }
            if (otherPermission != null && !otherPermission.has(self)) {
                messages().send(sender, "general.no-permission");
                return;
            }
        }
        PlayerLookup.find(plugin, ref.owner(), owner -> {
            if (owner == null) {
                messages().send(sender, "general.player-not-found", ref.owner());
                return;
            }
            ImageMap map = maps().findMap(owner.getUniqueId(), ref.query());
            if (map == null) {
                messages().send(sender, "general.map-not-found");
                return;
            }
            action.accept(map, owner);
        });
    }

    /** Completes a map argument: own map IDs, and {@code player:} prefixes when other maps are allowed. */
    protected List<String> completeMaps(CommandSender sender, String prefix, Perm otherPermission) {
        List<String> options = new ArrayList<>();
        Player self = sender instanceof Player player ? player : null;
        boolean others = self == null || otherPermission == null || otherPermission.has(sender);
        int colon = prefix.indexOf(':');
        if (colon > 0) {
            String ownerName = prefix.substring(0, colon);
            Player owner = Bukkit.getPlayerExact(ownerName);
            if (owner != null && (others || owner == self)) {
                for (ImageMap map : maps().getMaps(owner.getUniqueId())) {
                    options.add(owner.getName() + ":" + quoteIfNeeded(map.getId()));
                }
            }
            return filter(options, prefix);
        }
        if (self != null) {
            for (ImageMap map : maps().getMaps(self.getUniqueId())) {
                options.add(quoteIfNeeded(map.getId()));
            }
        }
        if (others) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != self) {
                    options.add(player.getName() + ":");
                }
            }
        }
        return filter(options, prefix);
    }

    protected static String quoteIfNeeded(String value) {
        return value.indexOf(' ') >= 0 || value.indexOf(':') >= 0 ? "\"" + value + "\"" : value;
    }

    protected static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    protected static List<String> onlinePlayers(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filter(names, prefix);
    }
}
