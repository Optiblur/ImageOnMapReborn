package pl.iomreborn.command.sub;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.PlayerLookup;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.PosterMap;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** /maptool list [player]: clickable list of the images of a player. */
public final class ListCommand extends SubCommand {

    public ListCommand(ImageOnMapReborn plugin) {
        super(plugin, "list");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.LIST.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ArgParser.Parsed parsed = ArgParser.parse(args);
        if (parsed.size() > 1) {
            usage(sender);
            return;
        }
        Player self = sender instanceof Player player ? player : null;
        if (parsed.size() == 0 || (self != null && parsed.get(0).value().equalsIgnoreCase(self.getName()))) {
            if (self == null) {
                usage(sender);
                return;
            }
            show(sender, self.getUniqueId(), self.getName(), true);
            return;
        }
        if (self != null && !Perm.LIST_OTHER.has(self)) {
            messages().send(sender, "general.no-permission");
            return;
        }
        String name = parsed.get(0).value();
        PlayerLookup.find(plugin, name, owner -> {
            if (owner == null) {
                messages().send(sender, "general.player-not-found", name);
                return;
            }
            show(sender, owner.getUniqueId(), PlayerLookup.nameOf(owner, name), false);
        });
    }

    private void show(CommandSender sender, UUID owner, String ownerName, boolean own) {
        List<ImageMap> maps = maps().getMaps(owner);
        if (maps.isEmpty()) {
            messages().send(sender, "command.list.none");
            return;
        }
        messages().sendPlural(sender, "command.list.header", maps.size());

        if (!(sender instanceof Player player)) {
            for (ImageMap map : maps) {
                messages().send(sender, "command.list.console-entry", map.getId(), map.getName(), size(sender, map));
            }
            return;
        }

        TextComponent line = new TextComponent();
        for (int i = 0; i < maps.size(); i++) {
            ImageMap map = maps.get(i);
            if (i > 0) {
                TextComponent separator = new TextComponent(", ");
                separator.setColor(net.md_5.bungee.api.ChatColor.GRAY);
                line.addExtra(separator);
            }
            TextComponent entry = new TextComponent(map.getId());
            entry.setColor(net.md_5.bungee.api.ChatColor.WHITE);
            String command = "/maptool get " + (own ? "" : ownerName + ":") + pl.iomreborn.util.Text.quote(map.getId());
            entry.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
            BaseComponent[] hover = TextComponent.fromLegacyText(
                    messages().get(sender, "command.list.hover", map.getName(), map.getId(), size(sender, map)));
            entry.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
            line.addExtra(entry);
        }
        player.spigot().sendMessage(line);
    }

    private String size(CommandSender sender, ImageMap map) {
        if (map instanceof PosterMap poster) {
            if (poster.hasColumnData()) {
                return poster.getColumns() + " × " + poster.getRows();
            }
            return messages().get(sender, "command.list.parts", poster.getMapCount());
        }
        return "1 × 1";
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && Perm.LIST_OTHER.has(sender)) {
            return onlinePlayers(args[0]);
        }
        return Collections.emptyList();
    }
}
