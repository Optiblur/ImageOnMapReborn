package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.ArgParser;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.image.Dithering;
import pl.iomreborn.image.ScalingMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /maptool update [player:]<map name> <new URL> [stretched|covered] [dither|nodither]:
 * new picture on the same maps.
 */
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
        if (urlIndex <= 0 || parsed.size() > urlIndex + 3) {
            usage(sender);
            return;
        }
        ScalingMode mode = ScalingMode.CONTAINED;
        boolean modeGiven = false;
        Boolean dither = null;
        for (int i = urlIndex + 1; i < parsed.size(); i++) {
            String word = parsed.get(i).value();
            Boolean ditherWord = Dithering.parseCommandWord(word);
            if (ditherWord != null && dither == null) {
                dither = ditherWord;
                continue;
            }
            ScalingMode parsedMode = modeGiven ? null : ScalingMode.parse(word).orElse(null);
            if (parsedMode == null) {
                messages().send(sender, "command.update.invalid-mode");
                return;
            }
            mode = parsedMode;
            modeGiven = true;
        }
        for (String flag : List.of("dither", "nodither", "no-dither")) {
            if (dither == null && parsed.hasFlag(flag)) {
                dither = Dithering.parseCommandWord(flag);
            }
        }
        String url = parsed.get(urlIndex).value();
        ScalingMode scaling = mode;
        Boolean ditherChoice = dither;
        resolveMap(sender, mapRef(parsed, 0, urlIndex), Perm.UPDATE_OTHER,
                (map, owner) -> plugin.renderService().update(sender, map, url, scaling, ditherChoice));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return completeMaps(sender, args[0], Perm.UPDATE_OTHER);
        }
        int urlIndex = -1;
        for (int i = 0; i < args.length - 1; i++) {
            if (ArgParser.isUrl(args[i])) {
                urlIndex = i;
                break;
            }
        }
        if (urlIndex < 0) {
            return Collections.emptyList();
        }
        boolean modeGiven = false;
        boolean ditherGiven = false;
        for (int i = urlIndex + 1; i < args.length - 1; i++) {
            if (Dithering.parseCommandWord(args[i]) != null) {
                ditherGiven = true;
            } else {
                modeGiven = true;
            }
        }
        List<String> options = new ArrayList<>();
        if (!modeGiven) {
            options.addAll(List.of("stretched", "covered"));
        }
        if (!ditherGiven) {
            options.addAll(NewCommand.DITHER_WORDS);
        }
        return filter(options, args[args.length - 1]);
    }
}
