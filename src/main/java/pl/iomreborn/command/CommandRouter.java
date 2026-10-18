package pl.iomreborn.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import pl.iomreborn.ImageOnMapReborn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Executor of /maptool (with subcommands) and of the shortcut commands bound to a single subcommand. */
public final class CommandRouter implements TabExecutor {

    private final ImageOnMapReborn plugin;
    private final List<SubCommand> subcommands;
    private final Map<String, SubCommand> lookup = new LinkedHashMap<>();
    private final SubCommand direct;

    private CommandRouter(ImageOnMapReborn plugin, List<SubCommand> subcommands, SubCommand direct) {
        this.plugin = plugin;
        this.subcommands = subcommands;
        this.direct = direct;
        for (SubCommand sub : subcommands) {
            lookup.put(sub.name(), sub);
            for (String alias : sub.aliases()) {
                lookup.putIfAbsent(alias, sub);
            }
        }
    }

    public static CommandRouter of(ImageOnMapReborn plugin, List<SubCommand> subcommands) {
        return new CommandRouter(plugin, List.copyOf(subcommands), null);
    }

    public static CommandRouter direct(ImageOnMapReborn plugin, SubCommand subcommand) {
        return new CommandRouter(plugin, List.of(subcommand), subcommand);
    }

    public SubCommand get(String name) {
        return lookup.get(name);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (direct != null) {
            dispatch(direct, sender, label, args);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equals("?")) {
            sendHelp(sender, label);
            return true;
        }
        SubCommand sub = lookup.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            plugin.messages().send(sender, "general.unknown-subcommand", label);
            return true;
        }
        dispatch(sub, sender, label + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    private void dispatch(SubCommand sub, CommandSender sender, String label, String[] args) {
        if (!sub.canUse(sender)) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        try {
            sub.execute(sender, label, args);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error while running /" + label, ex);
            plugin.messages().send(sender, "general.internal-error");
        }
    }

    @SuppressWarnings("deprecation")
    private void sendHelp(CommandSender sender, String label) {
        plugin.messages().send(sender, "command.help.header", plugin.getDescription().getVersion());
        for (SubCommand sub : subcommands) {
            if (sub.canUse(sender)) {
                plugin.messages().send(sender, "command.help.entry",
                        plugin.messages().get(sender, "command." + sub.name() + ".usage"),
                        plugin.messages().get(sender, "command." + sub.name() + ".description"));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            if (direct != null) {
                return direct.canUse(sender) ? direct.complete(sender, args) : Collections.emptyList();
            }
            if (args.length <= 1) {
                List<String> names = new ArrayList<>();
                for (SubCommand sub : subcommands) {
                    if (sub.canUse(sender)) {
                        names.add(sub.name());
                    }
                }
                names.add("help");
                return SubCommand.filter(names, args.length == 0 ? "" : args[0]);
            }
            SubCommand sub = lookup.get(args[0].toLowerCase(Locale.ROOT));
            if (sub == null || !sub.canUse(sender)) {
                return Collections.emptyList();
            }
            return sub.complete(sender, Arrays.copyOfRange(args, 1, args.length));
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
    }
}
