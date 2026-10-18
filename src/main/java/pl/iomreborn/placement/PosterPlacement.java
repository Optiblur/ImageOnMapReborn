package pl.iomreborn.placement;

import org.bukkit.Rotation;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.item.MapItems;
import pl.iomreborn.map.PosterMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Places a whole poster from a splatter map, and finds every frame of a placed poster to remove it. */
public final class PosterPlacement {

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final ImageOnMapReborn plugin;

    public PosterPlacement(ImageOnMapReborn plugin) {
        this.plugin = plugin;
    }

    /**
     * Fills empty item frames with the poster, the clicked frame being the bottom-left corner as seen by the player.
     *
     * @return false (and changes nothing) when some frame is missing or not empty
     */
    public boolean place(Player player, ItemFrame clicked, PosterMap poster, boolean invisible) {
        BlockFace facing = clicked.getFacing();
        Orientation orientation = Orientation.forPlacement(facing, player.getFacing());
        int columns = poster.getColumns();
        int rows = poster.getRows();
        Block origin = clicked.getLocation().getBlock();

        ItemFrame[] frames = new ItemFrame[columns * rows];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                ItemFrame frame;
                if (column == 0 && row == rows - 1) {
                    frame = clicked;
                } else {
                    Block block = origin.getRelative(orientation.right(), column)
                            .getRelative(orientation.up(), rows - 1 - row);
                    frame = findFrame(block, facing, PosterPlacement::isEmpty);
                }
                if (frame == null || !isEmpty(frame)) {
                    return false;
                }
                frames[row * columns + column] = frame;
            }
        }

        Rotation rotation = Rotation.values()[orientation.rotation()];
        MapItems items = plugin.mapItems();
        for (int i = 0; i < frames.length; i++) {
            int mapId = poster.getMapIdAt(i);
            frames[i].setItem(items.frameItem(mapId), false);
            frames[i].setRotation(rotation);
            if (invisible) {
                frames[i].setVisible(false);
            }
            plugin.rendererManager().ensure(mapId);
        }
        clicked.getWorld().playSound(clicked.getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM,
                SoundCategory.BLOCKS, 1f, 1f);
        return true;
    }

    /** Rotation (in 90 degree steps) that shows a single map upright for the player placing it. */
    public static Rotation rotationFor(ItemFrame frame, Player player) {
        return Rotation.values()[Orientation.forPlacement(frame.getFacing(), player.getFacing()).rotation()];
    }

    /**
     * Collects the frames showing parts of the poster, starting from one of them and following neighbouring
     * frames on the same surface. Works whatever the orientation or the plugin version that placed it.
     */
    public List<ItemFrame> collectPosterFrames(ItemFrame start, PosterMap poster) {
        BlockFace facing = start.getFacing();
        BlockFace[] directions = facing == BlockFace.UP || facing == BlockFace.DOWN
                ? HORIZONTAL
                : new BlockFace[] {BlockFace.UP, BlockFace.DOWN,
                Orientation.clockwise(facing), Orientation.counterClockwise(facing)};

        Set<Integer> remaining = new HashSet<>();
        for (int id : poster.getMapIds()) {
            remaining.add(id);
        }

        List<ItemFrame> found = new ArrayList<>();
        Deque<ItemFrame> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        remaining.remove(MapItems.getMapId(start.getItem()));
        found.add(start);
        queue.add(start);
        visited.add(start.getLocation().getBlock());

        while (!queue.isEmpty() && !remaining.isEmpty()) {
            Block block = queue.poll().getLocation().getBlock();
            for (BlockFace direction : directions) {
                Block next = block.getRelative(direction);
                if (!visited.add(next)) {
                    continue;
                }
                ItemFrame frame = findFrame(next, facing,
                        candidate -> remaining.contains(MapItems.getMapId(candidate.getItem())));
                if (frame != null) {
                    remaining.remove(MapItems.getMapId(frame.getItem()));
                    found.add(frame);
                    queue.add(frame);
                }
            }
        }
        return found;
    }

    /** The item frame hanging in the given block space with the given facing, if it matches the filter. */
    public static ItemFrame findFrame(Block block, BlockFace facing, Predicate<ItemFrame> filter) {
        for (Entity entity : block.getWorld().getNearbyEntities(BoundingBox.of(block),
                entity -> entity instanceof ItemFrame)) {
            ItemFrame frame = (ItemFrame) entity;
            if (frame.getFacing() != facing || !sameBlock(frame, block) || !filter.test(frame)) {
                continue;
            }
            return frame;
        }
        return null;
    }

    private static boolean sameBlock(ItemFrame frame, Block block) {
        return frame.getLocation().getBlockX() == block.getX()
                && frame.getLocation().getBlockY() == block.getY()
                && frame.getLocation().getBlockZ() == block.getZ();
    }

    public static boolean isEmpty(ItemFrame frame) {
        ItemStack item = frame.getItem();
        return !frame.isFixed() && (item == null || item.getType().isAir());
    }
}
