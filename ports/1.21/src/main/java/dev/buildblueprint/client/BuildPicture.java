package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** The picture of a build (3D, top or front), in block colors. Used by Saved builds and Get free builds. */
public final class BuildPicture {
    private static final Map<String, Integer> COLOR = new HashMap<>();
    private static int colorOf(String state) {
        return COLOR.computeIfAbsent(state, st -> {
            try {
                BlockState bs = V.parseState(st);
                if (bs.isAir()) return 0;
                int col = V.mapColor(bs);
                if (col == 0) return st.contains("glass") ? 0xFFA8D8F0 : 0xFF9A9A9A; // glass, panes, small decor
                return col | 0xFF000000;
            } catch (Exception e) { return 0xFF808080; }
        });
    }
    private static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f)), g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private Blueprint cachedBp; private int cachedTurn = -1; private int[] grid; private int W, H, D;
    private int turn, view, panX, panY; private float zoom = 1f;
    /** colours in view space: x across, y up, d = depth (0 = nearest) */
    private void prepare(Blueprint bp) {
        if (bp == cachedBp && turn == cachedTurn) return;
        cachedBp = bp; cachedTurn = turn;
        boolean swap = turn % 2 == 1;
        W = swap ? bp.sz : bp.sx; D = swap ? bp.sx : bp.sz; H = bp.sy;
        grid = new int[W * H * D];
        for (int y = 0; y < H; y++) for (int z = 0; z < bp.sz; z++) for (int x = 0; x < bp.sx; x++) {
            String st = bp.stateAt(x, y, z); if (st == null) continue;
            int c = colorOf(st); if (c == 0) continue;
            int vx, vd;
            switch (turn) { case 1 -> { vx = z; vd = bp.sx - 1 - x; } case 2 -> { vx = bp.sx - 1 - x; vd = bp.sz - 1 - z; } case 3 -> { vx = bp.sz - 1 - z; vd = x; } default -> { vx = x; vd = bp.sz - 1 - z; } }
            grid[(y * D + vd) * W + vx] = c;
        }
    }
    private int at(int x, int y, int d) { return x < 0 || y < 0 || d < 0 || x >= W || y >= H || d >= D ? 0 : grid[(y * D + d) * W + x]; }

    private String rasterKey; private int[] rects = new int[0]; private int rectCount;
    // The picture is worked out once into a pixel buffer (only when the build, view, zoom, position or size changes),
    // then drawn as a few flat rectangles. Drawing every block every frame made big builds lag badly.
    /** Draws the picture of bp in the box px, py, pw x ph. turn 0-3, view 0 = 3D, 1 = top, 2 = front. */
    public void draw(GuiGraphics g, Blueprint bp, int turn, int view, float zoom, int panX, int panY, int px, int py, int pw, int ph) {
        if (pw <= 0 || ph <= 0 || bp == null) return;
        this.turn = turn; this.view = view; this.zoom = zoom; this.panX = panX; this.panY = panY;
        prepare(bp);
        String key = System.identityHashCode(bp) + "|" + turn + "|" + view + "|" + zoom + "|" + panX + "|" + panY + "|" + pw + "|" + ph;
        if (!key.equals(rasterKey)) { rasterKey = key; raster(pw, ph); }
        g.enableScissor(px, py, px + pw, py + ph);
        for (int i = 0; i < rectCount; i++) {
            int o = i * 5;
            g.fill(px + rects[o], py + rects[o + 1], px + rects[o + 2], py + rects[o + 3], rects[o + 4]);
        }
        g.disableScissor();
    }

    /** Test only: how long the picture of a build takes to work out, and how many rectangles it draws. */
    static String bench(Blueprint bp) {
        BuildPicture s = new BuildPicture();
        long t0 = System.nanoTime();
        s.prepare(bp);
        long t1 = System.nanoTime();
        s.raster(400, 260);
        long t2 = System.nanoTime();
        return "grid " + (t1 - t0) / 1000000 + " ms, picture " + (t2 - t1) / 1000000 + " ms, " + s.rectCount + " rectangles";
    }

    private int[] buf; private int bw, bh;
    private void rect(int x0, int y0, int x1, int y1, int c) {
        if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0; if (x1 > bw) x1 = bw; if (y1 > bh) y1 = bh;
        for (int y = y0; y < y1; y++) { int row = y * bw; for (int x = x0; x < x1; x++) buf[row + x] = c; }
    }

    /** Paints the build into buf (far blocks first), then turns it into rectangles of one color. */
    private void raster(int pw, int ph) {
        bw = pw; bh = ph;
        if (buf == null || buf.length < pw * ph) buf = new int[pw * ph]; else java.util.Arrays.fill(buf, 0, pw * ph, 0);
        if (view == 0) {
            float fit = Math.min(pw / (W + D * 0.5f + 1), ph / (H + D * 0.5f + 1));
            int s = Math.max(2, Math.round(fit * zoom)); int hs = Math.max(1, s / 2);
            int totW = W * s + D * hs, totH = H * s + D * hs;
            int ox = (pw - totW) / 2 + panX, oy = (ph - totH) / 2 + panY + totH;
            for (int d = D - 1; d >= 0; d--) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int c = at(x, y, d); if (c == 0) continue;
                boolean front = at(x, y, d - 1) == 0, top = at(x, y + 1, d) == 0, right = at(x + 1, y, d) == 0;
                if (!front && !top && !right) continue;
                int X = ox + x * s + d * hs, Y = oy - y * s - d * hs;
                if (X > pw + s || X + s + hs < 0 || Y < -s || Y - s - hs > ph) continue;
                if (front) rect(X, Y - s, X + s, Y, shade(c, 0.86f));
                if (top) for (int k = 0; k < hs; k++) rect(X + k + 1, Y - s - k - 1, X + s + k + 1, Y - s - k, c);
                if (right) for (int k = 0; k < hs; k++) rect(X + s + k, Y - s - k, X + s + k + 1, Y - k, shade(c, 0.66f));
            }
        } else {
            boolean topView = view == 1;
            int cw = W, chh = topView ? D : H;
            float fit = Math.min(pw / (float) (cw + 1), ph / (float) (chh + 1));
            int s = Math.max(1, Math.round(fit * zoom));
            int ox = (pw - cw * s) / 2 + panX, oy = (ph - chh * s) / 2 + panY;
            for (int a = 0; a < cw; a++) for (int b = 0; b < chh; b++) {
                int X = ox + a * s, Y = oy + b * s;
                if (X + s < 0 || Y + s < 0 || X > pw || Y > ph) continue;
                int c = 0, depth = 0;
                if (topView) { int d = D - 1 - b; for (int y = H - 1; y >= 0; y--) if ((c = at(a, y, d)) != 0) { depth = y; break; } }
                else { int y = H - 1 - b; for (int d = 0; d < D; d++) if ((c = at(a, y, d)) != 0) { depth = D - 1 - d; break; } }
                if (c == 0) continue;
                float f = 0.55f + 0.45f * (topView ? depth / (float) Math.max(1, H - 1) : depth / (float) Math.max(1, D - 1));
                rect(X, Y, X + s, Y + s, shade(c, f));
            }
        }
        // a very busy picture (thousands of tiny different blocks) is drawn with bigger pixels, so it stays quick
        for (int k = 1; k <= 4; k++) { rectsFrom(pw, ph, k); if (rectCount <= 12000) break; }
    }

    /** Rows of one color -> spans; a span the same as the one right above it just makes that rectangle taller.
     *  k = pixel size (1 = full detail). */
    private void rectsFrom(int pw0, int ph0, int k) {
        int pw = (pw0 + k - 1) / k, ph = (ph0 + k - 1) / k;
        int[] out = new int[1024]; int n = 0;
        int[] open = new int[pw * 2 + 2]; int openN = 0;   // indexes into out of the rectangles that reached the row above
        int[] next = new int[pw * 2 + 2];
        for (int y = 0; y < ph; y++) {
            int nextN = 0, row = Math.min(y * k, ph0 - 1) * pw0, oi = 0;
            for (int x = 0; x < pw; ) {
                int c = buf[row + Math.min(x * k, pw0 - 1)];
                if (c == 0) { x++; continue; }
                int x0 = x; while (x < pw && buf[row + Math.min(x * k, pw0 - 1)] == c) x++;
                while (oi < openN && out[open[oi] * 5] < x0) oi++;
                int idx = -1;
                if (oi < openN) { int r = open[oi] * 5; if (out[r] == x0 && out[r + 2] == x && out[r + 4] == c && out[r + 3] == y) idx = open[oi]; }
                if (idx >= 0) out[idx * 5 + 3] = y + 1;
                else {
                    if ((n + 1) * 5 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                    int r = n * 5; out[r] = x0; out[r + 1] = y; out[r + 2] = x; out[r + 3] = y + 1; out[r + 4] = c; idx = n++;
                }
                next[nextN++] = idx;
            }
            int[] t = open; open = next; next = t; openN = nextN;
        }
        if (k > 1) for (int i = 0; i < n * 5; i++) if (i % 5 != 4) out[i] = Math.min(out[i] * k, (i % 5) % 2 == 0 ? pw0 : ph0);
        rects = out; rectCount = n;
    }

}
