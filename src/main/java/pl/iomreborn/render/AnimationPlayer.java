package pl.iomreborn.render;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.scheduler.BukkitTask;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.image.MapColors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Plays animated maps.
 * <p>
 * Every tick the current frame of each watched animation is drawn on its canvas. The server then sends the change
 * by itself to players carrying the map (every tick) and to players near an item frame holding it, but only
 * every 10 ticks for frames ({@code item-frame-cursor-update-interval} on Paper), which would make animations
 * on walls jerky. So players who see the map in a frame and were not updated by the server right after a new
 * frame get the whole map sent with {@link Player#sendMap}.
 * <p>
 * Who sees a map is learned from the server itself: it renders the map for every player it sends it to.
 * Animations nobody looked at for a while stop being drawn, and their frames are dropped from memory later.
 */
public final class AnimationPlayer {

    /** An animation keeps being drawn this long after the server last rendered it for someone. */
    static final int ACTIVE_TICKS = 60;
    /** Frames of an animation unseen for this long are dropped from memory (read again when needed). */
    static final int UNLOAD_TICKS = 20 * 60 * 5;
    private static final int MIN_WINDOW = 25;
    private static final int MAX_WINDOW = 200;

    /** When the server last rendered a map for a player, and how far apart its renders were. */
    static final class Viewer {
        int lastSeen;
        int gap;

        Viewer(int tick) {
            this.lastSeen = tick;
        }

        /** How long a player still counts as watching without a new render. */
        int window() {
            return gap <= 0 ? MIN_WINDOW : Math.max(MIN_WINDOW, Math.min(MAX_WINDOW, gap * 2 + 5));
        }
    }

    private final ImageOnMapReborn plugin;
    private final Set<ImageMapRenderer> playing = new LinkedHashSet<>();
    private BukkitTask task;
    private int tick;
    private boolean pushing;
    private boolean pushFailed;

    AnimationPlayer(ImageOnMapReborn plugin) {
        this.plugin = plugin;
    }

    void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ImageMapRenderer renderer : new ArrayList<>(playing)) {
            renderer.viewers.clear();
        }
        playing.clear();
    }

    /** Number of animations being played right now (frames in memory). */
    public int playingCount() {
        return playing.size();
    }

    /** A renderer got new frames: it starts playing at once. */
    void started(ImageMapRenderer renderer) {
        playing.add(renderer);
        renderer.lastServerRender = tick;
        renderer.pushTick = -1;
        if (draw(renderer, System.currentTimeMillis())) {
            renderer.lastDraw = tick;
        }
    }

    /** A renderer switched to a still picture or was removed. */
    void stopped(ImageMapRenderer renderer) {
        playing.remove(renderer);
        renderer.viewers.clear();
        renderer.pushTick = -1;
    }

    /** Called when the server renders a playing animation for a player (or while we push frames ourselves). */
    void rendered(ImageMapRenderer renderer, Player player) {
        if (pushing) {
            return;
        }
        boolean wasActive = renderer.shown != null && tick - renderer.lastServerRender <= ACTIVE_TICKS;
        renderer.lastServerRender = tick;
        if (player != null) {
            Viewer viewer = renderer.viewers.get(player.getUniqueId());
            if (viewer == null) {
                renderer.viewers.put(player.getUniqueId(), new Viewer(tick));
            } else if (tick > viewer.lastSeen) {
                viewer.gap = tick - viewer.lastSeen;
                viewer.lastSeen = tick;
            }
        }
        if (!wasActive && draw(renderer, System.currentTimeMillis())) {
            // Someone looks at it again: show the current frame now instead of an old one.
            renderer.lastDraw = tick;
        }
    }

    private int minInterval() {
        int fps = plugin.settings().animationMaxFps();
        return Math.max(1, Math.round(20f / Math.max(1, fps)));
    }

    private void tick() {
        tick++;
        if (playing.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int interval = minInterval();
        // A copy: sending a map renders it, and new content applied by that render may start or stop animations.
        for (ImageMapRenderer renderer : new ArrayList<>(playing)) {
            if (renderer.animation == null || renderer.canvas == null) {
                playing.remove(renderer);
                continue;
            }
            int idle = tick - renderer.lastServerRender;
            if (idle > UNLOAD_TICKS) {
                playing.remove(renderer);
                renderer.unload();
                continue;
            }
            if (renderer.pushTick >= 0 && renderer.pushTick < tick) {
                push(renderer);
            }
            if (idle > ACTIVE_TICKS || tick - renderer.lastDraw < interval) {
                continue;
            }
            if (draw(renderer, now)) {
                renderer.lastDraw = tick;
                renderer.pushTick = tick;
            }
        }
    }

    /** Draws the frame of the moment if it differs from the one shown. Only changed pixels are touched. */
    @SuppressWarnings("deprecation")
    private static boolean draw(ImageMapRenderer renderer, long now) {
        MapCanvas canvas = renderer.canvas;
        if (renderer.animation == null || canvas == null) {
            return false;
        }
        byte[] frame = renderer.animation.frameAt(now);
        byte[] old = renderer.shown;
        if (frame == old) {
            return false;
        }
        for (int i = 0; i < MapColors.PIXELS; i++) {
            byte color = frame[i];
            if (old == null || old[i] != color) {
                canvas.setPixel(i & 127, i >> 7, color);
            }
        }
        renderer.shown = frame;
        return true;
    }

    /**
     * Sends the whole map to watching players the server did not update since the frame was drawn
     * (players near item frames; players carrying the map were updated by the server in the same tick).
     */
    private void push(ImageMapRenderer renderer) {
        int drawnAt = renderer.pushTick;
        renderer.pushTick = -1;
        if (renderer.view == null || renderer.viewers.isEmpty()) {
            return;
        }
        List<Player> targets = null;
        Iterator<Map.Entry<UUID, Viewer>> iterator = renderer.viewers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Viewer> entry = iterator.next();
            Viewer viewer = entry.getValue();
            if (tick - viewer.lastSeen > viewer.window()) {
                iterator.remove();
                continue;
            }
            if (viewer.lastSeen >= drawnAt) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (targets == null) {
                targets = new ArrayList<>(2);
            }
            targets.add(player);
        }
        if (targets == null) {
            return;
        }
        pushing = true;
        try {
            for (Player player : targets) {
                player.sendMap(renderer.view);
            }
        } catch (RuntimeException ex) {
            if (!pushFailed) {
                pushFailed = true;
                plugin.getLogger().log(Level.WARNING, "Cannot send animation frames to players", ex);
            }
        } finally {
            pushing = false;
        }
    }
}
