package dev.buildblueprint.core;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * "Build from a picture": turns a picture into blocks, one block per pixel, each pixel the block whose texture color is
 * closest. Standing up (a wall facing you) or lying flat (a floor, like map art). Plain Java, no Minecraft classes.
 */
public final class PictureBuild {
    private PictureBuild() {}

    /** A picture's pixels (0xAARRGGBB, row by row from the top). */
    public static final class Picture {
        public final int w, h;
        public final int[] argb;
        public Picture(int w, int h, int[] argb) { this.w = w; this.h = h; this.argb = argb; }
    }

    /** One block that can be used, with its average color. */
    public static final class Block {
        public final String id; public final int rgb; public final boolean basic;
        Block(String id, int rgb, boolean basic) { this.id = id; this.rgb = rgb; this.basic = basic; }
    }

    public static final int MAX_SIZE = 256;

    /** Reads a PNG, JPG, GIF or BMP file. */
    public static Picture read(byte[] file) throws BlueprintException {
        try {
            java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(new ByteArrayInputStream(file));
            if (im == null) throw new BlueprintException("That isn't a picture Build Blueprint can read (use PNG or JPG).");
            int w = im.getWidth(), h = im.getHeight();
            if (w <= 0 || h <= 0 || (long) w * h > 50_000_000L) throw new BlueprintException("That picture is too big.");
            return new Picture(w, h, im.getRGB(0, 0, w, h, null, 0, w));
        } catch (IOException | RuntimeException e) {
            throw new BlueprintException("That isn't a picture Build Blueprint can read (use PNG or JPG).");
        }
    }

    /** True for file names that look like pictures. */
    public static boolean isPictureFile(String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replaceAll("[?#].*$", "");
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    private static List<Block> all;
    /** Every block in the mod's color table (made from the game's own textures). */
    public static synchronized List<Block> blocks() {
        if (all != null) return all;
        List<Block> out = new ArrayList<>();
        try (InputStream in = PictureBuild.class.getResourceAsStream("/assets/buildblueprint/tables/block-colors.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    String[] p = line.split("\t");
                    if (line.startsWith("#") || p.length < 3) continue;
                    out.add(new Block(p[0], Integer.parseInt(p[1].substring(1), 16), p[2].equals("basic")));
                }
            }
        } catch (IOException | RuntimeException ignored) {}
        all = out;
        return all;
    }

    /** The blocks to use: wool, concrete and terracotta, or every plain full block; only ones this game has. */
    public static List<Block> palette(boolean everyBlock, Predicate<String> exists) {
        List<Block> out = new ArrayList<>();
        for (Block b : blocks()) if ((everyBlock || b.basic) && exists.test(b.id)) out.add(b);
        return out;
    }

    /** The build's size for a picture: the long side is size blocks. */
    public static int[] fit(Picture p, int size) {
        size = Math.max(1, Math.min(MAX_SIZE, size));
        if (p.w >= p.h) return new int[]{size, Math.max(1, Math.round(size * (float) p.h / p.w))};
        return new int[]{Math.max(1, Math.round(size * (float) p.w / p.h)), size};
    }

    /**
     * The blocks for each spot of a bw x bh build (row by row from the top), as indexes into the palette; -1 = nothing
     * there (see-through pixels). Each spot takes the average color of the pixels it covers.
     */
    public static int[] match(Picture p, int bw, int bh, List<Block> pal) {
        int[] out = new int[bw * bh];
        Map<Integer, Integer> memo = new HashMap<>();
        for (int by = 0; by < bh; by++)
            for (int bx = 0; bx < bw; bx++) {
                int x0 = bx * p.w / bw, x1 = Math.max(x0 + 1, (bx + 1) * p.w / bw), y0 = by * p.h / bh, y1 = Math.max(y0 + 1, (by + 1) * p.h / bh);
                long r = 0, g = 0, b = 0, a = 0, n = 0;
                for (int y = y0; y < y1; y++)
                    for (int x = x0; x < x1; x++) {
                        int c = p.argb[y * p.w + x], al = c >>> 24;
                        r += ((c >> 16) & 255) * al; g += ((c >> 8) & 255) * al; b += (c & 255) * al; a += al; n++;
                    }
                if (a < n * 128) { out[by * bw + bx] = -1; continue; }   // mostly see-through: leave it empty
                int rgb = (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                out[by * bw + bx] = memo.computeIfAbsent(rgb >> 2 & 0x3F3F3F, k -> nearest(rgb, pal));
            }
        return out;
    }

    /** The palette block closest in color ("redmean" distance: close to how people see color differences). */
    static int nearest(int rgb, List<Block> pal) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < pal.size(); i++) {
            int c = pal.get(i).rgb, dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255), rm = (r + ((c >> 16) & 255)) / 2;
            long d = (long) (512 + rm) * dr * dr / 256 + 4L * dg * dg + (long) (767 - rm) * db * db / 256;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** The build: standing up (x = across, y = up, one block thick) or lying flat (x = across, z = from the top of the picture). */
    public static Blueprint build(String name, int[] spots, int bw, int bh, List<Block> pal, boolean floor) {
        int[] slot = new int[pal.size()];   // only the blocks the picture uses go in the build's palette
        List<String> used = new ArrayList<>();
        for (int v : spots) if (v >= 0 && slot[v] == 0) { used.add(pal.get(v).id); slot[v] = used.size(); }
        String[] palette = used.toArray(new String[0]);
        int sx = bw, sy = floor ? 1 : bh, sz = floor ? bh : 1;
        int[] cells = new int[sx * sy * sz];
        for (int py = 0; py < bh; py++)
            for (int px = 0; px < bw; px++) {
                int v = spots[py * bw + px];
                if (v < 0) continue;
                int y = floor ? 0 : bh - 1 - py, z = floor ? py : 0;
                cells[(y * sz + z) * sx + px] = slot[v];   // 0 = air, palette entries start at 1
            }
        return new Blueprint(name, sx, sy, sz, palette, cells);
    }
}
