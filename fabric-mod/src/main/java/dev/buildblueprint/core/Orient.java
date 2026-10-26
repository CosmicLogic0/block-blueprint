package dev.buildblueprint.core;

import java.util.HashMap;
import java.util.Map;

/**
 * "Is it the right way round?" A placed block only counts as done when it faces the way the build says:
 * stairs (facing, upside down or not), slabs (top / bottom / double), logs and pillars (axis), doors (hinge),
 * buttons and levers (floor / wall / ceiling), signs and banners (rotation, one step off is fine)...
 * Things the game works out by itself from the blocks around (stair corners, fence and wall connections, rails,
 * redstone, open / closed, lit, waterlogged...) are not checked, so they never make a correct block look wrong.
 */
public final class Orient {
    private Orient() {}

    /** The "Check block direction" setting. */
    public static boolean on = true;

    private static final String[] CHECK = {"facing", "half", "axis", "type", "rotation", "face", "hinge", "attachment", "hanging", "vertical_direction", "orientation"};

    /**
     * True when the block in the world (its state as text, e.g. "Block{minecraft:oak_stairs}[facing=north,half=bottom,...]")
     * is the right way round for the wanted state ("minecraft:oak_stairs[facing=north,half=bottom,...]").
     * Only call it when the block itself is already the right one.
     */
    public static boolean ok(String want, String have) {
        if (!on || want == null || have == null || want.indexOf('[') < 0) return true;
        Map<String, String> w = props(want), h = props(have);
        for (String k : CHECK) {
            String a = w.get(k), b = h.get(k);
            if (a == null || b == null || a.equals(b)) continue;
            if (k.equals("type") && !(a.equals("top") || a.equals("bottom") || a.equals("double"))) continue;   // only slabs (chests use "type" for left / right halves)
            if (k.equals("rotation") && near16(a, b)) continue;
            return false;
        }
        return true;
    }

    /** What's wrong, in words: "should face north, upside down". Empty when it's fine. */
    public static String fix(String want, String have) {
        if (ok(want, have)) return "";
        Map<String, String> w = props(want), h = props(have);
        StringBuilder sb = new StringBuilder();
        for (String k : CHECK) {
            String a = w.get(k), b = h.get(k);
            if (a == null || b == null || a.equals(b)) continue;
            String t = say(k, a);
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(t);
        }
        return sb.length() == 0 ? "is the wrong way round" : "should " + sb;
    }

    private static String say(String k, String v) {
        switch (k) {
            case "facing": return v.equals("up") ? "face up" : v.equals("down") ? "face down" : "face " + v;
            case "half": return v.equals("top") ? "be upside down (top half)" : v.equals("bottom") ? "be the right way up (bottom half)" : "";
            case "type": return v.equals("double") ? "be a double slab" : v.equals("top") ? "be a top slab" : "be a bottom slab";
            case "axis": return v.equals("y") ? "stand upright" : "lie " + (v.equals("x") ? "east-west" : "north-south");
            case "rotation": return "be turned to face " + dir16(v);
            case "face": return v.equals("floor") ? "be on the floor" : v.equals("ceiling") ? "hang from the ceiling" : "be on a wall";
            case "hinge": return "have its hinge on the " + v;
            case "hanging": return v.equals("true") ? "hang from the block above" : "stand on the block below";
            default: return "have " + k.replace('_', ' ') + " " + v;
        }
    }

    private static boolean near16(String a, String b) {
        try { int d = Math.abs(Integer.parseInt(a) - Integer.parseInt(b)) % 16; return d <= 1 || d >= 15; } catch (NumberFormatException e) { return false; }
    }

    private static String dir16(String v) {
        String[] n = {"south", "south", "south-west", "west", "west", "west", "north-west", "north", "north", "north", "north-east", "east", "east", "east", "south-east", "south"};
        try { return n[Integer.parseInt(v) & 15]; } catch (NumberFormatException e) { return v; }
    }

    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();
    /** The [key=value,...] part of a state as a map. */
    static Map<String, String> props(String s) {
        Map<String, String> m = CACHE.get(s);
        if (m != null) return m;
        m = new HashMap<>();
        int a = s.indexOf('['), b = s.lastIndexOf(']');
        if (a >= 0 && b > a) for (String kv : s.substring(a + 1, b).split(",")) {
            int e = kv.indexOf('=');
            if (e > 0) m.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
        }
        if (CACHE.size() > 4096) CACHE.clear();
        CACHE.put(s, m);
        return m;
    }
}
