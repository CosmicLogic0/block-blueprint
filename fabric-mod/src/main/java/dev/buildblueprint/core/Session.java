package dev.buildblueprint.core;

import java.util.Locale;

/** The loaded blueprint plus where it sits in the world. Pure logic, no Minecraft classes. */
public final class Session {
    private Blueprint base;
    private Blueprint current;
    private int turns;
    public int ox, oy, oz;          // world position of local cell (0,0,0)
    public boolean visible = true;
    /** Show only this local layer (0-based), or -1 for everything. */
    public int layer = -1;
    /** Placed: the blueprint can't be moved or rotated until unlocked (stops accidental key presses). */
    public boolean locked = false;

    /** Turns block names this Minecraft version doesn't know into the name it uses (set by the game side, e.g. chain <-> iron_chain). */
    public static java.util.function.UnaryOperator<String> fixState = st -> st;

    public boolean loaded() { return base != null; }
    public Blueprint blueprint() { return current; }
    public int turns() { return turns; }

    /** Load a blueprint so it sits in front of a player standing at (px,py,pz) facing the given yaw. */
    public void load(Blueprint bp, int px, int py, int pz, float yaw) {
        bp = withPalette(bp, fixState);
        base = bp; turns = 0; current = bp; layer = -1; visible = true; locked = false; mirrored = false; swapUndo.clear();
        int[] f = forward(yaw);
        // Centre it sideways, put its near edge a few blocks ahead, base level with the player's feet.
        ox = px + f[0] * (3 + (f[0] > 0 ? 0 : f[0] < 0 ? bp.sx - 1 : 0)) - (f[0] == 0 ? bp.sx / 2 : 0);
        oz = pz + f[1] * (3 + (f[1] > 0 ? 0 : f[1] < 0 ? bp.sz - 1 : 0)) - (f[1] == 0 ? bp.sz / 2 : 0);
        oy = py;
    }

    public void clear() { base = null; current = null; turns = 0; layer = -1; locked = false; }

    /** Unit (dx, dz) the player faces. Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east. */
    public static int[] forward(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> new int[]{0, 1};
            case 1 -> new int[]{-1, 0};
            case 2 -> new int[]{0, -1};
            default -> new int[]{1, 0};
        };
    }

    /** Move relative to the way the player is facing, or along a compass direction. Returns false for a bad name. */
    public boolean move(String dir, int n, float yaw) {
        int[] f = forward(yaw);
        int dx = 0, dy = 0, dz = 0;
        switch (dir.toLowerCase(Locale.ROOT)) {
            case "up" -> dy = n;
            case "down" -> dy = -n;
            case "forward", "forwards", "ahead" -> { dx = f[0] * n; dz = f[1] * n; }
            case "back", "backward", "backwards" -> { dx = -f[0] * n; dz = -f[1] * n; }
            case "left" -> { dx = f[1] * n; dz = -f[0] * n; }
            case "right" -> { dx = -f[1] * n; dz = f[0] * n; }
            case "north" -> dz = -n;
            case "south" -> dz = n;
            case "east" -> dx = n;
            case "west" -> dx = -n;
            default -> { return false; }
        }
        ox += dx; oy += dy; oz += dz;
        return true;
    }

    /** Rotate about its centre by quarter turns (clockwise from above if positive). */
    public void rotate(int quarter) {
        if (base == null) return;
        Blueprint before = current;
        turns = (((turns + quarter) % 4) + 4) % 4;
        current = base.rotate(turns);
        ox += Math.floorDiv(before.sx - current.sx, 2);
        oz += Math.floorDiv(before.sz - current.sz, 2);
    }

    /** Flip left <-> right (as the build currently faces). Stays in the same spot. */
    public void mirror() {
        if (base == null) return;
        base = current.mirrorX(); turns = 0; current = base;
        mirrored = !mirrored;
    }
    public boolean mirrored = false;

    private final java.util.ArrayDeque<Blueprint[]> swapUndo = new java.util.ArrayDeque<>();

    /** Change block states in the palette (block swaps). Keeps position, rotation and mirror. */
    public void mapPalette(java.util.function.UnaryOperator<String> fn) {
        if (base == null) return;
        swapUndo.push(new Blueprint[]{base, current});
        base = withPalette(base, fn); current = withPalette(current, fn);
    }
    /** Take blocks out of the build (e.g. snow on a roof). Undone like a swap. */
    public void removeBlocks(java.util.function.Predicate<String> drop) {
        if (base == null) return;
        swapUndo.push(new Blueprint[]{base, current});
        base = base.without(drop); current = current.without(drop);
    }
    public boolean undoSwap() {
        if (swapUndo.isEmpty() || base == null) return false;
        Blueprint[] b = swapUndo.pop(); base = b[0]; current = b[1];
        return true;
    }
    public boolean canUndoSwap() { return !swapUndo.isEmpty(); }
    private static Blueprint withPalette(Blueprint bp, java.util.function.UnaryOperator<String> fn) {
        String[] pal = new String[bp.palette.length];
        for (int i = 0; i < pal.length; i++) pal[i] = fn.apply(bp.palette[i]);
        return new Blueprint(bp.name, bp.sx, bp.sy, bp.sz, pal, bp.cells);
    }
    /** Restore a saved session exactly (used when you rejoin a world). */
    public void restore(Blueprint bp, int x, int y, int z, int layer, boolean locked) {
        bp = withPalette(bp, fixState);
        base = bp; current = bp; turns = 0; ox = x; oy = y; oz = z; this.layer = layer; this.locked = locked; visible = true; mirrored = false; swapUndo.clear();
    }

    public boolean layerShown(int y) { return layer < 0 || layer == y; }
}
