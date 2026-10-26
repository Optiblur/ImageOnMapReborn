package pl.iomreborn;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * Permission nodes. Names are identical to ImageOnMap so existing permission setups keep working; the nodes for
 * features ImageOnMap did not have follow the same style.
 */
public enum Perm {
    NEW("imageonmap.new", "imageonmap.userender"),
    LIST("imageonmap.list"),
    LIST_OTHER("imageonmap.listother"),
    EXPLORE("imageonmap.explore"),
    EXPLORE_OTHER("imageonmap.exploreother"),
    GET("imageonmap.get"),
    GET_OTHER("imageonmap.getother"),
    RENAME("imageonmap.rename"),
    RENAME_OTHER("imageonmap.renameother"),
    DELETE("imageonmap.delete"),
    DELETE_OTHER("imageonmap.deleteother"),
    UPDATE("imageonmap.update"),
    UPDATE_OTHER("imageonmap.updateother"),
    GIVE("imageonmap.give"),
    PLACE_SPLATTER("imageonmap.placesplattermap"),
    PLACE_INVISIBLE_SPLATTER("imageonmap.placeinvisiblesplattermap"),
    REMOVE_SPLATTER("imageonmap.removesplattermap"),
    ADMINISTRATIVE("imageonmap.administrative"),
    BYPASS_SIZE("imageonmap.bypasssize"),
    BYPASS_IMAGE_LIMIT("imageonmap.bypassimagelimit"),
    BYPASS_MAP_LIMIT("imageonmap.bypassmaplimit"),
    BYPASS_WHITELIST("imageonmap.bypasswhitelist"),
    /** New in ImageOnMapReborn: animated GIFs become animated maps. */
    ANIMATED("imageonmap.animated"),
    /** New in ImageOnMapReborn: told about new plugin versions on join. */
    NOTIFY_UPDATE("imageonmap.notifyupdate"),
    /** New in ImageOnMapReborn: pictures from the upload folder ({@code /tomap file:<name>}). */
    LOCAL_FILES("imageonmap.localfiles");

    /** {@code imageonmap.maplimit.<n>}: maximum number of Minecraft maps a player may use. */
    public static final String MAP_LIMIT_PREFIX = "imageonmap.maplimit.";
    /** {@code imageonmap.imagelimit.<n>}: maximum number of images a player may own. */
    public static final String IMAGE_LIMIT_PREFIX = "imageonmap.imagelimit.";

    private final String node;
    private final String[] aliases;

    Perm(String node, String... aliases) {
        this.node = node;
        this.aliases = aliases;
    }

    public String node() {
        return node;
    }

    public boolean has(Permissible permissible) {
        if (permissible.hasPermission(node)) {
            return true;
        }
        for (String alias : aliases) {
            if (permissible.hasPermission(alias)) {
                return true;
            }
        }
        return false;
    }

    /** Highest {@code n} among the granted {@code <prefix><n>} nodes, or empty if none is granted. */
    public static OptionalInt numericLimit(Permissible permissible, String prefix) {
        int best = -1;
        for (PermissionAttachmentInfo info : permissible.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }
            try {
                int value = Integer.parseInt(permission.substring(prefix.length()).trim());
                if (value > best) {
                    best = value;
                }
            } catch (NumberFormatException ignored) {
                // e.g. imageonmap.maplimit.* is not a limit
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }
}
