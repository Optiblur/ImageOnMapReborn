package pl.iomreborn.command.sub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void findsTheDitheringWordAnywhereAfterTheLink() {
        NewCommand.Options plain = NewCommand.extractDithering(new String[] {"url", "resize", "4", "3"});
        assertNull(plain.dither());
        assertArrayEquals(new String[] {"url", "resize", "4", "3"}, plain.args());

        NewCommand.Options end = NewCommand.extractDithering(new String[] {"url", "resize", "4x3", "nodither"});
        assertEquals(Boolean.FALSE, end.dither());
        assertArrayEquals(new String[] {"url", "resize", "4x3"}, end.args());

        NewCommand.Options middle = NewCommand.extractDithering(new String[] {"url", "--dither", "resize-covered"});
        assertEquals(Boolean.TRUE, middle.dither());
        assertArrayEquals(new String[] {"url", "resize-covered"}, middle.args());

        NewCommand.Options only = NewCommand.extractDithering(new String[] {"url", "dither"});
        assertEquals(Boolean.TRUE, only.dither());
        assertArrayEquals(new String[] {"url"}, only.args());

        // The link itself is never taken for a dithering word.
        NewCommand.Options link = NewCommand.extractDithering(new String[] {"dither"});
        assertNull(link.dither());
        assertArrayEquals(new String[] {"dither"}, link.args());
    }
}
