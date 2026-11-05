package pl.iomreborn.image;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDownloaderTest {

    /** A web server on 127.0.0.1 that answers every connection with what the handler writes. */
    private static final class LocalServer implements AutoCloseable {

        @FunctionalInterface
        interface Handler {
            void answer(String path, OutputStream out) throws Exception;
        }

        private final ServerSocket socket;

        LocalServer(Handler handler) throws IOException {
            socket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket client = socket.accept()) {
                        handler.answer(requestPath(client.getInputStream()), client.getOutputStream());
                    } catch (Exception ignored) {
                        // the client gave up, or the server was closed
                    }
                }
            }, "test-web-server");
            thread.setDaemon(true);
            thread.start();
        }

        String url(String path) {
            return "http://127.0.0.1:" + socket.getLocalPort() + path;
        }

        /** Reads the request headers and returns the path from the first line. */
        private static String requestPath(InputStream in) throws IOException {
            StringBuilder head = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                head.append((char) c);
                if (head.length() >= 4 && head.lastIndexOf("\r\n\r\n") == head.length() - 4) {
                    break;
                }
            }
            String[] words = head.toString().split("\r\n", 2)[0].split(" ");
            return words.length > 1 ? words[1] : "/";
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static ImageDownloader.Request local(String url, int timeoutMillis, long maxBytes) {
        return new ImageDownloader.Request(url, timeoutMillis, maxBytes, false, "test", List.of(), false);
    }

    private static void send(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    @Test
    void downloadsAndFollowsRedirects() throws Exception {
        try (LocalServer server = new LocalServer((path, out) -> {
            if (path.equals("/start")) {
                send(out, "HTTP/1.1 302 Found\r\nLocation: /picture.png\r\nContent-Length: 0\r\n"
                        + "Connection: close\r\n\r\n");
            } else {
                send(out, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello");
            }
        })) {
            assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII),
                    ImageDownloader.download(local(server.url("/start"), 5000, 1024)));
        }
    }

    @Test
    void stopsDownloadsBiggerThanTheLimit() throws Exception {
        // Without a Content-Length header the size is checked while reading.
        try (LocalServer server = new LocalServer((path, out) -> {
            send(out, "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n");
            out.write(new byte[4096]);
            out.flush();
        })) {
            RenderException ex = assertThrows(RenderException.class,
                    () -> ImageDownloader.download(local(server.url("/big.png"), 5000, 1024)));
            assertEquals("error.download-too-large", ex.getKey());
        }
    }

    @Test
    void stopsAServerSendingThePictureByteByByte() throws Exception {
        // Every byte comes well within the 300 ms read timeout; only the limit of the whole download (900 ms) helps.
        try (LocalServer server = new LocalServer((path, out) -> {
            send(out, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n");
            for (int i = 0; i < 100; i++) {
                Thread.sleep(50);
                out.write('x');
                out.flush();
            }
        })) {
            assertStoppedInTime(server.url("/slow.png"));
        }
    }

    @Test
    void stopsAServerSendingTheHeadersByteByByte() throws Exception {
        try (LocalServer server = new LocalServer((path, out) -> {
            send(out, "HTTP/1.1 200 OK\r\nX-Slow: ");
            for (int i = 0; i < 100; i++) {
                Thread.sleep(50);
                out.write('a');
                out.flush();
            }
            send(out, "\r\nContent-Length: 0\r\n\r\n");
        })) {
            assertStoppedInTime(server.url("/slow-headers.png"));
        }
    }

    /** Without the limit of the whole download these servers would take 5 seconds, and the download would work. */
    private static void assertStoppedInTime(String url) {
        long start = System.nanoTime();
        RenderException ex = assertThrows(RenderException.class,
                () -> ImageDownloader.download(local(url, 300, 1024)));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals("error.timeout", ex.getKey());
        assertTrue(millis < 3000, "stopped after " + millis + " ms, the limit was 900 ms");
    }

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
