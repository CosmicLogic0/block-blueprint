package dev.buildblueprint.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Map art: a picture built so that a Minecraft map of the spot shows the picture. One map is 128 x 128 pixels; at zoom
 * level z one pixel is 2^z x 2^z blocks. Flat art uses one shade of each map color; "staircase" art steps blocks up and
 * down (higher than the block north of it = light, lower = dark) for three shades. Plain Java, no Minecraft classes.
 */
public final class MapArt {
    private MapArt() {}

    /** Biggest build side, in blocks (bigger needs more memory than a game usually has). */
    public static final int MAX_SIDE = 2048;
    /** The three shades a map draws a color in: darker (block lower than the one north of it), normal (same), lighter (higher). */
    static final int[] SHADE = {180, 220, 255};
    /** Blocks up or down per shade step, by zoom level (zoomed-out maps average the heights, so they need bigger steps). */
    static final int[] STEP = {1, 2, 2, 3, 5};

    /** A block the art can use, with the base color the map shows for it. support = needs a block under it (carpet). */
    public static final class Block {
        public final String id; public final int mapRgb; public final boolean support;
        public Block(String id, int mapRgb, boolean support) { this.id = id; this.mapRgb = mapRgb; this.support = support; }
    }

    /** Blocks per side of one map at this zoom (0 to 4): 128, 256, 512, 1024, 2048. */
    public static int mapBlocks(int zoom) { return 128 << Math.max(0, Math.min(4, zoom)); }

    /** True when mapsW x mapsH maps at this zoom fit in MAX_SIDE. */
    public static boolean fits(int mapsW, int mapsH, int zoom) { return Math.max(mapsW, mapsH) * (long) mapBlocks(zoom) <= MAX_SIDE; }

    /** Where the map grid starts on the x or z axis for a spot: maps line up at -64 + k * size. */
    public static int gridStart(int coord, int zoom) {
        int size = mapBlocks(zoom);
        return Math.floorDiv(coord + 64, size) * size - 64;
    }

    /** The color the map shows for a block in a shade (0 dark, 1 normal, 2 light). */
    static int shaded(int rgb, int shade) {
        int m = SHADE[shade];
        return ((rgb >> 16 & 255) * m / 255) << 16 | ((rgb >> 8 & 255) * m / 255) << 8 | (rgb & 255) * m / 255;
    }

    /** The result: per map pixel the block (index into the palette) and shade, plus the picture as the map will show it. */
    public static final class Plan {
        public final int w, h;               // map pixels
        public final int[] block, shade;      // -1 block = nothing (see-through)
        public final int[] shown;             // 0xRRGGBB the map will show (for the preview)
        Plan(int w, int h) { this.w = w; this.h = h; block = new int[w * h]; shade = new int[w * h]; shown = new int[w * h]; }
    }

