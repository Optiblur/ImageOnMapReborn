package pl.iomreborn.placement;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.bukkit.block.BlockFace.DOWN;
import static org.bukkit.block.BlockFace.EAST;
import static org.bukkit.block.BlockFace.NORTH;
import static org.bukkit.block.BlockFace.SOUTH;
import static org.bukkit.block.BlockFace.UP;
import static org.bukkit.block.BlockFace.WEST;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrientationTest {

    private static void check(Orientation actual, BlockFace right, BlockFace up, int rotation) {
        assertEquals(new Orientation(right, up, rotation), actual);
    }

    @Test
    void wallsGrowToTheViewersRight() {
        // A frame facing south is seen by a player looking north: right is east.
        check(Orientation.forPlacement(SOUTH, NORTH), EAST, UP, 0);
        check(Orientation.forPlacement(NORTH, SOUTH), WEST, UP, 0);
        check(Orientation.forPlacement(EAST, WEST), NORTH, UP, 0);
        check(Orientation.forPlacement(WEST, EAST), SOUTH, UP, 0);
        // The player's facing does not matter on walls.
        check(Orientation.forPlacement(SOUTH, EAST), EAST, UP, 0);
    }

    @Test
    void floorsFaceThePlayer() {
        // Rotation 0 shows the top of a map towards north; each step turns it clockwise.
        check(Orientation.forPlacement(UP, NORTH), EAST, NORTH, 0);
        check(Orientation.forPlacement(UP, EAST), SOUTH, EAST, 1);
        check(Orientation.forPlacement(UP, SOUTH), WEST, SOUTH, 2);
        check(Orientation.forPlacement(UP, WEST), NORTH, WEST, 3);
    }

    @Test
    void ceilingsFaceThePlayerLookingUp() {
        // Looking up while facing north, the top of the screen is south and the right is east.
        check(Orientation.forPlacement(DOWN, NORTH), EAST, SOUTH, 0);
        check(Orientation.forPlacement(DOWN, WEST), NORTH, EAST, 1);
        check(Orientation.forPlacement(DOWN, SOUTH), WEST, NORTH, 2);
        check(Orientation.forPlacement(DOWN, EAST), SOUTH, WEST, 3);
    }

    @Test
    void placedRotationGivesBackTheLayout() {
        for (BlockFace surface : new BlockFace[] {UP, DOWN, NORTH, EAST, SOUTH, WEST}) {
            for (BlockFace player : new BlockFace[] {NORTH, EAST, SOUTH, WEST}) {
                Orientation placed = Orientation.forPlacement(surface, player);
                assertEquals(placed, Orientation.forFrame(surface, placed.rotation()), surface + "/" + player);
            }
        }
    }

    @Test
    void rotatesHorizontalFaces() {
        assertEquals(EAST, Orientation.clockwise(NORTH));
        assertEquals(WEST, Orientation.counterClockwise(NORTH));
        assertEquals(NORTH, Orientation.horizontal(UP));
    }
}
