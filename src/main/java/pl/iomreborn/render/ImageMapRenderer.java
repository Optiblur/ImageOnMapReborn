package pl.iomreborn.render;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import pl.iomreborn.image.Animation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Draws a stored image on a map. The renderer is not contextual, so the canvas is shared by all players
 * and still pictures only have to be drawn once (and again after /maptool update).
 * <p>
 * Animated maps are played by {@link AnimationPlayer}, which draws the frames on the same canvas over time.
 */
public final class ImageMapRenderer extends MapRenderer {

    private final RendererManager manager;
    private final int mapId;
    /** Content waiting to be drawn: map colors (byte[]) or an {@link Animation}. */
    private Object pending;
    private int version;
    private boolean loadRequested;
    private volatile boolean done;

    // Main thread only.
    MapView view;
    MapCanvas canvas;
    Animation animation;
    /** Frame currently on the canvas (animations only). */
    byte[] shown;
    final Map<UUID, AnimationPlayer.Viewer> viewers = new HashMap<>();
    int lastServerRender;
    int lastDraw = Integer.MIN_VALUE / 2;
    /** Tick of a drawn frame some viewers may still need, or -1. */
    int pushTick = -1;

    ImageMapRenderer(RendererManager manager, int mapId, Object content) {
        super(false);
        this.manager = manager;
        this.mapId = mapId;
        if (content != null) {
            offer(content);
        }
    }

    RendererManager getManager() {
        return manager;
    }

    int getMapId() {
        return mapId;
    }

    /** New content from a render or an update; it always wins over an image file still being loaded. */
    synchronized void offer(Object content) {
        version++;
        loadRequested = true;
        pending = content;
    }

    synchronized int version() {
        return version;
    }

    /** Content read from the image files; dropped if newer content was offered while the files were read. */
    synchronized void offerLoaded(Object content, int loadVersion) {
        if (version == loadVersion) {
            pending = content;
        }
    }

    private synchronized Object takePending() {
        Object content = pending;
        pending = null;
        return content;
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

    /** Forgets the frames of an animation nobody looked at for a while; they are read again when needed. */
    synchronized void unload() {
        animation = null;
        shown = null;
        viewers.clear();
        pushTick = -1;
        if (pending == null) {
            loadRequested = false;
            done = false;
        }
    }

    /**
     * Draws waiting content right away (main thread) instead of waiting for the next render, which the server
     * may delay for a few seconds for maps in item frames. Needs a canvas, so the map must have been rendered once.
     */
    void applyPendingNow() {
        if (canvas == null) {
            return;
        }
        Object content = takePending();
        if (content != null) {
            apply(content);
        }
    }

    @SuppressWarnings("deprecation")
    private void apply(Object content) {
        if (content instanceof Animation next) {
            animation = next;
            shown = null;
            done = true;
            manager.animations().started(this);
            return;
        }
        if (content instanceof byte[] pixels) {
            if (animation != null) {
                animation = null;
                manager.animations().stopped(this);
            }
            shown = null;
            int length = Math.min(pixels.length, 128 * 128);
            for (int i = 0; i < length; i++) {
                canvas.setPixel(i & 127, i >> 7, pixels[i]);
            }
            done = true;
        }
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        this.view = view;
        this.canvas = canvas;
        Object content = takePending();
        if (content != null) {
            apply(content);
        }
        if (animation != null) {
            manager.animations().rendered(this, player);
            return;
        }
        if (!done && claimLoad()) {
            manager.load(this);
        }
    }
}
