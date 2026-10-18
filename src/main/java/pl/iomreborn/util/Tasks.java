package pl.iomreborn.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/** Small helpers to hop back to the main server thread from worker threads. */
public final class Tasks {

    private static volatile Plugin plugin;

    private Tasks() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    /** Runs the task on the main thread (right away if already on it). Dropped once the plugin is disabled. */
    public static void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        nextTick(task);
    }

    /** Runs the task on the main thread during the next tick. Dropped once the plugin is disabled. */
    public static void nextTick(Runnable task) {
        Plugin owner = plugin;
        if (owner == null || !owner.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(owner, task);
        } catch (IllegalPluginAccessException ignored) {
            // plugin is being disabled
        }
    }
}
