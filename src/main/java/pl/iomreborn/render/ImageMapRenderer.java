package pl.iomreborn.render;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Draws a stored image on a map. The renderer is not contextual, so the canvas is shared by all players
 * and the pixels only have to be drawn once (and again after /maptool update).
 */
public final class ImageMapRenderer extends MapRenderer {

    private final RendererManager manager;
    private final int mapId;
    private byte[] pending;
    private int version;
    private boolean loadRequested;
    private volatile boolean done;

    ImageMapRenderer(RendererManager manager, int mapId, byte[] pixels) {
        super(false);
        this.manager = manager;
        this.mapId = mapId;
        if (pixels != null) {
            offer(pixels);
        }
    }

    RendererManager getManager() {
        return manager;
    }

    int getMapId() {
        return mapId;
    }

    /** New pixels from a render or an update; they always win over an image file still being loaded. */
    synchronized void offer(byte[] pixels) {
        version++;
        loadRequested = true;
        pending = pixels;
    }

    synchronized int version() {
        return version;
    }

    /** Pixels read from the image file; dropped if newer pixels were offered while the file was being read. */
    synchronized void offerLoaded(byte[] pixels, int loadVersion) {
        if (version == loadVersion) {
            pending = pixels;
        }
    }

    private synchronized byte[] takePending() {
        byte[] pixels = pending;
        pending = null;
        return pixels;
    }

    /** Called when the image file could not be loaded, so it is not retried on every render. */
    void markFailed() {
        done = true;
    }

    /** Returns true the first time it is called, so the image file is only loaded once. */
    synchronized boolean claimLoad() {
        if (loadRequested) {
            return false;
        }
        loadRequested = true;
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void render(MapView view, MapCanvas canvas, Player player) {
        byte[] pixels = takePending();
        if (pixels != null) {
            int length = Math.min(pixels.length, 128 * 128);
            for (int i = 0; i < length; i++) {
                canvas.setPixel(i & 127, i >> 7, pixels[i]);
            }
            done = true;
            return;
        }
        if (!done && claimLoad()) {
            manager.load(this);
        }
    }
}
