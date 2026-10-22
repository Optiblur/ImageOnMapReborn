package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.image.Dithering;
import pl.iomreborn.image.ScalingMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /tomap and /maptool new: {@code <URL> [resize|resize-stretched|resize-covered] [width height | WxH]
 * [dither|nodither]}. The dithering word may be anywhere after the URL.
 */
public final class NewCommand extends SubCommand {

    private static final List<String> MODES = List.of("resize", "resize-stretched", "resize-covered");
    static final List<String> DITHER_WORDS = List.of("dither", "nodither");

    public NewCommand(ImageOnMapReborn plugin) {
        super(plugin, "new");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.NEW.has(sender);
    }

    /** The arguments without the dithering word, and that word as TRUE/FALSE (null when absent). */
    record Options(String[] args, Boolean dither) {
    }

    static Options extractDithering(String[] args) {
        List<String> rest = new ArrayList<>(args.length);
        Boolean dither = null;
        for (int i = 0; i < args.length; i++) {
            Boolean word = i == 0 ? null : Dithering.parseCommandWord(args[i]);
            if (word != null) {
                dither = word;
            } else {
                rest.add(args[i]);
            }
        }
        return new Options(rest.toArray(new String[0]), dither);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] rawArgs) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Options options = extractDithering(rawArgs);
        String[] args = options.args();
        if (args.length < 1) {
            messages().send(sender, "command.new.no-url");
            usage(sender);
            return;
        }
        if (args.length > 4) {
            usage(sender);
            return;
        }
        ScalingMode mode = ScalingMode.NONE;
        int width = 0;
        int height = 0;
        if (args.length >= 2) {
            Optional<ScalingMode> parsed = ScalingMode.parse(args[1]);
            if (parsed.isEmpty()) {
                messages().send(sender, "command.new.invalid-mode");
                return;
            }
            mode = parsed.get();
            if (args.length >= 3) {
                int[] size = parseSize(args);
                if (size == null) {
                    messages().send(sender, "command.new.invalid-size");
                    return;
                }
                width = size[0];
                height = size[1];
            }
        }
        plugin.renderService().create(player, args[0], mode, width, height, options.dither());
    }

    /** Accepts "4 3", "4x3" and "4*3"; "0 0" means a single map. Returns null when invalid. */
    static int[] parseSize(String[] args) {
        try {
            int width;
            int height;
            if (args.length >= 4) {
                width = Integer.parseInt(args[2].trim());
                height = Integer.parseInt(args[3].trim());
            } else {
                String[] parts = args[2].toLowerCase(Locale.ROOT).split("[x*]");
                if (parts.length != 2) {
                    return null;
                }
                width = Integer.parseInt(parts[0].trim());
                height = Integer.parseInt(parts[1].trim());
            }
            if (width == 0 && height == 0) {
                return new int[] {0, 0};
            }
            if (width < 1 || height < 1) {
                return null;
            }
            return new int[] {width, height};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return args.length == 1 && args[0].isEmpty() ? List.of("https://") : Collections.emptyList();
        }
        // Earlier words decide what comes next; a dithering word may be given anywhere after the link.
        String[] before = new String[args.length - 1];
        System.arraycopy(args, 0, before, 0, before.length);
        Options options = extractDithering(before);
        String last = args[args.length - 1];
        List<String> suggestions = new ArrayList<>();
        switch (options.args().length) {
            case 1:
                suggestions.addAll(MODES);
                break;
            case 2:
                suggestions.addAll(List.of("1", "2", "3", "4", "2x2", "3x2", "4x3"));
                break;
            case 3:
                if (!options.args()[2].matches("\\d+")) {
                    break;
                }
                suggestions.addAll(List.of("1", "2", "3", "4"));
                break;
            default:
                break;
        }
        if (options.dither() == null) {
            suggestions.addAll(DITHER_WORDS);
        }
        return filter(suggestions, last);
    }
}
