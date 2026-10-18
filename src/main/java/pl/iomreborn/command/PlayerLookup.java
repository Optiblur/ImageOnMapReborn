package pl.iomreborn.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.util.Tasks;

import java.util.UUID;
import java.util.function.Consumer;

/** Finds players by name; offline players are looked up in the background (may contact Mojang). */
public final class PlayerLookup {

    private PlayerLookup() {
    }

    /** The callback runs on the main thread, with null when the player is unknown. */
    public static void find(ImageOnMapReborn plugin, String name, Consumer<OfflinePlayer> callback) {
        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().equalsIgnoreCase(name)) {
                    online = player;
                    break;
                }
            }
        }
        if (online != null) {
            callback.accept(online);
            return;
        }
        UUID uuid = parseUuid(name);
        if (uuid != null) {
            callback.accept(Bukkit.getOfflinePlayer(uuid));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer found;
            try {
                found = Bukkit.getOfflinePlayer(name);
            } catch (RuntimeException ex) {
                found = null;
            }
            OfflinePlayer result = found;
            Tasks.sync(() -> {
                if (result == null) {
                    callback.accept(null);
                    return;
                }
                boolean known = result.hasPlayedBefore() || result.isOnline()
                        || plugin.mapManager().getStore(result.getUniqueId()) != null;
                callback.accept(known ? result : null);
            });
        });
    }

    private static UUID parseUuid(String value) {
        if (value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Best display name for an offline player. */
    public static String nameOf(OfflinePlayer player, String fallback) {
        String name = player.getName();
        return name == null || name.isEmpty() ? fallback : name;
    }
}
