package pl.iomreborn.placement;

import org.bukkit.block.BlockFace;

/**
 * How a poster is laid out on a surface: the world direction of the image's right and top edges, and the
 * item frame rotation (in 90 degree steps) that makes each map upright.
 * <p>
 * Item frames on a floor show a map with its top towards north at rotation 0, every step turning it clockwise
 * (seen from above). On a ceiling the top points south at rotation 0. On walls rotation 0 is always upright.
 */
public record Orientation(BlockFace right, BlockFace up, int rotation) {

    private static final BlockFace[] FLOOR_TOP = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final BlockFace[] CEILING_TOP = {BlockFace.SOUTH, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST};

    /**
     * Layout for a poster placed from the frame the player clicked, so that it reads correctly for that player.
     *
     * @param frameFacing  direction the clicked item frame faces
     * @param playerFacing horizontal direction the player looks at
     */
    public static Orientation forPlacement(BlockFace frameFacing, BlockFace playerFacing) {
        BlockFace looking = horizontal(playerFacing);
        switch (frameFacing) {
            case UP: {
                // Looking down at the floor: the far side is the top of the image.
                return new Orientation(clockwise(looking), looking, indexOf(FLOOR_TOP, looking));
            }
            case DOWN: {
                // Looking up at the ceiling: the side behind the player is the top of the image.
                BlockFace top = looking.getOppositeFace();
                return new Orientation(clockwise(looking), top, indexOf(CEILING_TOP, top));
            }
            default:
                return forWall(frameFacing);
        }
    }

    /** Layout deduced from a frame that already shows a map (used to find the other parts of a poster). */
    public static Orientation forFrame(BlockFace frameFacing, int rotation) {
        int steps = Math.floorMod(rotation, 4);
        switch (frameFacing) {
            case UP: {
                BlockFace top = FLOOR_TOP[steps];
                return new Orientation(clockwise(top), top, steps);
            }
            case DOWN: {
                BlockFace top = CEILING_TOP[steps];
                return new Orientation(counterClockwise(top), top, steps);
            }
            default:
                return forWall(frameFacing);
        }
    }

    private static Orientation forWall(BlockFace frameFacing) {
        // The viewer faces the wall, so the image's right is the frame direction turned counter-clockwise.
        return new Orientation(counterClockwise(frameFacing), BlockFace.UP, 0);
    }

    static BlockFace horizontal(BlockFace face) {
        switch (face) {
            case NORTH:
            case EAST:
            case SOUTH:
            case WEST:
                return face;
            default:
                return BlockFace.NORTH;
        }
    }

    static BlockFace clockwise(BlockFace face) {
        switch (face) {
            case NORTH:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.WEST;
            case WEST:
                return BlockFace.NORTH;
            default:
                return face;
        }
    }

    static BlockFace counterClockwise(BlockFace face) {
        switch (face) {
            case NORTH:
                return BlockFace.WEST;
            case WEST:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.NORTH;
            default:
                return face;
        }
    }

    private static int indexOf(BlockFace[] faces, BlockFace face) {
        for (int i = 0; i < faces.length; i++) {
            if (faces[i] == face) {
                return i;
            }
        }
        return 0;
    }
}
