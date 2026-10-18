package pl.iomreborn.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Map items that did not fit in a player's inventory, retrievable with /maptool getremaining. */
public final class PendingItems {

    private final Map<UUID, Deque<ItemStack>> pending = new HashMap<>();

    public void add(UUID player, ItemStack item) {
        pending.computeIfAbsent(player, id -> new ArrayDeque<>()).add(item);
    }

    public int size(UUID player) {
        Deque<ItemStack> queue = pending.get(player);
        return queue == null ? 0 : queue.size();
    }

    /** Moves as many waiting items as possible into the inventory; returns how many were given. */
    public int giveAll(Player player) {
        Deque<ItemStack> queue = pending.get(player.getUniqueId());
        if (queue == null) {
            return 0;
        }
        int given = 0;
        while (!queue.isEmpty()) {
            ItemStack next = queue.peek();
            Map<Integer, ItemStack> left = player.getInventory().addItem(next.clone());
            if (!left.isEmpty()) {
                ItemStack rest = left.values().iterator().next();
                if (rest.getAmount() == next.getAmount()) {
                    break;
                }
                next.setAmount(rest.getAmount());
                given++;
                break;
            }
            queue.poll();
            given++;
        }
        if (queue.isEmpty()) {
            pending.remove(player.getUniqueId());
        }
        return given;
    }
}
