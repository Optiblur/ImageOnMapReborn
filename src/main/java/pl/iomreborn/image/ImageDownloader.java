package pl.iomreborn.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads images over HTTP(S) with size limits, manual redirect handling and an optional private-network guard. */
public final class ImageDownloader {

    private static final int MAX_REDIRECTS = 5;
    private static final Pattern IMGUR_PAGE = Pattern.compile("^/([A-Za-z0-9]{5,10})/?$");

    /** Everything a download needs, captured on the main thread. */
    public record Request(String url, int timeoutMillis, long maxBytes, boolean blockPrivateAddresses,
                          String userAgent, List<String> whitelist, boolean enforceWhitelist) {
    }

    private ImageDownloader() {
    }

    /** Parses and checks a URL the way commands do before starting a download. */
    public static URL parseUrl(String raw) throws RenderException {
        URL url;
        try {
            url = new URL(raw.trim());
        } catch (MalformedURLException ex) {
            throw new RenderException("error.invalid-url");
        }
        checkProtocol(url);
        if (url.getHost() == null || url.getHost().isEmpty()) {
            throw new RenderException("error.invalid-url");
        }
        return url;
    }

    private static void checkProtocol(URL url) throws RenderException {
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        if (!protocol.equals("http") && !protocol.equals("https")) {
            throw new RenderException("error.unsupported-protocol");
        }
    }

    public static boolean isWhitelisted(String host, List<String> whitelist) {
        if (whitelist.isEmpty()) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String entry : whitelist) {
            String allowed = entry.trim().toLowerCase(Locale.ROOT)
                    .replaceFirst("^https?://", "")
                    .replaceFirst("[/:].*$", "");
            if (allowed.isEmpty()) {
                continue;
            }
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(1);
                if (h.endsWith(suffix) || h.equals(suffix.substring(1))) {
                    return true;
                }
            } else if (h.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /** Turns imgur.com page links into direct image links, like ImageOnMap did. */
    static URL rewriteImgur(URL url) throws RenderException {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("imgur.com") && !host.equals("www.imgur.com") && !host.equals("m.imgur.com")) {
            return url;
        }
        String path = url.getPath();
        if (path.startsWith("/gallery/") || path.startsWith("/a/") || path.startsWith("/t/")) {
            throw new RenderException("error.imgur-gallery");
        }
        Matcher matcher = IMGUR_PAGE.matcher(path);
        if (!matcher.matches()) {
            return url;
        }
        try {
            return new URL("https://i.imgur.com/" + matcher.group(1) + ".png");
        } catch (MalformedURLException ex) {
            return url;
        }
    }

    public static byte[] download(Request request) throws RenderException {
        URL url = rewriteImgur(parseUrl(request.url()));
        if (request.enforceWhitelist() && !isWhitelisted(url.getHost(), request.whitelist())) {
            throw new RenderException("error.untrusted-host");
        }

        for (int redirects = 0; ; redirects++) {
            if (request.blockPrivateAddresses()) {
                checkPublicHost(url.getHost());
            }
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(request.timeoutMillis());
                connection.setReadTimeout(request.timeoutMillis());
                connection.setRequestProperty("User-Agent", request.userAgent());
                connection.setRequestProperty("Accept",
                        "image/webp,image/png,image/jpeg,image/gif,image/bmp,image/*;q=0.8,*/*;q=0.5");

                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new RenderException("error.http", code, nullToEmpty(connection.getResponseMessage()));
                    }
                    if (redirects >= MAX_REDIRECTS) {
                        throw new RenderException("error.too-many-redirects");
                    }
                    try {
                        url = new URL(url, location.trim());
                    } catch (MalformedURLException ex) {
                        throw new RenderException("error.invalid-url");
                    }
                    checkProtocol(url);
                    continue;
                }
                if (code / 100 != 2) {
                    throw new RenderException("error.http", code, nullToEmpty(connection.getResponseMessage()));
                }
                long length = connection.getContentLengthLong();
                if (length > request.maxBytes()) {
                    throw new RenderException("error.download-too-large", request.maxBytes() / (1024 * 1024));
                }
                try (InputStream in = connection.getInputStream()) {
                    return readLimited(in, request.maxBytes());
                }
            } catch (SocketTimeoutException ex) {
                throw new RenderException("error.timeout");
            } catch (UnknownHostException ex) {
                throw new RenderException("error.unknown-host", url.getHost());
            } catch (IOException ex) {
                throw new RenderException("error.download-failed", describe(ex));
            } catch (ClassCastException ex) {
                throw new RenderException("error.unsupported-protocol");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static byte[] readLimited(InputStream in, long maxBytes) throws IOException, RenderException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new RenderException("error.download-too-large", maxBytes / (1024 * 1024));
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void checkPublicHost(String host) throws RenderException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new RenderException("error.unknown-host", host);
        }
        for (InetAddress address : addresses) {
            if (isPrivateAddress(address)) {
                throw new RenderException("error.private-address");
            }
        }
    }

    /** Loopback, LAN, link-local, carrier-grade NAT, unique-local IPv6, multicast and reserved ranges. */
    public static boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0
                    || (first == 100 && (second & 0xC0) == 64)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 224;
        }
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private static String describe(IOException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
