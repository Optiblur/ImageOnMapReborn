package pl.iomreborn.map;

import pl.iomreborn.config.PluginConfig;
import pl.iomreborn.image.RenderException;

import java.util.OptionalInt;

/**
 * The limits that apply when a player creates images: the map limit of the whole server, the player's map limits
 * ({@code map-player-limit} and {@code imageonmap.maplimit.<n>}, both of them count, neither with
 * {@code imageonmap.bypassmaplimit}) and the image limit ({@code imageonmap.imagelimit.<n>}, not with
 * {@code imageonmap.bypassimagelimit}). The same rules decide whether an image can be created and what the
 * {@code /maps} overview shows.
 *
 * @param serverMaps      {@code map-global-limit}, 0 = no limit
 * @param playerMaps      {@code map-player-limit} for this player, 0 = no limit
 * @param permissionMaps  {@code imageonmap.maplimit.<n>} of this player
 * @param permissionImages {@code imageonmap.imagelimit.<n>} of this player
 */
public record Quota(int serverMaps, int playerMaps, OptionalInt permissionMaps, OptionalInt permissionImages) {

    public static Quota of(PluginConfig config, LimitContext limits) {
        boolean bypassMaps = limits.bypassMapLimit();
        return new Quota(
                config.mapGlobalLimit(),
                bypassMaps ? 0 : config.mapPlayerLimit(),
                bypassMaps ? OptionalInt.empty() : limits.mapLimit(),
                limits.bypassImageLimit() ? OptionalInt.empty() : limits.imageLimit());
    }

    /** True when any limit applies to the player. */
    public boolean hasLimits() {
        return serverMaps > 0 || playerMapLimit().isPresent() || permissionImages.isPresent();
    }

    /** The lowest of the player's own map limits, or empty when there is none. */
    public OptionalInt playerMapLimit() {
        if (playerMaps > 0 && permissionMaps.isPresent()) {
            return OptionalInt.of(Math.min(playerMaps, permissionMaps.getAsInt()));
        }
        return playerMaps > 0 ? OptionalInt.of(playerMaps) : permissionMaps;
    }

    /**
     * Maps the player can still create (never below 0), or empty when no map limit applies.
     *
     * @param playerMapsUsed maps of all the player's images
     * @param serverMapsUsed maps of all images on the server
     */
    public OptionalInt mapsLeft(int playerMapsUsed, int serverMapsUsed) {
        long left = Long.MAX_VALUE;
        if (serverMaps > 0) {
            left = serverMaps - (long) serverMapsUsed;
        }
        OptionalInt own = playerMapLimit();
        if (own.isPresent()) {
            left = Math.min(left, own.getAsInt() - (long) playerMapsUsed);
        }
        return left == Long.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of((int) Math.max(0, left));
    }

    /**
     * Checks that the player can create one more image made of {@code newMaps} maps.
     *
     * @throws RenderException with the message of the first limit that would be exceeded
     */
    public void check(int newMaps, int playerMapsUsed, int playerImages, int serverMapsUsed) throws RenderException {
        if (serverMaps > 0 && (long) serverMapsUsed + newMaps > serverMaps) {
            throw new RenderException("error.limit-server-maps");
        }
        if (permissionImages.isPresent() && playerImages + 1L > permissionImages.getAsInt()) {
            throw new RenderException("error.limit-permission-images", permissionImages.getAsInt(), playerImages);
        }
        if (playerMaps > 0 && (long) playerMapsUsed + newMaps > playerMaps) {
            throw new RenderException("error.limit-player-maps", playerMaps);
        }
        if (permissionMaps.isPresent() && (long) playerMapsUsed + newMaps > permissionMaps.getAsInt()) {
            throw new RenderException("error.limit-permission-maps", permissionMaps.getAsInt(), playerMapsUsed);
        }
    }
}
