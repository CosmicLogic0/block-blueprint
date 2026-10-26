package dev.buildblueprint.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable 3D grid of block states. Local axes: +x east, +y up, +z south. */
public final class Blueprint {
    public final String name;
    public final int sx, sy, sz;
    /** Block-state strings, e.g. "minecraft:oak_stairs[facing=north,half=bottom]". */
    public final String[] palette;
    /** 0 = air, n = palette[n-1]. Index = (y * sz + z) * sx + x. */
    public final int[] cells;

    public Blueprint(String name, int sx, int sy, int sz, String[] palette, int[] cells) {
        this.name = name; this.sx = sx; this.sy = sy; this.sz = sz; this.palette = palette; this.cells = cells;
    }

    public int index(int x, int y, int z) { return (y * sz + z) * sx + x; }

    /** The block state at a local cell, or null for air / outside the grid. */
    public String stateAt(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return null;
        int c = cells[index(x, y, z)];
        return c == 0 ? null : palette[c - 1];
    }

    public int blockCount() {
        int n = 0;
        for (int c : cells) if (c != 0) n++;
        return n;
    }

    /** Rotate clockwise (seen from above) by quarter turns, rotating block states too. */
    public Blueprint rotate(int turns) {
        int t = ((turns % 4) + 4) % 4;
        if (t == 0) return this;
        String[] pal = new String[palette.length];
        for (int i = 0; i < pal.length; i++) pal[i] = StateRotator.rotate(palette[i], t);
        int cx = sx, cz = sz;
        int[] cur = cells;
        for (int step = 0; step < t; step++) {
            int nx = cz, nz = cx; // dimensions swap each quarter turn
            int[] next = new int[cur.length];
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < cz; z++)
                    for (int x = 0; x < cx; x++) {
                        int v = cur[(y * cz + z) * cx + x];
                        int px = cz - 1 - z, pz = x; // clockwise
                        next[(y * nz + pz) * nx + px] = v;
                    }
            cur = next; cx = nx; cz = nz;
        }
        return new Blueprint(name, cx, sy, cz, pal, cur);
    }

    /** Mirror image, flipped east <-> west. */
    public Blueprint mirrorX() {
        String[] pal = new String[palette.length];
        for (int i = 0; i < pal.length; i++) pal[i] = StateMirror.mirror(palette[i]);
        int[] out = new int[cells.length];
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++)
            out[index(sx - 1 - x, y, z)] = cells[index(x, y, z)];
        return new Blueprint(name, sx, sy, sz, pal, out);
    }

    /** A copy with every block whose state matches taken out (left empty). */
    public Blueprint without(java.util.function.Predicate<String> drop) {
        boolean[] d = new boolean[palette.length];
        for (int i = 0; i < d.length; i++) d[i] = drop.test(palette[i]);
        int[] out = cells.clone();
        for (int k = 0; k < out.length; k++) if (out[k] != 0 && d[out[k] - 1]) out[k] = 0;
        return new Blueprint(name, sx, sy, sz, palette, out);
    }

    /** What the player must hold: item id -> count (air, fluids and second halves excluded). */
    public Map<String, Integer> materials() {
        int[] perPalette = new int[palette.length];
        for (int c : cells) if (c != 0) perPalette[c - 1]++;
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < palette.length; i++) {
            if (perPalette[i] == 0) continue;
            MaterialMap.Req r = MaterialMap.of(palette[i]);
            if (r != null) out.merge(r.itemId(), r.count() * perPalette[i], Integer::sum);
        }
        return out;
    }
}
