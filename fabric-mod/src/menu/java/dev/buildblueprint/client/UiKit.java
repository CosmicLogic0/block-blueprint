package dev.buildblueprint.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One look for every Build Blueprint screen: dark panels with a coloured top edge, a gold title, progress bars and
 * small key hints. Only plain fills and text, so it draws the same on every Minecraft version.
 */
public final class UiKit {
    private UiKit() {}

    public static final int GOLD = 0xFFFFD75A, WHITE = 0xFFFFFFFF, TEXT = 0xFFE0E0E0, MUTED = 0xFF9A9A9A, DIM = 0xFF6F6F6F;
    public static final int GREEN = 0xFF55E055, RED = 0xFFFF6060, BLUE = 0xFF7FC8FF, ORANGE = 0xFFFFB050, CYAN = 0xFF20E0FF;
    public static final int BAR_BUILD = 0xFF3FAF3F, BAR_LAYER = 0xFF3F7FCF, PANEL = 0xE0101018;

    /** A dark panel with a 1-pixel coloured line along the top. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, accent);
    }

    /** A little eye, 13 x 10 pixels: open (you can see it) or closed (hidden). */
    public static void eye(GuiGraphicsExtractor g, int x, int y, boolean open, int color) {
        if (open) {
            int[][] lid = {{4, 8}, {2, 3}, {1, 1}, {0, 0}, {0, 0}, {0, 0}, {1, 1}, {2, 3}, {4, 8}};   // left half of each row (mirrored)
            for (int r = 0; r < lid.length; r++) {
                int a = lid[r][0], b = lid[r][1];
                if (r == 0 || r == 8) { g.fill(x + a, y + r, x + b + 1, y + r + 1, color); continue; }
                g.fill(x + a, y + r, x + b + 1, y + r + 1, color);
                g.fill(x + 12 - b, y + r, x + 12 - a + 1, y + r + 1, color);
            }
            g.fill(x + 5, y + 2, x + 8, y + 7, color);       // the iris
            g.fill(x + 4, y + 3, x + 9, y + 6, color);
            g.fill(x + 6, y + 3, x + 7, y + 5, 0xFF101018);  // pupil
        } else {
            g.fill(x, y + 3, x + 1, y + 5, color); g.fill(x + 12, y + 3, x + 13, y + 5, color);
            g.fill(x + 1, y + 5, x + 2, y + 6, color); g.fill(x + 11, y + 5, x + 12, y + 6, color);
            g.fill(x + 2, y + 6, x + 4, y + 7, color); g.fill(x + 9, y + 6, x + 11, y + 7, color);
            g.fill(x + 4, y + 7, x + 9, y + 8, color);
            g.fill(x + 2, y + 7, x + 3, y + 9, color); g.fill(x + 6, y + 8, x + 7, y + 10, color); g.fill(x + 10, y + 7, x + 11, y + 9, color);   // lashes
        }
    }

    /** Centred text. */
    public static void center(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
        g.text(font, s, cx - font.width(s) / 2, y, color, true);
    }

    /** Cuts text that doesn't fit and adds "..". */
    public static String fit(Font font, String s, int w) {
        if (s == null) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width(".."))) + "..";
    }

    /** A progress bar with its label in the middle, e.g. "120 / 400 placed  30%". */
    public static void bar(GuiGraphicsExtractor g, Font font, int x, int y, int w, int done, int total, int color, String label) {
        total = Math.max(1, total);
        g.fill(x - 1, y - 1, x + w + 1, y + 11, 0xFF000000);
        g.fill(x, y, x + w, y + 10, 0xFF2B2B2B);
        int f = (int) Math.round(w * Math.min(1.0, done / (double) total));
        g.fill(x, y, x + f, y + 10, color);
        if (f > 0) g.fill(x, y, x + f, y + 2, 0x50FFFFFF);
        String t = label + "  " + (int) Math.floor(100.0 * done / total) + "%";
        g.text(font, t, x + w / 2 - font.width(t) / 2, y + 1, WHITE, true);
    }

    /** A little key cap like [Insert] followed by what it does. Returns the x after it. */
    public static int key(GuiGraphicsExtractor g, Font font, int x, int y, String key, String what) {
        int kw = font.width(key) + 6;
        g.fill(x, y - 1, x + kw, y + 9, 0xFF3A3A44);
        g.fill(x, y + 9, x + kw, y + 10, 0xFF1A1A20);
        g.text(font, key, x + 3, y, WHITE, false);
        g.text(font, what, x + kw + 4, y, 0xFFCCCCCC, false);
        return x + kw + 4 + font.width(what) + 10;
    }

    /** Width of a key hint drawn by {@link #key}. */
    public static int keyWidth(Font font, String key, String what) { return font.width(key) + 6 + 4 + font.width(what) + 10; }

    /** Evenly spaced button positions across a row: returns the x of each button. */
    public static int[] row(int centerX, int count, int buttonW, int gap) {
        int total = count * buttonW + (count - 1) * gap, x = centerX - total / 2;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = x + i * (buttonW + gap);
        return out;
    }
}
