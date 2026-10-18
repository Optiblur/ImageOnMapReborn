package pl.iomreborn.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.iomreborn.Perm;

import java.util.OptionalInt;

/** Permission-based limits of a player, captured on the main thread before rendering starts. */
public record LimitContext(boolean bypassMapLimit, boolean bypassImageLimit,
                           OptionalInt mapLimit, OptionalInt imageLimit) {

    public static final LimitContext UNLIMITED =
            new LimitContext(true, true, OptionalInt.empty(), OptionalInt.empty());

    public static LimitContext of(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return UNLIMITED;
        }
        return new LimitContext(
                Perm.BYPASS_MAP_LIMIT.has(sender),
                Perm.BYPASS_IMAGE_LIMIT.has(sender),
                Perm.numericLimit(sender, Perm.MAP_LIMIT_PREFIX),
                Perm.numericLimit(sender, Perm.IMAGE_LIMIT_PREFIX));
    }
}
