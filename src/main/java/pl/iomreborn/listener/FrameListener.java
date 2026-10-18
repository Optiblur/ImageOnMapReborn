package pl.iomreborn.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.item.MapItems;
import pl.iomreborn.map.ImageMap;
import pl.iomreborn.map.PosterMap;
import pl.iomreborn.placement.PosterPlacement;

import java.util.List;

/** Item frame handling: placing (splatter) maps, removing whole posters, and protecting images. */
public final class FrameListener implements Listener {

    private final ImageOnMapReborn plugin;

    public FrameListener(ImageOnMapReborn plugin) {
        this.plugin = plugin;
    }

    private ImageMap imageIn(ItemFrame frame) {
        return plugin.mapItems().getImage(frame.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack held = player.getInventory().getItem(hand);
        MapItems items = plugin.mapItems();
        ImageMap image = items.getImage(held);
        if (image == null || !PosterPlacement.isEmpty(frame)) {
            return;
        }

        if (image instanceof PosterMap poster && items.isSplatter(held)) {
            event.setCancelled(true);
            if (!Perm.PLACE_SPLATTER.has(player)) {
                plugin.messages().send(player, "splatter.no-permission");
                return;
            }
            boolean invisible = Perm.PLACE_INVISIBLE_SPLATTER.has(player);
            if (!plugin.posterPlacement().place(player, frame, poster, invisible)) {
                plugin.messages().actionBar(player, "splatter.not-enough-space", poster.getColumns(), poster.getRows());
                return;
            }
            consumeOne(player, hand, held);
            return;
        }

        // A single map or one part of a poster: place it ourselves, without its name, facing the player.
        event.setCancelled(true);
        frame.setItem(items.frameCopy(held), false);
        BlockFace facing = frame.getFacing();
        frame.setRotation(facing == BlockFace.UP || facing == BlockFace.DOWN
                ? PosterPlacement.rotationFor(frame, player)
                : org.bukkit.Rotation.NONE);
        frame.getWorld().playSound(frame.getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1f, 1f);
        plugin.rendererManager().ensure(MapItems.getMapId(held));
        consumeOne(player, hand, held);
    }

    private static void consumeOne(Player player, EquipmentSlot hand, ItemStack held) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, new ItemStack(Material.AIR));
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        ImageMap image = imageIn(frame);
        if (image == null) {
            return;
        }
        Player player = event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player damager ? damager : null;
        if (player == null) {
            // Images are protected from mobs, projectiles, explosions...
            if (plugin.settings().protectItemFrames()) {
                event.setCancelled(true);
            }
            return;
        }

        if (player.isSneaking() && image instanceof PosterMap poster && poster.hasColumnData()
                && Perm.REMOVE_SPLATTER.has(player)) {
            event.setCancelled(true);
            List<ItemFrame> frames = plugin.posterPlacement().collectPosterFrames(frame, poster);
            for (ItemFrame part : frames) {
                part.setItem(null, false);
                part.setVisible(true);
            }
            frame.getWorld().playSound(frame.getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM,
                    SoundCategory.BLOCKS, 1f, 1f);
            if (player.getGameMode() != GameMode.CREATIVE || !plugin.mapItems().hasSplatter(player, poster)) {
                if (plugin.mapItems().give(player, poster)) {
                    plugin.messages().send(player, "general.inventory-overflow");
                }
            }
            return;
        }

        // One map taken out: the frame becomes visible again and the dropped item gets its name back.
        frame.setVisible(true);
        frame.setItem(plugin.mapItems().withTitle(frame.getItem(), image, player), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.settings().protectItemFrames() || !(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        if (imageIn(frame) == null) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEntity) {
            Entity remover = byEntity.getRemover();
            if (!(remover instanceof Player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            event.setCancelled(true);
        }
    }

    /** Maps marked with an enchantment (splatter maps) must not be disenchanted for free experience. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inventory = event.getInventory();
        for (int slot = 0; slot < 2; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (plugin.mapItems().getImage(item) == null) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                event.setResult(null);
                return;
            }
        }
    }
}
