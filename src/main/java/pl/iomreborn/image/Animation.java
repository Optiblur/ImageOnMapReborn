package pl.iomreborn.image;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The frames of one animated map: 128x128 map colors per frame and how long each frame is shown.
 * <p>
 * Identical frames share the same array, so a map that does not change during the animation costs the memory of
 * one frame, and {@link #frameAt(long)} returning the same array means "nothing to redraw". All maps of an
 * animated poster have the same delays, so they always show the same moment when they use the same clock.
 */
public final class Animation {

    /** Shortest and longest frame duration accepted, in milliseconds. */
    public static final int MIN_DELAY = 10;
    public static final int MAX_DELAY = 60_000;

    private final byte[][] frames;
    private final int[] delays;
    private final int[] ends;
    private final int duration;

    public Animation(byte[][] frames, int[] delays) {
        if (frames.length == 0 || frames.length != delays.length) {
            throw new IllegalArgumentException("Need the same, non-zero number of frames and delays");
        }
        this.frames = frames.clone();
        this.delays = new int[delays.length];
        this.ends = new int[delays.length];
        long total = 0;
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] == null || frames[i].length != MapColors.PIXELS) {
                throw new IllegalArgumentException("Frame " + i + " is not a 128x128 map");
            }
            this.delays[i] = Math.max(MIN_DELAY, Math.min(MAX_DELAY, delays[i]));
            total += this.delays[i];
            this.ends[i] = (int) total;
        }
        this.duration = (int) total;
    }

    public int frameCount() {
        return frames.length;
    }

    public byte[] frame(int index) {
        return frames[index];
    }

    public int delay(int index) {
        return delays[index];
    }

    /** Length of one loop of the animation, in milliseconds. */
    public int duration() {
        return duration;
    }

    /** Index of the frame shown at a time (milliseconds of any clock); the animation loops forever. */
    public int indexAt(long timeMillis) {
        int t = (int) Math.floorMod(timeMillis, (long) duration);
        int index = Arrays.binarySearch(ends, t);
        // ends[i] is exclusive: at exactly ends[i] the next frame starts.
        index = index >= 0 ? index + 1 : -index - 1;
        return Math.min(index, frames.length - 1);
    }

    public byte[] frameAt(long timeMillis) {
        return frames[indexAt(timeMillis)];
    }

    /** Number of different frame arrays (identical frames are stored once). */
    public int uniqueFrameCount() {
        Map<byte[], Boolean> seen = new IdentityHashMap<>();
        for (byte[] frame : frames) {
            seen.put(frame, Boolean.TRUE);
        }
        return seen.size();
    }

    /** Approximate memory used by the frames, in bytes. */
    public long memoryBytes() {
        return (long) uniqueFrameCount() * MapColors.PIXELS;
    }
}
