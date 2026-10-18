package pl.iomreborn.image;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDownloaderTest {

    @Test
    void detectsPrivateAddresses() throws Exception {
        for (String address : new String[] {"127.0.0.1", "10.1.2.3", "192.168.1.10", "172.16.0.5", "169.254.1.1",
                "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1"}) {
            assertTrue(ImageDownloader.isPrivateAddress(InetAddress.getByName(address)), address);
        }
        for (String address : new String[] {"8.8.8.8", "1.1.1.1", "151.101.1.69", "2606:4700::6810:84e5"}) {
            assertFalse(ImageDownloader.isPrivateAddress(InetAddress.getByName(address)), address);
        }
    }

    @Test
    void checksTheWhitelist() {
        assertTrue(ImageDownloader.isWhitelisted("anything.com", List.of()));
        assertTrue(ImageDownloader.isWhitelisted("i.imgur.com", List.of("i.imgur.com")));
        assertFalse(ImageDownloader.isWhitelisted("i.imgur.com", List.of("imgur.com")));
        assertTrue(ImageDownloader.isWhitelisted("i.imgur.com", List.of("*.imgur.com")));
        assertTrue(ImageDownloader.isWhitelisted("imgur.com", List.of("*.imgur.com")));
        assertTrue(ImageDownloader.isWhitelisted("cdn.discordapp.com", List.of("https://cdn.discordapp.com/")));
        assertTrue(ImageDownloader.isWhitelisted("CDN.discordapp.com", List.of("cdn.discordapp.com")));
        assertFalse(ImageDownloader.isWhitelisted("evil.com", List.of("cdn.discordapp.com")));
    }

    @Test
    void rewritesImgurPages() throws Exception {
        assertEquals("https://i.imgur.com/AbC123x.png",
                ImageDownloader.rewriteImgur(new URL("https://imgur.com/AbC123x")).toString());
        assertEquals("https://i.imgur.com/AbC123x.jpg",
                ImageDownloader.rewriteImgur(new URL("https://i.imgur.com/AbC123x.jpg")).toString());
        RenderException ex = assertThrows(RenderException.class,
                () -> ImageDownloader.rewriteImgur(new URL("https://imgur.com/gallery/xyz")));
        assertEquals("error.imgur-gallery", ex.getKey());
    }

    @Test
    void acceptsOnlyWebUrls() throws Exception {
        assertEquals("example.com", ImageDownloader.parseUrl(" https://example.com/a.png ").getHost());
        assertEquals("error.unsupported-protocol",
                assertThrows(RenderException.class, () -> ImageDownloader.parseUrl("file:///etc/passwd")).getKey());
        assertEquals("error.invalid-url",
                assertThrows(RenderException.class, () -> ImageDownloader.parseUrl("not a url")).getKey());
    }

    @Test
    void blocksLocalhostDownloads() {
        ImageDownloader.Request request = new ImageDownloader.Request("http://127.0.0.1:1/image.png", 1000,
                1024, true, "test", List.of(), false);
        assertEquals("error.private-address",
                assertThrows(RenderException.class, () -> ImageDownloader.download(request)).getKey());
    }
}
