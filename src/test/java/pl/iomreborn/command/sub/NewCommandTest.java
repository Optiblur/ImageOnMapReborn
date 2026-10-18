package pl.iomreborn.command.sub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NewCommandTest {

    private static int[] size(String... args) {
        return NewCommand.parseSize(args);
    }

    @Test
    void parsesSizes() {
        assertArrayEquals(new int[] {4, 3}, size("url", "resize", "4", "3"));
        assertArrayEquals(new int[] {4, 3}, size("url", "resize", "4x3"));
        assertArrayEquals(new int[] {4, 3}, size("url", "resize", "4X3"));
        assertArrayEquals(new int[] {4, 3}, size("url", "resize", "4*3"));
        assertArrayEquals(new int[] {0, 0}, size("url", "resize", "0", "0"));
        assertNull(size("url", "resize", "0", "3"));
        assertNull(size("url", "resize", "-1", "3"));
        assertNull(size("url", "resize", "a", "b"));
        assertNull(size("url", "resize", "4"));
    }
}
