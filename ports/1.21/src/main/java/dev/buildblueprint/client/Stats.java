package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;

/**
 * Build timer: counts the time you spend building this blueprint (pauses when nothing has been placed for
 * 2 minutes) and how fast you place blocks. Shows a summary when the build is finished.
 */
public final class Stats {
    private Stats() {}

    private static final long IDLE_MS = 120_000;
    private static Blueprint bp;
    private static int lastPlaced = -1, startPlaced, placedByYou;
    private static long activeMs, lastTick, lastProgress;
    private static boolean announced;

    public static long activeMs() { return activeMs; }
    public static int placedByYou() { return placedByYou; }
    public static boolean started() { return bp != null && placedByYou > 0; }
    public static double perMinute() { return activeMs < 5000 ? 0 : placedByYou / (activeMs / 60000.0); }

    public static String time(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    /** Called once a second by Materials.refresh with the current numbers. */
    static void update(Blueprint now, int placed, int total) {
        long t = System.currentTimeMillis();
        if (now != bp) {                       // new build loaded (or rotated/mirrored: same build, keep going)
            boolean same = bp != null && now != null && bp.name != null && bp.name.equals(now.name) && bp.blockCount() == now.blockCount();
            bp = now;
            if (!same) { activeMs = 0; placedByYou = 0; announced = false; lastProgress = 0; }
            lastPlaced = placed; startPlaced = placed; lastTick = t;
            return;
        }
        if (bp == null) return;
        if (placed > lastPlaced) { placedByYou += placed - lastPlaced; lastProgress = t; }
        if (lastProgress > 0 && t - lastProgress < IDLE_MS) activeMs += t - lastTick;
        lastTick = t;
        if (total > 0 && placed >= total && lastPlaced < total && !announced && placedByYou > 0) {
            announced = true;
            Chat.ok("Build complete! " + total + " blocks · " + time(activeMs) + " building time"
                    + (perMinute() > 0 ? " · " + Math.round(perMinute()) + " blocks a minute" : "") + ".");
        }
        if (placed < total) announced = false;
        lastPlaced = placed;
    }
}