    /** Picks the block and shade for each map pixel. The picture is cropped to the maps' shape, from its middle. */
    public static Plan plan(PictureBuild.Picture p, int mapsW, int mapsH, List<Block> pal, boolean staircase) {
        int w = 128 * mapsW, h = 128 * mapsH;
        Plan out = new Plan(w, h);
        // crop the picture to the same shape as the maps (keep the middle)
        double want = (double) w / h, have = (double) p.w / p.h;
        int cw = p.w, ch = p.h, cx = 0, cy = 0;
        if (have > want) { cw = (int) Math.round(p.h * want); cx = (p.w - cw) / 2; }
        else if (have < want) { ch = (int) Math.round(p.w / want); cy = (p.h - ch) / 2; }
        // every (block, shade) the art may use
        int shades = staircase ? 3 : 1;
        int[] candRgb = new int[pal.size() * shades], candBlock = new int[candRgb.length], candShade = new int[candRgb.length];
        for (int i = 0, k = 0; i < pal.size(); i++)
            for (int s = 0; s < shades; s++, k++) { int sh = staircase ? s : 1; candRgb[k] = shaded(pal.get(i).mapRgb, sh); candBlock[k] = i; candShade[k] = sh; }
        Map<Integer, Integer> memo = new HashMap<>();
        for (int py = 0; py < h; py++)
            for (int px = 0; px < w; px++) {
                int x0 = cx + px * cw / w, x1 = Math.max(x0 + 1, cx + (px + 1) * cw / w), y0 = cy + py * ch / h, y1 = Math.max(y0 + 1, cy + (py + 1) * ch / h);
                long r = 0, g = 0, b = 0, a = 0, n = 0;
                for (int y = y0; y < y1; y++)
                    for (int x = x0; x < x1; x++) {
                        int c = p.argb[y * p.w + x], al = c >>> 24;
                        r += ((c >> 16) & 255) * al; g += ((c >> 8) & 255) * al; b += (c & 255) * al; a += al; n++;
                    }
                int i = py * w + px;
                if (a < n * 128) { out.block[i] = -1; continue; }
                int rgb = (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                int k = memo.computeIfAbsent(rgb >> 2 & 0x3F3F3F, key -> nearest(rgb, candRgb));
                out.block[i] = candBlock[k]; out.shade[i] = candShade[k]; out.shown[i] = candRgb[k];
            }
        return out;
    }

    private static int nearest(int rgb, int[] cands) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < cands.length; i++) {
            int c = cands[i], dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255), rm = (r + ((c >> 16) & 255)) / 2;
            long d = (long) (512 + rm) * dr * dr / 256 + 4L * dg * dg + (long) (767 - rm) * db * db / 256;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /**
     * The build. x = east (map pixels left to right), z = south (top of the picture is north). Each map pixel is a
     * 2^zoom square of its block. Staircase: each pixel is higher than the one north of it (light), lower (dark) or level;
     * an extra row of stone on the north edge gives the first row something to compare with. Carpets get a block under them.
     */
    public static Blueprint build(String name, Plan plan, List<Block> pal, int zoom, boolean staircase, String supportBlock) {
        int px = 1 << Math.max(0, Math.min(4, zoom));
        int w = plan.w, h = plan.h;
        int north = staircase ? 1 : 0;                       // the extra north row (in pixels)
        int[] height = new int[w * (h + north)];             // per pixel, the height of its top block
        int maxH = 0;
        // how many blocks one shade step needs: the map compares heights * 4 / (2^zoom + 4) (+-0.2) against 0.6
        int step = STEP[Math.max(0, Math.min(4, zoom))];
        if (staircase) {
            for (int x = 0; x < w; x++) {
                int y = 0, lo = 0, hi = 0;
                int[] col = new int[h + 1];
                col[0] = 0;
                for (int z = 0; z < h; z++) {
                    int i = z * w + x, s = plan.block[i] < 0 ? 1 : plan.shade[i];
                    y += s == 2 ? step : s == 0 ? -step : 0;
                    col[z + 1] = y; lo = Math.min(lo, y); hi = Math.max(hi, y);
                }
                // dark pixels after dark pixels sink forever: only the steps matter, so bring the column up to start at 0
                for (int z = 0; z <= h; z++) { height[z * w + x] = col[z] - lo; maxH = Math.max(maxH, col[z] - lo); }
            }
        }
        boolean anySupport = false;
        for (Block b : pal) anySupport |= b.support;
        int base = anySupport ? 1 : 0;                       // carpets stand on a block
        int sx = w * px, sz = (h + north) * px, sy = maxH + 1 + base;
        List<String> palette = new ArrayList<>();
        Map<String, Integer> slot = new HashMap<>();
        int[] cells = new int[sx * sy * sz];
        for (int pz = 0; pz < h + north; pz++)
            for (int pxi = 0; pxi < w; pxi++) {
                int top = staircase ? height[pz * w + pxi] : 0;
                String block, under = null;
                if (pz < north) block = supportBlock;           // the north edge row
                else {
                    int bi = plan.block[(pz - north) * w + pxi];
                    if (bi < 0) continue;
                    Block b = pal.get(bi);
                    block = b.id;
                    if (b.support) under = supportBlock;
                }
                int y = top + base;
                int c = slot.computeIfAbsent(block, k -> { palette.add(k); return palette.size(); });
                int cu = under == null ? 0 : slot.computeIfAbsent(under, k -> { palette.add(k); return palette.size(); });
                for (int dz = 0; dz < px; dz++)
                    for (int dx = 0; dx < px; dx++) {
                        int X = pxi * px + dx, Z = pz * px + dz;
                        cells[(y * sz + Z) * sx + X] = c;
                        if (cu != 0) cells[((y - 1) * sz + Z) * sx + X] = cu;
                    }
            }
        return new Blueprint(name, sx, sy, sz, palette.toArray(new String[0]), cells);
    }
}
