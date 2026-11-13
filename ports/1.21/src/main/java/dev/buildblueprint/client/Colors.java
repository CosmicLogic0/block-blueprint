package dev.buildblueprint.client;

/**
 * The colors players can pick in Settings > Colors: the ghost blocks, the red boxes on mistakes, the next-block box,
 * chests that hold blocks you need, and scaffold hints. The defaults are the colors the mod always used.
 */
public final class Colors {
    private Colors() {}

    public static final String[] NAMES = {"Red", "Orange", "Yellow", "Green", "Aqua", "Light blue", "Blue", "Purple", "Pink", "White"};
    private static final int[] RGB = {0xFF2020, 0xFF8C1A, 0xFFE040, 0x40FF80, 0x20E0FF, 0x60C8FF, 0x4070FF, 0xB060FF, 0xFF60C0, 0xFFFFFF};

    /** A saved number that is still a color (ghost: -1 = the blocks' own colors). */
    static int fix(int v, boolean ghost) { return v >= (ghost ? -1 : 0) && v < RGB.length ? v : (ghost ? -1 : 0); }
    /** The next color when the button is clicked. */
    public static int next(int v, boolean ghost) { return v + 1 < RGB.length ? v + 1 : (ghost ? -1 : 0); }
    public static String name(int v) { return v < 0 ? "Normal" : NAMES[v]; }

    /** Full-alpha ARGB for boxes and lines. */
    public static int red() { return 0xFF000000 | RGB[fix(Settings.redColor, false)]; }
    public static int chest() { return 0xFF000000 | RGB[fix(Settings.chestColor, false)]; }
    public static int spots() { return 0xFF000000 | RGB[fix(Settings.spotColor, false)]; }
    public static int scaffold() { return 0xFF000000 | RGB[fix(Settings.scaffoldColor, false)]; }
    /** The next-block box: the chosen color, or green once you hold that block (yellow when green is the chosen color). */
    public static int hint(boolean inHand) {
        int c = fix(Settings.hintColor, false);
        if (!inHand) return 0xFF000000 | RGB[c];
        return c == 3 ? 0xFFFFE040 : 0xFF60FF60;
    }
    public static int hintLine() { return 0xC0000000 | RGB[fix(Settings.hintColor, false)]; }

    /** A ghost block's color (RGB, no alpha) after the chosen tint: half the block's own color, half the tint. */
    public static int ghost(int rgb) {
        int g = Settings.ghostColor;
        if (g < 0 || g >= RGB.length) return rgb;
        int t = RGB[g];
        int r = (((rgb >> 16) & 255) + ((t >> 16) & 255)) >> 1, gr = (((rgb >> 8) & 255) + ((t >> 8) & 255)) >> 1, b = ((rgb & 255) + (t & 255)) >> 1;
        return (r << 16) | (gr << 8) | b;
    }
}
