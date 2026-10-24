package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.Session;

/**
 * Layer-by-layer building. Home = next layer up, End = next layer down (past the top = show everything).
 * You can switch to any layer whenever you like. The mod only changes layer by itself when something
 * just happened: you finished the layer you're on (it moves to the next unfinished one), or a block in a
 * lower layer that was complete just got broken (it goes back there so you can fix it).
 */
public final class Layers {
    private Layers() {}

    private static int[] prev;              // blocks missing per layer at the last recount
    private static Blueprint prevBp;        // ...for this blueprint
    private static long prevPos = Long.MIN_VALUE;

    /** Lowest layer that still has blocks missing (0 if not counted yet). */
    public static int firstUnfinished() {
        if (prev != null) for (int y = 0; y < prev.length; y++) if (prev[y] > 0) return y;
        return 0;
    }

    public static void step(int d) {
        Session s = BuildBlueprintClient.SESSION;
        if (!s.loaded()) return;
        int top = s.blueprint().sy - 1;
        if (s.layer < 0) s.layer = d > 0 ? 0 : top;
        else { s.layer += d; if (s.layer > top) s.layer = -1; else if (s.layer < 0) s.layer = 0; }
        Chat.bar(s.layer < 0 ? "Showing all layers" : "Layer " + (s.layer + 1) + " of " + (top + 1));
    }

    /** Called after each recount with how many blocks are still missing on every layer. */
    static void auto(int[] left) {
        Session s = BuildBlueprintClient.SESSION;
        if (!s.loaded() || left == null) { prev = null; return; }
        long pos = ((long) s.ox * 31 + s.oy) * 31 + s.oz;
        // A different build, a rotation or a move: just remember the new counts, don't jump around.
        if (prev == null || prev.length != left.length || prevBp != s.blueprint() || prevPos != pos) {
            prev = left.clone(); prevBp = s.blueprint(); prevPos = pos; return;
        }
        int[] before = prev;
        prev = left.clone();
        if (s.layer < 0 || !Settings.autoLayer) return;
        int cur = Math.min(s.layer, left.length - 1);
        // 1) a lower layer that was finished just lost a block -> go fix it
        for (int y = 0; y < cur; y++)
            if (before[y] == 0 && left[y] > 0) { s.layer = y; Chat.bar("A block in layer " + (y + 1) + " was broken - back to layer " + (y + 1)); return; }
        // 2) the layer you're on was just finished -> next unfinished layer above it (or any left below)
        if (before[cur] > 0 && left[cur] == 0) {
            int next = -1;
            for (int y = cur + 1; y < left.length; y++) if (left[y] > 0) { next = y; break; }
            if (next < 0) for (int y = 0; y < cur; y++) if (left[y] > 0) { next = y; break; }
            if (next < 0) { s.layer = -1; Chat.bar("Build complete!"); Chat.ding(true); return; }
            s.layer = next;
            Chat.bar("Layer " + (cur + 1) + " done! Now layer " + (next + 1));
            Chat.ding(false);
        }
    }
}
