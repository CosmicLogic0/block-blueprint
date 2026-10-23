package dev.buildblueprint.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rotates a block-state string such as "minecraft:oak_stairs[facing=north,half=bottom]" by quarter turns,
 * clockwise seen from above. Mirrors rotateProps() in the app's export.js.
 */
public final class StateRotator {
    private StateRotator() {}

    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final Pattern RAIL = Pattern.compile(
            "^(north_south|east_west|ascending_(north|east|south|west)|north_east|south_east|south_west|north_west)$");

    private static int dirIndex(String v) {
        for (int i = 0; i < 4; i++) if (DIRS[i].equals(v)) return i;
        return -1;
    }

    private static String rotDir(String v, int r) {
        int i = dirIndex(v);
        return i < 0 ? v : DIRS[(i + r) % 4];
    }

    private static String railShape(String v, int r) {
        if (!RAIL.matcher(v).matches()) return v;
        if (v.startsWith("ascending_")) return "ascending_" + rotDir(v.substring(10), r);
        boolean n = false, e = false, s = false, w = false;
        for (String word : v.split("_")) {
            switch (rotDir(word, r)) {
                case "north" -> n = true;
                case "east" -> e = true;
                case "south" -> s = true;
                default -> w = true;
            }
        }
        if (n && s) return "north_south";
        if (e && w) return "east_west";
        if (n && e) return "north_east";
        if (s && e) return "south_east";
        if (s && w) return "south_west";
        return "north_west";
    }

    public static String rotate(String state, int turns) {
        int r = ((turns % 4) + 4) % 4;
        if (r == 0) return state;
        int b = state.indexOf('[');
        if (b < 0 || !state.endsWith("]")) return state;
        String head = state.substring(0, b);
        Map<String, String> kv = new LinkedHashMap<>();
        for (String part : state.substring(b + 1, state.length() - 1).split(",")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            kv.put(part.substring(0, eq), part.substring(eq + 1));
        }
        Map<String, String> sides = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : kv.entrySet()) if (dirIndex(en.getKey()) >= 0) sides.put(en.getKey(), en.getValue());

        StringBuilder out = new StringBuilder(head).append('[');
        boolean first = true;
        for (Map.Entry<String, String> en : kv.entrySet()) {
            String k = en.getKey(), v = en.getValue(), nv = v;
            if (k.equals("facing")) nv = rotDir(v, r);
            else if (k.equals("axis")) nv = (r % 2 == 1 && !v.equals("y")) ? (v.equals("x") ? "z" : "x") : v;
            else if (k.equals("rotation")) nv = String.valueOf((Integer.parseInt(v) + 4 * r) % 16);
            else if (k.equals("shape") && v.matches(".*(north|east|south|west|ascending).*")) nv = railShape(v, r);
            else if (dirIndex(k) >= 0) nv = sides.get(DIRS[(dirIndex(k) - r + 4) % 4]);
            if (!first) out.append(',');
            out.append(k).append('=').append(nv);
            first = false;
        }
        return out.append(']').toString();
    }
}
