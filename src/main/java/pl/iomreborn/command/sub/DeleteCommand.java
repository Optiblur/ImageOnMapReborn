package pl.iomreborn.command.sub;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.PlayerLookup;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.map.ImageMap;

import java.util.Collections;
import java.util.List;

/** /maptool delete [player:]<map name> [--confirm]: asks for confirmation, then deletes the image. */
public final class DeleteCommand extends SubCommand {

    public DeleteCommand(ImageOnMapReborn plugin) {
        super(plugin, "delete");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.DELETE.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ArgParser.Parsed parsed = ArgParser.parse(args);
        if (parsed.size() == 0) {
            usage(sender);
            return;
        }
        boolean confirmed = parsed.hasFlag("confirm");
        MapRef ref = mapRef(parsed, 0, parsed.size());
        resolveMap(sender, ref, Perm.DELETE_OTHER, (map, owner) -> {
            if (!plugin.mapManager().exists(map)) {
                messages().send(sender, "general.map-not-found");
                return;
            }
            if (!confirmed) {
                askConfirmation(sender, map, owner);
                return;
            }
            if (sender instanceof Player player) {
                plugin.removeFromInventory(player, map);
            }
            plugin.deleteImage(map);
            messages().send(sender, "command.delete.success");
        });
    }

    private void askConfirmation(CommandSender sender, ImageMap map, OfflinePlayer owner) {
        boolean own = sender instanceof Player player && player.getUniqueId().equals(map.getOwner());
        String command = "/maptool delete "
                + (own ? "" : PlayerLookup.nameOf(owner, map.getOwner().toString()) + ":")
                + pl.iomreborn.util.Text.quote(map.getId()) + " --confirm";
        if (!(sender instanceof Player player)) {
            messages().send(sender, "command.delete.confirm-console", map.getId(), command.substring(1));
            return;
        }
        TextComponent message = new TextComponent(TextComponent.fromLegacyText(
                messages().get(sender, "command.delete.confirm-prompt", map.getId(), map.getName())));
        TextComponent button = new TextComponent(TextComponent.fromLegacyText(
                messages().get(sender, "command.delete.confirm-button")));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacyText(messages().get(sender, "command.delete.confirm-hover")))));
        message.addExtra(" ");
        message.addExtra(button);
        player.spigot().sendMessage(message);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return completeMaps(sender, args[0], Perm.DELETE_OTHER);
        }
        if (args.length == 2) {
            return filter(List.of("--confirm"), args[1]);
        }
        return Collections.emptyList();
    }
}
