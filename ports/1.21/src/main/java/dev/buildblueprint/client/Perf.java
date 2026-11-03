package dev.buildblueprint.client;

/** Timings for the self-test: how long the ghost preview takes each frame. Only adds up a few numbers. */
public final class Perf {
    private Perf() {}

    private static long scanNs, scanMax, drawNs, drawMax;
    private static int frames, draws;
    public static int ghosts;

    public static void scan(long ns) { scanNs += ns; scanMax = Math.max(scanMax, ns); frames++; }
    public static void draw(long ns) { drawNs += ns; drawMax = Math.max(drawMax, ns); draws++; }
    public static void reset() { scanNs = scanMax = drawNs = drawMax = 0; frames = draws = 0; }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "%d frames, working out %.2f ms a frame (worst %.1f), drawing %.2f ms a frame (worst %.1f), %d ghost blocks",
                frames, frames == 0 ? 0 : scanNs / 1e6 / frames, scanMax / 1e6, draws == 0 ? 0 : drawNs / 1e6 / draws, drawMax / 1e6, ghosts);
    }
}
