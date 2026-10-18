package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.image.ScalingMode;

import java.util.Collections;
import java.util.List;

/** /maptool update [player:]<map name> <new URL> [stretched|covered]: new picture on the same maps. */
public final class UpdateCommand extends SubCommand {

    public UpdateCommand(ImageOnMapReborn plugin) {
        super(plugin, "update");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.UPDATE.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ArgParser.Parsed parsed = ArgParser.parse(args);
        int urlIndex = -1;
        for (int i = 0; i < parsed.size(); i++) {
            if (!parsed.get(i).quoted() && ArgParser.isUrl(parsed.get(i).value())) {
                urlIndex = i;
                break;
            }
        }
        if (urlIndex <= 0 || parsed.size() > urlIndex + 2) {
            usage(sender);
            return;
        }
        ScalingMode mode = ScalingMode.CONTAINED;
        if (parsed.size() == urlIndex + 2) {
            mode = ScalingMode.parse(parsed.get(urlIndex + 1).value()).orElse(null);
            if (mode == null) {
                messages().send(sender, "command.update.invalid-mode");
                return;
            }
        }
        String url = parsed.get(urlIndex).value();
        ScalingMode scaling = mode;
        resolveMap(sender, mapRef(parsed, 0, urlIndex), Perm.UPDATE_OTHER,
                (map, owner) -> plugin.renderService().update(sender, map, url, scaling));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return completeMaps(sender, args[0], Perm.UPDATE_OTHER);
        }
        if (args.length >= 3 && ArgParser.isUrl(args[args.length - 2])) {
            return filter(List.of("stretched", "covered"), args[args.length - 1]);
        }
        return Collections.emptyList();
    }
}
