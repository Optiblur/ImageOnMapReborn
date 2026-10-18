package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.map.MapManager;

import java.util.Collections;
import java.util.List;

/** /maptool rename [player:]<map name> <new name>; names with spaces go between quotes. */
public final class RenameCommand extends SubCommand {

    public RenameCommand(ImageOnMapReborn plugin) {
        super(plugin, "rename");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.RENAME.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ArgParser.Parsed parsed = ArgParser.parse(args);
        String owner = null;
        int start = 0;
        if (parsed.size() > 0 && parsed.get(0).qualifier()) {
            owner = parsed.get(0).value();
            start = 1;
        }
        if (parsed.size() - start != 2) {
            usage(sender);
            messages().send(sender, "command.rename.quotes-hint");
            return;
        }
        String newName = MapManager.sanitizeName(parsed.get(start + 1).value());
        if (newName.isEmpty()) {
            messages().send(sender, "command.rename.invalid-name");
            return;
        }
        resolveMap(sender, new MapRef(owner, parsed.get(start).value()), Perm.RENAME_OTHER, (map, mapOwner) -> {
            maps().rename(map, newName);
            messages().send(sender, "command.rename.success", map.getName(), map.getId());
        });
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? completeMaps(sender, args[0], Perm.RENAME_OTHER) : Collections.emptyList();
    }
}
