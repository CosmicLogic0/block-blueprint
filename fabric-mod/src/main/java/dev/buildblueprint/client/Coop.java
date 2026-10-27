package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;

/**
 * Co-op: friends build the same blueprint together. Everyone loads it at the same spot (the co-op code has the exact
 * position), picks how many builders there are and which one they are, and then only sees - and only counts - their own
 * strip of the build. The build is cut into equal strips along its longer side, so nobody gets in anyone's way.
 */
public final class Coop {
    private Coop() {}

    public static int parts = 1;   // 1 = off
    public static int me = 1;      // 1..parts

    public static boolean on() { return parts > 1; }
    public static void off() { parts = 1; me = 1; }
    public static void set(int p, int m) { parts = Math.max(1, Math.min(4, p)); me = Math.max(1, Math.min(parts, m)); }

    /** Is this cell (blueprint coordinates) in my part? */
    public static boolean mine(Blueprint bp, int x, int z) {
        if (parts <= 1 || bp == null) return true;
        boolean alongX = bp.sx >= bp.sz;
        int c = alongX ? x : z, size = alongX ? bp.sx : bp.sz;
        return Math.min(parts - 1, c * parts / Math.max(1, size)) == me - 1;
    }

    /** "Part 1 of 2 (the west half)" style description of my strip. */
    public static String where(Blueprint bp) {
        if (!on() || bp == null) return "";
        boolean alongX = bp.sx >= bp.sz;
        String a = alongX ? "west" : "north", b = alongX ? "east" : "south";
        String side = me == 1 ? a + " end" : me == parts ? b + " end" : "middle";
        return "part " + me + " of " + parts + " (the " + side + ")";
    }
}
