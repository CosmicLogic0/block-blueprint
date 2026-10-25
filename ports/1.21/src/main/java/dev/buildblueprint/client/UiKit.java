package dev.buildblueprint.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

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
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, accent);
    }

    /** Centred text. */
    public static void center(GuiGraphics g, Font font, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, true);
    }

    /** Cuts text that doesn't fit and adds "..". */
    public static String fit(Font font, String s, int w) {
        if (s == null) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width(".."))) + "..";
    }

    /** A progress bar with its label in the middle, e.g. "120 / 400 placed  30%". */
    public static void bar(GuiGraphics g, Font font, int x, int y, int w, int done, int total, int color, String label) {
        total = Math.max(1, total);
        g.fill(x - 1, y - 1, x + w + 1, y + 11, 0xFF000000);
        g.fill(x, y, x + w, y + 10, 0xFF2B2B2B);
        int f = (int) Math.round(w * Math.min(1.0, done / (double) total));
        g.fill(x, y, x + f, y + 10, color);
        if (f > 0) g.fill(x, y, x + f, y + 2, 0x50FFFFFF);
        String t = label + "  " + (int) Math.floor(100.0 * done / total) + "%";
        g.drawString(font, t, x + w / 2 - font.width(t) / 2, y + 1, WHITE, true);
    }

    /** A little key cap like [Insert] followed by what it does. Returns the x after it. */
    public static int key(GuiGraphics g, Font font, int x, int y, String key, String what) {
        int kw = font.width(key) + 6;
        g.fill(x, y - 1, x + kw, y + 9, 0xFF3A3A44);
        g.fill(x, y + 9, x + kw, y + 10, 0xFF1A1A20);
        g.drawString(font, key, x + 3, y, WHITE, false);
        g.drawString(font, what, x + kw + 4, y, 0xFFCCCCCC, false);
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
